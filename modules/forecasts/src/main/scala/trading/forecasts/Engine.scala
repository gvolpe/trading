package trading.forecasts

import trading.commands.ForecastCommand
import trading.domain.*
import trading.events.{ AuthorEvent, ForecastEvent }
import trading.forecasts.store.{ AuthorStore, ForecastStore }
import trading.lib.*
import trading.lib.Consumer.{ Msg, MsgId }

import cats.MonadThrow
import cats.syntax.all.*
import fs2.Stream

trait Engine[F[_]]:
  def run: Msg[ForecastCommand] => F[Unit]

object Engine:
  def make[F[_]: GenUUID: Logger: MonadThrow: Time](
      authors: Producer[F, AuthorEvent],
      forecasts: Producer[F, ForecastEvent],
      atStore: AuthorStore[F],
      fcStore: ForecastStore[F],
      acker: Acker[F, ForecastCommand]
  ): Engine[F] = new:
    def run: Msg[ForecastCommand] => F[Unit] = { case Msg(msgId, cmd) =>
      (GenUUID[F].make[EventId], Time[F].timestamp).tupled.flatMap { (eid, ts) =>
        cmd match
          case ForecastCommand.Publish(_, cid, aid, fid, symbol, desc, tag, _) =>
            atStore
              .addForecast(aid, fid)
              .flatMap { _ =>
                val fc = Forecast(fid, symbol, tag, desc, ForecastScore(0))
                val ev = ForecastEvent.Published(eid, cid, aid, fid, symbol, ts)
                fcStore.save(fc).as(ev)
              }
              .handleError { case AuthorStore.AuthorNotFound =>
                ForecastEvent.NotPublished(eid, cid, aid, fid, Reason("Author not found"), ts)
              }
              .flatMap(e => forecasts.send(e) *> acker.ack(msgId))
              .handleErrorWith(e => Logger[F].error(s"Publish: $e") *> acker.nack(msgId))
          case ForecastCommand.Register(_, cid, name, website, _) =>
            GenUUID[F].make[AuthorId].flatMap { aid =>
              atStore
                .save(Author(aid, name, website, Set.empty))
                .as(AuthorEvent.Registered(eid, cid, aid, name, ts))
                .handleError { case AuthorStore.DuplicateAuthorError(_) =>
                  AuthorEvent.NotRegistered(eid, cid, name, Reason("Duplicate username"), ts)
                }
                .flatMap(e => authors.send(e) *> acker.ack(msgId))
                .handleErrorWith(e => Logger[F].error(s"Register: $e") *> acker.nack(msgId))
            }
          case ForecastCommand.Vote(_, cid, fid, res, _) =>
            val ev = ForecastEvent.Voted(eid, cid, fid, res, ts)
            fcStore
              .castVote(fid, res)
              .flatMap(_ => (forecasts.send(ev) *> acker.ack(msgId)).attempt.void)
              .handleErrorWith(e => Logger[F].error(s"Vote:$e") *> acker.nack(msgId))

      }
    }
