module Model exposing (..)

import Dict exposing (Dict)


type alias SocketId =
    String


type alias Symbol =
    String


type alias Price =
    Float


type AlertType
    = Buy
    | Sell
    | Neutral
    | StrongBuy
    | StrongSell


type alias AlertValue =
    { symbol : Symbol
    , askPrice : Price
    , bidPrice : Price
    , high : Price
    , low : Price
    }


type alias Alert =
    { alertType : AlertType
    , prices : AlertValue
    }


type WsIn
    = Attached SocketId
    | Notification Alert
    | Unknown String


type Msg
    = CloseAlerts
    | SymbolChanged Symbol
    | Subscribe
    | Unsubscribe Symbol
    | Recv WsIn
    | NoOp


type alias Model =
    { symbol : Symbol
    , socketId : Maybe SocketId
    , alerts : Dict Symbol Alert
    , sub : Maybe Symbol
    , unsub : Maybe Symbol
    , error : Maybe String
    }


init : () -> ( Model, Cmd Msg )
init _ =
    ( { symbol = "", socketId = Nothing, alerts = Dict.fromList [], sub = Nothing, unsub = Nothing, error = Nothing }
    , Cmd.none
    )
