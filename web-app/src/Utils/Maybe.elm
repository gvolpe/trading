-- import Utils.Maybe as M


module Utils.Maybe exposing (..)


toList : Maybe a -> List a
toList ma =
    Maybe.withDefault [] (Maybe.map (\x -> [ x ]) ma)


isEmpty : Maybe a -> Bool
isEmpty ma =
    case ma of
        Just _ ->
            False

        Nothing ->
            True


nonEmpty : Maybe a -> Bool
nonEmpty ma =
    not (isEmpty ma)


fold : Maybe a -> b -> (a -> b) -> b
fold ma b f =
    case ma of
        Just a ->
            f a

        Nothing ->
            b
