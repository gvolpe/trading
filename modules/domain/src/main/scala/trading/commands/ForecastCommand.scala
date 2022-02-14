package trading.commands

import trading.domain.{ given, * }
import trading.domain.ForecastTag.given // to derive Eq (should not be needed, though)
import trading.domain.VoteResult.given  // to derive Eq (should not be needed, though)

import cats.derived.*
import cats.syntax.all.*
import cats.{ Applicative, Eq, Show }
import io.circe.Codec
import monocle.Traversal

sealed trait ForecastCommand derives Codec.AsObject, Eq, Show:
  def id: CommandId
  def cid: CorrelationId
  def createdAt: Timestamp

object ForecastCommand:
  final case class Register(
      id: CommandId,
      cid: CorrelationId,
      authorName: AuthorName,
      authorWebsite: Option[Website],
      createdAt: Timestamp
  ) extends ForecastCommand

  final case class Publish(
      id: CommandId,
      cid: CorrelationId,
      authorId: AuthorId,
      symbol: Symbol,
      description: ForecastDescription,
      tag: ForecastTag,
      createdAt: Timestamp
  ) extends ForecastCommand

  final case class Vote(
      id: CommandId,
      cid: CorrelationId,
      forecastId: ForecastId,
      result: VoteResult,
      createdAt: Timestamp
  ) extends ForecastCommand

  val _CommandId: Traversal[ForecastCommand, CommandId] = new:
    def modifyA[F[_]: Applicative](f: CommandId => F[CommandId])(s: ForecastCommand): F[ForecastCommand] =
      f(s.id).map { newId =>
        s match
          case c: Publish  => c.copy(id = newId)
          case c: Register => c.copy(id = newId)
          case c: Vote     => c.copy(id = newId)
      }

  val _CreatedAt: Traversal[ForecastCommand, Timestamp] = new:
    def modifyA[F[_]: Applicative](f: Timestamp => F[Timestamp])(s: ForecastCommand): F[ForecastCommand] =
      f(s.createdAt).map { ts =>
        s match
          case c: Publish  => c.copy(createdAt = ts)
          case c: Register => c.copy(createdAt = ts)
          case c: Vote     => c.copy(createdAt = ts)
      }
