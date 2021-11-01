package trading.trace.log

import trading.lib.Logger

import cats.effect.{ Resource, Sync }
import cats.syntax.functor.*
import io.circe.Json
import natchez.*

// NOTICE: Copied and adapted from the `natchez-log` module: https://github.com/tpolecat/natchez/blob/series/0.1/modules/log/shared/src/main/scala/Log.scala
object LogEntryPoint:
  def apply[F[_]: Logger: Sync](
      service: String,
      format: Json => String = _.spaces2
  ): EntryPoint[F] = new:
    def make(span: F[LogSpan[F]]): Resource[F, Span[F]] =
      Resource.makeCase(span)(LogSpan.finish(format)).widen

    def continue(name: String, kernel: Kernel): Resource[F, Span[F]] =
      make(LogSpan.fromKernel(service, name, kernel))

    def continueOrElseRoot(name: String, kernel: Kernel): Resource[F, Span[F]] =
      make(LogSpan.fromKernelOrElseRoot(service, name, kernel))

    def root(name: String): Resource[F, Span[F]] =
      make(LogSpan.root(service, name))
