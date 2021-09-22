package trading.ws

import trading.domain._

import derevo.circe.magnolia.{ decoder, encoder }
import derevo.derive

@derive(decoder, encoder)
sealed trait WsOut
object WsOut {
  //final case class Attached(sid: SocketId)             extends WsOut
  final case class Alert(symbol: Symbol, price: Price) extends WsOut
}
