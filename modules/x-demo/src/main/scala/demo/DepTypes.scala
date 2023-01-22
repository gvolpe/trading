package demo

import cats.effect.IO

abstract class Graph[F[_], St, Dep, In, Out]:
  val dep: Dep

type CatState = Map[String, String]
type DogState = Set[Int]
type FoxState = Array[Long]

val cat = new Graph[IO, CatState, List[String], String, Unit]:
  val dep = List.empty

val dog = new Graph[IO, DogState, Vector[Int], Int, Unit]:
  val dep = Vector.empty

val fox = new Graph[IO, FoxState, Set[Long], Long, Unit]:
  val dep = Set.empty

type GraphSt[In] = In match
  case String => CatState
  case Int    => DogState
  case Long   => FoxState

type GraphDep[In] = In match
  case String => List[String]
  case Int    => Vector[Int]
  case Long   => Set[Long]

type Graf[In] = Graph[IO, GraphSt[In], GraphDep[In], In, Unit]

object Graph:
  def make[In](_dep: GraphDep[In]): Graf[In] = new:
    val dep = _dep

val _cat = Graph.make[String](List.empty)
val _dog = Graph.make[Int](Vector.empty)
val _fox = Graph.make[Long](Set.empty)

/**
 * the following does not compile as we do not declare a match type for Long
 *
 * [error] dotty.tools.dotc.core.TypeError: Match type reduction failed since selector Long [error] matches none of the
 * cases [error] [error] case String => List[String] [error] case Int => Vector[Int]
 */
//val nope = Graph.make[Long]
