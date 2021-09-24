module Model exposing (..)


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
    = SymbolChanged String
    | Subscribe
    | Unsubscribe
    | Recv String


type alias Model =
    { symbol : String
    , alerts : List Alert
    }


init : () -> ( Model, Cmd Msg )
init _ =
    ( { symbol = "", alerts = [] }
    , Cmd.none
    )
