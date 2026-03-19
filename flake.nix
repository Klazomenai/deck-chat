{
  description = "DeckChat — Secure voice interface to AI crew members";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
  };

  outputs = { self, nixpkgs }:
    let
      systems = [ "x86_64-linux" "x86_64-darwin" ];
      forAllSystems = nixpkgs.lib.genAttrs systems;
    in
    {
      devShells = forAllSystems (system:
        let
          pkgs = nixpkgs.legacyPackages.${system};
          androidComposition = pkgs.androidenv.composeAndroidPackages {
            buildToolsVersions = [ "36.0.0" ];
            platformVersions = [ "36" ];
            includeNDK = false;
            includeEmulator = false;
            includeSources = false;
            includeSystemImages = false;
          };
        in
        {
          default = pkgs.mkShell {
            packages = [
              pkgs.jdk17
              pkgs.gradle
              androidComposition.androidsdk
            ];

            # Set Android SDK paths as shell environment variables.
            # pkgs.jdk17's setupHook sets JAVA_HOME automatically.
            ANDROID_HOME = "${androidComposition.androidsdk}/libexec/android-sdk";
            ANDROID_SDK_ROOT = "${androidComposition.androidsdk}/libexec/android-sdk";

            shellHook = ''
              echo "DeckChat Android build environment"
              echo "ANDROID_HOME: $ANDROID_HOME"
              echo ""
              echo "Run: nix develop --command ./gradlew lint test assembleDebug"
            '';
          };
        }
      );
    };
}
