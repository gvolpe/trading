package trading.lib

import cats.Id

case class FSM[F[_], S, I, O](run: (S, I) => F[(S, O)])

object FSM:
  def identity[S, I, O](run: (S, I) => Id[(S, O)]) = FSM(run)
