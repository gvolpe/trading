module View exposing (..)

import Debug exposing (toString)
import Dict
import Html exposing (..)
import Html.Attributes exposing (..)
import Html.Events exposing (..)
import Json.Decode as D
import Model exposing (..)
import UI.Alerts exposing (..)


view : Model -> Html Msg
view model =
    div [ class "container" ]
        [ genericErrorAlert model
        , subscriptionSuccess model
        , unsubscriptionSuccess model
        , h2 [ align "center" ] [ text "Trading WS" ]
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
                    , th [] [ text "Bid" ]
                    , th [] [ text "Ask" ]
                    , th [] [ text "High" ]
                    , th [] [ text "Low" ]
                    , th [] [ text "Status" ]
                    , th [] []
                    ]
                ]
            , tbody [] (List.map renderAlertRow (Dict.toList model.alerts))
            ]
        ]


renderSocketId : Maybe SocketId -> Html Msg
renderSocketId ma =
    case ma of
        Just sid ->
            span [ id "socket-id", class "badge badge-pill badge-success" ] [ text ("Socket ID: " ++ sid) ]

        Nothing ->
            div []
                [ span [ id "socket-id", class "badge badge-pill badge-secondary" ] [ text "<Disconnected>" ]
                , span [] [ text " " ]
                , button [ class "badge badge-pill badge-primary", onClick Connect ] [ text "Connect" ]
                ]


renderAlertRow : ( Symbol, Alert ) -> Html Msg
renderAlertRow ( symbol, alert ) =
    tr []
        [ th [] [ text symbol ]
        , th [] [ alert.prices.bidPrice |> toString |> text ]
        , th [] [ alert.prices.askPrice |> toString |> text ]
        , th [] [ alert.prices.high |> toString |> text ]
        , th [] [ alert.prices.low |> toString |> text ]
        , th [] [ alert.alertType |> toString |> text ]
        , th []
            [ button
                [ class "badge badge-pill badge-danger", onClick (Unsubscribe symbol), title "Unsubscribe" ]
                [ img [ src "assets/icons/delete.png", width 16, height 16 ] [] ]
            ]
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
