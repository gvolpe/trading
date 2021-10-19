import sbt._

object Dependencies {

  object V {
    val cats       = "2.6.1"
    val catsEffect = "3.2.9"
    val circe      = "0.14.1"
    val ciris      = "2.2.0"
    val fs2        = "3.1.5"
    val fs2Kafka   = "2.2.0"
    val http4s     = "1.0.0-M28"
    val kittens    = "3.0.0-M1"
    val monocle    = "3.1.0"
    val neutron    = "0.1.0-SNAPSHOT"
    val redis4cats = "1.0.0"
    val refined    = "0.9.27"

    val scalacheck = "1.15.4"
    val weaver     = "0.7.6"

    val organizeImports = "0.5.0+42-7e4a4f8a-SNAPSHOT"
    val semanticDB      = "4.4.28"
  }

  object Libraries {
    def http4s(artifact: String): ModuleID = "org.http4s" %% s"http4s-$artifact" % V.http4s

    val cats       = "org.typelevel" %% "cats-core"   % V.cats
    val catsEffect = "org.typelevel" %% "cats-effect" % V.catsEffect
    val fs2        = "co.fs2"        %% "fs2-core"    % V.fs2
    val kittens    = "org.typelevel" %% "kittens"     % V.kittens

    val ciris = "is.cir" %% "ciris" % V.ciris

    val circeCore   = "io.circe" %% "circe-core"   % V.circe
    val circeExtras = "io.circe" %% "circe-extras" % V.circe

    val http4sDsl    = http4s("dsl")
    val http4sServer = http4s("ember-server")
    val http4sClient = http4s("ember-client")
    val http4sCirce  = http4s("circe")

    val neutronCore       = "dev.profunktor" %% "neutron-core"       % V.neutron
    val neutronCirce      = "dev.profunktor" %% "neutron-circe"      % V.neutron
    val redis4catsEffects = "dev.profunktor" %% "redis4cats-effects" % V.redis4cats

    val monocleCore = "dev.optics" %% "monocle-core" % V.monocle

    val refinedCore = "eu.timepit" %% "refined" % V.refined

    // test
    val monocleLaw       = "dev.optics"          %% "monocle-law"       % V.monocle
    val scalacheck       = "org.scalacheck"      %% "scalacheck"        % V.scalacheck
    val weaverCats       = "com.disneystreaming" %% "weaver-cats"       % V.weaver
    val weaverDiscipline = "com.disneystreaming" %% "weaver-discipline" % V.weaver
    val weaverScalaCheck = "com.disneystreaming" %% "weaver-scalacheck" % V.weaver

    // only for demo
    val fs2Kafka = "com.github.fd4s" %% "fs2-kafka" % V.fs2Kafka

    // scalafix rules
    val organizeImports = "com.github.liancheng" %% "organize-imports" % V.organizeImports
  }

  object CompilerPlugins {
    val semanticDB = compilerPlugin(
      "org.scalameta" % "semanticdb-scalac" % V.semanticDB cross CrossVersion.full
    )
  }

}
