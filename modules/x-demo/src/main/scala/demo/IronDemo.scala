package demo

import cats.*
import cats.data.EitherNel
import cats.derived.*
import cats.syntax.all.*
import io.circe.*
import io.circe.syntax.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.cats.{ given, * }
import io.github.iltotore.iron.circe.given
import io.github.iltotore.iron.constraint.all.*

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

object Alien:
  def make(
      name: String,
      age: Int
  ): EitherNel[String, Alien] =
    (
      name.refineNel[NameR],
      age.refineNel[AgeR]
    ).parMapN(Alien.apply)

case class Pet2 private (
    name: String
)
object Pet2:
  val pet2 = Pet2("")

sealed abstract case class Pet(
    name: String
)

val pet = new Pet("") {}

//def foo(pet: Pet2): Pet2 = pet.copy(_.name = "")

// compile error: value copy is not a member of Pet
//def tryMe(pet: Pet): Pet =
//pet.copy(name = "By-pass?")

@main def ironDemo =
  val alien = Alien("Bob", 120)
  println(alien.asJson.noSpaces)
  val runtime = Alien.make("", 0)
  println(runtime)
