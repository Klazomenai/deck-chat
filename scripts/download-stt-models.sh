#!/usr/bin/env bash
# Downloads Whisper Tiny EN (int8 ONNX) models from HuggingFace for on-device STT.
#
# Model: csukuangfj/sherpa-onnx-whisper-tiny.en (~37 MB total)
# Runtime: Sherpa-ONNX OfflineRecognizer + OfflineWhisperModelConfig
#
# Run from the repo root before building for real device use.
# Models are gitignored — not committed to the repository.

set -euo pipefail

DEST="app/src/main/assets/stt"
HF_BASE="https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny.en/resolve/main"

mkdir -p "$DEST"

echo "Downloading Whisper Tiny EN (int8 ONNX) to $DEST..."

for file in "tiny.en-encoder.int8.onnx" "tiny.en-decoder.int8.onnx"; do
    if [ -f "$DEST/$file" ]; then
        echo "  $file already present, skipping"
        continue
    fi
    echo "  Downloading $file..."
    curl -fL --progress-bar "$HF_BASE/$file" -o "$DEST/$file"
done

echo "STT models ready."
