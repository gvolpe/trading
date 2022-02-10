{
  description = "Scala development shell";

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
          jdk = pkgs.jdk17_headless;
        in
        {
          devShell = pkgs.mkShell {
            name = "scala-dev-shell";
            buildInputs = [
              jdk
              pkgs.coursier
              pkgs.kubectl
              pkgs.minikube
              pkgs.sbt
            ];
            shellHook = ''
              JAVA_HOME="${jdk}"
            '';
          };
        };
    in
    flake-utils.lib.eachDefaultSystem forSystem;
}
