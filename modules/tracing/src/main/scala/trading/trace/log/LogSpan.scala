package trading.trace.log

import trading.lib.Logger

import cats.effect.Ref
import cats.effect.*
import cats.effect.Resource.ExitCase
import cats.effect.Resource.ExitCase.*
import cats.syntax.all.*
import java.time.Instant
import java.util.UUID
import natchez.*
import natchez.TraceValue.*
import io.circe.Json
import io.circe.Encoder
import io.circe.syntax.*
import io.circe.JsonObject
import java.net.URI

// NOTICE: Copied and adapted from the `natchez-log` module: https://github.com/tpolecat/natchez/blob/series/0.1/modules/log/shared/src/main/scala/LogSpan.scala
private[log] final case class LogSpan[F[_]: Logger: Sync](
    service: String,
    name: String,
    sid: UUID,
    parent: Option[Either[UUID, LogSpan[F]]],
    traceUUID: UUID,
    timestamp: Instant,
    fields: Ref[F, Map[String, Json]],
    children: Ref[F, List[JsonObject]]
) extends Span[F]:
  import LogSpan.*

  def parentId: Option[UUID] =
    parent.map(_.fold(identity, _.traceUUID))

  def get(key: String): F[Option[Json]] =
    fields.get.map(_.get(key))

  def kernel: F[Kernel] =
    Kernel(
      Map(
        Headers.TraceId -> traceUUID.toString,
        Headers.SpanId  -> sid.toString
      )
    ).pure[F]

  def put(fields: (String, TraceValue)*): F[Unit] =
    putAny(fields.map { case (k, v) => (k -> v.asJson) }*)

  def putAny(fields: (String, Json)*): F[Unit] =
    this.fields.update(_ ++ fields.toMap)

  def span(label: String): Resource[F, Span[F]] =
    Span.putErrorFields(Resource.makeCase(LogSpan.child(this, label))(LogSpan.finishChild[F]).widen)

  def json(finish: Instant, exitCase: ExitCase): F[JsonObject] =
    (fields.get, children.get).mapN { (fs, cs) =>

      // Assemble our JSON object such that the Natchez fields always come first, in the same
      // order, followed by error fields (if any), followed by span fields.

      def exitFields(ex: Throwable): List[(String, Json)] =
        List(
          "exit.case"             -> "error".asJson,
          "exit.error.class"      -> ex.getClass.getName.asJson,
          "exit.error.message"    -> ex.getMessage.asJson,
          "exit.error.stackTrace" -> ex.getStackTrace.map(_.toString).asJson
        )

      val fields: List[(String, Json)] =
        List(
          "name"            -> name.asJson,
          "service"         -> service.asJson,
          "timestamp"       -> timestamp.asJson,
          "duration_ms"     -> (finish.toEpochMilli - timestamp.toEpochMilli).asJson,
          "trace.span_id"   -> sid.asJson,
          "trace.parent_id" -> parentId.asJson,
          "trace.trace_id"  -> traceUUID.asJson
        ) ++ {
          exitCase match {
            case Succeeded           => List("exit.case" -> "succeeded".asJson)
            case Canceled            => List("exit.case" -> "canceled".asJson)
            case Errored(ex: Fields) => exitFields(ex) ++ ex.fields.toList.map { case (k, v) => (k, v.asJson) }
            case Errored(ex)         => exitFields(ex)
          }
        } ++ fs ++ List("children" -> cs.reverse.map(Json.fromJsonObject).asJson)

      JsonObject.fromIterable(fields)

    }

  def traceId: F[Option[String]] =
    traceUUID.toString.some.pure[F]

  def spanId: F[Option[String]] =
    sid.toString.some.pure[F]

  def traceUri: F[Option[URI]] = none.pure[F]

