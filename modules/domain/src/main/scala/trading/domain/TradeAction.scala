package trading.domain

import derevo.cats.show
import derevo.circe.magnolia.{ decoder, encoder }
import derevo.derive

@derive(decoder, encoder, show)
sealed trait TradeAction

object TradeAction {
  case object Ask extends TradeAction
  case object Bid extends TradeAction
}
