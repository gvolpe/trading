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


type alias OnlineUsers =
    Int


type AlertType
    = Buy
    | Sell
    | Neutral
    | StrongBuy
    | StrongSell


type TradeStatus
    = On
    | Off


type Alert
    = TradeAlert
        { alertType : AlertType
        , symbol : Symbol
        , askPrice : Price
        , bidPrice : Price
        , high : Price
        , low : Price
        }
    | TradeUpdate TradeStatus


type WsIn
    = Attached SocketId
    | CloseConnection
    | ConnectionError String
    | Notification Alert
    | OnlineUsers Int
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
    , tradeStatus : TradeStatus
    , sub : Maybe Symbol
    , unsub : Maybe Symbol
    , error : Maybe String
    }


dummyAlerts : Dict Symbol Alert
dummyAlerts =
    Dict.fromList
        [ ( "EURUSD", TradeAlert { alertType = Sell, symbol = "EURUSD", askPrice = 1.287434123, bidPrice = 1.3567576891, high = 1.4712312454, low = 1.23545623114 } )
        , ( "CHFEUR", TradeAlert { alertType = Buy, symbol = "CHFEUR", askPrice = 1.301236451, bidPrice = 1.4328765419, high = 1.4789877536, low = 1.27054836753 } )
        , ( "CHFGBP", TradeAlert { alertType = Buy, symbol = "CHFEUR", askPrice = 1.301236451, bidPrice = 1.4328765419, high = 1.4789877536, low = 1.27054836753 } )
        , ( "GBPUSD", TradeAlert { alertType = StrongSell, symbol = "GBPUSD", askPrice = 2.487465452, bidPrice = 2.7344545629, high = 2.9983565471, low = 2.21236312235 } )
        , ( "EURPLN", TradeAlert { alertType = Neutral, symbol = "EURPLN", askPrice = 4.691272348, bidPrice = 4.4534524323, high = 4.8347145275, low = 3.83476129853 } )
        , ( "AUDCAD", TradeAlert { alertType = StrongBuy, symbol = "AUDCAD", askPrice = 10.209676347, bidPrice = 10.3723136644, high = 10.5430958726, low = 10.01236543289 } )
        ]


init : () -> ( Model, Cmd Msg )
init _ =
    ( { symbol = ""
      , wsUrl = "ws://localhost:9000/v1/ws"
      , socketId = Nothing
      , onlineUsers = 0
      , alerts = Dict.fromList [] -- dummyAlerts
      , tradeStatus = On
      , sub = Nothing
      , unsub = Nothing
      , error = Nothing
      }
    , Cmd.none
    )
