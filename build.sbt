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
  }
  // nativeLinkingOptions += "-static",
)

lazy val root = (project in file("."))
  .enablePlugins(ScalaNativePlugin)
  .settings(
    name := "http4s-cgi",
    libraryDependencies ++= List(
      "org.http4s" %%% "http4s-server" % "1.0.0-M37"
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
    name := "calculator",
    libraryDependencies ++= List(
      "org.http4s" %%% "http4s-circe"  % "1.0.0-M37",
      "org.http4s" %%% "http4s-dsl"    % "1.0.0-M37",
      "io.circe"   %%% "circe-generic" % "0.14.3",
      "co.fs2"     %%% "fs2-io"        % "3.4.0"
    )
  )

lazy val examples = (project in file("examples")).aggregate(hello, streamed, httpbin, calculator)
