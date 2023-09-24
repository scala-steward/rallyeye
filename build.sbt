ThisBuild / scalaVersion := "3.3.1"
ThisBuild / scalafmtOnCompile := true

ThisBuild / organization := "lt.dvim.rallyeye"
ThisBuild / organizationName := "github.com/2m/rallyeye/contributors"
ThisBuild / startYear := Some(2022)
ThisBuild / licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))

ThisBuild / dynverSeparator := "-"

lazy val shared = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("modules/shared"))
  .settings(
    name := "shared",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-core"       % "1.7.4",
      "io.bullet"                   %% "borer-derivation" % "1.11.0"
    ),
    // for borer semi-automatic derivation
    scalacOptions ++= Seq("-Xmax-inlines", "64")
  )
  .enablePlugins(AutomateHeaderPlugin)

def linkerOutputDirectory(v: Attributed[org.scalajs.linker.interface.Report], t: File): Unit = {
  val output = v.get(scalaJSLinkerOutputDirectory.key).getOrElse {
    throw new MessageOnlyException(
      "Linking report was not attributed with output directory. " +
        "Please report this as a Scala.js bug."
    )
  }
  IO.write(t / "linker-output.txt", output.getAbsolutePath.toString)
  ()
}

val publicDev = taskKey[Unit]("output directory for `npm run dev`")
val publicProd = taskKey[Unit]("output directory for `npm run build`")

lazy val frontend = project
  .in(file("modules/frontend"))
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-js"                %%% "scalajs-dom"                 % "2.6.0",
      "org.scala-js"                %%% "scala-js-macrotask-executor" % "1.1.1",
      "com.softwaremill.sttp.tapir" %%% "tapir-sttp-client"           % "1.7.4",
      "com.raquo"                   %%% "laminar"                     % "16.0.0",
      "com.raquo"                   %%% "waypoint"                    % "7.0.0",
      "io.github.cquiroz"           %%% "scala-java-time"             % "2.5.0",
      "io.bullet"                   %%% "borer-core"                  % "1.11.0",
      "io.bullet"                   %%% "borer-derivation"            % "1.11.0",
      "com.lihaoyi"                 %%% "utest"                       % "0.8.1" % "test"
    ),
    // Tell Scala.js that this is an application with a main method
    scalaJSUseMainModuleInitializer := true,

    /* Configure Scala.js to emit modules in the optimal way to
     * connect to Vite's incremental reload.
     * - emit ECMAScript modules
     * - emit as many small modules as possible for classes in the "rallyeye" package
     * - emit as few (large) modules as possible for all other classes
     *   (in particular, for the standard library)
     */
    scalaJSLinkerConfig ~= {
      import org.scalajs.linker.interface.ModuleSplitStyle
      _.withModuleKind(ModuleKind.ESModule)
        .withModuleSplitStyle(
          ModuleSplitStyle.SmallModulesFor(List("rallyeye"))
        )
    },
    publicDev := linkerOutputDirectory((Compile / fastLinkJS).value, target.value),
    publicProd := linkerOutputDirectory((Compile / fullLinkJS).value, target.value),

    // scalably typed
    externalNpm := baseDirectory.value, // Tell ScalablyTyped that we manage `npm install` ourselves

    // build info
    buildInfoKeys := Seq[BuildInfoKey](version, isSnapshot),
    buildInfoPackage := "rallyeye"
  )
  .dependsOn(shared.js)
  .enablePlugins(AutomateHeaderPlugin)
  .enablePlugins(ScalaJSPlugin)
  .enablePlugins(
    (if (!sys.env.get("SCALA_STEWARD").isDefined) Seq(ScalablyTypedConverterExternalNpmPlugin) else Seq.empty): _*
  )
  .enablePlugins(BuildInfoPlugin)

lazy val backend = project
  .in(file("modules/backend"))
  .settings(
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % "1.7.4",
      "com.softwaremill.sttp.tapir" %% "tapir-http4s-client" % "1.7.4",
      "org.http4s"                  %% "http4s-ember-server" % "0.23.23",
      "org.http4s"                  %% "http4s-ember-client" % "0.23.23",
      "io.chrisdavenport"           %% "mules-http4s"        % "0.4.0",
      "io.chrisdavenport"           %% "mules-caffeine"      % "0.7.0",
      "ch.qos.logback"               % "logback-classic"     % "1.4.11",
      "org.scalameta"               %% "munit"               % "1.0.0-M10" % Test,
      "com.softwaremill.diffx"      %% "diffx-munit"         % "0.8.3"     % Test
    ),

    // jib docker image builder
    jibRegistry := "registry.fly.io",
    jibCustomRepositoryPath := Some("rallyeye-data"),
    jibTags += "latest",

    // for diffx assertions in tests
    Test / scalacOptions ++= Seq("-Xmax-inlines", "64")
  )
  .dependsOn(shared.jvm)
  .enablePlugins(AutomateHeaderPlugin)
