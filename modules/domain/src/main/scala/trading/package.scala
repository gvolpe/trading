package trading

import cats.Show

package object domain {
  type Symbol    = String
  type Price     = BigDecimal
  type Quantity  = Int
  type Source    = String
  type TickPrice = Double
  type TickSize  = Double
  type Timestamp = java.time.Instant

  type SocketId = java.util.UUID

  type AskPrice = Price
  type BidPrice = Price

  implicit val timestampShow: Show[Timestamp] = Show.show(_.toString)
}
