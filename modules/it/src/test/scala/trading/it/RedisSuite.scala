package trading.it

import scala.concurrent.duration.*

import java.util.UUID

import trading.core.dedup.DedupRegistry
import trading.core.snapshots.*
import trading.domain.{ AppId, KeyExpiration }
import trading.domain.generators.*
import trading.forecasts.Config.{ AuthorExpiration, ForecastExpiration }
import trading.forecasts.store.*
import trading.lib.{ given, * }
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

    NonEmptyList
      .of(tradeStateGen.sample.replicateA(3).toList.flatten.last)
      .traverse { ts =>
        val ex1 = ts.prices.keySet

        for
          x <- reader.latest
          _ <- writer.save(ts)
          y <- reader.latest
        yield NonEmptyList
          .of(
            expect.same(None, x),
            expect.same(Some(ts.status), y.map(_.status)),
            expect.same(Some(ex1), y.map(_.prices.keySet))
          )
      }
      .map(_.flatten.reduce)

  }

  test("author store") { redis =>
    val store = AuthorStore.from(redis, AuthorExpiration(30.seconds))

    NonEmptyList
      .of(authorGen.sample.replicateA(3).toList.flatten.last)
      .traverse { author =>
        for
          x <- store.fetch(author.id)
          _ <- store.save(author)
          y <- store.fetch(author.id)
        yield NonEmptyList.of(
          expect.same(None, x),
          expect.same(Some(author), y)
        )
      }
      .map(_.flatten.reduce)
  }

  test("forecast store") { redis =>
    val store = ForecastStore.from(redis, ForecastExpiration(30.seconds))

    NonEmptyList
      .of(forecastGen.sample.replicateA(3).toList.flatten.last)
      .traverse { fc =>
        for
          x <- store.fetch(fc.id)
          _ <- store.save(fc)
          y <- store.fetch(fc.id)
        yield NonEmptyList.of(
          expect.same(None, x),
          expect.same(Some(fc), y)
        )
      }
      .map(_.flatten.reduce)
  }

  test("dedup registry") { redis =>
    def makeRegistry = DedupRegistry.from(redis, AppId("test-app", UUID.randomUUID()), KeyExpiration(30.seconds))

    val reg1 = makeRegistry
    val reg2 = makeRegistry
    val reg3 = makeRegistry

    val st1 = dedupStateGen.sample.replicateA(3).toList.flatten.last
    val st2 = dedupStateGen.sample.replicateA(3).toList.flatten.last
    val st3 = dedupStateGen.sample.replicateA(3).toList.flatten.last

    val union12  = st1.ids.map(_.id).union(st2.ids.map(_.id))
    val union123 = union12.union(st3.ids.map(_.id))

    for
      x1 <- reg1.get
      x2 <- reg2.get
      x3 <- reg2.get
      _  <- reg1.save(st1)
      y1 <- reg1.get
      _  <- reg2.save(st2)
      y2 <- reg1.get
      y3 <- reg3.get
      _  <- reg3.save(st3)
      y4 <- reg2.get
    yield NonEmptyList
      .of(
        expect(x1.ids.isEmpty),
        expect(x2.ids.isEmpty),
        expect(x3.ids.isEmpty),
        expect.same(st1.ids.map(_.id), y1.ids.map(_.id)),
        expect.same(union12, y2.ids.map(_.id)),
        expect.same(y2.ids.map(_.id), y3.ids.map(_.id)),
        expect.same(union123, y4.ids.map(_.id))
      )
      .reduce
  }
