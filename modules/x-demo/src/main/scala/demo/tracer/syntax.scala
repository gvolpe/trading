package demo.tracer

import cats.~>
import cats.data.{ Kleisli, OptionT }
import cats.effect.{ IO, Resource }
import cats.syntax.all.*
import natchez.{ EntryPoint, Kernel, Span, Trace }
import natchez.http4s.syntax.EntryPointOps.ExcludedHeaders
import org.http4s.{ HttpRoutes, Request, Response }
import org.typelevel.ci.CIString

extension (ep: EntryPoint[IO])
  def liftRoutes(f: Trace[IO] ?=> HttpRoutes[IO]): HttpRoutes[IO] =
    Kleisli { req =>
      val isKernelHeader: CIString => Boolean = name => !ExcludedHeaders.contains(name)

      val kernelHeaders = req.headers.headers.collect {
        case header if isKernelHeader(header.name) => header.name.toString -> header.value
      }.toMap

      val kernel = Kernel(kernelHeaders)
      val spanR  = ep.continueOrElseRoot(req.uri.path.toString, kernel)

      OptionT {
        spanR.use { span =>
          Trace.ioTrace(span).flatMap { implicit trace =>
            f.run(req).value
          }
        }
      }
    }
