package trading.it

import scala.concurrent.duration.*

import trading.core.snapshots.*
import trading.domain.{ KeyExpiration, TradingStatus }
import trading.domain.generators.*
import trading.lib.{ given, * }
import trading.lib.Consumer.MsgId
import trading.lib.Logger.NoOp.given
import trading.it.suite.ResourceSuite

import cats.data.NonEmptyList
import cats.effect.*
import cats.syntax.all.*
import dev.profunktor.redis4cats.{ Redis, RedisCommands }

object RedisSuite extends ResourceSuite:

  type Res = RedisCommands[IO, String, String]

  override def sharedResource: Resource[IO, Res] =
    Redis[IO]
      .utf8("redis://localhost")
      .beforeAll(_.flushAll)

  test("snapshots reader and writer") { redis =>
    val reader = SnapshotReader.from[IO](redis)
    val writer = SnapshotWriter.from[IO](redis, KeyExpiration(30.seconds))
    val msgId  = MsgId.earliest

    ignore("FIXME: flaky CI test") *> NonEmptyList
      .of(tradeStateGen.sample.replicateA(3).toList.flatten.last)
      .traverse { ts =>
        val ex1 = ts.prices.keySet

        for
          x <- reader.latest
          _ <- writer.save(ts, msgId)
          y <- reader.latest
        yield NonEmptyList
          .of(
            expect.same(None, x),
            expect.same(Some(ts.status), y.map(_._1.status)),
            expect.same(Some(ex1), y.map(_._1.prices.keySet)),
            expect.same(Some(msgId.serialize), y.map(_._2.serialize))
          )
      }
      .map(_.flatten.reduce)

  }
