package trading.forecasts

import trading.commands.ForecastCommand
import trading.domain.EventId
import trading.events.TradeEvent
import trading.lib.*

import cats.MonadThrow
import cats.syntax.all.*
import fs2.Stream

trait Engine[F[_]]:
  def run: Stream[F, Unit]

object Engine:
  def make[F[_]: GenUUID: Logger: MonadThrow: Time](
      consumer: Consumer[F, ForecastCommand],
      producer: Producer[F, TradeEvent]
  ): Engine[F] =
    new Engine[F]:
      def run: Stream[F, Unit] =
        consumer.receiveM
          .evalMap {
            case Consumer.Msg(id, cmd: ForecastCommand.Publish) =>
              // TODO: fetch author info, update reputation
              Logger[F].info(cmd.toString).as(id)
            case Consumer.Msg(id, cmd: ForecastCommand.Register) =>
              // TODO: add author store in Redis (though, it should be in SQL), validate username is unique
              // also publish event AuthorCreated(authorId, etc) or AuthorNotCreated(reason: Duplicate)
              Logger[F].info(cmd.toString).as(id)
            case Consumer.Msg(id, cmd: ForecastCommand.Vote) =>
              // TODO: fetch author info, update reputation, maybe also update article points?
              Logger[F].info(cmd.toString).as(id)
          }.evalMap(consumer.ack)
