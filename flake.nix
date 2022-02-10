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
      kompose-overlay = f: p: {
        kompose = p.buildGoModule rec {
          pname = "kompose";
          version = "1.26.1";

          doCheck = false;

          src = p.fetchFromGitHub {
            rev = "v${version}";
            owner = "kubernetes";
            repo = "kompose";
            sha256 = "sha256-NfzqGG5ZwPpmjhvcvXN1AA+kfZG/oujbAEtXkm1mzeU=";
          };

          vendorSha256 = "sha256-OR5U2PnebO0a+lwU09Dveh0Yxk91cmSRorTxQIO5lHc=";
        };
      };

      forSystem = system:
        let
          pkgs = import nixpkgs {
            inherit system;
            overlays = [ kompose-overlay ];
          };
          jdk = pkgs.jdk17_headless;
        in
        {
          devShell = pkgs.mkShell {
            name = "scala-dev-shell";
            buildInputs = [
              jdk
              pkgs.coursier
              pkgs.kubectl
              pkgs.kompose
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
