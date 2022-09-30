package trading.client

import trading.client.ui.*
import trading.domain.*

import cats.syntax.show.*

import tyrian.{ Html, Tyrian }
import tyrian.Html.*

def render(model: Model): Html[Msg] =
  div(`class` := "container")(
    genericErrorAlert(model),
    subscriptionSuccess(model),
    unsubscriptionSuccess(model),
    h2(attribute("align", "center"))(text("Trading WS")),
    div(`class` := "input-group mb-3")(
      input(
        `type` := "text",
        id     := "symbol-input",
        autoFocus,
        placeholder := "Symbol (e.g. EURUSD)",
        onInput(s => Msg.SymbolChanged(InputText(s))),
        onKeyDown(subscribeOnEnter),
        value := model.input.value
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
        span(" "),
        renderConnectionDetails(model.socket.id, model.onlineUsers)
      )
    ),
    p(),
    table(`class` := "table table-inverse", hidden(model.alerts.isEmpty))(
      thead(
        tr(
          th("Symbol"),
          th("Bid"),
          th("Ask"),
          th("High"),
          th("Low"),
          th("Status"),
          th()
        )
      ),
      tbody(
        model.alerts.toList.flatMap((sl, alt) => renderAlertRow(sl)(alt))
      )
    )
  )

def renderConnectionDetails: (Option[SocketId], Int) => Html[Msg] =
  case (Some(sid), online) =>
    span(
      span(id := "socket-id", `class` := "badge badge-pill badge-success")(text(s"Socket ID: ${sid.show}")),
      span(text(" ")),
      span(id := "online-users", `class` := "badge badge-pill badge-success")(text(s"Online: ${online.show}"))
    )

  case (None, users) =>
    span(
      span(id := "socket-id", `class` := "badge badge-pill badge-secondary")(text("<Disconnected>")),
      span(text(" ")),
      button(`class` := "badge badge-pill badge-primary", onClick(WsMsg.Connecting.asMsg))(text("Connect"))
    )

def renderTradeStatus: TradingStatus => Html[Msg] =
  case TradingStatus.On =>
    span(id := "trade-status", `class` := "badge badge-pill badge-success")(text("Trading On"))

  case TradingStatus.Off =>
    span(id := "trade-status", `class` := "badge badge-pill badge-danger")(text("Trading Off"))

def alertTypeColumn(imgName: String, value: String): Html[Msg] =
  th(
    img(
      src := s"assets/icons/$imgName.png",
      attribute("width", "28"),
      attribute("height", "28")
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
        th(symbol.show),
        th(t.bidPrice.show),
        th(t.askPrice.show),
        th(t.high.show),
        th(t.low.show),
        renderAlertType(t.alertType),
        th(
          button(
            `class` := "badge badge-pill badge-danger",
            onClick(Msg.Unsubscribe(symbol)),
            title := "Unsubscribe"
          )(
            img(
              src := "assets/icons/delete.png",
              attribute("width", "16"),
              attribute("height", "16")
            )
          )
        )
      )
    )

  case _: Alert.TradeUpdate =>
    List.empty

def subscribeOnEnter: Tyrian.KeyboardEvent => Msg =
  case ev if ev.key == "Enter" => Msg.Subscribe
  case _                       => Msg.NoOp
