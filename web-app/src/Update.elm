module Update exposing (..)

import Json.Decode exposing (Decoder, decodeString, field, map, map2, oneOf)
import Json.Encode exposing (encode, object, string)
import Model exposing (..)
import Ports exposing (sendMessage)



-- Use the `sendMessage` port when someone presses ENTER or clicks
-- the "Send" button. Check out index.html to see the corresponding
-- JS where this is piped into a WebSocket.


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



--{"Notification":{"alert":{"Neutral":{"symbol":"EURUSD","price":4.5273707507049124919333666092838607}}}}


parseAlert : String -> List Alert
parseAlert msg =
    case decodeString alertDecoder msg of
        Ok alert ->
            [ alert ]

        Err _ ->
            []


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case msg of
        SymbolChanged symbol ->
            ( { model | symbol = symbol }
            , Cmd.none
            )

        Subscribe ->
            ( { model | symbol = "" }
            , sendMessage (encodeSubscribe model.symbol)
            )

        Unsubscribe ->
            ( { model | symbol = "" }
            , sendMessage (encodeUnsubscribe model.symbol)
            )

        Recv message ->
            ( { model | alerts = model.alerts ++ parseAlert message }
            , Cmd.none
            )
