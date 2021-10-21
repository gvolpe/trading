{ jdk ? "jdk17" }:

let
  config = {
    packageOverrides = p: rec {
      java = p.${jdk};

      sbt = p.sbt.overrideAttrs (
        old: rec {
          patchPhase = ''
            echo -java-home ${java} >> conf/sbtopts
          '';
        }
      );
    };
  };

  nixpkgs = fetchTarball {
    #name   = "nixos-unstable-2021-09-02";
    #url    = "https://github.com/NixOS/nixpkgs/archive/8a2ec31e224.tar.gz";
    name   = "nixos-jdk17-pr";
    url    = "https://github.com/Uthar/nixpkgs/archive/64a379be05a.tar.gz";
    sha256 = "1jjmixv7m3b7i55ri7p188bmx0n8dbwlslrj6yzcqcn4g9y1nsly";
  };

  pkgs = import nixpkgs { inherit config; };
in
pkgs.mkShell {
  name = "scala-shell";

  buildInputs = [
    pkgs.coursier
    pkgs.${jdk}
    pkgs.sbt
  ];
}
