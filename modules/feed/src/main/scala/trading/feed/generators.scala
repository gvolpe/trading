package trading.feed

import java.time.Instant

import trading.commands.TradeCommand
import trading.domain._

import org.scalacheck.Gen

object generators {

  val tradeActionGen: Gen[TradeAction] =
    Gen.oneOf(TradeAction.Ask, TradeAction.Bid)

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

  val addCommandGen: Gen[TradeCommand.Add] =
    for {
      s <- symbolGen
      a <- tradeActionGen
      p <- priceGen
      q <- quantityGen
      c <- sourceGen
      t <- timestampGen
    } yield TradeCommand.Add(s, a, p, q, c, t)

  val deleteCommandGen: Gen[TradeCommand.Delete] =
    for {
      s <- symbolGen
      a <- tradeActionGen
      p <- priceGen
      c <- sourceGen
      t <- timestampGen
    } yield TradeCommand.Delete(s, a, p, c, t)

  val tradeCommandGen: Gen[TradeCommand] =
    Gen.oneOf(addCommandGen, deleteCommandGen)

  def commandsGen: List[TradeCommand] =
    Gen.listOfN(10, tradeCommandGen).sample.toList.flatten

}
