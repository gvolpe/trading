package trading.domain

import trading.RefNewtype

import cats.Monoid
import eu.timepit.refined.cats.*
import eu.timepit.refined.types.string.NonEmptyFiniteString
import io.circe.refined.*

type Symbol = Symbol.Type
object Symbol extends RefNewtype[String, NonEmptyFiniteString[6]]:
  // Note: unsafeFrom should not be needed once Refined macros work in Scala 3
  val CHFEUR = unsafeFrom("CHFEUR")
  val EURPLN = unsafeFrom("EURPLN")
  val EURUSD = unsafeFrom("EURUSD")
  val GBPUSD = unsafeFrom("GBPUSD")
  val AUDCAD = unsafeFrom("AUDCAD")
  val USDCAD = unsafeFrom("USDCAD")
  val CHFGBP = unsafeFrom("CHFGBP")
  val XEMPTY = unsafeFrom("XXXXXX")

  given Monoid[Symbol] with
    def empty: Symbol = XEMPTY
    def combine(x: Symbol, y: Symbol): Symbol =
      (x, y) match
        case (XEMPTY, z) => z
        case (z, XEMPTY) => z
        case (_, z)      => z
