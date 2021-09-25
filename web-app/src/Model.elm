module Model exposing (..)

import Dict exposing (Dict)


type AlertType
    = Buy
    | Sell
    | Neutral
    | StrongBuy
    | StrongSell


type alias Alert =
    { alertType : AlertType
    , symbol : String
    , price : Float
    }


type Msg
    = CloseAlerts
    | SymbolChanged Symbol
    | Subscribe
    | Unsubscribe Symbol
    | Recv String


type alias SocketId =
    String


type alias Symbol =
    String


type alias Model =
    { symbol : Symbol
    , socketId : Maybe SocketId
    , alerts : Dict Symbol Alert
    , sub : Maybe Symbol
    , unsub : Maybe Symbol
    }


init : () -> ( Model, Cmd Msg )
init _ =
    ( { symbol = "", socketId = Nothing, alerts = Dict.fromList [], sub = Nothing, unsub = Nothing }
    , Cmd.none
    )
