package trading.trace
package fsm

import trading.lib.FSM
import trading.trace.tracer.*

type St[In] = In match
  case ForecastIn => ForecastState
  case TradeIn    => TradeState

type Tracer[F[_], In] = In match
  case ForecastIn => ForecastingTracer[F]
  case TradeIn    => TradingTracer[F]

type SM[F[_], In] = Tracer[F, In] => FSM[F, St[In], In, Unit]
