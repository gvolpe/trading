module Subscriptions exposing (..)

import Json.Decode exposing (Decoder, decodeString, errorToString, field, map, map5, oneOf)
import Model exposing (..)
import Ports exposing (messageReceiver)


alertTypeDecoder : Decoder a -> Decoder ( AlertType, a )
alertTypeDecoder f =
    oneOf
        [ map (\c -> ( Buy, c )) (field "Buy" f)
        , map (\c -> ( Sell, c )) (field "Sell" f)
        , map (\c -> ( Neutral, c )) (field "Neutral" f)
        , map (\c -> ( StrongBuy, c )) (field "StrongBuy" f)
        , map (\c -> ( StrongSell, c )) (field "StrongSell" f)
        ]


alertValueDecoder : Decoder AlertValue
alertValueDecoder =
    map5 AlertValue
        (field "symbol" Json.Decode.string)
        (field "askPrice" Json.Decode.float)
        (field "bidPrice" Json.Decode.float)
        (field "high" Json.Decode.float)
        (field "low" Json.Decode.float)


notificationDecoder : Decoder Alert
notificationDecoder =
    field "Notification"
        (field "alert"
            (map (\( t, a ) -> Alert t a)
                (alertTypeDecoder alertValueDecoder)
            )
        )


attachedDecoder : Decoder SocketId
attachedDecoder =
    field "Attached" (field "sid" Json.Decode.string)


wsInDecoder : Decoder WsIn
wsInDecoder =
    oneOf
        [ map Attached attachedDecoder
        , map Notification notificationDecoder
        ]


subscriptions : Model -> Sub Msg
subscriptions _ =
    messageReceiver
        (\str ->
            case decodeString wsInDecoder str of
                Ok input ->
                    Recv input

                Err error ->
                    Recv (Unknown (errorToString error))
        )
