package trading.ws

import trading.domain._

import derevo.circe.magnolia.{ decoder, encoder }
import derevo.derive

@derive(decoder, encoder)
sealed trait WsIn
object WsIn {
  case object Close                            extends WsIn
  final case class Subscribe(symbol: Symbol)   extends WsIn
  final case class Unsubscribe(symbol: Symbol) extends WsIn
}
