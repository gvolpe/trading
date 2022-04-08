package trading.domain

import java.util.UUID

import cats.Show
import cats.syntax.show.*

case class AppId(name: String, id: UUID)

object AppId:
  given Show[AppId] = Show.show(x => s"${x.name}-${x.id.show}")
