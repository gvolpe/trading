package trading

import cats.Monoid

object Extensions:
  def mempty[A: Monoid]: A = Monoid[A].empty

  extension [A](a: A)
    def length(using w: Wrapper[String, A]): Int =
      w.iso.reverseGet(a).length
