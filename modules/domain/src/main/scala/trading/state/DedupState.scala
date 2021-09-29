package trading.state

import scala.concurrent.duration._

import trading.domain._

import derevo.cats._
import derevo.derive

// it should be enough to keep track of the ids processed in the past 5 mins or so
@derive(eqv, show)
final case class DedupState(
    ids: Set[IdRegistry]
) {
  def removeOld(now: Timestamp): Set[IdRegistry] =
    ids.filterNot(_.ts.isBefore(now.minusSeconds(5.seconds.toSeconds)))
}

@derive(eqv, show)
final case class IdRegistry(
    id: CommandId,
    ts: Timestamp
)

object DedupState {
  def empty: DedupState = DedupState(Set.empty)

  // TODO: Semilattice instance?
}
