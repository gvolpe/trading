package trading.trace
package tracer

import cats.effect.{ IO, Resource }
import natchez.EntryPoint
import natchez.honeycomb.Honeycomb as NatchezHoneycomb

object Honeycomb:
  def makeEntryPoint(
      key: Config.HoneycombApiKey
  ): Resource[IO, EntryPoint[IO]] =
    NatchezHoneycomb.entryPoint[IO]("trading-app") { ep =>
      IO {
        ep.setWriteKey(key.value)
          .setDataset("demo")
          .build
      }
    }
