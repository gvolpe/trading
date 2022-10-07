{ system ? builtins.currentSystem or "unknown-system" }:

(builtins.getFlake ("git+file://" + toString ./.)).devShell.${system}
