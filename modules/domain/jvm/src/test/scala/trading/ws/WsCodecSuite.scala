package trading.ws

import trading.domain.*
import trading.domain.generators.*

import cats.effect.IO
import cats.syntax.all.*
import io.circe.parser.decode as jsonDecode
import io.circe.syntax.*
import org.scalacheck.Gen
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

/* Golden testing would be much better but this does the trick without external libs */
object WsCodecSuite extends SimpleIOSuite with Checkers:

  val in = """
    |[
    |  {
    |    "Subscribe" : {
    |      "symbol" : "CHFUSD"
    |    }
    |  },
    |  {
    |    "Unsubscribe" : {
    |      "symbol" : "EURUSD"
    |    }
    |  }
    |]
  """.stripMargin('|').trim

  val in2 = """[{"Close":{}},{"Heartbeat":{}}]"""

  val out = """
    |[
    |  {
    |    "Attached" : {
    |      "sid" : "f74eb2bf-cead-4bad-9bd1-ed5a3aaf32ca",
    |      "onlineUsers" : 183
    |    }
    |  },
    |  {
    |    "Notification" : {
    |      "alert" : {
    |        "TradeUpdate" : {
    |          "id" : "32fc6e5a-f5e2-4c3d-b667-0ff923f6bbeb",
    |          "cid" : "5144e1dc-3337-4249-be81-9b6b0b743e4a",
    |          "status" : "Off",
    |          "createdAt" : "2021-09-16T14:00:00Z"
    |        }
    |      }
    |    }
    |  }
    |]
  """.stripMargin('|').trim

  test("roundtrip conversion of WsIn messages") {
    IO.pure {
      (jsonDecode[List[WsIn]](in), jsonDecode[List[WsIn]](in2)) match
        case (Right(res1), Right(res2)) =>
          expect.same(res1.asJson.spaces2, in) && expect.same(res2.asJson.noSpaces, in2)
        case _ =>
          failure("fail to decode")
    }
  }

  test("roundtrip conversion of WsOut messages") {
    IO.pure {
      jsonDecode[List[WsOut]](out) match
        case Right(res) =>
          expect.same(res.asJson.spaces2, out)
        case Left(e) =>
          failure(e.getMessage)
    }
  }

  test("generate WsIn JSON messages") {
    ignore("only for JSON generation") *>
      forall(Gen.listOf(wsInGen)) { out =>
        IO.println(out.asJson.spaces2).as(success)
      }
  }

  test("generate WsOut JSON messages") {
    ignore("only for JSON generation") *>
      forall(Gen.listOf(wsOutGen)) { out =>
        IO.println(out.asJson.spaces2).as(success)
      }
  }
