package trading.core

import trading.commands.TradeCommand
import trading.domain.Timestamp
import trading.state.{ DedupState, IdRegistry }

import cats.syntax.all.*

// Not used in the project but left here to demonstrate how deduplication could be
// implemented for other brokers that do not support it natively (see also DedupRegistry).
object Conflicts:
  def dedup(st: DedupState)(command: TradeCommand): Option[TradeCommand] =
    (!st.ids.map(_.id).contains(command.id)).guard[Option].as(command)

  def update(ds: DedupState)(command: TradeCommand, ts: Timestamp): DedupState =
    updateMany(ds)(command :: Nil, ts)

  def updateMany(ds: DedupState)(commands: List[TradeCommand], ts: Timestamp): DedupState =
    DedupState(ds.removeOld(ts) ++ commands.map(c => IdRegistry(c.id, ts)).toSet)
