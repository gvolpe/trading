package demo.tracer

import cats.data.{ Kleisli, OptionT }
import cats.effect.IO
import cats.syntax.all.*
import natchez.{ EntryPoint, Kernel, Trace }
import natchez.http4s.syntax.EntryPointOps.ExcludedHeaders
import org.http4s.HttpRoutes
import org.typelevel.ci.*

extension (kv: Map[String, String])
  def toKernel: Kernel = Kernel(kv.map { (k, v) => ci"$k" -> v })

extension (k: Kernel)
  def headers: Map[String, String] = k.toHeaders.map {
    (k, v) => k.show -> v
  }

extension (ep: EntryPoint[IO])
  def liftRoutes(f: Trace[IO] ?=> HttpRoutes[IO]): HttpRoutes[IO] =
    Kleisli { req =>
      val isKernelHeader: CIString => Boolean = name => !ExcludedHeaders.contains(name)

      val kernelHeaders = req.headers.headers.collect {
        case header if isKernelHeader(header.name) => ci"${header.name}" -> header.value
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
