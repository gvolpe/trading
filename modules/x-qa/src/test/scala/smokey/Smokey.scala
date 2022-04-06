package smokey

import java.time.Instant
import java.util.UUID

import scala.concurrent.duration.*

import trading.commands.TradeCommand
import trading.commands.TradeCommand.*
import trading.core.AppTopic
import trading.domain.*
import trading.domain.Symbol.*
import trading.lib.{ Producer, Shard }
import trading.ws.*

import cats.effect.*
import cats.syntax.all.*
import dev.profunktor.pulsar.{ Config, Producer as PulsarProducer, Pulsar, SeqIdMaker }
import fs2.Stream
import io.circe.parser.decode as jsonDecode
import io.circe.syntax.*
import org.http4s.client.websocket.{ WSClient, WSDataFrame, WSFrame, WSRequest }
import org.http4s.implicits.*
import org.http4s.jdkhttpclient.JdkWSClient
import weaver.{ Expectations, IOSuite }

object Smokey extends IOSuite:
  type Res = (Pulsar.T, WSClient[IO])

  val pulsarCfg  = Config.Builder.default
  val connectReq = WSRequest(uri"ws://localhost:9000/v1/ws")

  override def sharedResource: Resource[IO, Res] =
    (Pulsar.make[IO](pulsarCfg.url), JdkWSClient.simple[IO]).tupled

  val symbols1: List[WsIn] = List(EURUSD, USDCAD, GBPUSD).map(WsIn.Subscribe(_))
  val symbols2: List[WsIn] = List(EURPLN, CHFEUR).map(WsIn.Subscribe(_))

  def mkId  = CommandId(UUID.randomUUID)
  def mkCid = CorrelationId(UUID.randomUUID)
  val mkTs  = Timestamp(Instant.now)

  val commands: List[TradeCommand] = List(
    Create(mkId, mkCid, EURUSD, TradeAction.Ask, Price(4.5), Quantity(10), "test", mkTs),
    Create(mkId, mkCid, CHFEUR, TradeAction.Ask, Price(2.3), Quantity(55), "test", mkTs),
    Create(mkId, mkCid, USDCAD, TradeAction.Bid, Price(3.7), Quantity(7), "test", mkTs),
    Create(mkId, mkCid, GBPUSD, TradeAction.Bid, Price(2.6), Quantity(89), "test", mkTs),
    Update(mkId, mkCid, GBPUSD, TradeAction.Bid, Price(2.8), Quantity(24), "test", mkTs)
  )

  val aid = AlertId(UUID.randomUUID)
  val cid = mkCid

  // See Eq[TradeAlert]: AlertId, CorrelationId and Timestamp are not used for equality.
  val expected: List[WsOut] = List(
    Alert.TradeAlert(aid, cid, AlertType.Neutral, Symbol.EURUSD, Price(4.5), Price(0), Price(4.5), Price(4.5), mkTs),
    Alert.TradeAlert(aid, cid, AlertType.StrongSell, Symbol.USDCAD, Price(0), Price(3.7), Price(3.7), Price(3.7), mkTs)
  ).map(_.wsOut)

  def encode(wsIn: WsIn): WSDataFrame =
    WSFrame.Text(wsIn.asJson.noSpaces)

  val tcSettings =
    PulsarProducer
      .Settings[IO, TradeCommand]()
      .withDeduplication(SeqIdMaker.fromEq)
      .withShardKey(Shard[TradeCommand].key)
      .some

  val topic = AppTopic.TradingCommands.make(pulsarCfg)

  def p1(pulsar: Pulsar.T): IO[Unit] =
    Producer.pulsar[IO, TradeCommand](pulsar, topic, tcSettings).use { p =>
      IO.sleep(2.seconds) *>
        Stream.emits(commands).metered[IO](100.millis).evalMap(p.send).compile.drain
    }

  // TODO: Next we could add a second WSClient subscribing to symbols2
  def p2(client: WSClient[IO]): IO[List[WsOut]] =
    (IO.ref(List.empty[WsOut]), IO.deferred[Either[Throwable, Unit]]).tupled.flatMap { (ref, switch) =>
      client
        .connectHighLevel(connectReq)
        .use { ws =>
          val recv =
            ws.receiveStream
              .evalMap {
                case WSFrame.Text(str, _) =>
                  jsonDecode[WsOut](str) match
                    case Right(out) => IO.println(s"<<< $out") *> ref.update(_ :+ out)
                    case Left(err)  => IO.println(s"Fail to decode WsOut: $err")
                case WSFrame.Binary(_, _) =>
                  IO.unit
              }
              .evalMapAccumulate(1) { (acc, _) =>
                switch.complete(().asRight).whenA(acc == 3).tupleLeft(acc + 1)
              }

          val heartbeats =
            Stream.eval(ws.send(encode(WsIn.Heartbeat))).metered[IO](5.seconds)

          val send =
            Stream.emits(symbols1).evalMap { s =>
              ws.send(encode(s))
            }

          Stream(heartbeats, recv, send)
            .parJoin(3)
            .interruptWhen(switch)
            .compile
            .drain
        }
        .handleErrorWith { e =>
          IO.println(s"<<< WS conn error: ${e.getMessage}")
        } *> ref.get
    }

  test("Trading smoke test") { case (pulsar, ws) =>
    (p1(pulsar) &> p2(ws))
      .flatMap {
        case ((x: WsOut.Attached) :: xs) =>
          IO.pure(expect.same(xs, expected))
        case xs =>
          for
            _ <- IO.println("\n--- WsOut list ---\n")
            _ <- xs.traverse_(IO.println)
          yield failure("Expected 3 messages")
      }
  }
