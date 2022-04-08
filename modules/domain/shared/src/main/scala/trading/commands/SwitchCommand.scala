package trading.commands

import trading.domain.{ given, * }

// FIXME: importing all `given` yield ambiguous implicits
import cats.derived.semiauto.{ coproductEq, product, productEq, * }
import cats.syntax.all.*
import cats.{ Applicative, Eq, Show }
import io.circe.Codec
import monocle.Traversal

sealed trait SwitchCommand derives Codec.AsObject, Eq, Show:
  def id: CommandId
  def cid: CorrelationId
  def createdAt: Timestamp

object SwitchCommand:
  final case class Start(
      id: CommandId,
      cid: CorrelationId,
      createdAt: Timestamp
  ) extends SwitchCommand

  final case class Stop(
      id: CommandId,
      cid: CorrelationId,
      createdAt: Timestamp
  ) extends SwitchCommand

  val _CommandId: Traversal[SwitchCommand, CommandId] = new:
    def modifyA[F[_]: Applicative](f: CommandId => F[CommandId])(s: SwitchCommand): F[SwitchCommand] =
      f(s.id).map { newId =>
        s match
          case c: Start  => c.copy(id = newId)
          case c: Stop   => c.copy(id = newId)
      }

  val _CorrelationId: Traversal[SwitchCommand, CorrelationId] = new:
    def modifyA[F[_]: Applicative](f: CorrelationId => F[CorrelationId])(s: SwitchCommand): F[SwitchCommand] =
      f(s.cid).map { newCid =>
        s match
          case c: Start  => c.copy(cid = newCid)
          case c: Stop   => c.copy(cid = newCid)
      }

  val _CreatedAt: Traversal[SwitchCommand, Timestamp] = new:
    def modifyA[F[_]: Applicative](f: Timestamp => F[Timestamp])(s: SwitchCommand): F[SwitchCommand] =
      f(s.createdAt).map { ts =>
        s match
          case c: Start  => c.copy(createdAt = ts)
          case c: Stop   => c.copy(createdAt = ts)
      }
