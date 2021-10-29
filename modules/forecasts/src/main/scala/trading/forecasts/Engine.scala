package trading.forecasts

import trading.commands.ForecastCommand
import trading.domain.*
import trading.events.{ AuthorEvent, ForecastEvent }
import trading.forecasts.store.{ AuthorStore, ForecastStore }
import trading.lib.*

import cats.MonadThrow
import cats.syntax.all.*
import fs2.Stream

trait Engine[F[_]]:
  def run: ForecastCommand => F[Unit]

object Engine:
  def make[F[_]: GenUUID: Logger: MonadThrow: Time](
      authors: Producer[F, AuthorEvent],
      forecasts: Producer[F, ForecastEvent],
      atStore: AuthorStore[F],
      fcStore: ForecastStore[F]
  ): Engine[F] =
    new Engine[F]:
      def run: ForecastCommand => F[Unit] = cmd =>
        (
          GenUUID[F].make[AuthorId],
          GenUUID[F].make[EventId],
          Time[F].timestamp
        ).tupled.flatMap { (aid, eid, ts) =>
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
                .flatMap(forecasts.send)
            case ForecastCommand.Register(_, cid, name, website, _) =>
              atStore
                .save(Author(aid, name, website, List.empty))
                .as(AuthorEvent.Registered(eid, cid, aid, name, ts))
                .handleError { case AuthorStore.DuplicateAuthorError(_) =>
                  AuthorEvent.NotRegistered(eid, cid, name, Reason("Duplicate username"), ts)
                }
                .flatMap(authors.send)
            case ForecastCommand.Vote(_, cid, fid, res, _) =>
              val ev = ForecastEvent.Voted(eid, cid, fid, res, ts)
              fcStore.castVote(fid, res) *> forecasts.send(ev)
        }
