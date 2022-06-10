package trading.lib

import scala.annotation.nowarn

import monocle.Iso

/*
Trying to law-check this instance seems pointless; so many instances missing for union types.

[error] - Iso[String | Int, Either[String, Int]]: Iso.consistent modify with modifyId 3ms
[error]   Discipline$PropertyException: Property failed with an exception
[error]   ARG 0:
[error]   ARG 1: org.scalacheck.GenArities$$Lambda$10602/0x0000000802852850@6cdab30a
[error]
[error]
[error]   Caused by: java.lang.NoSuchMethodError: 'cats.Comonad cats.Invariant$.catsInstancesForId()'
[error]
[error]   IsoLaws.scala:28          monocle.law.IsoLaws#consistentModifyModifyId
 *
 * Matchable: https://www-dev.scala-lang.org/scala3/reference/other-new-features/matchable.html
 *
 * The @nowarn goes awat by adding two constraints: (using TypeTest[E | A, E], TypeTest[E | A, A]),
 * but it still causes issues at call site, not worth it.
 * See: https://docs.scala-lang.org/scala3/reference/other-new-features/type-test.html
 */
@nowarn
def eitherUnionIso[E <: Matchable, A <: Matchable]: Iso[Either[E, A], E | A] =
  Iso[Either[E, A], E | A] {
    case Left(e)  => e
    case Right(a) => a
  } {
    case e: E => Left(e)
    case a: A => Right(a)
  }
