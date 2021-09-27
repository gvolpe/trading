module Model exposing (..)

import Dict exposing (Dict)


type alias SocketId =
    String


type alias Symbol =
    String


type alias Price =
    Float


type alias WSUrl =
    String


type AlertType
    = Buy
    | Sell
    | Neutral
    | StrongBuy
    | StrongSell


type alias Alert =
    { alertType : AlertType
    , symbol : Symbol
    , askPrice : Price
    , bidPrice : Price
    , high : Price
    , low : Price
    }


type WsIn
    = Attached SocketId
    | Notification Alert
    | SocketClosed
    | Unknown String


type Msg
    = CloseAlerts
    | Connect
    | SymbolChanged Symbol
    | Subscribe
    | Unsubscribe Symbol
    | Recv WsIn
    | NoOp


type alias Model =
    { symbol : Symbol
    , wsUrl : WSUrl
    , socketId : Maybe SocketId
    , alerts : Dict Symbol Alert
    , sub : Maybe Symbol
    , unsub : Maybe Symbol
    , error : Maybe String
    }


dummyAlerts : Dict Symbol Alert
dummyAlerts =
    Dict.fromList
        [ ( "EURUSD", Alert Sell "EURUSD" 1.287434123 1.3567576891 1.4712312454 1.23545623114 )
        , ( "CHFEUR", Alert Buy "CHFEUR" 4.691272348 4.4534524323 5.6509123454 3.65876653451 )
        , ( "GBPUSD", Alert StrongSell "GBPUSD" 4.691272348 4.4534524323 5.6509123454 3.65876653451 )
        , ( "EURPLN", Alert Neutral "EURPLN" 4.691272348 4.4534524323 5.6509123454 3.65876653451 )
        , ( "AUDCAD", Alert StrongBuy "AUDCAD" 4.691272348 4.4534524323 5.6509123454 3.65876653451 )
        ]


init : () -> ( Model, Cmd Msg )
init _ =
    ( { symbol = ""
      , wsUrl = "ws://localhost:9000/ws"
      , socketId = Nothing
      , alerts = dummyAlerts -- Dict.fromList []
      , sub = Nothing
      , unsub = Nothing
      , error = Nothing
      }
    , Cmd.none
    )
