module Subscriptions exposing (..)

import Json.Decode exposing (..)
import Model exposing (..)
import WS


alertTypeDecoder : Decoder AlertType
alertTypeDecoder =
    oneOf
        [ field "Buy" (succeed Buy)
        , field "Sell" (succeed Sell)
        , field "Neutral" (succeed Neutral)
        , field "StrongBuy" (succeed StrongBuy)
        , field "StrongSell" (succeed StrongSell)
        ]


alertValueDecoder : Decoder Alert
alertValueDecoder =
    map6 Alert
        (field "alertType" alertTypeDecoder)
        (field "symbol" string)
        (field "askPrice" float)
        (field "bidPrice" float)
        (field "high" float)
        (field "low" float)


notificationDecoder : Decoder WsIn
notificationDecoder =
    map Notification (field "Notification" (field "alert" alertValueDecoder))


attachedDecoder : Decoder WsIn
attachedDecoder =
    map Attached (field "Attached" (field "sid" string))


socketClosedDecoder : Decoder WsIn
socketClosedDecoder =
    field "SocketClosed" (succeed SocketClosed)


wsInDecoder : Decoder WsIn
wsInDecoder =
    oneOf
        [ attachedDecoder
        , notificationDecoder
        , socketClosedDecoder
        ]


subscriptions : Model -> Sub Msg
subscriptions _ =
    WS.receive
        (\str ->
            case decodeString wsInDecoder str of
                Ok input ->
                    Recv input

                Err error ->
                    Recv (Unknown (errorToString error))
        )
