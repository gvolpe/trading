package trading.lib

import java.nio.charset.StandardCharsets.UTF_8

import cats.Inject
import io.circe.*
import io.circe.parser.decode
import io.circe.syntax.*

object inject:
  given circeBytesInject[T: Decoder: Encoder]: Inject[T, Array[Byte]] with
    val inj: T => Array[Byte] =
      _.asJson.noSpaces.getBytes(UTF_8)

    val prj: Array[Byte] => Option[T] =
      bytes => decode[T](new String(bytes, UTF_8)).toOption
