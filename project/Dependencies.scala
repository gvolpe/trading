import sbt._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._

object Dependencies {

  object V {
    val cats          = "2.10.0"
    val catsEffect    = "3.4.11"
    val circe         = "0.14.6"
    val ciris         = "3.2.0"
    val doobie        = "1.0.0-RC4"
    val flyway        = "8.5.13"
    val fs2Core       = "3.9.1"
    val fs2Kafka      = "3.0.1"
    val http4s        = "1.0.0-M39"
    val http4sMetrics = "1.0.0-M38"
    val http4sWs      = "1.0.0-M3"
    val ip4s          = "3.3.0"
    val iron          = "2.2.1"
    val kittens       = "3.0.0"
    val monocle       = "3.2.0"
    val natchez       = "0.3.3"
    val natchezHttp4s = "0.5.0"
    val neutron       = "0.7.2"
    val odin          = "0.13.0"
    val redis4cats    = "1.4.3"
    val refined       = "0.11.0"

    val scalajsTime = "2.4.0"
    val tyrian      = "0.6.1"

    val scalacheck = "1.17.0"
    val weaver     = "0.8.2"

    val organizeImports = "0.6.0"
    val zerowaste       = "0.2.12"
  }

  object Libraries {
    def circe(artifact: String) = Def.setting("io.circe" %%% s"circe-$artifact" % V.circe)

    def http4s(artifact: String): ModuleID = "org.http4s" %% s"http4s-$artifact" % V.http4s

    val cats       = Def.setting("org.typelevel" %%% "cats-core" % V.cats)
    val catsEffect = Def.setting("org.typelevel" %%% "cats-effect" % V.catsEffect)
    val fs2Core    = Def.setting("co.fs2" %%% "fs2-core" % V.fs2Core)
    val kittens    = Def.setting("org.typelevel" %%% "kittens" % V.kittens)

    val cirisCore    = Def.setting("is.cir" %%% "ciris" % V.ciris)
    val cirisRefined = Def.setting("is.cir" %%% "ciris-refined" % V.ciris)

    val circeCore    = circe("core")
    val circeParser  = circe("parser")
    val circeRefined = circe("refined")

    val doobieH2 = "org.tpolecat" %% "doobie-h2"       % V.doobie
    val doobiePg = "org.tpolecat" %% "doobie-postgres" % V.doobie
    val flyway   = "org.flywaydb"  % "flyway-core"     % V.flyway

    val http4sDsl    = http4s("dsl")
    val http4sServer = http4s("ember-server")
    val http4sClient = http4s("ember-client")
    val http4sCirce  = http4s("circe")

    val http4sJdkWs   = "org.http4s" %% "http4s-jdk-http-client"    % V.http4sWs
    val http4sMetrics = "org.http4s" %% "http4s-prometheus-metrics" % V.http4sMetrics

    val ironCore  = Def.setting("io.github.iltotore" %%% "iron" % V.iron)
    val ironCats  = Def.setting("io.github.iltotore" %%% "iron-cats" % V.iron)
    val ironCirce = Def.setting("io.github.iltotore" %%% "iron-circe" % V.iron)

    val ip4sCore = Def.setting("com.comcast" %%% "ip4s-core" % V.ip4s)

    val natchezCore      = "org.tpolecat" %% "natchez-core"      % V.natchez
    val natchezHoneycomb = "org.tpolecat" %% "natchez-honeycomb" % V.natchez
    val natchezHttp4s    = "org.tpolecat" %% "natchez-http4s"    % V.natchezHttp4s

    val neutronCore       = "dev.profunktor" %% "neutron-core"       % V.neutron
    val redis4catsEffects = "dev.profunktor" %% "redis4cats-effects" % V.redis4cats

    val monocleCore = Def.setting("dev.optics" %%% "monocle-core" % V.monocle)

    val odin = "com.github.valskalla" %% "odin-core" % V.odin

    // webapp
    val scalajsTime = Def.setting("io.github.cquiroz" %%% "scala-java-time" % V.scalajsTime)
    val tyrian      = Def.setting("io.indigoengine" %%% "tyrian" % V.tyrian)
    val tyrianIO    = Def.setting("io.indigoengine" %%% "tyrian-io" % V.tyrian)

    // test
    val catsLaws         = "org.typelevel"       %% "cats-laws"         % V.cats
    val monocleLaw       = "dev.optics"          %% "monocle-law"       % V.monocle
    val scalacheck       = "org.scalacheck"      %% "scalacheck"        % V.scalacheck
    val weaverCats       = "com.disneystreaming" %% "weaver-cats"       % V.weaver
    val weaverDiscipline = "com.disneystreaming" %% "weaver-discipline" % V.weaver
    val weaverScalaCheck = "com.disneystreaming" %% "weaver-scalacheck" % V.weaver

    // only for demo
    val fs2Kafka    = "com.github.fd4s" %% "fs2-kafka" % V.fs2Kafka
    val refinedCore = Def.setting("eu.timepit" %%% "refined" % V.refined)
    val refinedCats = Def.setting("eu.timepit" %%% "refined-cats" % V.refined)

    // scalafix rules
    val organizeImports = "com.github.liancheng" %% "organize-imports" % V.organizeImports
  }

  object CompilerPlugins {
    val zerowaste = compilerPlugin("com.github.ghik" % "zerowaste" % V.zerowaste cross CrossVersion.full)
  }

}
