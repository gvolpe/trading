package trading.forecasts

import trading.commands.ForecastCommand
import trading.domain.*
import trading.events.{ AuthorEvent, ForecastEvent }
import trading.forecasts.store.*
import trading.lib.*
import trading.lib.Consumer.{ Msg, MsgId }

import cats.effect.kernel.{ MonadCancelThrow, Resource }
import cats.syntax.all.*
import fs2.Stream

trait Engine[F[_]]:
  def run: Msg[ForecastCommand] => F[Unit]

object Engine:
  def make[F[_]: GenUUID: Logger: MonadCancelThrow: Time](
      producer: Producer[F, ForecastEvent],
      atStore: AuthorStore[F],
      fcStore: ForecastStore[F],
      acker: Acker[F, ForecastCommand]
  ): Engine[F] = new:
    def run: Msg[ForecastCommand] => F[Unit] =
      case Msg(msgId, _, cmd) =>
        extension (fa: F[Unit])
          def handleNack: F[Unit] =
            fa.handleErrorWith {
              case DuplicateEventId(eid) =>
                Logger[F].error(s"Ignoring duplicate event id: ${eid.show}") *> acker.ack(msgId)
              case e =>
                Logger[F].error(s"Unexpected error: ${e.getMessage}") *> acker.nack(msgId)
            }

        (GenUUID[F].make[EventId], Time[F].timestamp).tupled.flatMap { (eid, ts) =>
          cmd match
            case ForecastCommand.Register(_, cid, name, site, _) =>
              GenUUID[F].make[AuthorId].flatMap { aid =>
                atStore.tx
                  .use { db =>
                    db.save(Author(aid, name, site, Set.empty)) *>
                      db.outbox(AuthorEvent.Registered(eid, cid, aid, name, site, ts))
                  }
                  .productR(acker.ack(msgId))
                  .recoverWith { case DuplicateAuthorError =>
                    Logger[F].error(s"Author name $name already registered!") *> acker.ack(msgId)
                  }
                  .handleNack
              }

            case ForecastCommand.Publish(_, cid, aid, symbol, desc, tag, _) =>
              GenUUID[F].make[ForecastId].flatMap { fid =>
                fcStore.tx
                  .use { db =>
                    db.save(aid, Forecast(fid, symbol, tag, desc, ForecastScore(0))) *>
                      db.outbox(ForecastEvent.Published(eid, cid, aid, fid, symbol, ts))
                  }
                  .productR(acker.ack(msgId))
                  .recoverWith { case AuthorNotFound =>
                    Logger[F].error(s"Author not found: $aid") *> acker.ack(msgId)
                  }
                  .handleNack
              }

            case ForecastCommand.Vote(_, cid, fid, res, _) =>
              producer.send(ForecastEvent.Voted(eid, cid, fid, res, ts)) *> acker.ack(msgId)
        }
