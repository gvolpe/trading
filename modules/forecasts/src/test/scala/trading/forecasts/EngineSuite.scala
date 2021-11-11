package trading.forecasts

import java.time.Instant
import java.util.UUID

import trading.IsUUID
import trading.commands.ForecastCommand
import trading.domain.TradingStatus.*
import trading.domain.*
import trading.events.*
import trading.forecasts.store.AuthorStore.{ AuthorNotFound, DuplicateAuthorError }
import trading.forecasts.store.*
import trading.lib.*
import trading.state.*

import cats.data.NonEmptyList
import cats.effect.IO
import cats.syntax.all.*
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers
import cats.effect.kernel.Ref

object EngineSuite extends SimpleIOSuite with Checkers:
  val id  = CommandId(UUID.randomUUID())
  val cid = CorrelationId(UUID.randomUUID())
  val ts  = Timestamp(Instant.parse("2021-09-16T14:00:00.00Z"))

  val genId = UUID.randomUUID()

  val authorId   = AuthorId(genId)
  val authorName = AuthorName("swilson")

  val eventId = EventId(genId)

  given GenUUID[IO] with
    def make[A: IsUUID]: IO[A] = IO.pure(IsUUID[A].iso.get(genId))

  given Time[IO] with
    def timestamp: IO[Timestamp] = IO.pure(ts)

  def mkAuthorStore(
      atRef: Ref[IO, List[Author]],
      fcRef: Ref[IO, Map[AuthorId, List[ForecastId]]]
  ): AuthorStore[IO] = new:
    def fetch(id: AuthorId): IO[Option[Author]] =
      atRef.get.map(_.collectFirstSome(at => (at.id === id).guard[Option].as(at)))

    def save(author: Author): IO[Unit] =
      atRef.modify {
        case xs if xs.find(_.name === author.name).nonEmpty => xs -> DuplicateAuthorError(author.name).raiseError
        case xs => (xs :+ author) -> fcRef.update(_.updated(author.id, List.empty))
      }.flatten

    def addForecast(id: AuthorId, fid: ForecastId): IO[Unit] =
      fcRef.update(_.updatedWith(id) {
        case Some(xs) => Some(xs :+ fid)
        case None     => Some(List(fid))
      })

  def mkForecastStore(
      fcRef: Ref[IO, List[Forecast]],
      vtRef: Ref[IO, Map[ForecastId, List[VoteResult]]]
  ): ForecastStore[IO] = new:
    def fetch(fid: ForecastId): IO[Option[Forecast]] =
      fcRef.get.map(_.collectFirstSome(fc => (fc.id === fid).guard[Option].as(fc)))

    def save(forecast: Forecast): IO[Unit] =
      fcRef.modify {
        case xs if xs.contains(forecast) => xs               -> IO.unit
        case xs                          => (xs :+ forecast) -> vtRef.update(_.updated(forecast.id, List.empty))
      }.flatten

    def castVote(fid: ForecastId, res: VoteResult): IO[Unit] =
      vtRef.update(_.updatedWith(fid) {
        case Some(xs) => Some(xs :+ res)
        case None     => Some(List(res))
      })

  // TODO: add other events and make it property-based
  test("Forecasts engine") {
    for
      authors   <- IO.ref(none[AuthorEvent])
      forecasts <- IO.ref(none[ForecastEvent])
      p1 = Producer.test(authors)
      p2 = Producer.test(forecasts)
      atRef   <- IO.ref(List.empty[Author])
      fcRef   <- IO.ref(List.empty[Forecast])
      atfcRef <- IO.ref(Map.empty[AuthorId, List[ForecastId]])
      fcvtRef <- IO.ref(Map.empty[ForecastId, List[VoteResult]])
      atStore = mkAuthorStore(atRef, atfcRef)
      fcStore = mkForecastStore(fcRef, fcvtRef)
      engine  = Engine.make(p1, p2, atStore, fcStore)

      // register author
      _    <- engine.run(ForecastCommand.Register(id, cid, authorName, None, ts))
      ae1  <- authors.get
      fe1  <- forecasts.get
      as1  <- atRef.get
      fs1  <- fcRef.get
      afs1 <- atfcRef.get
      fvs1 <- fcvtRef.get

      res1 = NonEmptyList
        .of(
          expect.same(ae1, Some(AuthorEvent.Registered(eventId, cid, authorId, authorName, ts))),
          expect.same(as1, List(Author(authorId, authorName, None, Nil))),
          expect.same(afs1, Map(authorId -> Nil)),
          expect(fe1.isEmpty),
          expect(fs1.isEmpty),
          expect(fvs1.isEmpty)
        )
        .reduce

      // try to register same author again
      _    <- engine.run(ForecastCommand.Register(id, cid, authorName, None, ts))
      ae2  <- authors.get
      fe2  <- forecasts.get
      as2  <- atRef.get
      fs2  <- fcRef.get
      afs2 <- atfcRef.get
      fvs2 <- fcvtRef.get

      res2 = NonEmptyList
        .of(
          expect.same(ae2, Some(AuthorEvent.NotRegistered(eventId, cid, authorName, Reason("Duplicate username"), ts))),
          expect.same(as2, List(Author(authorId, authorName, None, Nil))),
          expect.same(afs2, Map(authorId -> Nil)),
          expect(fe2.isEmpty),
          expect(fs2.isEmpty),
          expect(fvs2.isEmpty)
        )
        .reduce
    yield res1 && res2
  }
