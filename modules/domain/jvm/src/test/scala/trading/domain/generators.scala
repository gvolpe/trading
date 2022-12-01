package trading.domain

import java.time.Instant
import java.util.UUID

import trading.commands.*
import trading.domain.*
import trading.events.*
import trading.state.*
import trading.ws.*

import cats.Order
import cats.syntax.all.*
import io.github.iltotore.iron.*
import org.scalacheck.{ Arbitrary, Cogen, Gen }

object arbitraries:
  import generators.*

  given Arbitrary[CommandId]    = Arbitrary(commandIdGen)
  given Arbitrary[TradeCommand] = Arbitrary(tradeCommandGen)
  given Arbitrary[Prices]       = Arbitrary(pricesGen)
  given Arbitrary[Price]        = Arbitrary(priceGen)
  given Arbitrary[Quantity]     = Arbitrary(quantityGen)
  given Arbitrary[Symbol]       = Arbitrary(symbolWithEmptyGen)
  given Arbitrary[TradeState]   = Arbitrary(tradeStateGen)
  given Arbitrary[Timestamp]    = Arbitrary(timestampGen)

object cogen:
  given uuidCogen: Cogen[UUID] =
    Cogen[(Long, Long)].contramap { uuid =>
      uuid.getLeastSignificantBits -> uuid.getMostSignificantBits
    }

  given Cogen[CommandId] =
    uuidCogen.contramap(_.value)

  given Cogen[Timestamp] =
    Cogen[String].contramap(_.toString)

  given Cogen[Quantity] =
    Cogen.cogenInt.contramap[Quantity](_.value)

  given Ordering[Price] =
    Order[Price].toOrdering

  given Cogen[Price] =
    Cogen.bigDecimal.contramap[Price](_.value)

  given Cogen[Map[Price, Quantity]] =
    Cogen.cogenMap[BigDecimal, Int].contramap[Map[Price, Quantity]] {
      _.map { (k, v) => k.value -> v.value }
    }

  given Cogen[Prices] =
    Cogen.tuple2[Prices.Ask, Prices.Bid].contramap[Prices] { p =>
      p.ask -> p.bid
    }

