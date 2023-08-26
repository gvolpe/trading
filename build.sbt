import Dependencies._
import sbtwelcome._

ThisBuild / scalaVersion     := "3.3.0"
ThisBuild / version          := "0.1.0"
ThisBuild / organization     := "dev.profunktor"
ThisBuild / organizationName := "ProfunKtor"

ThisBuild / evictionErrorLevel := Level.Warn
ThisBuild / scalafixDependencies += Libraries.organizeImports

ThisBuild / resolvers := Resolver.sonatypeOssRepos("snapshots")

ThisBuild / pushRemoteCacheTo := Some(MavenCache("local-cache", file("tmp/remote-cache")))

Compile / run / fork := true

Global / onChangedBuildSource := ReloadOnSourceChanges
Global / semanticdbEnabled    := true // for metals

lazy val copyJsFileTask = TaskKey[Unit]("copyJsFileTask")

def fedaLogo(scalaVersion: String, project: String) =
  s"""
   |${scala.Console.YELLOW}░█▀▀░█░█░█▀█░█▀▀░▀█▀░▀█▀░█▀█░█▀█░█▀█░█░░░░░█▀▀░█░█░█▀▀░█▀█░▀█▀░░░░░█▀▄░█▀▄░▀█▀░█░█░█▀▀░█▀█░░░█▀█░█▀▄░█▀▀░█░█░▀█▀░▀█▀░█▀▀░█▀▀░▀█▀░█░█░█▀▄░█▀▀
   |${scala.Console.RED}░█▀▀░█░█░█░█░█░░░░█░░░█░░█░█░█░█░█▀█░█░░░░░█▀▀░▀▄▀░█▀▀░█░█░░█░░▄▄▄░█░█░█▀▄░░█░░▀▄▀░█▀▀░█░█░░░█▀█░█▀▄░█░░░█▀█░░█░░░█░░█▀▀░█░░░░█░░█░█░█▀▄░█▀▀
   |${scala.Console.CYAN}░▀░░░▀▀▀░▀░▀░▀▀▀░░▀░░▀▀▀░▀▀▀░▀░▀░▀░▀░▀▀▀░░░▀▀▀░░▀░░▀▀▀░▀░▀░░▀░░░░░░▀▀░░▀░▀░▀▀▀░░▀░░▀▀▀░▀░▀░░░▀░▀░▀░▀░▀▀▀░▀░▀░▀▀▀░░▀░░▀▀▀░▀▀▀░░▀░░▀▀▀░▀░▀░▀▀▀
   |
   |Powered by ${scala.Console.YELLOW}Scala ${scalaVersion}${scala.Console.RESET}
   |
   |Get the book at: ${scala.Console.YELLOW} https://leanpub.com/feda${scala.Console.RESET}
   |
   |Project: ${scala.Console.CYAN}$project ${scala.Console.RESET}
  """.stripMargin

logo := fedaLogo(scalaVersion.value, "root")

usefulTasks := List(
  UsefulTask("lint", "Run scalafmtAll and scalafix OrganizeImports rule"),
  UsefulTask("smoke", "Run smoke tests (smokey)")
)

val commonSettings = List(
  scalafmtOnCompile := false, // recommended in Scala 3
  logo              := fedaLogo(scalaVersion.value, name.value),
  testFrameworks += new TestFramework("weaver.framework.CatsEffect"),
  libraryDependencies ++= List(
    CompilerPlugins.zerowaste,
    Libraries.cats.value,
    Libraries.catsEffect.value,
    Libraries.circeCore.value,
    Libraries.circeParser.value,
    Libraries.cirisCore.value,
    Libraries.cirisRefined.value,
    Libraries.fs2Core.value,
    Libraries.kittens.value,
    Libraries.ip4sCore.value,
    Libraries.monocleCore.value,
    Libraries.catsLaws         % Test,
    Libraries.monocleLaw       % Test,
    Libraries.scalacheck       % Test,
    Libraries.weaverCats       % Test,
    Libraries.weaverDiscipline % Test,
    Libraries.weaverScalaCheck % Test
  )
)

val commonJvmSettings = List(
  libraryDependencies ++= List(
    Libraries.fs2Kafka,
    Libraries.http4sDsl,
    Libraries.http4sMetrics,
    Libraries.http4sServer,
    Libraries.ironCore.value,
    Libraries.ironCats.value,
    Libraries.ironCirce.value,
    Libraries.neutronCore,
    Libraries.odin,
    Libraries.redis4catsEffects
  )
)

def dockerSettings(name: String) = List(
  Docker / packageName := s"trading-$name",
  dockerBaseImage      := "jdk17-curl:latest",
  dockerExposedPorts ++= List(8080),
  makeBatScripts     := Nil,
  dockerUpdateLatest := true
)

