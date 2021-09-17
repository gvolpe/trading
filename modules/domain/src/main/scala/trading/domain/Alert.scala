package trading.domain

sealed trait Alert

object Alert {
  final case class StrongBuy(symbol: Symbol, price: Price)  extends Alert
  final case class StrongSell(symbol: Symbol, price: Price) extends Alert
  final case class Neutral(symbol: Symbol, price: Price)    extends Alert
  final case class Buy(symbol: Symbol, price: Price)        extends Alert
  final case class Sell(symbol: Symbol, price: Price)       extends Alert
}
