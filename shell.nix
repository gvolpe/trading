{ system ? builtins.currentSystem or "unknown-system", shell ? "scala" }:

(builtins.getFlake ("git+file://" + toString ./.)).devShells.${system}.${shell}
