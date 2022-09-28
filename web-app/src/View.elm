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
                [ renderTradeStatus model.tradeStatus
                , span [] [ text " " ]
                , renderConnectionDetails model.socketId model.onlineUsers
                ]
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
            , tbody [] (List.concatMap renderAlertRow (Dict.toList model.alerts))
            ]
        ]


renderConnectionDetails : Maybe SocketId -> OnlineUsers -> Html Msg
renderConnectionDetails ma users =
    case ma of
        Just sid ->
            span []
                [ span [ id "socket-id", class "badge badge-pill badge-success" ] [ text ("Socket ID: " ++ sid) ]
                , span [] [ text " " ]
                , span [ id "online-users", class "badge badge-pill badge-success" ] [ text ("Online: " ++ toString users) ]
                ]

        Nothing ->
            span []
                [ span [ id "socket-id", class "badge badge-pill badge-secondary" ] [ text "<Disconnected>" ]
                , span [] [ text " " ]
                , button [ class "badge badge-pill badge-primary", onClick Connect ] [ text "Connect" ]
                ]


renderTradeStatus : TradeStatus -> Html Msg
renderTradeStatus ts =
    case ts of
        On ->
            span [ id "trade-status", class "badge badge-pill badge-success" ] [ text ("Trading " ++ toString ts) ]

        Off ->
            span [ id "trade-status", class "badge badge-pill badge-danger" ] [ text ("Trading " ++ toString ts) ]


alertTypeColumn : String -> String -> Html Msg
alertTypeColumn imgName val =
    th [] [ img [ src ("assets/icons/" ++ imgName ++ ".png"), width 28, height 28 ] [], text val ]


renderAlertType : AlertType -> Html Msg
renderAlertType at =
    case at of
        StrongBuy ->
            alertTypeColumn "strong-buy" "Strong Buy"

        StrongSell ->
            alertTypeColumn "strong-sell" "Strong Sell"

        Neutral ->
            alertTypeColumn "neutral" "Neutral"

        Sell ->
            alertTypeColumn "sell" "Sell"

        Buy ->
            alertTypeColumn "buy" "Buy"


renderAlertRow : ( Symbol, Alert ) -> List (Html Msg)
renderAlertRow ( symbol, alert ) =
    case alert of
        TradeAlert t ->
            [ tr []
                [ th [] [ text symbol ]
                , th [] [ t.bidPrice |> toString |> text ]
                , th [] [ t.askPrice |> toString |> text ]
                , th [] [ t.high |> toString |> text ]
                , th [] [ t.low |> toString |> text ]
                , renderAlertType t.alertType
                , th []
                    [ button
                        [ class "badge badge-pill badge-danger", onClick (Unsubscribe symbol), title "Unsubscribe" ]
                        [ img [ src "assets/icons/delete.png", width 16, height 16 ] [] ]
                    ]
                ]
            ]

        TradeUpdate _ ->
            []


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
