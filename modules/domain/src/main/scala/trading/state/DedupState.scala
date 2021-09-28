package trading.state

import java.time.Instant

import scala.concurrent.duration._

import trading.domain._

import derevo.cats._
import derevo.derive

// it should be enough to keep track of the ids processed in the past 5 mins or so
@derive(eqv, show)
final case class DedupState(
    ids: Set[IdRegistry]
) {
  // TODO: Should probably be moved to a Ref + Instant.now is effectful
  def removeOld: Set[IdRegistry] =
    ids.filter(_.ts.isBefore(Instant.now().minusSeconds(5.seconds.toSeconds)))
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
