package trading.core

import trading.commands.*
import trading.domain.TradingStatus.*
import trading.domain.*
import trading.events.*
import trading.lib.FSM
import trading.state.TradeState

import cats.Id

object TradeEngine:
  val fsm = FSM.id[TradeState, TradeCommand | SwitchCommand, (EventId, Timestamp) => TradeEvent | SwitchEvent] {
    // Trading status: On
    case (st @ TradeState(On, _), cmd @ TradeCommand.Create(_, cid, symbol, action, price, quantity, _, _)) =>
      val nst = st.modify(symbol)(action, price, quantity)
      nst -> ((id, ts) => TradeEvent.CommandExecuted(id, cid, cmd, ts))
    case (st @ TradeState(On, _), cmd @ TradeCommand.Update(_, cid, symbol, action, price, quantity, _, _)) =>
      val nst = st.modify(symbol)(action, price, quantity)
      nst -> ((id, ts) => TradeEvent.CommandExecuted(id, cid, cmd, ts))
    case (st @ TradeState(On, _), cmd @ TradeCommand.Delete(_, cid, symbol, action, price, _, _)) =>
      val nst = st.remove(symbol)(action, price)
      nst -> ((id, ts) => TradeEvent.CommandExecuted(id, cid, cmd, ts))
    // Trading status: Off
    case (st @ TradeState(Off, _), cmd: TradeCommand) =>
      st -> ((id, ts) => TradeEvent.CommandRejected(id, cmd.cid, cmd, Reason("Trading is Off"), ts))
    // Trading switch: On / Off
    case (st @ TradeState(Off, _), SwitchCommand.Start(_, cid, _)) =>
      val nst = TradeState._Status.replace(On)(st)
      nst -> ((id, ts) => SwitchEvent.Started(id, cid, ts))
    case (st @ TradeState(On, _), SwitchCommand.Stop(_, cid, _)) =>
      val nst = TradeState._Status.replace(Off)(st)
      nst -> ((id, ts) => SwitchEvent.Stopped(id, cid, ts))
    case (st @ TradeState(On, _), SwitchCommand.Start(_, cid, _)) =>
      st -> ((id, ts) => SwitchEvent.Ignored(id, cid, ts))
    case (st @ TradeState(Off, _), SwitchCommand.Stop(_, cid, _)) =>
      st -> ((id, ts) => SwitchEvent.Ignored(id, cid, ts))
  }
