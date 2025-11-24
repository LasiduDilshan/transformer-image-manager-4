---
title: automatic-anamoly-detection
sdk: docker
app_file: Dockerfile
app_port: 7860
pinned: false
---

# Automatic Anomaly Detection (PatchCore)

FastAPI wrapper around the PatchCore inference pipeline. Provides a single `/infer` endpoint that accepts an image and returns the boxed overlay plus detections JSON (base64 image payload, JSON text/object, label).

## Local setup (venv)
- `python -m venv .venv && .venv\Scripts\activate` on Windows or `python -m venv .venv && source .venv/bin/activate` on Linux/WSL.
- `pip install --upgrade pip setuptools wheel`
- `pip install -r requirements.txt`
- Run locally: `python app.py` (uses `PORT` env if set, default 7860).

## Deploy to Hugging Face Space
1. `pip install huggingface_hub hf_transfer` (once) and `huggingface-cli login`.
2. `git clone https://huggingface.co/spaces/Lasidu/automatic-anamoly-detection` (or add a HF remote to this repo).
3. Copy this project into that clone (including `Model_Inference` and `app.py`).
4. Provide weights:
   - Track `Model_Inference/model_weights/model.ckpt` with Git LFS (already configured) and push, **or**
   - Keep weights out of git and add a Space secret `CKPT_URL` pointing to your checkpoint; optionally set `CKPT_PATH` to change the on-disk location.
5. Commit and push: `git add . && git commit -m "Sync API build"` then `git push`.

## API usage
`POST /infer` with multipart form fields:
- `file`: image file
- `sensitivity` (optional float, default 1.0)
- `feedback_json` (optional JSON string)
- `ckpt_url` (optional checkpoint URL override)

Response includes `label`, `json` (object), `json_text`, `boxed_image_base64`, `boxed_image_ext`, `feedback_applied`, `duration_ms`.
