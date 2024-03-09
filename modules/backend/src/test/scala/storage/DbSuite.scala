/*
 * Copyright 2022 github.com/2m/rallyeye/contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rallyeye
package storage

import java.nio.file.Files
import java.nio.file.Paths
import java.sql.SQLException
import java.time.Instant
import java.time.LocalDate

import scala.collection.immutable.ArraySeq

import cats.effect.IO
import com.softwaremill.diffx.Diff
import com.softwaremill.diffx.munit.DiffxAssertions
import doobie.implicits.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.*
import io.github.iltotore.iron.scalacheck.numeric.given
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalacheck.Prop
import org.scalacheck.ops.*
import rallyeye.shared.RallyKind

class DbSuite extends munit.ScalaCheckSuite with DiffxAssertions with IronDiffxSupport:
  import cats.effect.unsafe.implicits.global

  given Diff[Instant] = Diff[Long].contramap(_.getEpochSecond)
  given Diff[RallyKind] = Diff.derived[RallyKind]
  given Diff[Rally] = Diff.derived[Rally]
  given Diff[Result] = Diff.derived[Result]
  given Diff[SQLException] = Diff[String].contramap(_.getMessage)

  case class RallyWithResults(rally: Rally, results: List[Result])

  given Arbitrary[RallyKind] = Arbitrary:
    Gen.oneOf(ArraySeq.unsafeWrapArray(RallyKind.values))
  given Arbitrary[Rally] = Arbitrary:
    Gen.resultOf(Rally.apply)
  given Arbitrary[Result] = Arbitrary:
    Gen.resultOf(Result.apply)
  given Arbitrary[RallyWithResults] = Arbitrary:
    for
      rally <- arbitrary[Rally]
      stageCount <- Gen.oneOf(1 to 10)
      stageNumbers <- Gen.setOfN(stageCount, arbitrary[Int :| Greater[0]])
      driverCount <- Gen.oneOf(1 to 10)
      driverNames <- Gen.setOfN(driverCount, arbitrary[String])
    yield
      val results =
        for
          stageNumber <- stageNumbers
          driverName <- driverNames
        yield arbitrary[Result].sample.get.copy(
          rallyKind = rally.kind,
          externalId = rally.externalId,
          stageNumber = stageNumber,
          driverPrimaryName = driverName
        )
      RallyWithResults(rally, results.toList)

  val db = FunFixture[Unit](
    setup = test =>
      Files.deleteIfExists(Paths.get(Db.file))
      migrations.use(_ => IO.unit).unsafeRunSync()
    ,
    teardown = _ => ()
  )

  db.test("should insert and select rally") { _ =>
    Prop.forAll { (rally: Rally) =>
      val inserted = Db.insertRally(rally).unsafeRunSync()
      assertEqual(inserted, Right(1))

      val selected = Db.selectRally(rally.kind, rally.externalId).unsafeRunSync()
      assertEqual(selected, Right(Some(rally)))
    }
  }

  db.test("should insert and select results") { _ =>
    Prop.forAll { (rallyWithResults: RallyWithResults) =>
      sql"delete from results".update.run.attemptSql.transact(Db.xa).unsafeRunSync()

      val RallyWithResults(rally, results) = rallyWithResults
      Db.insertRally(rally).unsafeRunSync()

      val inserted = Db
        .insertManyResults(results)
        .unsafeRunSync()
      assertEqual(inserted, Right(results.size))

      val selected = Db.selectResults(rally.kind, rally.externalId).unsafeRunSync()
      assertEquals(selected, Right(results))
    }
  }

  db.test("should insert or update rally") { _ =>
    val rally = arbitrary[Rally].sample.get
    val inserted = Db.insertRally(rally).unsafeRunSync()
    assertEqual(inserted, Right(1))

    val rally2 = rally.copy(name = rally.name + " changed")
    val inserted2 = Db.insertRally(rally2).unsafeRunSync()
    assertEqual(inserted2, Right(1))

    val selected = Db.selectRally(rally.kind, rally.externalId).unsafeRunSync()
    assertEqual(selected, Right(Some(rally2)))
  }

  db.test("should insert or update results") { _ =>
    val RallyWithResults(rally, results) = arbitrary[RallyWithResults].sample.get
    val result = results.head

    Db.insertRally(rally).unsafeRunSync()

    val inserted = Db.insertManyResults(List(result)).unsafeRunSync()
    assertEqual(inserted, Right(1))

    val result2 = result.copy(stageName = result.stageName + " changed")
    val inserted2 = Db.insertManyResults(List(result2)).unsafeRunSync()
    assertEqual(inserted2, Right(1))

    val selected = Db.selectResults(rally.kind, rally.externalId).unsafeRunSync()
    assertEqual(selected, Right(List(result2)))
  }

  db.test("should select all rallies") { _ =>
    val rally1 = arbitrary[Rally].sample.get
    Db.insertRally(rally1).unsafeRunSync()

    val rally2 = arbitrary[Rally].sample.get
    Db.insertRally(rally2).unsafeRunSync()

    val selected = Db.selectRallies().unsafeRunSync()
    assertEqual(selected.map(_.toSet), Right(Set((rally1.kind, rally1.externalId), (rally2.kind, rally2.externalId))))
  }

  db.test("should delete rally and results") { _ =>
    val RallyWithResults(rally, results) = arbitrary[RallyWithResults].sample.get
    Db.insertRally(rally).unsafeRunSync()
    Db.insertManyResults(results).unsafeRunSync()

    val selectedRally = Db.selectRally(rally.kind, rally.externalId).unsafeRunSync()
    assertEqual(selectedRally, Right(Some(rally)))

    val selectedResults = Db.selectResults(rally.kind, rally.externalId).unsafeRunSync()
    assertEquals(selectedResults, Right(results))

    Db.deleteResultsAndRally(rally.kind, rally.externalId).unsafeRunSync()

    val deletedRally = Db.selectRally(rally.kind, rally.externalId).unsafeRunSync()
    assertEqual(deletedRally, Right(None))

    val deletedResults = Db.selectResults(rally.kind, rally.externalId).unsafeRunSync()
    assertEquals(deletedResults, Right(Nil))
  }

  db.test("should find rallies by championship") { _ =>
    val rally1 = arbitrary[Rally].sample.get.copy(championship = Some("champ1"))
    Db.insertRally(rally1).unsafeRunSync()

    val rally2 = arbitrary[Rally].sample.get.copy(championship = Some("champ2"))
    Db.insertRally(rally2).unsafeRunSync()

    given kind: RallyKind = rally1.kind
    val selected = Db.findRallies("champ1", None).unsafeRunSync()
    assertEqual(selected, Right(List(rally1)))
  }

  db.test("should not find rallies of different kind") { _ =>
    val rally1 = arbitrary[Rally].sample.get.copy(championship = Some("champ1"))
    Db.insertRally(rally1).unsafeRunSync()

    given kind: RallyKind = RallyKind.values.find(_ != rally1.kind).get
    val selected = Db.findRallies("champ1", None).unsafeRunSync()
    assertEqual(selected, Right(List.empty))
  }

  db.test("should find rallies by championship and year") { _ =>
    val rally1 =
      arbitrary[Rally].sample.get.copy(championship = Some("champ1"), start = LocalDate.parse("2022-01-01"))
    Db.insertRally(rally1).unsafeRunSync()

    val rally2 = arbitrary[Rally].sample.get.copy(championship = Some("champ1"), start = LocalDate.parse("2023-01-01"))
    Db.insertRally(rally2).unsafeRunSync()

    given kind: RallyKind = rally1.kind
    val selected = Db.findRallies("champ1", Some(2022)).unsafeRunSync()
    assertEqual(selected, Right(List(rally1)))
  }