object generators:

  val tradeActionGen: Gen[TradeAction] =
    Gen.oneOf(TradeAction.Ask, TradeAction.Bid)

  val commandIdGen: Gen[CommandId] = Gen.uuid.map(id => CommandId(id))

  val correlationIdGen: Gen[CorrelationId] = Gen.uuid.map(id => CorrelationId(id))

  val eventIdGen: Gen[EventId] = Gen.uuid.map(id => EventId(id))

  val authorIdGen: Gen[AuthorId] =
    Gen
      .oneOf(
        "059480cc-1686-43a1-80b6-3ef0a822b725",
        "9a3b1a22-d81d-4ff4-9f01-c2b21214d8a5"
      )
      .map(id => AuthorId(UUID.fromString(id)))

  val forecastIdGen: Gen[ForecastId] =
    Gen
      .oneOf(
        "b8f7470e-34d0-467f-a5a7-0eedb496ef1a",
        "db12c580-4856-4e9b-ae57-711f5887368d",
        "64cc2cd7-51b0-4252-9f5d-7c5c656a049a"
      )
      .map(id => ForecastId(UUID.fromString(id)))

  val symbolGen: Gen[Symbol] =
    Gen
      .oneOf("EURPLN", "GBPUSD", "CADUSD", "EURUSD", "CHFUSD", "CHFEUR")
      .map(s => Symbol(s.refine))

  val symbolWithEmptyGen: Gen[Symbol] =
    Gen.frequency(
      1 -> Gen.const(Symbol.XEMPTY),
      9 -> symbolGen
    )

  val priceGen: Gen[Price] =
    Gen.choose(0.78346, 4.78341).map(x => Price(x))

  val quantityGen: Gen[Quantity] =
    Gen.choose(1, 30).map(Quantity(_))

  val sourceGen: Gen[Source] =
    Gen.const("random-feed")

  val timestampGen: Gen[Timestamp] =
    Gen.const(Timestamp(Instant.parse("2021-09-16T14:00:00.00Z")))

  // ------ ForecastCommand ------

  def forecastDescriptionGen(symbol: Symbol): Gen[ForecastDescription] =
    Gen
      .oneOf(
        s"${symbol.show} to break under support",
        s"${symbol.show} selling trend continues!",
        s"${symbol.show} is on fire!",
        s"${symbol.show} new supports and resistances",
        s"${symbol.show} short trading opportunity",
        s"${symbol.show} before the next big move"
      )
      .map(ForecastDescription(_))

  val authorNameGen: Gen[AuthorName] =
    Gen
      .oneOf("Gabriel", "Steven", "Richard", "Martin")
      .map(AuthorName(_))

  val authorWebsiteGen: Gen[Option[Website]] =
    Gen.option(Gen.oneOf("www.foobar.com", "www.trading.com").map(Website(_)))

  val authorGen: Gen[Author] =
    for
      i <- authorIdGen
      n <- authorNameGen
      w <- authorWebsiteGen
      f <- Gen.listOf(forecastIdGen)
    yield Author(i, n, w, f.toSet)

  val forecastTagGen: Gen[ForecastTag] =
    import ForecastTag.*
    Gen.frequency(
      1 -> Gen.const(Unknown),
      9 -> Gen.oneOf(Long, Short)
    )

  val forecastScoreGen: Gen[ForecastScore] =
    Gen.posNum[Int].map(ForecastScore(_))

  val forecastGen: Gen[Forecast] =
    for
      i <- forecastIdGen
      s <- symbolGen
      t <- forecastTagGen
      d <- forecastDescriptionGen(s)
      c <- forecastScoreGen
    yield Forecast(i, s, t, d, c)

  val voteResultGen: Gen[VoteResult] =
    import VoteResult.*
    Gen.oneOf(Up, Down)

  val publishCommandGen: Gen[ForecastCommand.Publish] =
    for
      i <- commandIdGen
      c <- correlationIdGen
      a <- authorIdGen
      s <- symbolGen
      d <- forecastDescriptionGen(s)
      g <- forecastTagGen
      t <- timestampGen
    yield ForecastCommand.Publish(i, c, a, s, d, g, t)

  val registerCommandGen: Gen[ForecastCommand.Register] =
    for
      i <- commandIdGen
      c <- correlationIdGen
      a <- authorNameGen
      w <- authorWebsiteGen
      t <- timestampGen
    yield ForecastCommand.Register(i, c, a, w, t)

  val voteCommandGen: Gen[ForecastCommand.Vote] =
    for
      i <- commandIdGen
      c <- correlationIdGen
      f <- forecastIdGen
      v <- voteResultGen
      t <- timestampGen
    yield ForecastCommand.Vote(i, c, f, v, t)

  def registerCommandGen_(
      _id: CommandId
  ): Gen[ForecastCommand.Register] =
    registerCommandGen.map(_.copy(id = _id))

  def publishCommandGen_(
      _id: CommandId,
      _cid: CorrelationId,
      _aid: AuthorId
  ): Gen[ForecastCommand.Publish] =
    publishCommandGen.map(_.copy(id = _id, cid = _cid, authorId = _aid))

  def voteCommandGen_(
      _id: CommandId,
      _cid: CorrelationId,
      _fid: ForecastId
  ): Gen[ForecastCommand.Vote] =
    voteCommandGen.map(_.copy(id = _id, cid = _cid, forecastId = _fid))

  val forecastCommandGen: Gen[ForecastCommand] =
    Gen.oneOf(publishCommandGen, registerCommandGen, voteCommandGen)

  val forecastCommandListGen: List[ForecastCommand] =
    Gen.listOfN(10, forecastCommandGen).sample.toList.flatten

  // ------ TradeCommand ------

  val createCommandGen: Gen[TradeCommand.Create] =
    for
      i <- commandIdGen
      d <- correlationIdGen
      s <- symbolGen
      a <- tradeActionGen
      p <- priceGen
      q <- quantityGen
      c <- sourceGen
      t <- timestampGen
    yield TradeCommand.Create(i, d, s, a, p, q, c, t)

  val updateCommandGen: Gen[TradeCommand.Update] =
    for
      i <- commandIdGen
      d <- correlationIdGen
      s <- symbolGen
      a <- tradeActionGen
      p <- priceGen
      q <- quantityGen
      c <- sourceGen
      t <- timestampGen
    yield TradeCommand.Update(i, d, s, a, p, q, c, t)

  val deleteCommandGen: Gen[TradeCommand.Delete] =
    for
      i <- commandIdGen
      d <- correlationIdGen
      s <- symbolGen
      a <- tradeActionGen
      p <- priceGen
      c <- sourceGen
      t <- timestampGen
    yield TradeCommand.Delete(i, d, s, a, p, c, t)

  val startCommandGen: Gen[SwitchCommand.Start] =
    for
      i <- commandIdGen
      d <- correlationIdGen
      t <- timestampGen
    yield SwitchCommand.Start(i, d, t)

  val stopCommandGen: Gen[SwitchCommand.Stop] =
    for
      i <- commandIdGen
      d <- correlationIdGen
      t <- timestampGen
    yield SwitchCommand.Stop(i, d, t)

  val tradeCommandGen: Gen[TradeCommand] =
    Gen.oneOf(createCommandGen, updateCommandGen, deleteCommandGen)

  val switchCommandGen: Gen[SwitchCommand] =
    Gen.oneOf(startCommandGen, stopCommandGen)

  def tradeCommandListGen: List[TradeCommand | SwitchCommand] =
    val gen = Gen.frequency[TradeCommand | SwitchCommand](
      2 -> switchCommandGen,
      9 -> tradeCommandGen
    )
    Gen.listOfN(10, gen).sample.toList.flatten

  // ------ TradeEvent ------
  val genCommandExecEvt: Gen[TradeEvent] =
    for
      i <- eventIdGen
      d <- correlationIdGen
      c <- tradeCommandGen
      t <- timestampGen
    yield TradeEvent.CommandExecuted(i, d, c, t)

  val reasonGen: Gen[Reason] =
    Gen.const(Reason("test"))

  val genCommandRejEvt: Gen[TradeEvent] =
    for
      i <- eventIdGen
      d <- correlationIdGen
      c <- tradeCommandGen
      r <- reasonGen
      t <- timestampGen
    yield TradeEvent.CommandRejected(i, d, c, r, t)

  val genStartedEvt: Gen[SwitchEvent] =
    for
      i <- eventIdGen
      d <- correlationIdGen
      t <- timestampGen
    yield SwitchEvent.Started(i, d, t)

  val genStoppedEvt: Gen[SwitchEvent] =
    for
      i <- eventIdGen
      d <- correlationIdGen
      t <- timestampGen
    yield SwitchEvent.Stopped(i, d, t)

  val genSwitchEvent: Gen[SwitchEvent] =
    Gen.oneOf(genStartedEvt, genStoppedEvt)

  val genTradeEventNoCmdExec: Gen[Either[TradeEvent, SwitchEvent]] =
    Gen.oneOf(genCommandRejEvt.map(_.asLeft), genSwitchEvent.map(_.asRight))

  // ------ TradeState ------

  val askPricesGen: Gen[Prices.Ask] =
    Gen.mapOf[AskPrice, Quantity] {
      for
        p <- priceGen
        q <- quantityGen
      yield p -> q
    }

  val bidPricesGen: Gen[Prices.Bid] =
    Gen.mapOf[BidPrice, Quantity] {
      for
        p <- priceGen
        q <- quantityGen
      yield p -> q
    }

  val pricesGen: Gen[Prices] =
    for
      a <- askPricesGen
      b <- bidPricesGen
      h <- priceGen
      l <- priceGen
    yield Prices(a, b, h, l)

  val tradingStatusGen: Gen[TradingStatus] =
    Gen.oneOf(TradingStatus.On, TradingStatus.Off)

  val tradeStateGen: Gen[TradeState] =
    Gen
      .mapOf[Symbol, Prices] {
        for
          s <- symbolGen
          p <- pricesGen
        yield s -> p
      }
      .flatMap { kv =>
        tradingStatusGen.map { st =>
          TradeState(st, kv)
        }
      }

  val idRegistryGen: Gen[IdRegistry] =
    for
      i <- commandIdGen
      t <- timestampGen
    yield IdRegistry(i, t)

  val dedupStateGen: Gen[DedupState] =
    Gen.nonEmptyListOf(idRegistryGen).map(xs => DedupState(xs.toSet))

  /* WsOut messages */

  val alertIdGen: Gen[AlertId] =
    Gen.uuid.map(id => AlertId(id))

  val socketIdGen: Gen[SocketId] =
    Gen.uuid.map(id => SocketId(id))

  val alertTypeGen: Gen[AlertType] =
    Gen.oneOf(AlertType.values.toSeq)

  val tradeAlertGen: Gen[Alert] =
    for
      i <- alertIdGen
      c <- correlationIdGen
      t <- alertTypeGen
      s <- symbolGen
      a <- priceGen
      b <- priceGen
      h <- priceGen
      l <- priceGen
      p <- timestampGen
    yield Alert.TradeAlert(i, c, t, s, a, b, h, l, p)

  val tradeUpdateGen: Gen[Alert] =
    for
      i <- alertIdGen
      c <- correlationIdGen
      s <- tradingStatusGen
      t <- timestampGen
    yield Alert.TradeUpdate(i, c, s, t)

  val wsOnlineUsersGen: Gen[WsOut] =
    Gen.choose(1, 500).map(WsOut.OnlineUsers(_))

  val wsAttachedGen: Gen[WsOut] =
    socketIdGen.map(WsOut.Attached(_))

  val wsNotificationGen: Gen[WsOut] =
    Gen.oneOf(tradeAlertGen, tradeUpdateGen).map(WsOut.Notification(_))

  val wsOutGen: Gen[WsOut] =
    Gen.oneOf(wsAttachedGen, wsNotificationGen, wsOnlineUsersGen)

  /* WsIn messages */

  val wsCloseGen: Gen[WsIn] =
    Gen.const(WsIn.Close)

  val wsHeartbeatGen: Gen[WsIn] =
    Gen.const(WsIn.Heartbeat)

  val wsSubscribeGen: Gen[WsIn] =
    symbolGen.map(WsIn.Subscribe(_))

  val wsUnsubscribeGen: Gen[WsIn] =
    symbolGen.map(WsIn.Unsubscribe(_))

  val wsInGen: Gen[WsIn] =
    Gen.oneOf(wsCloseGen, wsHeartbeatGen, wsSubscribeGen, wsUnsubscribeGen)
