package trading.core.http

import cats.effect.kernel.{ Async, Resource }
import cats.effect.std.Console
import cats.syntax.all.*
import com.comcast.ip4s.*
import org.http4s.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.metrics.prometheus.{ Prometheus, PrometheusExportService }
import org.http4s.server.Server
import org.http4s.server.middleware.Metrics
import org.http4s.server.defaults.Banner
import org.http4s.server.websocket.WebSocketBuilder

object Ember:
  private def showBanner[F[_]: Console](s: Server): F[Unit] =
    Console[F].println(s"\n${Banner.mkString("\n")}\nHTTP Server started at ${s.address}")

  private def make[F[_]: Async](port: Port) =
    EmberServerBuilder
      .default[F]
      .withHost(host"0.0.0.0")
      .withPort(port)

  private def metrics[F[_]: Async]: Resource[F, HttpRoutes[F] => HttpRoutes[F]] =
    for
      prt <- PrometheusExportService.build[F]
      ops <- Prometheus.metricsOps[F](prt.collectorRegistry)
    yield rts => Metrics[F](ops)(prt.routes <+> rts)

  def websocket[F[_]: Async: Console](
      port: Port,
      f: WebSocketBuilder[F] => HttpRoutes[F]
  ): Resource[F, Server] =
    metrics[F].flatMap { mid =>
      make[F](port)
        .withHttpWebSocketApp { ws =>
          mid(f(ws) <+> HealthRoutes[F].routes).orNotFound
        }
        .build
        .evalTap(showBanner[F])
    }

  def default[F[_]: Async: Console](port: Port): Resource[F, Server] =
    metrics[F].flatMap { mid =>
      make[F](port)
        .withHttpApp(mid(HealthRoutes[F].routes).orNotFound)
        .build
        .evalTap(showBanner[F])
    }
