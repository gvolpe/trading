package trading.state

import scala.concurrent.duration.*

import trading.domain.*

import cats.syntax.all.*
import cats.{ Eq, Show }
import io.circe.Codec

// it should be enough to keep track of the ids processed in the past 5 mins or so
final case class DedupState(
    ids: Set[IdRegistry]
):
  def removeOld(now: Timestamp): Set[IdRegistry] =
    ids.filterNot(_.ts.value.isBefore(now.value.minusSeconds(5.seconds.toSeconds)))

final case class IdRegistry(
    id: CommandId,
    ts: Timestamp
)

// TODO: Use kittens when published
object IdRegistry:
  given Eq[IdRegistry]   = Eq.and(Eq.by(_.id), Eq.by(_.ts))
  given Show[IdRegistry] = Show.show[IdRegistry](_.toString)

object DedupState:
  def empty: DedupState = DedupState(Set.empty)

  given Eq[DedupState]   = Eq.by(_.ids)
  given Show[DedupState] = Show[Set[IdRegistry]].contramap[DedupState](_.ids)
