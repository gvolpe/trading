package demo

import scala.concurrent.duration._

import trading.lib.{ Consumer, Producer }

import cats.effect._
import cats.effect.std.Queue
import fs2.Stream

object MemDemo extends IOApp.Simple {

  def run: IO[Unit] =
    for {
      _     <- IO.println(">>> Initializing in-memory demo <<<")
      queue <- Queue.bounded[IO, Option[String]](500)
      consumer = Consumer.local(queue)
      producer = Producer.local(queue)
      _ <- Stream(
        consumer.receive.evalMap(s => IO.println(s">>> CONSUMED: $s")),
        Stream.awakeEvery[IO](100.millis).as("test").evalMap(producer.send)
      ).parJoin(2).interruptAfter(3.seconds).compile.drain
    } yield ()

}
