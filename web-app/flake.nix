{
  description = "Elm development shell";

  inputs = {
    nixpkgs.url = github:nixos/nixpkgs/nixpkgs-unstable;
    flake-utils.url = github:numtide/flake-utils;
  };

  outputs = { self, nixpkgs, flake-utils, ... }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};
        app = pkgs.callPackage ./app.nix { };
      in
      {
        devShell = pkgs.mkShell {
          name = "elm-dev-shell";

          buildInputs = with pkgs.elmPackages; [
            elm
            elm-format
            elm-language-server
            elm-review
            elm-test
          ];
        };

        packages = {
          inherit app;
        };

        defaultPackage = app;
      }
    );
}
