package demo

import scala.util.control.NoStackTrace
import scala.util.Random

import trading.lib.*

import cats.effect.*
import cats.syntax.all.*

object ErrorLifting extends IOApp.Simple:

  case class Err1() extends NoStackTrace
  case class Err2() extends NoStackTrace

  case object Err3 extends NoStackTrace
  type Err3 = Err3.type

  val f: IO[Unit] = IO(Random.nextInt(2)).flatMap {
    case n if n % 2 == 0 => IO.raiseError(Err1())
    case n if n % 2 != 0 => IO.raiseError(Err2())
  }

  val eitherLift: IO[Unit] =
    val g: IO[Either[Err1, Unit]]        = f.lift
    val h: IO[Unit]                      = g.rethrow
    val i: IO[Either[Err1 | Err2, Unit]] = f.lift

    // or with type inference
    val j = f.lift[Err1 | Err2 | Err3]

    val x =
      j.flatMap {
        case Right(_)     => IO.println("All good")
        case Left(Err1()) => IO.println("Got Err1!")
        case Left(e)      => IO.raiseError(e)
      }.lift[Err2 | Err2]

    // exhaustive pattern matching
    j.flatMap {
      case Right(_)     => IO.println("All good")
      case Left(Err1()) => IO.println("Got Err1!")
      case Left(Err2()) => IO.println("Got Err2!")
      case Left(Err3)   => IO.println("Got Err3!")
    }

  def unionTypes: IO[Unit] =
    val a: IO[Err1 | Err2 | Unit] = f.liftU
    val b: IO[Unit]               = a.rethrowU

    // or with type inference
    val c = f.liftU[Err1 | Err2]

    // exhaustive pattern matching
    c.flatMap {
      case ()     => IO.println("All good")
      case Err1() => IO.println("Got Err1!")
      case Err2() => IO.println("Got Err2!")
    }

  def run: IO[Unit] = unionTypes
