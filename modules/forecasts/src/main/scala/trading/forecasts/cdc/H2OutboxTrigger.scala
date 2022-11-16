package trading.forecasts.cdc

import java.io.BufferedReader
import java.sql.{ Connection, Timestamp as SQLTimestamp }
import java.time.Instant
import java.util.UUID

import scala.util.Try

import trading.domain.*
import trading.events.*

import cats.effect.IO
import cats.effect.std.Queue
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import io.circe.parser.decode as jsonDecode
import org.h2.api.Trigger

private[cdc] object OutboxState:
  val queue: Queue[IO, OutboxEvent] =
    Queue.bounded[IO, OutboxEvent](100).unsafeRunSync()

/*
 * Due to the lack of a transactional log in H2, we can use a database trigger instead.
 * It reacts to INSERT operations on the `outbox` table, parses each row as an OutboxEvent,
 * and it adds it to the "cdc-global" state (a queue), which is then used by `OutputProducer`
 * without leaking this internal state.
 */
final class H2OutboxTrigger extends Trigger:
  override def init(
      conn: Connection,
      schemaName: String,
      triggerName: String,
      tableName: String,
      before: Boolean,
      `type`: Int
  ): Unit = ()

  private def parseRow(row: Array[Object]): Option[OutboxEvent] =
    Try {
      val eid   = EventId(row(0).asInstanceOf[UUID])
      val cid   = CorrelationId(row(1).asInstanceOf[UUID])
      val br    = row(2).asInstanceOf[BufferedReader]
      val evStr = LazyList.continually(br.readLine()).takeWhile(_ != null).mkString("\n")
      val atEvt = jsonDecode[AuthorEvent](evStr).map(_.asLeft)
      val fcEvt = jsonDecode[ForecastEvent](evStr).map(_.asRight)
      val event = atEvt.orElse(fcEvt).toOption.get
      val ts    = Timestamp(row(3).asInstanceOf[SQLTimestamp].toInstant())
      OutboxEvent(eid, cid, event, ts)
    }.toOption

  override def fire(
      conn: Connection,
      oldRow: Array[Object],
      newRow: Array[Object]
  ): Unit =
    parseRow(newRow)
      .traverse_(OutboxState.queue.offer)
      .unsafeRunSync()

  override def close(): Unit  = ()
  override def remove(): Unit = ()
