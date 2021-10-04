package trading.commands

import trading.domain.{*, given}

import cats.syntax.all.*
import cats.{ Applicative, Show }
import io.circe.Codec
import monocle.Traversal

enum TradeCommand(
    val id: CommandId,
    val symbol: Symbol,
    tradeAction: TradeAction,
    price: Price,
    source: Source,
    timestamp: Timestamp
) derives Codec.AsObject:
  case Create(
      override val id: CommandId,
      override val symbol: Symbol,
      tradeAction: TradeAction,
      price: Price,
      quantity: Quantity,
      source: Source,
      timestamp: Timestamp
  ) extends TradeCommand(id, symbol, tradeAction, price, source, timestamp)

  case Update(
      override val id: CommandId,
      override val symbol: Symbol,
      tradeAction: TradeAction,
      price: Price,
      quantity: Quantity,
      source: Source,
      timestamp: Timestamp
  ) extends TradeCommand(id, symbol, tradeAction, price, source, timestamp)

  case Delete(
      override val id: CommandId,
      override val symbol: Symbol,
      tradeAction: TradeAction,
      price: Price,
      source: Source,
      timestamp: Timestamp
  ) extends TradeCommand(id, symbol, tradeAction, price, source, timestamp)

object TradeCommand:
  // FIXME: use kittens when snapshot is published
  given Show[TradeCommand] = Show.show[TradeCommand](_.toString)

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
