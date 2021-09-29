module Subscriptions exposing (..)

import Json.Decode exposing (..)
import Model exposing (..)
import WS


alertTypeDecoder : Decoder AlertType
alertTypeDecoder =
    string
        |> andThen
            (\s ->
                case s of
                    "Buy" ->
                        succeed Buy

                    "Sell" ->
                        succeed Sell

                    "Neutral" ->
                        succeed Neutral

                    "StrongBuy" ->
                        succeed StrongBuy

                    "StrongSell" ->
                        succeed StrongSell

                    _ ->
                        fail "Invalid AlertType"
            )


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


connectionErrorDecoder : Decoder WsIn
connectionErrorDecoder =
    map ConnectionError (field "ConnectionError" (field "cause" string))


socketClosedDecoder : Decoder WsIn
socketClosedDecoder =
    field "SocketClosed" (succeed SocketClosed)


wsInDecoder : Decoder WsIn
wsInDecoder =
    oneOf
        [ attachedDecoder
        , connectionErrorDecoder
        , notificationDecoder
        , socketClosedDecoder
        ]


wsSub : Sub Msg
wsSub =
    WS.receive
        (\str ->
            case decodeString wsInDecoder str of
                Ok input ->
                    Recv input

                Err error ->
                    Recv (Unknown (errorToString (Debug.log "fail to decode WsIn json: " error)))
        )


errorSub : Sub Msg
errorSub =
    WS.connectionError
        (\cause ->
            Recv (ConnectionError cause)
        )


subscriptions : Model -> Sub Msg
subscriptions _ =
    Sub.batch [ wsSub, errorSub ]
