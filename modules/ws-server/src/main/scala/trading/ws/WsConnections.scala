package trading.ws

import trading.domain.SocketId

import cats.Functor
import cats.effect.kernel.Concurrent
import cats.syntax.functor.*
import fs2.Stream
import fs2.concurrent.SignallingRef

trait WsConnections[F[_]]:
  def get: F[Int]
  def subscriptions: Stream[F, Int]
  def subscribe(sid: SocketId): F[Unit]
  def unsubscribe(sid: SocketId): F[Unit]

object WsConnections:
  def make[F[_]: Concurrent]: F[WsConnections[F]] =
    SignallingRef.of[F, Set[SocketId]](Set.empty).map { ref =>
      new:
        def get: F[Int]                         = ref.get.map(_.size)
        def subscriptions: Stream[F, Int]       = ref.discrete.map(_.size)
        def subscribe(sid: SocketId): F[Unit]   = ref.update(_ + sid)
        def unsubscribe(sid: SocketId): F[Unit] = ref.update(_ - sid)
    }
