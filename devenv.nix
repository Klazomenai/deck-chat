{ pkgs, ... }:

let
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
  languages.java = {
    enable = true;
    jdk.package = pkgs.jdk17;
  };

  packages = [
    pkgs.gradle
    androidComposition.androidsdk
  ];

  env = {
    ANDROID_HOME = "${androidComposition.androidsdk}/libexec/android-sdk";
    ANDROID_SDK_ROOT = "${androidComposition.androidsdk}/libexec/android-sdk";
  };

  scripts = {
    wrapper.exec = ''
      gradle wrapper --gradle-version=9.4.0 --distribution-type=bin
      chmod +x gradlew
      echo "Gradle wrapper regenerated (9.4.0)"
    '';

    check-gms.exec = ''
      ./gradlew dependencies | grep -i "gms\|play-services\|firebase" || echo "No GMS dependencies found"
    '';
  };

  enterShell = ''
    echo ""
    echo "DeckChat — Android Development Shell"
    echo ""
    echo "ANDROID_HOME: $ANDROID_HOME"
    echo "JAVA_HOME:    $JAVA_HOME"
    echo ""
    echo "Commands:"
    echo "  wrapper                    — Regenerate Gradle wrapper (9.4.0)"
    echo "  check-gms                  — Audit for Google Play Services dependencies"
    echo "  ./gradlew assembleDebug    — Build debug APK"
    echo "  ./gradlew lint test        — Lint + unit tests"
    echo ""
  '';
}
