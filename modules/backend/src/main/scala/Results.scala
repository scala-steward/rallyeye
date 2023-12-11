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

import java.time.Instant

import scala.collection.MapView
import scala.util.chaining.*

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.*
import rallyeye.shared.*
import rallyeye.storage.Rally
import rallyeye.storage.RallyKind

extension (kind: RallyKind)
  def link(rally: Rally) =
    rally.kind match
      case RallyKind.Rsf =>
        s"https://www.rallysimfans.hu/rbr/rally_online.php?centerbox=rally_list_details.php&rally_id=${rally.externalId}"
      case RallyKind.PressAuto =>
        s"https://raceadmin.eu/pr${rally.externalId}/pr${rally.externalId}/results/overall/all"
      case _ => ""

case class Entry(
    stageNumber: Int :| Greater[0],
    stageName: String,
    country: String,
    userName: String,
    realName: String,
    group: String,
    car: String,
    split1Time: Option[BigDecimal] = None,
    split2Time: Option[BigDecimal] = None,
    stageTimeMs: Int :| GreaterEqual[0],
    finishRealtime: Option[Instant] = None,
    penaltyInsideStageMs: Int :| GreaterEqual[0],
    penaltyOutsideStageMs: Int :| GreaterEqual[0],
    superRally: Boolean,
    finished: Boolean,
    comment: String,
    nominal: Boolean = false
)

case class TimeResult(
    stageNumber: Int,
    stageName: String,
    country: String,
    userName: String,
    realName: String,
    stageTimeMs: Int,
    overallTimeMs: Int,
    penaltyInsideStageMs: Int,
    penaltyOutsideStageMs: Int,
    superRally: Boolean,
    finished: Boolean,
    comment: String,
    nominal: Boolean
)

case class PositionResult(
    stageNumber: Int,
    country: String,
    userName: String,
    realName: String,
    stagePosition: Int,
    overallPosition: Int,
    stageTimeMs: Int,
    overallTimeMs: Int,
    penaltyInsideStageMs: Int,
    penaltyOutsideStageMs: Int,
    superRally: Boolean,
    rallyFinished: Boolean,
    comment: String,
    nominal: Boolean
)

def stages(entries: List[Entry]) =
  entries.map(e => Stage(e.stageNumber, e.stageName)).distinct.sortBy(_.number)

def results(entries: List[Entry]) =
  val withOverall = entries
    .groupBy(_.userName)
    .view
    .mapValues { results =>
      val overallTimes =
        results.scanLeft(0)((sofar, entry) => sofar + entry.stageTimeMs + entry.penaltyOutsideStageMs)
      results
        .zip(overallTimes.drop(1))
        .map((e, overall) =>
          TimeResult(
            e.stageNumber,
            e.stageName,
            e.country,
            e.userName,
            e.realName,
            e.stageTimeMs,
            overall,
            e.penaltyInsideStageMs,
            e.penaltyOutsideStageMs,
            e.superRally,
            e.finished,
            e.comment,
            e.nominal
          )
        )
    }
    .values
    .flatten

  val retired = withOverall.filterNot(_.finished).map(_.userName).toSet

  withOverall.groupBy(r => Stage(r.stageNumber, r.stageName)).view.mapValues { results =>
    val stageResults = results.toList.filter(_.finished).sortBy(_.stageTimeMs)
    val overallResults = results.toList.filter(_.finished).sortBy(_.overallTimeMs)
    overallResults.zipWithIndex.map { (result, overall) =>
      PositionResult(
        result.stageNumber,
        result.country,
        result.userName,
        result.realName,
        stageResults.indexOf(result) + 1,
        overall + 1,
        result.stageTimeMs,
        result.overallTimeMs,
        result.penaltyInsideStageMs,
        result.penaltyOutsideStageMs,
        result.superRally,
        !retired.contains(result.userName),
        result.comment,
        result.nominal
      )
    }
  }

def drivers(results: MapView[Stage, List[PositionResult]]) =
  results
    .flatMap((stage, positionResults) =>
      positionResults.map(r =>
        DriverResults(
          Driver(r.country, r.userName, r.realName),
          List(
            DriverResult(
              stage.number,
              r.stagePosition,
              r.overallPosition,
              r.stageTimeMs,
              r.overallTimeMs,
              r.penaltyInsideStageMs,
              r.penaltyOutsideStageMs,
              r.superRally,
              r.rallyFinished,
              r.comment,
              r.nominal
            )
          )
        )
      )
    )
    .groupBy(_.driver.userName)
    .map((_, results) =>
      DriverResults(
        results.head.driver,
        results.flatMap(_.results).toList.sortBy(_.stageNumber)
      )
    )
    .toList
    .sortBy(_.driver.userName)

def rallyData(rally: Rally, entries: List[Entry]) =
  val groupResults = entries.groupBy(_.group).map { case (group, entries) =>
    GroupResults(group, results(entries) pipe drivers)
  }
  val carResults = entries.groupBy(e => (e.group, e.car)).map { case ((group, car), entries) =>
    CarResults(car, group, results(entries) pipe drivers)
  }

  RallyData(
    rally.externalId,
    rally.name,
    rally.kind.link(rally),
    rally.retrievedAt,
    stages(entries),
    results(entries) pipe drivers,
    groupResults.toList,
    carResults.toList
  )
