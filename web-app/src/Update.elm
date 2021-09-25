module Update exposing (..)

import Dict exposing (Dict)
import Json.Decode exposing (Decoder, decodeString, field, map, map2, oneOf)
import Json.Encode exposing (encode, object, string)
import Model exposing (..)
import Ports exposing (sendMessage)


encodeSymbol : String -> Json.Encode.Value
encodeSymbol symbol =
    object [ ( "symbol", string symbol ) ]


encodeSubscribe : String -> String
encodeSubscribe symbol =
    encode 2 (object [ ( "Subscribe", encodeSymbol symbol ) ])


encodeUnsubscribe : String -> String
encodeUnsubscribe symbol =
    encode 2 (object [ ( "Unsubscribe", encodeSymbol symbol ) ])


alertTypeDecoder : Decoder a -> Decoder ( AlertType, a )
alertTypeDecoder f =
    oneOf
        [ map (\c -> ( Buy, c )) (field "Buy" f)
        , map (\c -> ( Sell, c )) (field "Sell" f)
        , map (\c -> ( Neutral, c )) (field "Neutral" f)
        , map (\c -> ( StrongBuy, c )) (field "StrongBuy" f)
        , map (\c -> ( StrongSell, c )) (field "StrongSell" f)
        ]


alertValueDecoder : Decoder ( String, Float )
alertValueDecoder =
    map2 (\s1 p1 -> ( s1, p1 ))
        (field "symbol" Json.Decode.string)
        (field "price" Json.Decode.float)


alertDecoder : Decoder Alert
alertDecoder =
    field "Notification"
        (field "alert"
            (map (\( t, ( s, p ) ) -> Alert t s p)
                (alertTypeDecoder alertValueDecoder)
            )
        )


attachedDecoder : Decoder (Maybe SocketId)
attachedDecoder =
    field "Attached" (Json.Decode.maybe (field "sid" Json.Decode.string))


parseAlert : String -> Maybe Alert
parseAlert json =
    case decodeString alertDecoder json of
        Ok alert ->
            Just alert

        Err _ ->
            Nothing


parseSocketId : Maybe SocketId -> String -> Maybe SocketId
parseSocketId currentSid json =
    case currentSid of
        Just sid ->
            Just sid

        Nothing ->
            case decodeString attachedDecoder json of
                Ok sid ->
                    sid

                Err _ ->
                    Nothing


updateAlerts : Dict Symbol Alert -> Maybe Alert -> Dict Symbol Alert
updateAlerts kvs optAlert =
    case optAlert of
        Just alert ->
            Dict.insert alert.symbol alert kvs

        Nothing ->
            kvs


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case msg of
        CloseAlerts ->
            ( { model | sub = Nothing, unsub = Nothing }
            , Cmd.none
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

        Recv message ->
            ( { model | alerts = updateAlerts model.alerts (parseAlert message), socketId = parseSocketId model.socketId message }
            , Cmd.none
            )
