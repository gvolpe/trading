package trading.state

import scala.concurrent.duration.*

import trading.domain.*

import cats.derived.*
import cats.syntax.all.*
import cats.{ Eq, Show }

// it should be enough to keep track of the ids processed in the past 5 mins or so
final case class DedupState(
    ids: Set[IdRegistry]
) derives Eq, Show:
  def removeOld(now: Timestamp): Set[IdRegistry] =
    ids.filterNot(_.ts.value.isBefore(now.value.minusSeconds(DedupState.Threshold.toSeconds)))

final case class IdRegistry(
    id: CommandId,
    ts: Timestamp
) derives Eq, Show

object DedupState:
  def empty: DedupState = DedupState(Set.empty)

  val Threshold = 5.minutes
