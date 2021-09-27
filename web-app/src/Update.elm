module Update exposing (..)

import Browser.Dom as Dom
import Dict
import Json.Encode exposing (encode, object, string)
import Model exposing (..)
import Task
import WS


encodeSymbol : String -> Json.Encode.Value
encodeSymbol symbol =
    object [ ( "symbol", string symbol ) ]


encodeSubscribe : String -> String
encodeSubscribe symbol =
    encode 2 (object [ ( "Subscribe", encodeSymbol symbol ) ])


encodeUnsubscribe : String -> String
encodeUnsubscribe symbol =
    encode 2 (object [ ( "Unsubscribe", encodeSymbol symbol ) ])


refocusInput : Cmd Msg
refocusInput =
    Task.attempt (\_ -> NoOp) (Dom.focus "symbol-input")


disconnected : Model -> ( Model, Cmd Msg )
disconnected model =
    ( { model | error = Just "Disconnected from server, please click on Connect." }
    , Cmd.none
    )


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case msg of
        NoOp ->
            ( model
            , Cmd.none
            )

        Connect ->
            ( { model | error = Nothing }
            , Cmd.batch [ WS.connect model.wsUrl, refocusInput ]
            )

        CloseAlerts ->
            ( { model | sub = Nothing, unsub = Nothing }
            , refocusInput
            )

        SymbolChanged symbol ->
            ( { model | symbol = symbol }
            , Cmd.none
            )

        Subscribe ->
            case model.socketId of
                Just _ ->
                    ( { model | sub = Just model.symbol, symbol = "" }
                    , WS.send (encodeSubscribe model.symbol)
                    )

                Nothing ->
                    disconnected model

        Unsubscribe symbol ->
            case model.socketId of
                Just _ ->
                    ( { model | unsub = Just symbol, alerts = Dict.remove symbol model.alerts }
                    , Cmd.batch [ WS.send (encodeUnsubscribe symbol), refocusInput ]
                    )

                Nothing ->
                    disconnected model

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

                SocketClosed ->
                    ( { model | socketId = Nothing }
                    , Cmd.none
                    )

                Unknown _ ->
                    ( model
                    , Cmd.none
                    )
