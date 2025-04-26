{
  description = "Scala development shell";

  inputs = {
    nixpkgs.url = github:nixos/nixpkgs/nixpkgs-unstable;
    flake-utils.url = github:numtide/flake-utils;
  };

  outputs = { self, nixpkgs, flake-utils, ... }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        dockerOverlay = f: p: with f.dockerTools; {
          # September 2022: this docker image stopped working with sbt-native-packager
          noRootDockerImage = buildLayeredImage {
            name = "jdk17-curl";
            tag = "latest";
            contents = [ f.curl f.jre ];
          };

          slimDockerImage = buildImage {
            name = "jdk17-curl";
            fromImage = pullImage {
              imageName = "openjdk";
              imageDigest = "sha256:2e7658fb62d1c6f319ff9870614deaf8e06038dd41eec3d1ecdcfabd186234fd";
              sha256 = "0m47vk8k97xqgc6bnxhmyyyvqnrn5jdqx8rw417740963h4i813v";
              finalImageName = "openjdk";
              finalImageTag = "17-jdk-slim-buster";
              os = "linux";
              arch = "amd64";
            };
            tag = "latest";
            copyToRoot = [ p.curl ];
          };
        };

        jreOverlay = f: p: {
          jre = p.jdk17_headless;
        };

        pkgs = import nixpkgs {
          inherit system;
          overlays = [ dockerOverlay jreOverlay ];
        };

        elm-webapp = pkgs.callPackage ./web-app/app.nix { };
        tyrian = pkgs.callPackage ./modules/ws-client/app.nix { };
      in
      {
        devShells = {
          default = self.devShells.${system}.scala;

          elm = pkgs.mkShell {
            name = "elm-dev-shell";

            buildInputs = with pkgs.elmPackages; [
              pkgs.elmPackages.elm
              elm-format
              elm-language-server
              elm-review
              elm-test
            ];
          };

          scala = pkgs.mkShell {
            name = "scala-dev-shell";

            buildInputs = with pkgs; [
              coursier
              envsubst
              jre
              kubectl
              minikube
              sbt
            ];

            shellHook = ''
              JAVA_HOME="${pkgs.jre}"
            '';
          };

          tyrian = pkgs.mkShell {
            name = "tyrian-dev-shell";
            buildInputs = with pkgs; [
              yarn
              yarn2nix
            ];
          };
        };

        apps.tyrian-webapp = {
          type = "app";
          program = "${tyrian.webserver}/bin/trading-webserver";
        };

        packages = {
          inherit elm-webapp;
          inherit (pkgs) sbt;
          tyrian-webapp = tyrian.webapp;
          docker = pkgs.noRootDockerImage;
          slimDocker = pkgs.slimDockerImage;
        };

        defaultPackage = pkgs.slimDockerImage;
      }
    );
}
