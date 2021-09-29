package trading.commands

import trading.domain._

import cats.Applicative
import cats.syntax.functor._
import derevo.cats.show
import derevo.circe.magnolia.{ decoder, encoder }
import derevo.derive
import monocle.Traversal

@derive(decoder, encoder, show)
sealed trait TradeCommand {
  def id: CommandId
  def symbol: Symbol
  def tradeAction: TradeAction
  def price: Price
  def source: Source
  def timestamp: Timestamp
}

object TradeCommand {
  final case class Create(
      id: CommandId,
      symbol: Symbol,
      tradeAction: TradeAction,
      price: Price,
      quantity: Quantity,
      source: Source,
      timestamp: Timestamp
  ) extends TradeCommand

  final case class Update(
      id: CommandId,
      symbol: Symbol,
      tradeAction: TradeAction,
      price: Price,
      quantity: Quantity,
      source: Source,
      timestamp: Timestamp
  ) extends TradeCommand

  final case class Delete(
      id: CommandId,
      symbol: Symbol,
      tradeAction: TradeAction,
      price: Price,
      source: Source,
      timestamp: Timestamp
  ) extends TradeCommand

  // TODO: law check
  val _CommandId =
    new Traversal[TradeCommand, CommandId] {
      def modifyA[F[_]: Applicative](f: CommandId => F[CommandId])(s: TradeCommand): F[TradeCommand] =
        s match {
          case c: Create => f(c.id).map(newId => c.copy(id = newId))
          case c: Update => f(c.id).map(newId => c.copy(id = newId))
          case c: Delete => f(c.id).map(newId => c.copy(id = newId))
        }
    }
}
