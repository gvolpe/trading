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
      authors: Producer[F, AuthorEvent],
      forecasts: Producer[F, ForecastEvent],
      atStore: AuthorStore[F],
      fcStore: ForecastStore[F],
      acker: Acker[F, ForecastCommand],
      pulsarTx: Resource[F, Txn]
  ): Engine[F] = new:

    private def atomically[T](
        pgTx: Resource[F, T],
        pulsarTx: Resource[F, Txn],
        msgId: Consumer.MsgId
    )(body: (T, Txn) => F[Unit]): F[Unit] =
      (pgTx, pulsarTx)
        .mapN(body)
        .use(identity)
        .handleErrorWith(e => Logger[F].error(e.getMessage) *> acker.nack(msgId))

    def run: Msg[ForecastCommand] => F[Unit] = { case Msg(msgId, _, cmd) =>
      (GenUUID[F].make[EventId], Time[F].timestamp).tupled.flatMap { (eid, ts) =>
        cmd match
          case ForecastCommand.Publish(_, cid, aid, symbol, desc, tag, _) =>
            GenUUID[F].make[ForecastId].flatMap { fid =>
              atomically(fcStore.tx, pulsarTx, msgId) { (pg, tx) =>
                pg.save(aid, Forecast(fid, symbol, tag, desc, ForecastScore(0)))
                  .as(ForecastEvent.Published(eid, cid, aid, fid, symbol, ts))
                  .recover { case AuthorNotFound =>
                    ForecastEvent.NotPublished(eid, cid, aid, fid, Reason("Author not found"), ts)
                  }
                  .flatMap(e => forecasts.send(e, tx) *> acker.ack(msgId, tx))
              }
            }
          case ForecastCommand.Register(_, cid, name, website, _) =>
            GenUUID[F].make[AuthorId].flatMap { aid =>
              atomically(atStore.tx, pulsarTx, msgId) { (pg, tx) =>
                pg.save(Author(aid, name, website, Set.empty))
                  .as(AuthorEvent.Registered(eid, cid, aid, name, ts))
                  .recover { case DuplicateAuthorError =>
                    AuthorEvent.NotRegistered(eid, cid, name, Reason("Duplicate username"), ts)
                  }
                  .flatMap(e => authors.send(e, tx) *> acker.ack(msgId))
              }
            }
          case ForecastCommand.Vote(_, cid, fid, res, _) =>
            atomically(fcStore.tx, pulsarTx, msgId) { (pg, tx) =>
              pg.castVote(fid, res)
                .as(ForecastEvent.Voted(eid, cid, fid, res, ts))
                .recover { case ForecastNotFound =>
                  ForecastEvent.NotVoted(eid, cid, fid, Reason("Forecast not found"), ts)
                }
                .flatMap(ev => (forecasts.send(ev) *> acker.ack(msgId)).attempt.void)
            }
      }
    }
