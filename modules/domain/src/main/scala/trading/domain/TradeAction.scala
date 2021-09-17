package trading.domain

sealed trait TradeAction

object TradeAction {
  case object Ask extends TradeAction
  case object Bid extends TradeAction
}
