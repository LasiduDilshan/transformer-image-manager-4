#!/usr/bin/env bash
# Runs inference_core_local.py using an EXISTING local venv (Linux/WSL).
#
# Run this inside WSL (Ubuntu). From Windows PowerShell, you can do:
#   wsl --cd "/mnt/c/Users/pasir/Desktop/Github/transformer-image-manager-3/automatic-anamoly-detection/Model_Inference" -- ./run_inference.sh [args]
#
# Usage (inside WSL):
#   ./run_inference.sh [--install] [--input <path>] [--outdir <dir>] [--config <yaml>] [--ckpt <ckpt>] [--size N] [--cpu] [--venv <path>] [--sensitivity <float>]
#   ENV: VENV=.venv (override virtualenv path; must already exist)

set -euo pipefail
cd "$(dirname "$0")"

# Ensure we are in WSL/Linux; warn if not
if ! grep -qiE 'microsoft|wsl' /proc/version 2>/dev/null; then
  echo "[WARN] This script is intended for WSL/Linux. On Windows PowerShell, run:"
  echo "       wsl --cd '$(pwd)' -- ./run_inference.sh [args]"
fi

# Defaults
INPUT="test_image"
OUTDIR="outputs"
CONFIG="config/patchcore_transformers.yaml"
CKPT="model_weights/model.ckpt"
SIZE=256
SENSITIVITY=1.0
FEEDBACK=""
CPU=""
INSTALL=0
CLI_VENV=""

# Parse args
while [[ $# -gt 0 ]]; do
  case "$1" in
    --input) INPUT="$2"; shift 2;;
    --outdir) OUTDIR="$2"; shift 2;;
    --config) CONFIG="$2"; shift 2;;
    --ckpt) CKPT="$2"; shift 2;;
    --size) SIZE="$2"; shift 2;;
    --sensitivity) SENSITIVITY="$2"; shift 2;;
    --feedback) FEEDBACK="$2"; shift 2;;
    --cpu) CPU="--cpu"; shift;;
    --install) INSTALL=1; shift;;
    --venv) CLI_VENV="$2"; shift 2;;
    *) echo "Unknown arg: $1"; exit 1;;
  esac
done

# Resolve venv path
if [[ -n "$CLI_VENV" ]]; then
  VENV_PATH="$CLI_VENV"
elif [[ -n "${VENV:-}" ]]; then
  VENV_PATH="$VENV"
else
  VENV_PATH=".venv"
fi

# Try parent .venv if local not found
if [[ ! -d "$VENV_PATH" ]] && [[ -d "../.venv" ]]; then
  VENV_PATH="../.venv"
fi

if [[ ! -d "$VENV_PATH" ]]; then
  echo "[ERROR] Virtual environment not found." >&2
  echo "        Tried: '$VENV_PATH' (and '../.venv')." >&2
  echo "        Provide one via --venv /path/to/venv or env VENV=/path/to/venv." >&2
  echo "        VENV='/mnt/c/Users/pasir/Desktop/Github/transformer-image-manager-3/automatic-anamoly-detection/.venv' ./run_inference.sh" >&2
  exit 1 
fi

# shellcheck disable=SC1090
source "$VENV_PATH/bin/activate"

if [[ "$INSTALL" -eq 1 ]]; then
  echo "[INFO] Upgrading pip and installing dependencies inside venv..."
  python -m pip install --upgrade pip setuptools wheel
  pip install opencv-python Pillow omegaconf hydra-core timm scikit-learn scikit-image matplotlib pandas tqdm
  pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cpu
  pip install anomalib
fi

echo "[INFO] Using venv: $VENV_PATH"
echo "[INFO] Python: $(python --version)"
echo "[INFO] Detection sensitivity: $SENSITIVITY"
if [[ -n "$FEEDBACK" ]]; then
  echo "[INFO] Feedback adjustments file: $FEEDBACK"
else
  echo "[INFO] Feedback adjustments file: <none>"
fi
echo "[INFO] Running inference_core_local.py"
python inference_core_local.py \
  --config "$CONFIG" \
  --ckpt   "$CKPT" \
  --input  "$INPUT" \
  --outdir "$OUTDIR" \
  --size   "$SIZE" \
  --sensitivity "$SENSITIVITY" \
  --feedback "$FEEDBACK" \
  $CPU

echo "[DONE] Inference completed. Check '$OUTDIR' for results."
