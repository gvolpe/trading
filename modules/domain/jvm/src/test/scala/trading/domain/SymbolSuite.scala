package trading.domain

import trading.domain.*
import trading.domain.arbitraries.given

import cats.kernel.laws.discipline.MonoidTests
import weaver.FunSuite
import weaver.discipline.Discipline

object SymbolSuite extends FunSuite with Discipline:
  checkAll("Monoid[Symbol]", MonoidTests[Symbol].monoid)
