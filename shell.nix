{ jdk ? "jdk17" }:

let

  nixpkgs = fetchTarball {
    name = "nixos-unstable-2021-10-27";
    url = "https://github.com/NixOS/nixpkgs/archive/a4bf44345706.tar.gz";
    sha256 = "0zag9yfqsf544vrfccfvn5yjagizqf69adza8fpmsmn5ll8jw8gw";
  };

  pkgs = import nixpkgs {
    overlays = [
      (final: prev: {
        jre = final.${jdk};
      })
    ];
  };
in
pkgs.mkShell {
  name = "scala-shell";

  buildInputs = [
    pkgs.coursier
    pkgs.jre
    pkgs.sbt
  ];
}
