package trading.domain

import derevo.cats.show
import derevo.derive
import io.circe.generic.extras.semiauto._
import io.circe.{ Decoder, Encoder }

@derive(show)
sealed trait AlertType

object AlertType {
  case object StrongBuy  extends AlertType
  case object StrongSell extends AlertType
  case object Neutral    extends AlertType
  case object Buy        extends AlertType
  case object Sell       extends AlertType

  implicit val alertTypeEncoder: Encoder[AlertType] = deriveEnumerationEncoder
  implicit val alertTypeDecoder: Decoder[AlertType] = deriveEnumerationDecoder
}
