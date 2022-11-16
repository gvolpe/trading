package trading.forecasts

import scala.concurrent.duration.*

import trading.domain.*
import trading.events.ForecastEvent
import trading.forecasts.store.*
import trading.lib.*
import trading.lib.Consumer.Msg

import cats.effect.MonadCancelThrow
import cats.syntax.all.*

// having the event store as the source of truth, we can just rely on the redelivery mechanism (via nack) to do retries,
// in case the DB operation fails. Eventually, after many unsuccessful retries, it'll land in the the dead-letter topic
// (assuming a dead letter policy is in place).
// from the dead-letter topic, we could have another VoteCanceled event be emitted or manually checked.
// we could also have an internal queue and manage retries, but that involves more code for no real benefit.
// this pattern is also known as "listen-to-yourself", and its pros are high performance, resilience and elasticity,
// while introducing eventual consistency as one of the cons.
trait VotesHandler[F[_]]:
  def run: Msg[ForecastEvent] => F[Unit]

object VotesHandler:
  def make[F[_]: Logger: MonadCancelThrow](
      store: ForecastStore[F],
      acker: Acker[F, ForecastEvent]
  ): VotesHandler[F] = new:
    def run: Msg[ForecastEvent] => F[Unit] =
      case Msg(msgId, _, ForecastEvent.Published(_, _, _, _, _, _)) =>
        acker.ack(msgId)
      case Msg(msgId, _, evt @ ForecastEvent.Voted(_, _, fid, res, _)) =>
        store.tx
          .use { db =>
            db.registerVote(evt) *> db.castVote(fid, res)
          }
          .productR(acker.ack(msgId))
          .handleErrorWith {
            case DuplicateEventId(eid) =>
              Logger[F].error(s"Duplicate event ID: $eid") *> acker.ack(msgId)
            case e =>
              Logger[F].error(s"Failed to register vote for forecast: $fid - ${e.getMessage}") *> acker.nack(msgId)
          }
