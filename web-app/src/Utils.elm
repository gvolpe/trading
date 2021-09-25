module Utils exposing (..)


maybeToList : Maybe a -> List a
maybeToList ma =
    Maybe.withDefault [] (Maybe.map (\x -> [ x ]) ma)


emptyMaybe : Maybe a -> Bool
emptyMaybe ma =
    case ma of
        Just _ ->
            False

        Nothing ->
            True
