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

package rallyeye.shared

import TapirJsonBorer._
import io.bullet.borer.Codec
import io.bullet.borer.derivation.MapBasedCodecs._
import sttp.tapir._
import sttp.tapir.generic.auto._

val dataEndpoint = endpoint
  .in("data" / path[Int])
  .out(jsonBody[RallyData])

case class Stage(number: Int, name: String)

case class PositionResult(
    stageNumber: Int,
    userName: String,
    stagePosition: Int,
    overallPosition: Int,
    stageTime: BigDecimal,
    overallTime: BigDecimal,
    superRally: Boolean,
    rallyFinished: Boolean,
    comment: String
)

case class DriverResults(name: String, results: List[PositionResult])

case class GroupResults(
    group: String,
    results: List[DriverResults]
)

case class CarResults(
    car: String,
    group: String,
    results: List[DriverResults]
)

case class RallyData(
    name: String,
    stages: List[Stage],
    allResults: List[DriverResults],
    groupResults: List[GroupResults],
    carResults: List[CarResults]
)

given Codec[Stage] = deriveCodec[Stage]
given Codec[PositionResult] = deriveCodec[PositionResult]
given Codec[DriverResults] = deriveCodec[DriverResults]
given Codec[GroupResults] = deriveCodec[GroupResults]
given Codec[CarResults] = deriveCodec[CarResults]
given Codec[RallyData] = deriveCodec[RallyData]
