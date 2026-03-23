#!/usr/bin/env bash
# Downloads Piper TTS voice models from k2-fsa/sherpa-onnx GitHub releases.
#
# Models: vits-piper-en_GB-cori-high (Maren), vits-piper-en_US-lessac-high (Crest)
# Source: https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/
# Each archive includes: <voice>.onnx + tokens.txt + espeak-ng-data/
#
# Run from anywhere — script derives repo root from its own location.
# Models are gitignored — not committed to the repository.

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DEST="$REPO_ROOT/app/src/main/assets/tts"
GH_BASE="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"

VOICES=(
    "vits-piper-en_GB-cori-high"
    "vits-piper-en_US-lessac-high"
)

mkdir -p "$DEST"

echo "Downloading Piper TTS voice models to $DEST..."

for voice in "${VOICES[@]}"; do
    voice_dir="$DEST/$voice"
    model_file="$voice_dir/${voice#vits-piper-}.onnx"

    if [ -d "$voice_dir" ] && [ -s "$model_file" ]; then
        echo "  $voice already present and valid, skipping"
        continue
    fi

    archive="${voice}.tar.bz2"
    archive_path="$DEST/$archive"

    echo "  Downloading $archive..."
    tmp_path="${archive_path}.tmp.$$"
    trap 'rm -f "$tmp_path"' EXIT
    curl -fSL --progress-bar "$GH_BASE/$archive" -o "$tmp_path"
    mv "$tmp_path" "$archive_path"
    trap - EXIT

    echo "  Extracting $archive..."
    tar xf "$archive_path" -C "$DEST"
    rm "$archive_path"
done

echo "TTS models ready."
