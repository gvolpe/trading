package trading.ws

import trading.domain.Alert.TradeAlert
import trading.domain.*
import trading.lib.*

import cats.effect.kernel.{ Concurrent, Deferred, Ref }
import cats.syntax.all.*
import fs2.{ Pipe, Stream }
import io.circe.parser.decode as jsonDecode
import io.circe.syntax.*
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.{ Close, Text }

trait Handler[F[_]]:
  def send: Stream[F, WebSocketFrame]
  def receive: Pipe[F, WebSocketFrame, Unit]

object Handler:
  def make[F[_]: Concurrent: Logger](
      sid: SocketId,
      conns: WsConnections[F],
      alerts: Stream[F, Alert]
  ): F[Handler[F]] =
    (
      Deferred[F, Either[Throwable, Unit]], // syncs the termination of the handler
      Deferred[F, Unit],                    // syncs the first received message to avoid losing subscriptions
      Ref.of[F, Set[Symbol]](Set.empty)     // keeps track of the symbol subscriptions
    ).mapN { case (switch, fuze, subs) =>
      new:
        val toWsFrame: WsOut => WebSocketFrame =
          out => Text(out.asJson.noSpaces)

        val encode: WsOut => F[Option[WebSocketFrame]] =
          case out @ WsOut.Notification(t: TradeAlert) =>
            subs.get.map(_.find(_ === t.symbol).as(toWsFrame(out)))
          case out =>
            toWsFrame(out).some.pure[F].widen

        val decode: WebSocketFrame => Either[String, WsIn] =
          case Close(_)     => WsIn.Close.asRight
          case Text(msg, _) => jsonDecode[WsIn](msg).leftMap(_.getMessage)
          case e            => s">>> [$sid] - Unexpected WS message: $e".asLeft

        val send: Stream[F, WebSocketFrame] =
          Stream
            .eval(conns.subscribe(sid) *> conns.get)
            .evalMap(n => encode(WsOut.Attached(sid, n)))
            .append {
              alerts
                .evalMap(x => fuze.get *> encode(x.wsOut))
                .interruptWhen(switch)
            }
            .unNone

        val close = conns.unsubscribe(sid)

        val receive: Pipe[F, WebSocketFrame, Unit] =
          _.evalMap {
            decode(_) match
              case Left(e) =>
                Logger[F].error(e)
              case Right(WsIn.Heartbeat) =>
                ().pure[F]
              case Right(WsIn.Close) =>
                Logger[F].info(s"[$sid] - Closing WS connection") *>
                  close *> switch.complete(().asRight).void
              case Right(WsIn.Subscribe(symbol)) =>
                Logger[F].info(s"[$sid] - Subscribing to $symbol alerts") *>
                  subs.update(_ + symbol)
              case Right(WsIn.Unsubscribe(symbol)) =>
                Logger[F].info(s"[$sid] - Unsubscribing from $symbol alerts") *>
                  subs.update(_ - symbol)
          }.onFinalize(Logger[F].info(s"[$sid] - WS connection terminated") *> close)
            .onFirstMessage(fuze.complete(()).void)
    }
