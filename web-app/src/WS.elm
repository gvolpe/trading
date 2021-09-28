port module WS exposing (..)

import Model exposing (..)


port connect : WSUrl -> Cmd msg


port disconnect : () -> Cmd msg


port send : String -> Cmd msg


port receive : (String -> msg) -> Sub msg


port connectionError : (String -> msg) -> Sub msg
