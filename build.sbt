import Dependencies._

ThisBuild / scalaVersion := "2.13.5"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "dev.profunktor"
ThisBuild / organizationName := "ProfunKtor"

ThisBuild / scalafixDependencies += Libraries.organizeImports

resolvers += Resolver.sonatypeRepo("snapshots")

Compile / run / fork := true
Global / semanticdbEnabled := true

val commonSettings = List(
  scalacOptions ++= List("-Ymacro-annotations", "-Yrangepos", "-Wconf:cat=unused:info"),
  scalafmtOnCompile := true,
  testFrameworks += new TestFramework("weaver.framework.CatsEffect"),
  libraryDependencies ++= Seq(
    CompilerPlugins.betterMonadicFor,
    CompilerPlugins.betterToString,
    CompilerPlugins.kindProjector,
    CompilerPlugins.semanticDB,
    Libraries.cats,
    Libraries.catsEffect,
    Libraries.derevoCats,
    Libraries.derevoCirceMagnolia,
    Libraries.derevoTagless,
    Libraries.fs2,
    Libraries.fs2Kafka,
    Libraries.monocleCore,
    Libraries.monocleMacro,
    Libraries.neutronCore,
    Libraries.neutronCirce,
    Libraries.newtype,
    Libraries.redis4catsEffects,
    Libraries.refinedCore,
    Libraries.refinedCats,
    Libraries.monocleLaw       % Test,
    Libraries.weaverCats       % Test,
    Libraries.weaverDiscipline % Test,
    Libraries.weaverScalaCheck % Test
  )
)

lazy val root = (project in file("."))
  .settings(
    name := "trading-app"
  )
  .aggregate(lib, domain, core, alerts, feed, processor, snapshots, wsClient, wsServer, demo)

lazy val domain = (project in file("modules/domain"))
  .settings(commonSettings: _*)

lazy val lib = (project in file("modules/lib"))
  .settings(commonSettings: _*)
  .dependsOn(domain % "compile->compile;test->test")

lazy val core = (project in file("modules/core"))
  .settings(commonSettings: _*)
  .dependsOn(lib)

lazy val alerts = (project in file("modules/alerts"))
  .settings(commonSettings: _*)
  .dependsOn(core)

lazy val feed = (project in file("modules/feed"))
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies += Libraries.scalacheck
  )
  .dependsOn(core)
  .dependsOn(domain % "compile->compile;compile->test")

lazy val snapshots = (project in file("modules/snapshots"))
  .settings(commonSettings: _*)
  .dependsOn(core)

lazy val processor = (project in file("modules/processor"))
  .settings(commonSettings: _*)
  .dependsOn(core)

lazy val wsClient = (project in file("modules/ws-client"))
  .settings(commonSettings: _*)
  .dependsOn(core)

lazy val wsServer = (project in file("modules/ws-server"))
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= List(
      Libraries.http4sDsl,
      Libraries.http4sCirce,
      Libraries.http4sServer
    )
  )
  .dependsOn(core)

// extension demo
lazy val demo = (project in file("modules/x-demo"))
  .settings(commonSettings: _*)
  .dependsOn(core)
  .dependsOn(domain % "compile->compile;compile->test")

addCommandAlias("runLinter", ";scalafixAll --rules OrganizeImports")
