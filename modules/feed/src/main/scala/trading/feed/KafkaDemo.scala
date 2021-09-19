package trading.feed

import scala.concurrent.duration._

import trading.core.lib.{ Consumer, Producer }
import trading.events.TradeEvent

import cats.effect._
import fs2.Stream
import fs2.kafka._
import io.circe.syntax._

object KafkaDemo extends IOApp.Simple {

  val event: TradeEvent = TradeEvent.CommandExecuted(
    command = generators.createCommandGen.sample.get,
    timestamp = generators.timestampGen.sample.get
  )

  def run: IO[Unit] =
    Stream
      .resource(resources)
      .flatMap { case (consumer, producer) =>
        Stream(
          Stream.awakeEvery[IO](1.second).as(event).evalMap(producer.send),
          consumer.receive.evalMap(e => IO.println(s">>> KAFKA: $e"))
        ).parJoin(2)
      }
      .interruptAfter(5.seconds)
      .compile
      .drain

  implicit val eventDeserializer = Deserializer.lift[IO, TradeEvent] { bs =>
    IO.fromEither(io.circe.parser.decode[TradeEvent](new String(bs, "UTF-8")))
  }

  implicit val eventSerializer = Serializer.lift[IO, TradeEvent] { e =>
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

}
