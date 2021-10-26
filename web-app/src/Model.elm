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
    = Attached SocketId Int
    | CloseConnection
    | ConnectionError String
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
    , onlineUsers : Int
    , alerts : Dict Symbol Alert
    , sub : Maybe Symbol
    , unsub : Maybe Symbol
    , error : Maybe String
    }


dummyAlerts : Dict Symbol Alert
dummyAlerts =
    Dict.fromList
        [ ( "EURUSD", Alert Sell "EURUSD" 1.287434123 1.3567576891 1.4712312454 1.23545623114 )
        , ( "CHFEUR", Alert Buy "CHFEUR" 1.301236451 1.4328765419 1.4789877536 1.27054836753 )
        , ( "CHFGBP", Alert Buy "CHFEUR" 1.301236451 1.4328765419 1.4789877536 1.27054836753 )
        , ( "GBPUSD", Alert StrongSell "GBPUSD" 2.487465452 2.7344545629 2.9983565471 2.21236312235 )
        , ( "EURPLN", Alert Neutral "EURPLN" 4.691272348 4.4534524323 4.8347145275 3.83476129853 )
        , ( "AUDCAD", Alert StrongBuy "AUDCAD" 10.209676347 10.3723136644 10.5430958726 10.01236543289 )
        ]


init : () -> ( Model, Cmd Msg )
init _ =
    ( { symbol = ""
      , wsUrl = "ws://localhost:9000/ws"
      , socketId = Nothing
      , onlineUsers = 0
      , alerts = dummyAlerts -- Dict.fromList []
      , sub = Nothing
      , unsub = Nothing
      , error = Nothing
      }
    , Cmd.none
    )
