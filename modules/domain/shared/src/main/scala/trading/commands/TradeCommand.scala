package trading.commands

import trading.domain.*

import cats.{ Applicative, Eq, Show }
import cats.derived.*
import cats.syntax.all.*
import io.circe.Codec
import monocle.Traversal

enum TradeCommand derives Codec.AsObject, Eq, Show:
  def id: CommandId
  def cid: CorrelationId
  def symbol: Symbol
  def createdAt: Timestamp

  case Create(
      id: CommandId,
      cid: CorrelationId,
      symbol: Symbol,
      tradeAction: TradeAction,
      price: Price,
      quantity: Quantity,
      source: Source,
      createdAt: Timestamp
  )

  case Update(
      id: CommandId,
      cid: CorrelationId,
      symbol: Symbol,
      tradeAction: TradeAction,
      price: Price,
      quantity: Quantity,
      source: Source,
      createdAt: Timestamp
  )

  case Delete(
      id: CommandId,
      cid: CorrelationId,
      symbol: Symbol,
      tradeAction: TradeAction,
      price: Price,
      source: Source,
      createdAt: Timestamp
  )

object TradeCommand:
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