private[log] object LogSpan:

  implicit val EncodeTraceValue: Encoder[TraceValue] =
    Encoder.instance {
      case StringValue(s)                       => s.asJson
      case BooleanValue(b)                      => b.asJson
      case NumberValue(n: java.lang.Byte)       => n.asJson
      case NumberValue(n: java.lang.Short)      => n.asJson
      case NumberValue(n: java.lang.Integer)    => n.asJson
      case NumberValue(n: java.lang.Long)       => n.asJson
      case NumberValue(n: java.lang.Float)      => n.asJson
      case NumberValue(n: java.lang.Double)     => n.asJson
      case NumberValue(n: java.math.BigDecimal) => n.asJson
      case NumberValue(n: java.math.BigInteger) => n.asJson
      case NumberValue(n: BigDecimal)           => n.asJson
      case NumberValue(n: BigInt)               => n.asJson
      case NumberValue(n)                       => n.doubleValue.asJson
    }

  object Headers:
    val TraceId = "X-Natchez-Trace-Id"
    val SpanId  = "X-Natchez-Parent-Span-Id"

  private def uuid[F[_]: Sync]: F[UUID] =
    Sync[F].delay(UUID.randomUUID)

  private def now[F[_]: Sync]: F[Instant] =
    Sync[F].delay(Instant.now)

  def finish[F[_]: Sync: Logger](format: Json => String): (LogSpan[F], ExitCase) => F[Unit] = (span, exitCase) =>
    for
      n <- now
      j <- span.json(n, exitCase)
      _ <- span.parent match
            case None | Some(Left(_)) => Logger[F].info(format(Json.fromJsonObject(j)))
            case Some(Right(s))       => s.children.update(j :: _)
    yield ()

  def finishChild[F[_]: Sync: Logger]: (LogSpan[F], ExitCase) => F[Unit] =
    finish(_ => sys.error("implementation error; child JSON should never be logged"))

  def child[F[_]: Sync: Logger](
      parent: LogSpan[F],
      name: String
  ): F[LogSpan[F]] =
    for
      spanId    <- uuid[F]
      timestamp <- now[F]
      fields    <- Ref[F].of(Map.empty[String, Json])
      children  <- Ref[F].of(List.empty[JsonObject])
    yield LogSpan(
      service = parent.service,
      name = name,
      sid = spanId,
      parent = Some(Right(parent)),
      traceUUID = parent.traceUUID,
      timestamp = timestamp,
      fields = fields,
      children = children
    )

  def root[F[_]: Sync: Logger](
      service: String,
      name: String
  ): F[LogSpan[F]] =
    for
      spanId    <- uuid[F]
      traceUUID <- uuid[F]
      timestamp <- now[F]
      fields    <- Ref[F].of(Map.empty[String, Json])
      children  <- Ref[F].of(List.empty[JsonObject])
    yield LogSpan(
      service = service,
      name = name,
      sid = spanId,
      parent = None,
      traceUUID = traceUUID,
      timestamp = timestamp,
      fields = fields,
      children = children
    )

  def fromKernel[F[_]: Sync: Logger](
      service: String,
      name: String,
      kernel: Kernel
  ): F[LogSpan[F]] =
    for
      traceUUID <- Sync[F].catchNonFatal(UUID.fromString(kernel.toHeaders(Headers.TraceId)))
      parentId  <- Sync[F].catchNonFatal(UUID.fromString(kernel.toHeaders(Headers.SpanId)))
      spanId    <- uuid[F]
      timestamp <- now[F]
      fields    <- Ref[F].of(Map.empty[String, Json])
      children  <- Ref[F].of(List.empty[JsonObject])
    yield LogSpan(
      service = service,
      name = name,
      sid = spanId,
      parent = Some(Left(parentId)),
      traceUUID = traceUUID,
      timestamp = timestamp,
      fields = fields,
      children = children
    )

  def fromKernelOrElseRoot[F[_]: Sync: Logger](
      service: String,
      name: String,
      kernel: Kernel
  ): F[LogSpan[F]] =
    fromKernel(service, name, kernel).recoverWith { case _: NoSuchElementException =>
      root(service, name)
    }
