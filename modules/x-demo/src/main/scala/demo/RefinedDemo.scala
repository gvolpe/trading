package demo

import eu.timepit.refined.*
import eu.timepit.refined.types.numeric.PosInt

@main def refinedDemo =
  val i1: Either[String, PosInt] = PosInt.from(5)
  val i2: Either[String, PosInt] = PosInt.from(-5)
  assert(i1.isRight)
  assert(i2.isLeft)
