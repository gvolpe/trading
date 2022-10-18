{
  description = "Trading Web App built on top of Tyrian (Scala.js)";

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
          baseOutDir = "libexec/trading-webapp";
          outDir = "${baseOutDir}/deps/trading-webapp";
          parcelBin = "${baseOutDir}/node_modules/.bin/parcel";
          indexHTML = "${outDir}/index.html";
        in
        rec {
          packages = rec {
            default = webserver;

            webserver = pkgs.writeShellScriptBin "trading-webserver" ''
              mkdir -p $PWD/nix-parcel-cache
              echo "Using cache dir: $PWD/nix-parcel-cache"
              ${webapp}/${parcelBin} --cache-dir $PWD/nix-parcel-cache ${webapp}/${indexHTML}
            '';

            webapp = pkgs.mkYarnPackage rec {
              name = "trading-webapp";
              version = "1.0.0";
              src = pkgs.lib.cleanSourceWith {
                src = ./.;
                name = "source-${version}";
                filter = name: type: !(
                  (builtins.elem (toString name) [
                    "dist"
                    "src"
                    "node_modules"
                    "flake.lock"
                    "flake.nix"
                  ]) ||
                  # Filter out nix-build result symlinks
                  (type == "symlink" && pkgs.lib.hasPrefix "result" (toString name))
                );
              };
              packageJSON = ./package.json;
              yarnLock = ./yarn.lock;
              yarnNix = ./yarn.nix;

              postInstall = ''
                substituteInPlace $out/${outDir}/tyrianapp.js \
                  --replace "./target/scala-3.2.1-RC3/webapp-fastopt/main.js" "./main.js"
              '';

              distPhase = "true";
            };
          };

          devShell = pkgs.mkShell {
            name = "tyrian-dev-shell";
            buildInputs = with pkgs; [
              yarn
              yarn2nix
            ];
          };
        };
    in
    flake-utils.lib.eachDefaultSystem forSystem;
}
