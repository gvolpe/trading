package trading.domain

import trading.RefNewtype

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
  val USDCAD = unsafeFrom("USDCAD")
  val XXXXXX = unsafeFrom("XXXXXX")
