package trading.lib

import cats.{ Functor, Id }
import cats.syntax.all.*

case class FSM[F[_], S, I, O](run: (S, I) => F[(S, O)]):
  def runS(using F: Functor[F]): (S, I) => F[S] =
    (s, i) => run(s, i).map(_._1)

object FSM:
  def identity[S, I, O](run: (S, I) => Id[(S, O)]) = FSM(run)
