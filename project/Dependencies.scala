import sbt._

object Dependencies {

  object V {
    val cats       = "2.7.0"
    val catsEffect = "3.3.3"
    val circe      = "0.14.1"
    val ciris      = "2.2.0"
    val fs2Core    = "3.2.4"
    val fs2Kafka   = "2.3.0"
    val http4s     = "1.0.0-M30"
    val kittens    = "3.0.0-M1"
    val monocle    = "3.1.0"
    val natchez    = "0.1.4"
    val neutron    = "0.2.0+41-af6d6bc4-SNAPSHOT"
    val odin       = "0.13.0"
    val redis4cats = "1.0.0+62-618c9d75-SNAPSHOT"
    val refined    = "0.9.27"

    val scalacheck = "1.15.4"
    val weaver     = "0.7.9"

    val organizeImports = "0.6.0"
  }

  object Libraries {
    def circe(artifact: String): ModuleID  = "io.circe"   %% s"circe-$artifact"  % V.circe
    def http4s(artifact: String): ModuleID = "org.http4s" %% s"http4s-$artifact" % V.http4s

    val cats       = "org.typelevel" %% "cats-core"   % V.cats
    val catsEffect = "org.typelevel" %% "cats-effect" % V.catsEffect
    val fs2Core    = "co.fs2"        %% "fs2-core"    % V.fs2Core
    val kittens    = "org.typelevel" %% "kittens"     % V.kittens

    val ciris = "is.cir" %% "ciris" % V.ciris

    val circeCore   = circe("core")
    val circeParser = circe("parser")
    val circeExtras = circe("extras")

    val http4sDsl     = http4s("dsl")
    val http4sServer  = http4s("ember-server")
    val http4sClient  = http4s("ember-client")
    val http4sCirce   = http4s("circe")
    val http4sMetrics = http4s("prometheus-metrics")

    val natchezCore      = "org.tpolecat" %% "natchez-core"      % V.natchez
    val natchezHoneycomb = "org.tpolecat" %% "natchez-honeycomb" % V.natchez

    val neutronCore       = "dev.profunktor" %% "neutron-core"       % V.neutron
    val redis4catsEffects = "dev.profunktor" %% "redis4cats-effects" % V.redis4cats

    val monocleCore = "dev.optics" %% "monocle-core" % V.monocle

    val odin = "com.github.valskalla" %% "odin-core" % V.odin

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

}
