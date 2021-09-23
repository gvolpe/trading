let
  nixpkgs = fetchTarball {
    name   = "nixos-unstable-2021-09-02";
    url    = "https://github.com/NixOS/nixpkgs/archive/8a2ec31e224.tar.gz";
    sha256 = "0w8sl1dwmvng2bd03byiaz8j9a9hlvv8n16641m8m5dd06dyqli7";
  };

  pkgs = import nixpkgs {};
in
pkgs.mkShell {
  name = "elm-shell";

  buildInputs = with pkgs.elmPackages; [
    elm
    elm-format
    elm-language-server
  ];
}
