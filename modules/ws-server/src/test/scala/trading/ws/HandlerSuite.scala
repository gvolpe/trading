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

  given Time[IO] with
    def timestamp: IO[Timestamp] = IO.pure(ts)

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

  test("WS message handler") {
    (
      Topic[IO, Alert],
      IO.ref(List.empty[WebSocketFrame]),  // all WsOut messages sent
      IO.deferred[Unit],                   // switch to sync the sending of the WsIn.Close message
      IO.deferred[Either[Throwable, Unit]] // to know when there is an active subscription
    ).tupled
      .flatMap { (topic, out, switch, connected) =>
        Handler.make(topic).flatMap { h =>
          val recv =
            Stream
              .emits(input)
              .append {
                Stream.eval(switch.get.as(Text(WsIn.Close.asJson.noSpaces)))
              }
              .through(h.receive)
              .void

          val noti =
            topic.subscribers
              .evalMap(s => connected.complete(().asRight).whenA(s >= 1))
              .interruptWhen(connected.get)
              .onFinalize {
                Stream
                  .emits(alerts)
                  .through(topic.publish)
                  .compile
                  .drain
              }

          val send =
            h.send
              .evalMap(wsf => out.update(_ :+ wsf))
              .onFinalize(switch.complete(()).void)

          Stream(recv, noti, send).parJoin(3).compile.drain
        } >> out.get.flatMap {
          case (Text(x, _) :: xs) =>
            // Attached message + 5 alerts for symbol EURUSD (sl1)
            IO.pure(expect(x.contains("Attached")) && expect.same(xs.size, alerts.size - 1))
          case _ =>
            out.get.flatMap(_.traverse_(IO.println)).as(failure("expected non-empty list"))
        }
      }
  }

  // a bit simpler without testing in terms of Topic
  test("WS message handler (alternative)") {
    (
      IO.ref(List.empty[WebSocketFrame]),  // all WsOut messages sent
      IO.deferred[Unit],                   // switch to sync the sending of the WsIn.Close message
      IO.deferred[Either[Throwable, Unit]] // to know when there is an active subscription
    ).tupled
      .flatMap { (out, switch, connected) =>
        val close       = IO.unit
        val subscribers = Stream.constant[IO, Int](0)
        val subscribe   = (_: Int) => Stream.emits(alerts)

        Handler.make(subscribers, subscribe, close).flatMap { h =>
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
        } >> out.get.flatMap {
          case (Text(x, _) :: xs) =>
            // Attached message + 5 alerts for symbol EURUSD (sl1)
            IO.pure(expect(x.contains("Attached")) && expect.same(xs.size, alerts.size - 1))
          case _ =>
            out.get.flatMap(_.traverse_(IO.println)).as(failure("expected non-empty list"))
        }
      }
  }
