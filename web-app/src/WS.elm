port module WS exposing (..)

import Model exposing (..)


port connect : WSUrl -> Cmd msg


port send : String -> Cmd msg


port receive : (String -> msg) -> Sub msg
