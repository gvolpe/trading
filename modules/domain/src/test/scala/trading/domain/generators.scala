package trading.domain

import java.time.Instant

import trading.commands.TradeCommand
import trading.domain.*
import trading.state.*

import org.scalacheck.{ Cogen, Gen }

object cogen {
  // TODO: When introducing newtypes we'll need this
  //implicit val askPricesCogen: Cogen[Prices.Ask] =
  //Cogen.cogenMap[AskPrice, Quantity]

  //implicit val bidPricesCogen: Cogen[Prices.Bid] =
  //Cogen.cogenMap[BidPrice, Quantity]

  //Cogen((seed, t) =>
  //c2.perturb(c1.perturb(seed, t._1), t._2)
  //)

  implicit val pricesCogen: Cogen[Prices] =
    Cogen.tuple2[Prices.Ask, Prices.Bid].contramap[Prices] { p =>
      p.ask -> p.bid
    }
}

object generators {

  val tradeActionGen: Gen[TradeAction] =
    Gen.oneOf(TradeAction.Ask, TradeAction.Bid)

  val commandIdGen: Gen[CommandId] = Gen.uuid

  val symbolGen: Gen[Symbol] =
    Gen.oneOf("EURPLN", "GBPUSD", "CADUSD", "EURUSD", "CHFUSD", "CHFEUR")

  val priceGen: Gen[Price] =
    Gen.choose(0.78346, 4.78341)

  val quantityGen: Gen[Quantity] =
    Gen.choose(1, 30)

  val sourceGen: Gen[Source] =
    Gen.const("random-feed")

  val timestampGen: Gen[Timestamp] =
    Instant.parse("2021-09-16T14:00:00.00Z")

  val createCommandGen: Gen[TradeCommand.Create] =
    for {
      i <- commandIdGen
      s <- symbolGen
      a <- tradeActionGen
      p <- priceGen
      q <- quantityGen
      c <- sourceGen
      t <- timestampGen
    } yield TradeCommand.Create(i, s, a, p, q, c, t)

  val updateCommandGen: Gen[TradeCommand.Update] =
    for {
      i <- commandIdGen
      s <- symbolGen
      a <- tradeActionGen
      p <- priceGen
      q <- quantityGen
      c <- sourceGen
      t <- timestampGen
    } yield TradeCommand.Update(i, s, a, p, q, c, t)

  val deleteCommandGen: Gen[TradeCommand.Delete] =
    for {
      i <- commandIdGen
      s <- symbolGen
      a <- tradeActionGen
      p <- priceGen
      c <- sourceGen
      t <- timestampGen
    } yield TradeCommand.Delete(i, s, a, p, c, t)

  val tradeCommandGen: Gen[TradeCommand] =
    Gen.oneOf(createCommandGen, updateCommandGen, deleteCommandGen)

  def commandsGen: List[TradeCommand] =
    Gen.listOfN(10, tradeCommandGen).sample.toList.flatten

  // ------ TradeState ------

  val askPricesGen: Gen[Prices.Ask] =
    Gen.mapOf[AskPrice, Quantity] {
      for {
        p <- Gen.choose[AskPrice](0.78346, 4.78341)
        q <- quantityGen
      } yield p -> q
    }

  val bidPricesGen: Gen[Prices.Bid] =
    Gen.mapOf[BidPrice, Quantity] {
      for {
        p <- Gen.choose[BidPrice](0.78346, 4.78341)
        q <- quantityGen
      } yield p -> q
    }

  val pricesGen: Gen[Prices] =
    for {
      a <- askPricesGen
      b <- bidPricesGen
      h <- priceGen
      l <- priceGen
    } yield Prices(a, b, h, l)

  val tradeStateGen: Gen[TradeState] =
    Gen
      .mapOf[Symbol, Prices] {
        for {
          s <- symbolGen
          p <- pricesGen
        } yield s -> p
      }
      .map(TradeState.apply)

}
