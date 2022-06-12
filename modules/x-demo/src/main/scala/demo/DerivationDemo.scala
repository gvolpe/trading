package demo

import io.circe.Codec

case class Address(
    streetName: String,
    streetNumber: Int,
    flat: Option[String]
) derives Codec.AsObject

import io.circe.parser.decode as jsonDecode
import io.circe.syntax.*

@main def jsonDerivationDemo =
  val address = Address("Baker", 221, Some("B"))
  val json = address.asJson.spaces2
  println(json)
  assert(jsonDecode[Address](json) == Right(address))

import cats.*
import cats.derived.*
import cats.syntax.all.*

case class Person(
    name: String,
    age: Int
) derives Eq, Order, Show

@main def derivationDemo =
  val p1 = Person("Joe", 33)
  val p2 = Person("Moe", 45)
  println(p1.show)
  assert(p1 < p2)
  assert(p1 =!= p2)
