package trading.client
package ui

import cats.syntax.show.*

import tyrian.{ Attr, Html }
import tyrian.Html.*

private def mkAlert(property: Option[String], divId: String, status: String, message: String): Html[Msg] =
  div(
    id      := divId,
    `class` := s"alert alert-$status fade show",
    hidden(property.isEmpty)
  )(
    button(
      `class` := "close",
      attribute("aria-label", "Close"),
      onClick(Msg.CloseAlerts)
    )(
      text("x")
    ),
    text(message ++ property.getOrElse("X"))
  )

def genericErrorAlert(model: Model): Html[Msg] =
  mkAlert(model.error, "generic-error", "danger", "Error: ")

def subscriptionSuccess(model: Model): Html[Msg] =
  mkAlert(model.sub.map(_.show), "subscription-success", "success", "Subscribed to ")

def unsubscriptionSuccess(model: Model): Html[Msg] =
  mkAlert(model.unsub.map(_.show), "unsubscription-success", "warning", "Unsubscribed from ")
