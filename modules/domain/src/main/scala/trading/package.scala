package trading

package object domain {
  type Symbol    = String
  type Price     = BigDecimal
  type Quantity  = Int
  type Source    = String
  type TickPrice = Double
  type TickSize  = Double
  type Timestamp = java.time.Instant

  type AskPrice = Price
  type BidPrice = Price
}
