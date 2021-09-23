module Model exposing (..)

type Msg
  = DraftChanged String
  | Send
  | Recv String

type alias Model =
  { draft : String
  , messages : List String
  }

init : () -> ( Model, Cmd Msg )
init flags =
  ( { draft = "", messages = [] }
  , Cmd.none
  )
