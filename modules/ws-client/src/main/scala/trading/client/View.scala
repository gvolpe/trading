package trading.client

import cats.syntax.show.*

import trading.domain.*
import trading.client.ui.*

import tyrian.*
import tyrian.Html.*

private val assets = "/home/gvolpe/workspace/trading/web-app/assets/"

def render(model: Model): Html[Msg] =
  div(`class` := "container")(
    genericErrorAlert(model),
    subscriptionSuccess(model),
    unsubscriptionSuccess(model),
    h2(attribute("align", "center"))(text("Trading WS")), // h2(align := "center") DOES NOT WORK
    div(`class` := "input-group mb-3")(
      input(
        `type` := "text",
        id     := "symbol-input",
        autoFocus,
        placeholder := "Symbol (e.g. EURUSD)",
        onInput(Msg.SymbolChanged(model.symbol.get)),
        onKeyDown(onEnter(Msg.Subscribe)),
        //value := model.symbol.show
      ),
      div(`class` := "input-group-append")(
        button(
          `class` := "btn btn-outline-primary btn-rounded",
          onClick(Msg.Subscribe)
        )(
          text("Subscribe")
        )
      )
    ),
    div(id := "sid-card", `class` := "card")(
      div(`class` := "sid-body")(
        renderTradeStatus(model.tradingStatus),
        span(text(" ")),
        renderSocketId(model.socketId)
      )
    ),
    p(),
    table(
      `class` := "table table-inverse",
      hidden // FIXME: hidden (Dict.isEmpty model.alerts)
    )(
      thead(
        tr(
          th(text("Symbol")),
          th(text("Bid")),
          th(text("Ask")),
          th(text("High")),
          th(text("Low")),
          th(text("Status")),
          th()
        )
      ),
      tbody(model.alerts.toList.flatMap((sl, alt) => renderAlertRow(sl)(alt)))
    )
  )

def renderSocketId: Option[SocketId] => Html[Msg] =
  case Some(sid) =>
    span(id := "socket-id", `class` := "badge badge-pill badge-success")(text(s"Socket ID: ${sid.show}"))

  case None =>
    span(
      span(id := "socket-id", `class` := "badge badge-pill badge-secondary")(text("<Disconnected>")),
      span(text(" ")),
      button(`class` := "badge badge-pill badge-primary", onClick(Msg.Connect))(text("Connect"))
    )

def renderTradeStatus: TradingStatus => Html[Msg] =
  case TradingStatus.On =>
    span(id := "trade-status", `class` := "badge badge-pill badge-success")(text("Trading On"))

  case TradingStatus.Off =>
    span(id := "trade-status", `class` := "badge badge-pill badge-danger")(text("Trading Off"))

// FIXME: size and hard-coded assets directory
def alertTypeColumn(imgName: String, value: String): Html[Msg] =
  th(
    img(
      src := s"$assets/icons/$imgName.png"
      //width  := 28,
      //height := 28
    ),
    text(value)
  )

def renderAlertType: AlertType => Html[Msg] =
  case AlertType.StrongBuy =>
    alertTypeColumn("strong-buy", "Strong Buy")

  case AlertType.StrongSell =>
    alertTypeColumn("strong-sell", "Strong Sell")

  case AlertType.Neutral =>
    alertTypeColumn("neutral", "Neutral")

  case AlertType.Sell =>
    alertTypeColumn("sell", "Sell")

  case AlertType.Buy =>
    alertTypeColumn("buy", "Buy")

def renderAlertRow(symbol: Symbol): Alert => List[Html[Msg]] =
  case t: Alert.TradeAlert =>
    List(
      tr(
        th(text(symbol.show)),
        th(text(t.bidPrice.show)),
        th(text(t.askPrice.show)),
        th(text(t.high.show)),
        th(text(t.low.show)),
        renderAlertType(t.alertType),
        th(
          button(
            `class` := "badge badge-pill badge-danger",
            onClick(Msg.Unsubscribe(symbol)),
            title := "Unsubscribe"
          )(
            img(
              src := s"$assets/icons/delete.png"
              //width := 16,
              //height := 16
            )
          )
        )
      )
    )

  case _: Alert.TradeUpdate =>
    List.empty

def onEnter(msg: Msg): PartialFunction[Tyrian.KeyboardEvent, Msg] =
  case ev if ev.key == "Enter" => msg