lazy val root = (project in file("."))
  .settings(
    name := "trading-app"
  )
  .aggregate(lib, domain.js, domain.jvm, core, alerts, feed, forecasts, processor, snapshots, tracing, ws, demo)

lazy val domain = crossProject(JSPlatform, JVMPlatform)
  .in(file("modules/domain"))
  .settings(commonSettings: _*)
  .jvmSettings(commonJvmSettings)
  .jsSettings(
    test          := {},
    scalacOptions := List("-scalajs")
  )

lazy val lib = (project in file("modules/lib"))
  .settings(commonSettings: _*)
  .dependsOn(domain.jvm % "compile->compile;test->test")

lazy val core = (project in file("modules/core"))
  .settings(commonSettings: _*)
  .dependsOn(lib)

lazy val alerts = (project in file("modules/alerts"))
  .enablePlugins(DockerPlugin)
  .enablePlugins(AshScriptPlugin)
  .settings(commonSettings: _*)
  .settings(dockerSettings("alerts"))
  .dependsOn(core)

lazy val feed = (project in file("modules/feed"))
  .enablePlugins(DockerPlugin)
  .enablePlugins(AshScriptPlugin)
  .settings(commonSettings: _*)
  .settings(dockerSettings("feed"))
  .settings(
    libraryDependencies += Libraries.scalacheck
  )
  .dependsOn(core)
  .dependsOn(domain.jvm % "compile->compile;compile->test")

lazy val forecasts = (project in file("modules/forecasts"))
  .enablePlugins(DockerPlugin)
  .enablePlugins(AshScriptPlugin)
  .settings(commonSettings: _*)
  .settings(dockerSettings("forecasts"))
  .settings(
    libraryDependencies ++= List(
      Libraries.doobieH2,
      Libraries.flyway
    )
  )
  .dependsOn(core)

lazy val snapshots = (project in file("modules/snapshots"))
  .enablePlugins(DockerPlugin)
  .enablePlugins(AshScriptPlugin)
  .settings(commonSettings: _*)
  .settings(dockerSettings("snapshots"))
  .dependsOn(core)
  .dependsOn(domain.jvm % "compile->compile;test->test")

lazy val processor = (project in file("modules/processor"))
  .enablePlugins(DockerPlugin)
  .enablePlugins(AshScriptPlugin)
  .settings(commonSettings: _*)
  .settings(dockerSettings("processor"))
  .dependsOn(core)

lazy val tracing = (project in file("modules/tracing"))
  .enablePlugins(DockerPlugin)
  .enablePlugins(AshScriptPlugin)
  .settings(commonSettings: _*)
  .settings(dockerSettings("tracing"))
  .settings(
    libraryDependencies ++= List(
      Libraries.http4sCirce,
      Libraries.natchezCore,
      Libraries.natchezHoneycomb
    )
  )
  .dependsOn(core)
  .dependsOn(domain.jvm % "compile->compile;test->test")

lazy val ws = (project in file("modules/ws-server"))
  .enablePlugins(DockerPlugin)
  .enablePlugins(AshScriptPlugin)
  .settings(commonSettings: _*)
  .settings(dockerSettings("ws"))
  .settings(
    libraryDependencies ++= List(
      Libraries.http4sCirce
    )
  )
  .dependsOn(core)

lazy val webapp = (project in file("modules/ws-client"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
    scalafmtOnCompile := false,
    libraryDependencies ++= List(
      Libraries.circeCore.value,
      Libraries.circeParser.value,
      Libraries.circeRefined.value,
      Libraries.monocleCore.value,
      Libraries.refinedCore.value,
      Libraries.refinedCats.value,
      Libraries.scalajsTime.value,
      Libraries.tyrian.value,
      Libraries.tyrianIO.value
    ),
    logo := fedaLogo(scalaVersion.value, name.value)
  )
  .dependsOn(domain.js)

// integration tests
lazy val it = (project in file("modules/it"))
  .settings(commonSettings: _*)
  .dependsOn(core)
  .dependsOn(domain.jvm % "compile->compile;compile->test")
  .dependsOn(forecasts)

// extension qa smoke tests
lazy val smokey = (project in file("modules/x-qa"))
  .settings(commonSettings: _*)
  .dependsOn(core, domain.jvm)
  .settings(
    libraryDependencies ++= List(
      Libraries.http4sJdkWs
    )
  )

// extension demo
lazy val demo = (project in file("modules/x-demo"))
  .settings(commonSettings: _*)
  .dependsOn(core, forecasts, tracing)
  .dependsOn(domain.jvm % "compile->compile;compile->test")
  .settings(
    libraryDependencies ++= List(
      Libraries.circeRefined.value,
      Libraries.doobiePg,
      Libraries.natchezHttp4s,
      Libraries.refinedCore.value,
      Libraries.refinedCats.value
    )
  )

addCommandAlias("lint", ";scalafmtAll ;scalafixAll --rules OrganizeImports")
addCommandAlias("smoke", "smokey/test")
