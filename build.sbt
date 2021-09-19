import Dependencies._

ThisBuild / scalaVersion := "2.13.5"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "dev.profunktor"
ThisBuild / organizationName := "ProfunKtor"

ThisBuild / scalafixDependencies += Libraries.organizeImports

resolvers += Resolver.sonatypeRepo("snapshots")

Compile / run / fork := true

val commonSettings = List(
  scalacOptions ++= List("-Ymacro-annotations", "-Yrangepos", "-Wconf:cat=unused:info"),
  scalafmtOnCompile := true,
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
    Libraries.refinedCats
  )
)

lazy val root = (project in file("."))
  .settings(
    name := "trading-app"
  )
  .aggregate(core, domain, alerts, feed, snapshots, trading, wsClient)

lazy val domain = (project in file("modules/domain"))
  .settings(commonSettings: _*)

lazy val core = (project in file("modules/core"))
  .settings(commonSettings: _*)
  .dependsOn(domain)

lazy val alerts = (project in file("modules/alerts"))
  .settings(commonSettings: _*)
  .dependsOn(core)

lazy val feed = (project in file("modules/feed"))
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies += Libraries.scalacheck
  )
  .dependsOn(core)

lazy val snapshots = (project in file("modules/snapshots"))
  .settings(commonSettings: _*)
  .dependsOn(core)

lazy val trading = (project in file("modules/trading"))
  .settings(commonSettings: _*)
  .dependsOn(core)

lazy val wsClient = (project in file("modules/ws-client"))
  .settings(commonSettings: _*)
  .dependsOn(core)

addCommandAlias("runLinter", ";scalafixAll --rules OrganizeImports")
