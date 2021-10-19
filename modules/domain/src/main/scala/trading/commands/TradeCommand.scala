package trading.commands

import trading.domain.{ given, * }

// FIXME: importing all `given` yield ambiguous implicits
import cats.derived.semiauto.{ coproductEq, product, productEq, * }
import cats.syntax.all.*
import cats.{ Applicative, Eq, Show }
import io.circe.Codec
import monocle.Traversal

sealed trait TradeCommand derives Codec.AsObject, Eq, Show:
  def id: CommandId
  def symbol: Symbol
  def tradeAction: TradeAction
  def price: Price
  def source: Source
  def timestamp: Timestamp

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
        f(s.id).map { newId =>
          s match
            case c: Create => c.copy(id = newId)
            case c: Update => c.copy(id = newId)
            case c: Delete => c.copy(id = newId)
        }
    }
