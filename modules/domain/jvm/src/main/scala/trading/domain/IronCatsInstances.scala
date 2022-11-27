package trading.domain

import cats.*
import io.github.iltotore.iron.*

object IronCatsInstances:
  inline given [A, B](using inline ev: Eq[A]): Eq[A :| B] = ev.asInstanceOf[Eq[A :| B]]
  inline given [A, B](using inline ev: Order[A]): Order[A :| B] = ev.asInstanceOf[Order[A :| B]]
  inline given [A, B](using inline ev: Show[A]): Show[A :| B] = ev.asInstanceOf[Show[A :| B]]
