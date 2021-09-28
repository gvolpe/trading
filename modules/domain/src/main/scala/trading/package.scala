package trading

import cats.{ Eq, Show }

package object domain {
  type Symbol    = String
  type Price     = BigDecimal
  type Quantity  = Int
  type Source    = String
  type TickPrice = Double
  type TickSize  = Double
  type Timestamp = java.time.Instant

  type CommandId = java.util.UUID
  type SocketId  = java.util.UUID

  type AskPrice = Price
  type BidPrice = Price

  implicit val timestampEq: Eq[Timestamp]     = Eq.by(_.getEpochSecond)
  implicit val timestampShow: Show[Timestamp] = Show.show(_.toString)
}
