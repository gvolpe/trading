package trading.ws

import trading.domain.Alert.TradeAlert
import trading.domain.*
import trading.lib.*

import cats.effect.kernel.{ Concurrent, Deferred, Ref }
import cats.syntax.all.*
import fs2.concurrent.Topic
import fs2.{ Pipe, Stream }
import io.circe.parser.decode as jsonDecode
import io.circe.syntax.*
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.{ Close, Text }

trait Handler[F[_]]:
  def send: Stream[F, WebSocketFrame]
  def receive: Pipe[F, WebSocketFrame, Unit]

object Handler:
  def make[F[_]: Concurrent: GenUUID: Logger](
      topic: Topic[F, Alert]
  ): F[Handler[F]] =
    (
      Deferred[F, Either[Throwable, Unit]], // syncs the termination of the handler
      Deferred[F, Unit],                    // syncs the first received message to avoid losing subscriptions
      Ref.of[F, Set[Symbol]](Set.empty),    // keeps track of the symbol subscriptions
      GenUUID[F].make[SocketId]             // unique socket id
    ).mapN { case (switch, fuze, subs, sid) =>
      new:
        val encode: WsOut => F[Option[WebSocketFrame]] = {
          case out @ WsOut.Notification(t: TradeAlert) =>
            subs.get.map(_.find(_ === t.symbol).as(Text((out: WsOut).asJson.noSpaces)))
          case out =>
            Text(out.asJson.noSpaces).some.pure[F].widen
        }

        val decode: WebSocketFrame => Either[String, WsIn] = {
          case Close(_)     => WsIn.Close.asRight
          case Text(msg, _) => jsonDecode[WsIn](msg).leftMap(_.getMessage)
          case e            => s">>> [$sid] - Unexpected WS message: $e".asLeft
        }

        val send: Stream[F, WebSocketFrame] =
          topic.subscribers
            .take(1)
            .evalMap(n => encode(WsOut.Attached(sid, n + 1)))
            .append {
              topic
                .subscribe(100)
                .evalMap(x => fuze.get *> encode(x.wsOut))
                .interruptWhen(switch)
            }
            .unNone

        val receive: Pipe[F, WebSocketFrame, Unit] =
          _.evalMap {
            decode(_) match
              case Left(e) =>
                Logger[F].error(e)
              case Right(WsIn.Heartbeat) =>
                ().pure[F]
              case Right(WsIn.Close) =>
                Logger[F].info(s"[$sid] - Closing WS connection") *>
                  topic.close *> switch.complete(().asRight).void
              case Right(WsIn.Subscribe(symbol)) =>
                Logger[F].info(s"[$sid] - Subscribing to $symbol alerts") *>
                  subs.update(_ + symbol)
              case Right(WsIn.Unsubscribe(symbol)) =>
                Logger[F].info(s"[$sid] - Unsubscribing from $symbol alerts") *>
                  subs.update(_ - symbol)
          }.onFinalize(Logger[F].info(s"[$sid] - WS connection terminated"))
            .onFirstMessage(fuze.complete(()).void)
    }
