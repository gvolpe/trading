import sbt._

object Dependencies {

  object V {
    val cats       = "2.6.1"
    val catsEffect = "3.1.1"
    val derevo     = "0.12.5"
    val fs2        = "3.0.4"
    val monocle    = "3.0.0-RC2"
    val newtype    = "0.4.4"
    val refined    = "0.9.26"

    val betterMonadicFor = "0.3.1"
    val kindProjector    = "0.13.0"
    val organizeImports  = "0.5.0"
    val semanticDB       = "4.4.20"
  }

  object Libraries {
    def derevo(artifact: String): ModuleID = "tf.tofu" %% s"derevo-$artifact" % V.derevo

    val cats       = "org.typelevel" %% "cats-core"   % V.cats
    val catsEffect = "org.typelevel" %% "cats-effect" % V.catsEffect
    val fs2        = "co.fs2"        %% "fs2-core"    % V.fs2

    val derevoCats          = derevo("cats")
    val derevoCirceMagnolia = derevo("circe-magnolia")
    val derevoTagless       = derevo("cats-tagless")

    val refinedCore = "eu.timepit" %% "refined"      % V.refined
    val refinedCats = "eu.timepit" %% "refined-cats" % V.refined

    val newtype = "io.estatico" %% "newtype" % V.newtype

    val monocleCore  = "dev.optics" %% "monocle-core"  % V.monocle
    val monocleMacro = "dev.optics" %% "monocle-macro" % V.monocle

    // Scalafix rules
    val organizeImports = "com.github.liancheng" %% "organize-imports" % V.organizeImports
  }

  object CompilerPlugins {
    val betterMonadicFor = compilerPlugin("com.olegpy" %% "better-monadic-for" % V.betterMonadicFor)
    val betterToString   = compilerPlugin("com.kubukoz" % "better-tostring" % "0.3.6" cross CrossVersion.full)
    val kindProjector = compilerPlugin(
      "org.typelevel" %% "kind-projector" % V.kindProjector cross CrossVersion.full
    )
    val semanticDB = compilerPlugin(
      "org.scalameta" % "semanticdb-scalac" % V.semanticDB cross CrossVersion.full
    )
  }

}
