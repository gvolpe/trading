package demo.dedup

import java.time.Instant
import java.util.UUID

import scala.concurrent.duration.*

import trading.commands.TradeCommand
import trading.domain.*
import trading.state.*

import cats.syntax.all.*
import weaver.FunSuite
import weaver.scalacheck.Checkers

object ConflictsSuite extends FunSuite with Checkers:
  val s: Symbol    = Symbol.EURUSD
  val p1: Price    = Price(1.1987)
  val q1: Quantity = Quantity(10)

  val cid: CorrelationId = CorrelationId(UUID.randomUUID())

  val id0: CommandId = CommandId(UUID.randomUUID())
  val id1: CommandId = CommandId(UUID.randomUUID())
  val id2: CommandId = CommandId(UUID.randomUUID())

  val ts0: Timestamp = Timestamp(Instant.parse("2021-09-16T14:00:00.00Z"))
  val ts1: Timestamp = Timestamp(ts0.value.minusSeconds((DedupState.Threshold + 1.second).toSeconds))

  test("De-duplication logic") {
    val cd1 = TradeCommand.Create(id0, cid, s, TradeAction.Ask, p1, q1, "test", ts0)
    val st1 = Conflicts.update(DedupState.empty)(cd1, ts0)
    val ex1 = DedupState(Set(IdRegistry(id0, ts0)))

    val cd2 = TradeCommand.Update(id1, cid, s, TradeAction.Ask, p1, q1, "test", ts0)
    val st2 = Conflicts.update(st1)(cd2, ts1) // older than 5 seconds, should be removed in next update
    val ex2 = DedupState(Set(IdRegistry(id0, ts0), IdRegistry(id1, ts1)))

    val rs2 = Conflicts.dedup(st1)(cd2)
    val rx2 = Some(cd2)

    // duplicate event (already processed)
    val cd3 = TradeCommand.Create(id0, cid, s, TradeAction.Ask, p1, q1, "test", ts0)
    val rs3 = Conflicts.dedup(st2)(cd3)
    val rx3 = None

    // update should now remove id1 (from cd2), which has old timestamp (ts1)
    val st4 = Conflicts.update(st2)(TradeCommand.Create(id2, cid, s, TradeAction.Bid, p1, q1, "test", ts0), ts0)
    val ex4 = DedupState(Set(IdRegistry(id0, ts0), IdRegistry(id2, ts0)))

    List(
      expect.same(st1, ex1),
      expect.same(st2, ex2),
      expect.same(rs2, rx2),
      expect.same(rs3, rx3),
      expect.same(st4, ex4)
    ).foldMap(identity)
  }
