package demo

import java.util.UUID

import scala.concurrent.duration.*

import cats.effect.*
import cats.effect.std.{ Console, Supervisor }
import cats.syntax.all.*

// https://docs.scala-lang.org/scala3/reference/contextual/context-functions.html
object EffectfulContext extends IOApp.Simple:

  final class Log(ref: Ref[IO, List[String]]):
    def add(str: => String): IO[Unit] = ref.update(_ :+ str)
    def get: IO[List[String]]         = ref.get

  final case class Ctx(
      id: UUID,
      sp: Supervisor[IO],
      log: Log
  )

  private val mkCtx =
    for
      id <- Resource.eval(IO(UUID.randomUUID()))
      sp <- Supervisor[IO]
      lg <- Resource.eval(Ref.of[IO, List[String]](List.empty))
    yield Ctx(id, sp, Log(lg))

  def withCtx(f: Ctx ?=> IO[Unit]): IO[Unit] =
    mkCtx.use { ctx =>
      f(using ctx) *> ctx.log.get.flatMap(_.traverse_(IO.println))
    }

  val p1: Ctx ?=> IO[Unit] =
    val ctx = summon[Ctx]
    IO.println("Running program #1") *> p2

  def p2(using ctx: Ctx): IO[Unit] =
    IO.println("Running program #2") *>
      ctx.sp
        .supervise {
          ctx.log.add(s"Start: ${ctx.id}") >>
            IO.sleep(1.second) >>
            ctx.log.add(s"Done: ${ctx.id}")
        }
        .flatMap { fb =>
          ctx.log.add(s"Waiting: ${ctx.id}") >>
            fb.join.void
        }

  val p3: Ctx ?=> IO[Unit] =
    IO.sleep(100.millis) *> IO.println("Running program #3") *> p4

  def p4(using ctx: Ctx): IO[Unit] =
    IO.println(s"Running program #4: ${ctx.id.show}")

  val run: IO[Unit] =
    withCtx {
      p1 &> p3
    }

  val run2: IO[Unit] =
    mkCtx.use { case (given Ctx) =>
      p1 &> p3
    }

  val run3: IO[Unit] =
    mkCtx.use { implicit ctx =>
      p1 &> p3
    }
