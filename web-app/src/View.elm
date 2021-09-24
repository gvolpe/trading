module View exposing (..)

import Html exposing (..)
import Html.Attributes exposing (..)
import Html.Events exposing (..)
import Json.Decode as D
import Model exposing (..)


view : Model -> Html Msg
view model =
    div []
        [ h1 [] [ text "Forex WS" ]
        , ul []
            (List.map (\alert -> li [] [ text alert.symbol ]) model.alerts)
        , input
            [ type_ "text"
            , placeholder "Symbol (e.g. EURUSD)"
            , onInput SymbolChanged
            , on "keydown" (ifIsEnter Subscribe)
            , value model.symbol
            ]
            []
        , button [ onClick Subscribe ] [ text "Subscribe" ]
        , button [ onClick Unsubscribe ] [ text "Unsubscribe" ]
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
