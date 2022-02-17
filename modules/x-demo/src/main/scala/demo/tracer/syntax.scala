package demo.tracer

import cats.data.{ Kleisli, OptionT }
import cats.effect.{ IO, Resource }
import cats.syntax.all.*
import natchez.{ EntryPoint, Kernel, Span, Trace }
import natchez.http4s.syntax.EntryPointOps.ExcludedHeaders
import org.http4s.{ HttpRoutes, Request, Response }
import org.typelevel.ci.CIString

extension (ep: EntryPoint[IO])
  private def ioTrace: Kleisli[OptionT[IO, *], Request[IO], Trace[IO]] =
    Kleisli { req =>
      val isKernelHeader: CIString => Boolean = name => !ExcludedHeaders.contains(name)

      val kernelHeaders = req.headers.headers.collect {
        case header if isKernelHeader(header.name) => header.name.toString -> header.value
      }.toMap

      val kernel = Kernel(kernelHeaders)
      val spanR  = ep.continueOrElseRoot(req.uri.path.toString, kernel)

      OptionT.liftF(spanR.use(Trace.ioTrace))
    }

  def liftRoutes(f: Trace[IO] => HttpRoutes[IO]): HttpRoutes[IO] =
    ioTrace.flatMap(f)
