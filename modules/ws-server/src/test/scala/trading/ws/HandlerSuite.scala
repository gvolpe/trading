package trading.ws

import java.time.Instant
import java.util.UUID

import scala.concurrent.duration.*

import trading.domain.*
import trading.lib.*
import trading.lib.Logger.NoOp.given

import cats.data.NonEmptyList
import cats.effect.IO
import cats.syntax.all.*
import fs2.Stream
import fs2.concurrent.Topic
import io.circe.syntax.*
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.{ Close, Text }
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

object HandlerSuite extends SimpleIOSuite with Checkers:
  val id  = AlertId(UUID.randomUUID())
  val cid = CorrelationId(UUID.randomUUID())
  val sl1 = Symbol.EURUSD
  val sl2 = Symbol.CHFEUR
  val ts  = Timestamp(Instant.parse("2021-09-16T14:00:00.00Z"))
  val p1  = Price(1.1987)
  val q1  = Quantity(10)

  val alerts = List(
    Alert.TradeAlert(id, cid, AlertType.Buy, sl1, p1, p1, p1, p1, ts),
    Alert.TradeUpdate(id, cid, TradingStatus.Off, ts),
    Alert.TradeAlert(id, cid, AlertType.Sell, sl1, p1, p1, p1, p1, ts),
    Alert.TradeUpdate(id, cid, TradingStatus.On, ts),
    Alert.TradeAlert(id, cid, AlertType.StrongSell, sl1, p1, p1, p1, p1, ts),
    Alert.TradeAlert(id, cid, AlertType.Neutral, sl2, p1, p1, p1, p1, ts)
  )

  val input = List(
    WsIn.Subscribe(sl1),
    WsIn.Heartbeat,
    WsIn.Unsubscribe(sl2),
    WsIn.Subscribe(sl1),
    WsIn.Heartbeat
  ).map(in => Text(in.asJson.noSpaces))

  // a bit simpler without testing in terms of Topic
  test("WS message handler (alternative)") {
    (
      GenUUID[IO].make[SocketId],          // unique socket id
      WsConnections.make[IO],              // subscribers
      IO.ref(List.empty[WebSocketFrame]),  // all WsOut messages sent
      IO.deferred[Unit],                   // switch to sync the sending of the WsIn.Close message
      IO.deferred[Either[Throwable, Unit]] // to know when there is an active subscription
    ).tupled
      .flatMap { (sid, conns, out, switch, connected) =>
        Handler.make(sid, conns, Stream.emits(alerts)).flatMap { h =>
          val recv =
            Stream
              .emits(input)
              .append {
                Stream.eval(switch.get.as(Text(WsIn.Close.asJson.noSpaces)))
              }
              .through(h.receive)
              .void

          val send =
            h.send
              .evalMap(wsf => out.update(_ :+ wsf))
              .onFinalize(switch.complete(()).void)

          Stream(recv, send).parJoin(2).compile.drain
        } >> (conns.get, out.get).tupled.flatMap {
          case (n, (Text(x, _) :: xs)) =>
            // Attached message + 5 alerts for symbol EURUSD (sl1)
            NonEmptyList
              .of(
                expect(x.contains("Attached")),
                expect.same(xs.size, alerts.size - 1),
                expect.same(n, 0)
              )
              .reduce
              .pure[IO]
          case _ =>
            out.get.flatMap(_.traverse_(IO.println)).as(failure("expected non-empty list"))
        }
      }
  }
