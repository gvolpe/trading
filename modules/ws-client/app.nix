{ lib, mkYarnPackage, writeShellScriptBin }:

let
  baseOutDir = "libexec/trading-webapp";
  outDir = "${baseOutDir}/deps/trading-webapp";
  parcelBin = "${baseOutDir}/node_modules/.bin/parcel";
  indexHTML = "${outDir}/index.html";
in
rec {
  webserver = writeShellScriptBin "trading-webserver" ''
    mkdir -p $PWD/nix-parcel-cache
    echo "Using cache dir: $PWD/nix-parcel-cache"
    ${webapp}/${parcelBin} --cache-dir $PWD/nix-parcel-cache ${webapp}/${indexHTML}
  '';

  webapp = mkYarnPackage rec {
    name = "trading-webapp";
    version = "1.0.0";
    src = lib.cleanSourceWith {
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
        (type == "symlink" && lib.hasPrefix "result" (toString name))
      );
    };
    packageJSON = ./package.json;
    yarnLock = ./yarn.lock;
    yarnNix = ./yarn.nix;

    postInstall = ''
      substituteInPlace $out/${outDir}/target/scala-3.2.1/webapp-opt/main.js \
        --replace "assets/icons/" "static/assets/icons/"
    '';

    distPhase = "true";
  };
}
