{
  description = "Tyrian (ScalaJS) development shell";

  inputs = {
    nixpkgs.url = github:nixos/nixpkgs/nixpkgs-unstable;
    flake-utils.url = github:numtide/flake-utils;
    flake-compat = {
      url = github:edolstra/flake-compat;
      flake = false;
    };
  };

  outputs = { self, nixpkgs, flake-utils, ... }:
    let
      forSystem = system:
        let
          pkgs = nixpkgs.legacyPackages.${system};
        in
        {
          devShell = pkgs.mkShell {
            name = "tyrian-dev-shell";
            buildInputs = [
              pkgs.yarn
            ];
            shellHook = ''
              yarn install
              yarn build
            '';
          };
        };
    in
    flake-utils.lib.eachDefaultSystem forSystem;
}
