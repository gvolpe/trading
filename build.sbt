import Dependencies._

ThisBuild / scalaVersion     := "3.1.1"
ThisBuild / version          := "0.1.0"
ThisBuild / organization     := "dev.profunktor"
ThisBuild / organizationName := "ProfunKtor"

ThisBuild / scalafixDependencies += Libraries.organizeImports

ThisBuild / resolvers += Resolver.sonatypeRepo("snapshots")

Compile / run / fork := true

Global / onChangedBuildSource := ReloadOnSourceChanges
Global / semanticdbEnabled    := true // for metals

val commonSettings = List(
  scalacOptions ++= List("-source:future"),
  scalafmtOnCompile := false, // recommended in Scala 3
  testFrameworks += new TestFramework("weaver.framework.CatsEffect"),
  libraryDependencies ++= Seq(
    Libraries.cats,
    Libraries.catsEffect,
    Libraries.circeCore,
    Libraries.circeParser,
    Libraries.circeExtras,
    Libraries.circeRefined,
    Libraries.cirisCore,
    Libraries.cirisRefined,
    Libraries.fs2Core,
    Libraries.fs2Kafka,
    Libraries.http4sDsl,
    Libraries.http4sMetrics,
    Libraries.http4sServer,
    Libraries.kittens,
    Libraries.monocleCore,
    Libraries.neutronCore,
    Libraries.odin,
    Libraries.redis4catsEffects,
    Libraries.refinedCore,
    Libraries.refinedCats,
    Libraries.monocleLaw       % Test,
    Libraries.scalacheck       % Test,
    Libraries.weaverCats       % Test,
    Libraries.weaverDiscipline % Test,
    Libraries.weaverScalaCheck % Test
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
  .aggregate(lib, domain, core, alerts, feed, forecasts, processor, snapshots, tracing, ws, demo)

lazy val domain = (project in file("modules/domain"))
  .settings(commonSettings: _*)

lazy val lib = (project in file("modules/lib"))
  .settings(commonSettings: _*)
  .dependsOn(domain % "compile->compile;test->test")

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
  .dependsOn(domain % "compile->compile;compile->test")

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
  .dependsOn(domain % "compile->compile;test->test")

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

// integration tests
lazy val it = (project in file("modules/it"))
  .settings(commonSettings: _*)
  .dependsOn(core)
  .dependsOn(domain % "compile->compile;compile->test")
  .dependsOn(forecasts)

// extension demo
lazy val demo = (project in file("modules/x-demo"))
  .settings(commonSettings: _*)
  .dependsOn(core)
  .dependsOn(domain % "compile->compile;compile->test")

addCommandAlias("runLinter", ";scalafixAll --rules OrganizeImports")
