import scala.scalanative.build.{GC, LTO, Mode}

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.10"

lazy val commonSettings = List(
  Compile / nativeConfig ~= {
    _.withMode(Mode.default)
      .withLTO(LTO.default)
      .withGC(GC.default)
  },
  Test / nativeConfig ~= {
    _.withMode(Mode.debug)
      .withLTO(LTO.default)
      .withGC(GC.default)
  },
  nativeLinkingOptions += "-static",
  libraryDependencies ++= List(
    "org.typelevel" %%% "munit-cats-effect" % "2.0.0-M3" % Test
  ),
  testFrameworks += new TestFramework("munit.Framework"),
  resolvers +=
    "Sonatype OSS Snapshots" at "https://s01.oss.sonatype.org/content/repositories/snapshots" // for the snapshot of fs2
)

lazy val root = (project in file("."))
  .settings(commonSettings: _*)
  .enablePlugins(ScalaNativePlugin)
  .settings(
    name := "http4s-cgi",
    libraryDependencies ++= List(
      "org.http4s" %%% "http4s-server" % "1.0.0-M37",
      "co.fs2"     %%% "fs2-io"        % "3.5-b0f71fe-SNAPSHOT"
    )
  )

lazy val hello = (project in file("examples/hello"))
  .enablePlugins(ScalaNativePlugin)
  .dependsOn(root)
  .settings(commonSettings: _*)
  .settings(
    name := "hello",
    libraryDependencies ++= List(
      "org.http4s" %%% "http4s-circe" % "1.0.0-M37",
      "org.http4s" %%% "http4s-dsl"   % "1.0.0-M37"
    )
  )

lazy val httpbin = (project in file("examples/httpbin"))
  .enablePlugins(ScalaNativePlugin)
  .dependsOn(root)
  .settings(commonSettings: _*)
  .settings(
    name := "httpbin",
    libraryDependencies ++= List(
      "org.http4s" %%% "http4s-circe" % "1.0.0-M37",
      "org.http4s" %%% "http4s-dsl"   % "1.0.0-M37"
    )
  )

lazy val streamed = (project in file("examples/streamed"))
  .enablePlugins(ScalaNativePlugin)
  .dependsOn(root)
  .settings(commonSettings: _*)
  .settings(
    name := "streamed",
    libraryDependencies ++= List(
      "org.http4s" %%% "http4s-circe" % "1.0.0-M37",
      "org.http4s" %%% "http4s-dsl"   % "1.0.0-M37"
    )
  )

lazy val calculator = (project in file("examples/calculator"))
  .enablePlugins(ScalaNativePlugin)
  .dependsOn(root)
  .settings(commonSettings: _*)
  .settings(
    name := "calculator",
    libraryDependencies ++= List(
      "org.http4s" %%% "http4s-circe"  % "1.0.0-M37",
      "org.http4s" %%% "http4s-dsl"    % "1.0.0-M37",
      "io.circe"   %%% "circe-generic" % "0.14.3"
    )
  )

lazy val hitcounter = (project in file("examples/hitcounter"))
  .enablePlugins(ScalaNativePlugin)
  .dependsOn(root)
  .settings(commonSettings: _*)
  .settings(
    name := "hitcounter",
    libraryDependencies ++= List(
      "org.http4s" %%% "http4s-circe"  % "1.0.0-M37",
      "org.http4s" %%% "http4s-dsl"    % "1.0.0-M37",
      "io.circe"   %%% "circe-generic" % "0.14.3",
      "co.fs2"     %%% "fs2-io"        % "3.5-b0f71fe-SNAPSHOT"
    )
  )

lazy val websocket = (project in file("examples/websocket"))
  .enablePlugins(ScalaNativePlugin)
  .dependsOn(root)
  .settings(commonSettings: _*)
  .settings(
    name := "websocket",
    libraryDependencies ++= List(
      "org.http4s" %%% "http4s-circe" % "1.0.0-M37",
      "org.http4s" %%% "http4s-dsl"   % "1.0.0-M37"
    )
  )

lazy val `websocket-chat` = (project in file("examples/websocket-chat"))
  .enablePlugins(ScalaNativePlugin)
  .dependsOn(root)
  .settings(commonSettings: _*)
  .settings(
    name := "websocket-chat",
    libraryDependencies ++= List(
      "org.http4s" %%% "http4s-circe"  % "1.0.0-M37",
      "org.http4s" %%% "http4s-dsl"    % "1.0.0-M37",
      "io.circe"   %%% "circe-generic" % "0.14.3",
      "io.circe"   %%% "circe-parser"  % "0.14.3",
      "co.fs2"     %%% "fs2-io"        % "3.5-b0f71fe-SNAPSHOT"
// "io.chrisdavenport" %%% "rediculous"    % "0.4.0-15-6f5aacf-SNAPSHOT"
    ),
    resolvers +=
      "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots" // for the snapshot of rediculous
  )

lazy val examples = (project in file("examples")).aggregate(hello, streamed, httpbin, calculator, hitcounter, websocket, `websocket-chat`)
