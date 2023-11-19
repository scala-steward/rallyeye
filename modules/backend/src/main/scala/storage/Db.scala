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

import cats.*
import cats.effect.*
import cats.implicits.*
import doobie.*
import doobie.implicits.*

object Db:
  case class Config(
      url: String,
      migrationsTable: String,
      migrationsLocations: List[String]
  )

  val file = sys.env.get("RALLYEYE_DB_PATH").map(_ + "/").getOrElse("") + "rallyeye.db"

  val config = Config(
    url = s"jdbc:sqlite:$file",
    migrationsTable = "flyway",
    migrationsLocations = List("db")
  )

  val xa =
    val transactor = Transactor.fromDriverManager[IO](
      driver = "org.sqlite.JDBC",
      url = config.url,
      logHandler = None
    )
    Transactor.before.modify(transactor, sql"PRAGMA foreign_keys = 1".update.run *> _)

  def insertRally(rally: Rally) =
    sql"insert or replace into rally (kind, external_id, name, retrieved_at) values ($rally)".update.run.attemptSql
      .transact(xa)

  def selectRally(kind: RallyKind, externalId: String) =
    sql"select kind, external_id, name, retrieved_at from rally where kind = $kind and external_id = $externalId"
      .query[Rally]
      .option
      .attemptSql
      .transact(xa)

  def insertManyResults(results: List[Result]) =
    val sql = """|insert or replace into results (
                 |  rally_kind,
                 |  rally_external_id,
                 |  stage_number,
                 |  stage_name,
                 |  driver_country,
                 |  driver_primary_name,
                 |  driver_secondary_name,
                 |  codriver_country,
                 |  codriver_primary_name,
                 |  codriver_secondary_name,
                 |  `group`,
                 |  car,
                 |  stage_time_ms,
                 |  super_rally,
                 |  finished,
                 |  comment,
                 |  nominal
                 |) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".stripMargin
    Update[Result](sql)
      .updateMany(results)
      .attemptSql
      .transact(xa)

  def selectResults(kind: RallyKind, externalId: String) =
    sql"""|select
          |  rally_kind,
          |  rally_external_id,
          |  stage_number,
          |  stage_name,
          |  driver_country,
          |  driver_primary_name,
          |  driver_secondary_name,
          |  codriver_country,
          |  codriver_primary_name,
          |  codriver_secondary_name,
          |  `group`,
          |  car,
          |  stage_time_ms,
          |  super_rally,
          |  finished,
          |  comment,
          |  nominal
          |from results where rally_kind = $kind and rally_external_id = $externalId""".stripMargin
      .query[Result]
      .to[List]
      .attemptSql
      .transact(xa)
