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

import cats.effect.IO
import org.http4s.ember.client.EmberClientBuilder

class RsfSuite extends munit.FunSuite with SnapshotSupport:

  val integration = new munit.Tag("integration")

  import cats.effect.unsafe.implicits.global

  val httpClient = EmberClientBuilder
    .default[IO]
    .withTimeout(Timeout)
    .withIdleConnectionTime(IdleTimeout)
    .build

  val csv =
    """|SS;Stage name;Nationality;User name;Real name;Group;Car name;time1;time2;time3;Finish realtime;Penalty;Service penalty;Super rally;Progress;Comment
       |1;Vauxhall Rally of Wales 1993;AD;CANA David;David Canaleta;Group A8;Subaru Impreza GC8 555 GrpA;73.3128;156.619;250.461;2022-10-24 16:37:02;;;;F;good stage
       |1;Vauxhall Rally of Wales 1993;LT;Markas Buteikis;Markas;Group A8;Subaru Impreza GC8 555 GrpA;77.5726;161.94;252.286;2022-10-24 18:12:24;;;;F;
       |1;Vauxhall Rally of Wales 1993;FI;Samppis70;Sami Klemetti;Group A8;Subaru Impreza GC8 555 GrpA;77.767;160.588;252.422;2022-10-23 18:55:54;;;;F;
       |1;Vauxhall Rally of Wales 1993;LT;Denas Kraulys;Denas Kraulys;Group A8;Audi 200 quattro GrpA;76.9378;161.409;254.086;2022-10-29 22:51:48;;;;F;
       |""".stripMargin

  test("parses a CSV file"):
    val obtained = Rsf.parseResults(csv)
    val expected = snapshot(obtained, "rsv-csv")
    assertEquals(obtained, expected)

  def checkRsfName[T](rally: String, expected: String)(using munit.Location): Unit =
    test(s"get rsf $rally name".tag(integration)):
      httpClient
        .use(Rsf.rallyName(_, rally))
        .unsafeRunSync() match
        case Right(name) =>
          assertEquals(name, expected)
        case Left(error) => fail(s"Unable to get rsf rally name: $error")

  checkRsfName("59862", "VRC #9 2023 - Barum Czech Rally Zlin")
  checkRsfName("59247", "Barum Rally Zlin")
