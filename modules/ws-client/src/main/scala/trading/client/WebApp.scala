package trading.client

import scala.scalajs.js.annotation.JSExportTopLevel

import tyrian.*

@JSExportTopLevel("TyrianApp")
object Main extends TyrianApp[Msg, Model]:

  def init(flags: Map[String, String]): (Model, Cmd[Msg]) =
    Model.init -> Cmd.Empty

  def update(msg: Msg, model: Model): (Model, Cmd[Msg]) =
    runUpdates(msg, model)

  def view(model: Model): Html[Msg] =
    render(model)

  def subscriptions(model: Model): Sub[Msg] =
    wsSub(model.ws)
