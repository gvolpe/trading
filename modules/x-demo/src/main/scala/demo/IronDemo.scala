package demo

import trading.domain.IronCatsInstances.given

import cats.*
import cats.data.EitherNel
import cats.derived.*
import cats.syntax.all.*
import io.circe.*
import io.circe.syntax.*
import io.github.iltotore.iron.{ given, * }
import io.github.iltotore.iron.circeSupport.given
import io.github.iltotore.iron.constraint.any.DescribedAs
import io.github.iltotore.iron.constraint.numeric.given
import io.github.iltotore.iron.constraint.numeric.{ Greater, Less }
import io.github.iltotore.iron.constraint.string.given
import io.github.iltotore.iron.constraint.string.{ Alphanumeric, MaxLength, MinLength }

type AgeR = DescribedAs[
  Greater[0] & Less[151],
  "Alien's age must be an integer between 1 and 150"
]

type NameR = DescribedAs[
  Alphanumeric & MinLength[1] & MaxLength[50],
  "Alien's name must be an alphanumeric of max length 50"
]

// format: off
case class Alien(
    name: String :| NameR,
    age: Int :| AgeR
) derives Codec.AsObject, Eq, Show
// format: on

// TODO: Remove this once this PR lands: https://github.com/Iltotore/iron/pull/68
extension [A](value: A)
  inline def refineNel[B](using inline constraint: Constraint[A, B]): EitherNel[String, A :| B] =
    Either.cond(constraint.test(value), value.asInstanceOf[A :| B], constraint.message).toEitherNel

object Alien:
  def make(
      name: String,
      age: Int
  ): EitherNel[String, Alien] =
    (
      name.refineNel[NameR],
      age.refineNel[AgeR]
    ).parMapN(Alien.apply)

@main def ironDemo =
  val alien = Alien("Bob", 120)
  println(alien.asJson.noSpaces)
  val runtime = Alien.make("", 0)
  println(runtime)
