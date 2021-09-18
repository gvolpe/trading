package trading.core

import trading.state.TradeState
import cats.effect.std.Console
import cats.effect.kernel.Ref
import cats.syntax.all._
import cats.Monad

trait StateCache[F[_]] {
  def save(state: TradeState): F[Unit]
}

object StateCache {
  // Prints out state every 10 invokations. Eventually this should be stored in Redis...
  def make[F[_]: Console: Monad: Ref.Make]: F[StateCache[F]] =
    Ref.of[F, Int](0).map { counter =>
      new StateCache[F] {
        def save(state: TradeState): F[Unit] =
          counter.modify {
            case n if n % 10 == 0 =>
              (n + 1) -> Console[F].println(state)
            case n =>
              (n + 1) -> Monad[F].unit
          }.flatten
      }
    }
}
