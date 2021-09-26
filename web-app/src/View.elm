module View exposing (..)

import Debug exposing (toString)
import Dict
import Html exposing (..)
import Html.Attributes exposing (..)
import Html.Events exposing (..)
import Json.Decode as D
import Model exposing (..)
import UI.Alerts exposing (..)
import Utils.Maybe as M


view : Model -> Html Msg
view model =
    div [ class "container" ]
        [ genericErrorAlert model
        , subscriptionSuccess model
        , unsubscriptionSuccess model
        , h1 [] [ text "Trading WS" ]
        , div [ class "input-group mb-3" ]
            [ input
                [ type_ "text"
                , id "symbol-input"
                , class "form-control"
                , autofocus True
                , placeholder "Symbol (e.g. EURUSD)"
                , onInput SymbolChanged
                , on "keydown" (ifIsEnter Subscribe)
                , value model.symbol
                ]
                []
            , div [ class "input-group-append" ]
                [ button [ class "btn btn-outline-primary btn-rounded", onClick Subscribe ]
                    [ text "Subscribe" ]
                ]
            ]
        , div [ id "sid-card", class "card" ]
            [ div [ class "sid-body" ]
                [ renderSocketId model.socketId ]
            ]
        , p [] []
        , table [ class "table table-inverse", hidden (Dict.isEmpty model.alerts) ]
            [ thead []
                [ tr []
                    [ th [] [ text "Symbol" ]
                    , th [] [ text "Ask" ]
                    , th [] [ text "Bid" ]
                    , th [] [ text "High" ]
                    , th [] [ text "Low" ]
                    , th [] [ text "Status" ]
                    , th [] []
                    ]
                ]
            , tbody [] (List.map renderAlertRow (Dict.toList model.alerts))
            ]
        ]


socketIdSpan : String -> String -> Html msg
socketIdSpan badgeClass sidText =
    span [ id "socket-id", class ("badge badge-pill badge-" ++ badgeClass) ] [ text sidText ]


renderSocketId : Maybe SocketId -> Html msg
renderSocketId ma =
    M.fold ma (socketIdSpan "danger" "<Disconnected>") (\sid -> socketIdSpan "primary" ("Socket ID: " ++ sid))


renderAlertRow : ( Symbol, Alert ) -> Html Msg
renderAlertRow ( symbol, alert ) =
    tr []
        [ th [] [ text symbol ]
        , th [] [ alert.prices.askPrice |> toString |> text ]
        , th [] [ alert.prices.bidPrice |> toString |> text ]
        , th [] [ alert.prices.high |> toString |> text ]
        , th [] [ alert.prices.low |> toString |> text ]
        , th [] [ alert.alertType |> toString |> text ]
        , th [] [ button [ class "btn btn-danger", onClick (Unsubscribe symbol), title "Unsubscribe" ] [ text "X" ] ]
        ]


ifIsEnter : msg -> D.Decoder msg
ifIsEnter msg =
    D.field "key" D.string
        |> D.andThen
            (\key ->
                if key == "Enter" then
                    D.succeed msg

                else
                    D.fail "some other key"
            )
