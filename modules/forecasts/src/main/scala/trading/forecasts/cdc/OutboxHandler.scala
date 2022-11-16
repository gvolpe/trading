package trading.forecasts.cdc

import scala.concurrent.duration.*

import trading.domain.*
import trading.events.*
import trading.lib.*
import trading.lib.Consumer.Msg

import cats.Applicative
import cats.syntax.all.*

trait OutboxHandler[F[_]]:
  def run: Msg[OutboxEvent] => F[Unit]

object OutboxHandler:
  def make[F[_]: Applicative: Logger](
      authors: Producer[F, AuthorEvent],
      forecasts: Producer[F, ForecastEvent],
      acker: Acker[F, OutboxEvent]
  ): OutboxHandler[F] = new:
    def run: Msg[OutboxEvent] => F[Unit] =
      case Msg(msgId, _, OutboxEvent(_, _, ev, _)) =>
        ev.bitraverse(authors.send, forecasts.send) *> acker.ack(msgId)
