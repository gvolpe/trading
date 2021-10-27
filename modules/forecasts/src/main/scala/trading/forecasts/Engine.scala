package trading.forecasts

import trading.domain.*
import trading.commands.ForecastCommand
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
      def run: ForecastCommand => F[Unit] =
        case ForecastCommand.Publish(_, aid, fid, symbol, desc, tag, _) =>
          (GenUUID[F].make[EventId], Time[F].timestamp).tupled.flatMap { (eid, ts) =>
            atStore
              .addForecast(aid, fid)
              .flatMap { _ =>
                val fc = Forecast(fid, symbol, tag, desc, ForecastScore(0))
                val ev = ForecastEvent.Published(eid, aid, fid, symbol, ts)
                fcStore.save(fc).as(ev)
              }
              .handleError { case AuthorStore.AuthorNotFound =>
                ForecastEvent.NotPublished(eid, aid, fid, Reason("Author not found"), ts)
              }
              .flatMap(forecasts.send)
          }
        case ForecastCommand.Register(_, name, website, _) =>
          (GenUUID[F].make[AuthorId], GenUUID[F].make[EventId], Time[F].timestamp).tupled.flatMap { (aid, eid, ts) =>
            val author = Author(aid, name, website, List.empty)
            atStore
              .save(author)
              .as(AuthorEvent.Registered(eid, aid, name, ts))
              .handleError { case AuthorStore.DuplicateAuthorError(_) =>
                AuthorEvent.NotRegistered(eid, name, Reason("Duplicate username"), ts)
              }
              .flatMap(authors.send)
          }
        case ForecastCommand.Vote(_, fid, res, _) =>
          (GenUUID[F].make[EventId], Time[F].timestamp).tupled
            .flatMap { (eid, ts) =>
              val ev = ForecastEvent.Voted(eid, fid, res, ts)
              fcStore.castVote(fid, res) *> forecasts.send(ev)
            }
