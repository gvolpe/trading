module Update exposing (..)

import Browser.Dom as Dom
import Dict
import Json.Encode exposing (encode, object, string)
import Model exposing (..)
import Ports exposing (sendMessage)
import Task


encodeSymbol : String -> Json.Encode.Value
encodeSymbol symbol =
    object [ ( "symbol", string symbol ) ]


encodeSubscribe : String -> String
encodeSubscribe symbol =
    encode 2 (object [ ( "Subscribe", encodeSymbol symbol ) ])


encodeUnsubscribe : String -> String
encodeUnsubscribe symbol =
    encode 2 (object [ ( "Unsubscribe", encodeSymbol symbol ) ])


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case msg of
        NoOp ->
            ( model
            , Cmd.none
            )

        CloseAlerts ->
            ( { model | sub = Nothing, unsub = Nothing }
            , Task.attempt (\_ -> NoOp) (Dom.focus "symbol-input")
            )

        SymbolChanged symbol ->
            ( { model | symbol = symbol }
            , Cmd.none
            )

        Subscribe ->
            ( { model | sub = Just model.symbol, symbol = "" }
            , sendMessage (encodeSubscribe model.symbol)
            )

        Unsubscribe symbol ->
            ( { model | unsub = Just symbol, alerts = Dict.remove symbol model.alerts }
            , sendMessage (encodeUnsubscribe symbol)
            )

        Recv input ->
            case input of
                Attached sid ->
                    ( { model | socketId = Just sid }
                    , Cmd.none
                    )

                Notification alert ->
                    ( { model | alerts = Dict.insert alert.symbol alert model.alerts }
                    , Cmd.none
                    )

                Unknown _ ->
                    ( model
                    , Cmd.none
                    )
