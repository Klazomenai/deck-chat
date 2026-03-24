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
          pkgs = import nixpkgs {
            inherit system;
            config = {
              allowUnfree = true;             # Android SDK has an unfree license
              android_sdk.accept_license = true;  # explicit androidenv license acceptance
            };
          };
          androidComposition = pkgs.androidenv.composeAndroidPackages {
            buildToolsVersions = [ "36.0.0" ];
            platformVersions = [ "35" "36" ];
            includeNDK = false;
            includeEmulator = true;
            includeSources = false;
            includeSystemImages = true;
            systemImageTypes = [ "google_apis" ];
            abiVersions = [ "x86_64" ];
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
