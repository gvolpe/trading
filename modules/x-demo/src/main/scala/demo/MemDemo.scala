package demo

import scala.concurrent.duration.*

import trading.lib.{ Consumer, Producer }

import cats.effect.*
import cats.effect.std.Queue
import fs2.Stream

object MemDemo extends IOApp.Simple:
  def run: IO[Unit] =
    Queue.bounded[IO, Option[String]](500).flatMap { queue =>
      val consumer = Consumer.local(queue)
      val producer = Producer.local(queue)

      val p1 =
        consumer.receive
          .evalMap(s => IO.println(s">>> GOT: $s"))

      val p2 =
        Stream
          .resource(producer)
          .flatMap { p =>
            Stream
              .sleep[IO](100.millis)
              .as("test")
              .repeatN(3)
              .evalMap(p.send)
          }

      IO.println(">>> Initializing in-memory demo <<<") *>
        p1.concurrently(p2).compile.drain
    }
