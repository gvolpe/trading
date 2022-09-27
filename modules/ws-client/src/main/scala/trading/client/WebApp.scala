package trading.client

import scala.scalajs.js.annotation.JSExportTopLevel

import cats.effect.IO

import tyrian.*

@JSExportTopLevel("TyrianApp")
object WebApp extends TyrianApp[Msg, Model]:

  def init(flags: Map[String, String]): (Model, Cmd[IO, Msg]) =
    Model.init -> Cmd.None

  def update(model: Model): Msg => (Model, Cmd[IO, Msg]) =
    runUpdates(_, model)

  def view(model: Model): Html[Msg] =
    render(model)

  def subscriptions(model: Model): Sub[IO, Msg] =
    wsSub(model.ws)
