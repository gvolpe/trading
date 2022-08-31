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
          dockerOverlay = f: p: with f.dockerTools; {
            dockerImage = buildLayeredImage {
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
                arch = "x86_64";
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
        in
        {
          devShell = pkgs.mkShell {
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

          packages = {
            docker = pkgs.dockerImage;
            slimDocker = pkgs.slimDockerImage;
          };

          defaultPackage = pkgs.dockerImage;
        };
    in
    flake-utils.lib.eachDefaultSystem forSystem;
}
