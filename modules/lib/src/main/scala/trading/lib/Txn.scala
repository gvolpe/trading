package trading.lib

import scala.concurrent.duration.*

import cats.effect.{ IO, Resource }
import dev.profunktor.pulsar.Pulsar
import dev.profunktor.pulsar.transactions.{ PulsarTx, Tx }

trait Txn:
  def get: Tx

object Txn:
  def make(pulsar: Pulsar.T): Resource[IO, Txn] =
    PulsarTx.make[IO](pulsar, 30.seconds, Logger[IO].debug).map { tx =>
      new:
        def get: Tx = tx
    }

  // for unit-testing
  def dummy: Resource[IO, Txn] = Resource.pure {
    new:
      def get: Tx = ???
  }
