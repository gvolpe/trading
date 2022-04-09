package trading.core

import trading.commands.*
import trading.commands.SwitchCommand.{ Start, Stop }
import trading.commands.TradeCommand.{ Create, Delete, Update }
import trading.domain.TradingStatus.*
import trading.domain.*
import trading.events.*
import trading.events.SwitchEvent.{ Ignored, Started, Stopped }
import trading.events.TradeEvent.{ CommandExecuted, CommandRejected }
import trading.lib.FSM
import trading.state.TradeState

import cats.Id

object TradeEngine:
  // Event generation and trading switch
  val fsm = FSM.id[TradeState, TradeCommand | SwitchCommand, (EventId, Timestamp) => TradeEvent | SwitchEvent] {
    // Trading status: On
    case (st @ TradeState(On, _), cmd @ Create(_, cid, symbol, action, price, quantity, _, _)) =>
      val nst = st.modify(symbol)(action, price, quantity)
      nst -> ((id, ts) => CommandExecuted(id, cid, cmd, ts))
    case (st @ TradeState(On, _), cmd @ Update(_, cid, symbol, action, price, quantity, _, _)) =>
      val nst = st.modify(symbol)(action, price, quantity)
      nst -> ((id, ts) => CommandExecuted(id, cid, cmd, ts))
    case (st @ TradeState(On, _), cmd @ Delete(_, cid, symbol, action, price, _, _)) =>
      val nst = st.remove(symbol)(action, price)
      nst -> ((id, ts) => CommandExecuted(id, cid, cmd, ts))
    // Trading status: Off
    case (st @ TradeState(Off, _), cmd: TradeCommand) =>
      st -> ((id, ts) => CommandRejected(id, cmd.cid, cmd, Reason("Trading is Off"), ts))
    // Trading switch: On / Off
    case (st @ TradeState(Off, _), Start(_, cid, _)) =>
      val nst = TradeState._Status.replace(On)(st)
      nst -> ((id, ts) => Started(id, cid, ts))
    case (st @ TradeState(On, _), Stop(_, cid, _)) =>
      val nst = TradeState._Status.replace(Off)(st)
      nst -> ((id, ts) => Stopped(id, cid, ts))
    case (st @ TradeState(On, _), Start(_, cid, _)) =>
      st -> ((id, ts) => Ignored(id, cid, ts))
    case (st @ TradeState(Off, _), Stop(_, cid, _)) =>
      st -> ((id, ts) => Ignored(id, cid, ts))
  }

  // Price fluctuation and trading switch without event generation
  val eventsFsm = FSM.id[TradeState, TradeEvent | SwitchEvent, Unit] {
    // Trading status: On
    case (st @ TradeState(On, _), CommandExecuted(_, _, cmd, _)) =>
      fsm.runS(st, cmd) -> ()
    // Trading status: Off
    case (st @ TradeState(Off, _), CommandExecuted(_, _, cmd, _)) =>
      fsm.runS(st, cmd) -> ()
    // Trading switch: On / Off
    case (st @ TradeState(Off, _), Started(_, _, _)) =>
      TradeState._Status.replace(On)(st) -> ()
    case (st @ TradeState(On, _), Stopped(_, _, _)) =>
      TradeState._Status.replace(Off)(st) -> ()
    // Ignore
    case (st, CommandRejected(_, _, _, _, _)) =>
      st -> ()
    case (st, _: SwitchEvent) =>
      st -> () // Ignored or Started / Stopped in switched status
  }
