package trading.commands

import trading.domain.{ given, * }

import cats.{ Applicative, Eq, Show }
import cats.derived.*
import cats.syntax.all.*
import io.circe.Codec
import monocle.Traversal

enum SwitchCommand derives Codec.AsObject, Eq, Show:
  def id: CommandId
  def cid: CorrelationId
  def createdAt: Timestamp

  case Start(
      id: CommandId,
      cid: CorrelationId,
      createdAt: Timestamp
  )

  case Stop(
      id: CommandId,
      cid: CorrelationId,
      createdAt: Timestamp
  )

object SwitchCommand:
  val _CommandId: Traversal[SwitchCommand, CommandId] = new:
    def modifyA[F[_]: Applicative](f: CommandId => F[CommandId])(s: SwitchCommand): F[SwitchCommand] =
      f(s.id).map { newId =>
        s match
          case c: Start => c.copy(id = newId)
          case c: Stop  => c.copy(id = newId)
      }

  val _CorrelationId: Traversal[SwitchCommand, CorrelationId] = new:
    def modifyA[F[_]: Applicative](f: CorrelationId => F[CorrelationId])(s: SwitchCommand): F[SwitchCommand] =
      f(s.cid).map { newCid =>
        s match
          case c: Start => c.copy(cid = newCid)
          case c: Stop  => c.copy(cid = newCid)
      }

  val _CreatedAt: Traversal[SwitchCommand, Timestamp] = new:
    def modifyA[F[_]: Applicative](f: Timestamp => F[Timestamp])(s: SwitchCommand): F[SwitchCommand] =
      f(s.createdAt).map { ts =>
        s match
          case c: Start => c.copy(createdAt = ts)
          case c: Stop  => c.copy(createdAt = ts)
      }
