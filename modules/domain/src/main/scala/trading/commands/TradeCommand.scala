package trading.commands

import trading.domain.{ given, * }

import cats.derived.semiauto.{ given, * }
import cats.syntax.all.*
import cats.{ Applicative, Eq, Show }
import io.circe.Codec
import monocle.Traversal

sealed trait TradeCommand derives Codec.AsObject, Eq, Show {
  def id: CommandId
  def symbol: Symbol
  def tradeAction: TradeAction
  def price: Price
  def source: Source
  def timestamp: Timestamp
}

object TradeCommand:
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

  val _CommandId =
    new Traversal[TradeCommand, CommandId] {
      def modifyA[F[_]: Applicative](f: CommandId => F[CommandId])(s: TradeCommand): F[TradeCommand] =
        s match {
          case c: Create => f(c.id).map(newId => c.copy(id = newId))
          case c: Update => f(c.id).map(newId => c.copy(id = newId))
          case c: Delete => f(c.id).map(newId => c.copy(id = newId))
        }
    }
