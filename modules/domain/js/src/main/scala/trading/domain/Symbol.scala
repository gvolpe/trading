package trading.domain

import trading.Newtype

import cats.*
import cats.derived.*
import io.circe.*

// JS definition does not use refinement types (iron does not support Scala.JS yet)
type Symbol = Symbol.Type
object Symbol extends Newtype[String]:
  val CHFEUR = Symbol("CHFEUR")
  val EURPLN = Symbol("EURPLN")
  val EURUSD = Symbol("EURUSD")
  val GBPUSD = Symbol("GBPUSD")
  val AUDCAD = Symbol("AUDCAD")
  val USDCAD = Symbol("USDCAD")
  val CHFGBP = Symbol("CHFGBP")
  val XEMPTY = Symbol("XXXXXX")

  given Monoid[Symbol] with
    def empty: Symbol = XEMPTY
    def combine(x: Symbol, y: Symbol): Symbol =
      (x, y) match
        case (XEMPTY, z) => z
        case (z, XEMPTY) => z
        case (_, z)      => z
