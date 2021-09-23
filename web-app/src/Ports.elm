port module Ports exposing (..)

import Model exposing (..)

port sendMessage : String -> Cmd msg
port messageReceiver : (String -> msg) -> Sub msg
