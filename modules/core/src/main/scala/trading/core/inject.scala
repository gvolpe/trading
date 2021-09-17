package trading.core

import java.nio.charset.StandardCharsets.UTF_8

import cats.Inject
import io.circe._
import io.circe.parser.decode
import io.circe.syntax._

object inject {

  implicit def circeBytesInject[T: Encoder: Decoder]: Inject[T, Array[Byte]] =
    new Inject[T, Array[Byte]] {
      val inj: T => Array[Byte] =
        _.asJson.noSpaces.getBytes(UTF_8)

      val prj: Array[Byte] => Option[T] =
        bytes => decode[T](new String(bytes, UTF_8)).toOption
    }

}
