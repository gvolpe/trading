module UI.Alerts exposing (..)

import Html exposing (..)
import Html.Attributes exposing (..)
import Html.Events exposing (..)
import Model exposing (..)
import Utils.Maybe as M


type alias DivId =
    String


type alias AlertClass =
    String


type alias AlertMessage =
    String


mkAlert : Maybe String -> DivId -> AlertClass -> AlertMessage -> Html Msg
mkAlert property divId status message =
    div [ hidden (M.isEmpty property), id divId, class ("alert alert-" ++ status ++ " fade show") ]
        [ button
            [ class "close"
            , attribute "aria-label" "Close"
            , onClick CloseAlerts
            ]
            [ text "x" ]
        , text (message ++ Maybe.withDefault "X" property)
        ]


genericErrorAlert : Model -> Html Msg
genericErrorAlert model =
    mkAlert model.error "generic-error" "danger" "Error: "


subscriptionSuccess : Model -> Html Msg
subscriptionSuccess model =
    mkAlert model.sub "subscription-success" "success" "Subscribed to "


unsubscriptionSuccess : Model -> Html Msg
unsubscriptionSuccess model =
    mkAlert model.unsub "unsubscription-success" "warning" "Unsubscribed from "
