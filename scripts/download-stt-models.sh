#!/usr/bin/env bash
# Downloads Whisper Tiny EN (int8 ONNX) models from HuggingFace for on-device STT.
#
# Model: csukuangfj/sherpa-onnx-whisper-tiny.en (~37 MB total)
# Runtime: Sherpa-ONNX OfflineRecognizer + OfflineWhisperModelConfig
#
# Run from the repo root before building for real device use.
# Models are gitignored — not committed to the repository.

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DEST="$REPO_ROOT/app/src/main/assets/stt"

# TODO: pin to a specific HuggingFace commit hash for reproducibility
# once the model repo (csukuangfj/sherpa-onnx-whisper-tiny.en) is cloned locally.
HF_BASE="https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny.en/resolve/main"

mkdir -p "$DEST"

echo "Downloading Whisper Tiny EN (int8 ONNX) to $DEST..."

for file in "tiny.en-encoder.int8.onnx" "tiny.en-decoder.int8.onnx"; do
    dest_path="$DEST/$file"
    if [ -s "$dest_path" ]; then
        echo "  $file already present and non-empty, skipping"
        continue
    elif [ -f "$dest_path" ]; then
        echo "  $file exists but is empty or incomplete, re-downloading"
    fi
    echo "  Downloading $file..."
    tmp_path="${dest_path}.tmp.$$"
    trap 'rm -f "$tmp_path"' EXIT
    curl -fL --progress-bar "$HF_BASE/$file" -o "$tmp_path"
    mv "$tmp_path" "$dest_path"
    trap - EXIT
done

echo "STT models ready."
