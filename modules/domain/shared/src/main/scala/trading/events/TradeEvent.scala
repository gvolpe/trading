package trading.events

import trading.commands.TradeCommand
import trading.domain.*

import cats.{ Applicative, Eq, Show }
import cats.derived.*
import cats.syntax.all.*
import io.circe.Codec
import monocle.Traversal

enum TradeEvent derives Codec.AsObject, Show:
  def id: EventId
  def cid: CorrelationId
  def command: TradeCommand
  def createdAt: Timestamp

  case CommandExecuted(
      id: EventId,
      cid: CorrelationId,
      command: TradeCommand,
      createdAt: Timestamp
  )

  case CommandRejected(
      id: EventId,
      cid: CorrelationId,
      command: TradeCommand,
      reason: Reason,
      createdAt: Timestamp
  )

object TradeEvent:
  // EventId and Timestamp are regenerated when reprocessed so we don't consider them.
  given Eq[TradeEvent] = Eq.and(Eq.by(_.cid), Eq.by(_.command))

  val _CorrelationId: Traversal[TradeEvent, CorrelationId] = new:
    def modifyA[F[_]: Applicative](f: CorrelationId => F[CorrelationId])(s: TradeEvent): F[TradeEvent] =
      f(s.cid).map { newCid =>
        s match
          case c: CommandExecuted => c.copy(cid = newCid)
          case c: CommandRejected => c.copy(cid = newCid)
      }
