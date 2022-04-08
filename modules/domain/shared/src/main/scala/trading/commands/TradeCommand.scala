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
  def cid: CorrelationId
  def symbol: Symbol
  def createdAt: Timestamp

object TradeCommand:
  final case class Create(
      id: CommandId,
      cid: CorrelationId,
      symbol: Symbol,
      tradeAction: TradeAction,
      price: Price,
      quantity: Quantity,
      source: Source,
      createdAt: Timestamp
  ) extends TradeCommand

  final case class Update(
      id: CommandId,
      cid: CorrelationId,
      symbol: Symbol,
      tradeAction: TradeAction,
      price: Price,
      quantity: Quantity,
      source: Source,
      createdAt: Timestamp
  ) extends TradeCommand

  final case class Delete(
      id: CommandId,
      cid: CorrelationId,
      symbol: Symbol,
      tradeAction: TradeAction,
      price: Price,
      source: Source,
      createdAt: Timestamp
  ) extends TradeCommand

  val _CommandId: Traversal[TradeCommand, CommandId] = new:
    def modifyA[F[_]: Applicative](f: CommandId => F[CommandId])(s: TradeCommand): F[TradeCommand] =
      f(s.id).map { newId =>
        s match
          case c: Create => c.copy(id = newId)
          case c: Update => c.copy(id = newId)
          case c: Delete => c.copy(id = newId)
      }

  val _CorrelationId: Traversal[TradeCommand, CorrelationId] = new:
    def modifyA[F[_]: Applicative](f: CorrelationId => F[CorrelationId])(s: TradeCommand): F[TradeCommand] =
      f(s.cid).map { newCid =>
        s match
          case c: Create => c.copy(cid = newCid)
          case c: Update => c.copy(cid = newCid)
          case c: Delete => c.copy(cid = newCid)
      }

  val _CreatedAt: Traversal[TradeCommand, Timestamp] = new:
    def modifyA[F[_]: Applicative](f: Timestamp => F[Timestamp])(s: TradeCommand): F[TradeCommand] =
      f(s.createdAt).map { ts =>
        s match
          case c: Create => c.copy(createdAt = ts)
          case c: Update => c.copy(createdAt = ts)
          case c: Delete => c.copy(createdAt = ts)
      }
