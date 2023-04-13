package trading.commands

import trading.domain.*
import trading.domain.arbitraries.given
import trading.domain.cogen.given

import monocle.law.discipline.*
import org.scalacheck.Arbitrary
import weaver.FunSuite
import weaver.discipline.Discipline

object TradeCommandSuite extends FunSuite with Discipline:
  checkAll("Traversal[TradeCommand, CommandId]", TraversalTests(TradeCommand._CommandId))
  checkAll("Traversal[TradeCommand, Timestamp]", TraversalTests(TradeCommand._CreatedAt))
