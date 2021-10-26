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
  def run: Stream[F, Unit]

object Engine:
  def make[F[_]: GenUUID: Logger: MonadThrow: Time](
      consumer: Consumer[F, ForecastCommand],
      authors: Producer[F, AuthorEvent],
      forecasts: Producer[F, ForecastEvent],
      atStore: AuthorStore[F],
      fcStore: ForecastStore[F]
  ): Engine[F] =
    new Engine[F]:
      def run: Stream[F, Unit] =
        consumer.receiveM
          // TODO: The entire pattern-match can be made pure logic somewhere else, taking ids and timestamp
          .evalMap {
            case Consumer.Msg(id, cmd: ForecastCommand.Publish) =>
              // TODO: fetch author info, update reputation
              val fc = Forecast(cmd.forecastId, cmd.authorId, cmd.symbol, cmd.tag, cmd.description, ForecastScore(0))
              fcStore.save(fc) *> Logger[F].info(cmd.toString).as(id)
            case Consumer.Msg(id, cmd: ForecastCommand.Register) =>
              (GenUUID[F].make[AuthorId], GenUUID[F].make[EventId], Time[F].timestamp).tupled.flatMap {
                (authorId, eventId, ts) =>
                  val author = Author(authorId, cmd.authorName, cmd.authorWebsite, Reputation.empty, List.empty)
                  atStore
                    .save(author)
                    .as(AuthorEvent.Registered(eventId, author.id, author.name, ts))
                    .handleError { case AuthorStore.DuplicateAuthorError(_) =>
                      AuthorEvent.NotRegistered(eventId, author.name, "duplicate username", ts)
                    }
                    .as(id)
              }
            case Consumer.Msg(id, cmd: ForecastCommand.Vote) =>
              // TODO: fetch author info, update reputation, maybe also update article points?
              Logger[F].info(cmd.toString).as(id)
          }
          .evalMap(consumer.ack)
