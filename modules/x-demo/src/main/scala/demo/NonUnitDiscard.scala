package demo

import scala.annotation.nowarn

import cats.effect.*

object NonUnitDiscard extends IOApp.Simple:
  @nowarn // remove this annotation to see the zerowaste plugin in action
  val run: IO[Unit] =
    IO.ref(List.empty[Int]).flatMap { ref =>
      IO.println("discarded value")
      ref.set(List.range(0, 11))
    }
