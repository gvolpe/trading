package demo

import scala.concurrent.duration.*

import trading.domain.generators.*
import trading.events.TradeEvent
import trading.lib.{ Consumer, Producer }

import cats.effect.*
import cats.syntax.all.*
import fs2.Stream
import fs2.kafka.*
import io.circe.syntax.*

object KafkaDemo extends IOApp.Simple:

  val event: Option[TradeEvent] =
    (createCommandGen.sample, timestampGen.sample).mapN { (cmd, ts) =>
      TradeEvent.CommandExecuted(cmd, ts)
    }

  def run: IO[Unit] =
    Stream
      .resource(resources)
      .flatMap { (consumer, producer) =>
        Stream(
          Stream.awakeEvery[IO](1.second).as(event).evalMap(_.traverse_(producer.send)),
          consumer.receive.evalMap(e => IO.println(s">>> KAFKA: $e"))
        ).parJoin(2)
      }
      .interruptAfter(5.seconds)
      .compile
      .drain

  given Deserializer[IO, TradeEvent] = Deserializer.lift[IO, TradeEvent] { bs =>
    IO.fromEither(io.circe.parser.decode[TradeEvent](new String(bs, "UTF-8")))
  }

  given Serializer[IO, TradeEvent] = Serializer.lift[IO, TradeEvent] { e =>
    IO.pure(e.asJson.noSpaces.getBytes("UTF-8"))
  }

  val consumerSettings =
    ConsumerSettings[IO, String, TradeEvent]
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withBootstrapServers("localhost:9092")
      .withGroupId("group")

  val producerSettings =
    ProducerSettings[IO, String, TradeEvent]
      .withBootstrapServers("localhost:9092")

  val topic = "trading-kafka"

  def resources =
    for {
      _        <- Resource.eval(IO.println(">>> Initializing kafka demo <<<"))
      consumer <- Consumer.kafka[IO, TradeEvent](consumerSettings, topic)
      producer <- Producer.kafka[IO, TradeEvent](producerSettings, topic)
    } yield consumer -> producer
