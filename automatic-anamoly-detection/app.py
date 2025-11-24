import base64
import json
import os
import shutil
import sys
import tempfile
import time
from pathlib import Path
from threading import Lock
from typing import Optional

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
import requests
import torch

# Ensure we can import inference_core_local from Model_Inference/
BASE_DIR = Path(__file__).parent.resolve()
MODEL_DIR = BASE_DIR / "Model_Inference"
sys.path.append(str(MODEL_DIR))
import inference_core_local as infer  # noqa: E402

CONFIG_PATH = str(MODEL_DIR / "config" / "patchcore_transformers.yaml")
DEFAULT_CKPT_PATH = MODEL_DIR / "model_weights" / "model.ckpt"
DEFAULT_INFER_SIZE = getattr(infer, "DEFAULT_INFER_SIZE", 256)

DEVICE = torch.device("cuda" if torch.cuda.is_available() else "cpu")

_model_lock = Lock()
_model = None
_loaded_ckpt: Optional[str] = None


def _clamp_sensitivity(raw_value: float) -> float:
    try:
        return max(0.1, min(2.0, float(raw_value)))
    except Exception:
        return 1.0


def _download_checkpoint(url: str, target_path: Path) -> Path:
    target_path.parent.mkdir(parents=True, exist_ok=True)
    with requests.get(url, stream=True, timeout=60) as resp:
        resp.raise_for_status()
        with open(target_path, "wb") as fh:
            for chunk in resp.iter_content(chunk_size=8192):
                if chunk:
                    fh.write(chunk)
    return target_path


def _ensure_checkpoint(override_url: Optional[str] = None) -> Path:
    """
    Ensure a .ckpt exists locally. If missing, try CKPT_URL env or override_url.
    """
    target = Path(os.environ.get("CKPT_PATH", DEFAULT_CKPT_PATH))
    if target.exists():
        return target

    ckpt_url = override_url or os.environ.get("CKPT_URL")
    if not ckpt_url:
        raise RuntimeError(
            "Model checkpoint not found. Provide CKPT_URL env or pass ckpt_url in the request."
        )

    return _download_checkpoint(ckpt_url, target)


def _load_model(ckpt_url: Optional[str] = None):
    global _model, _loaded_ckpt
    with _model_lock:
        ckpt_path = _ensure_checkpoint(ckpt_url)
        if _model is None or str(ckpt_path) != _loaded_ckpt:
            _model, _cfg = infer.load_model(CONFIG_PATH, str(ckpt_path), DEVICE)
            _loaded_ckpt = str(ckpt_path)
    return _model


def _parse_feedback(feedback_json: str):
    if not feedback_json:
        return {}
    try:
        return json.loads(feedback_json)
    except Exception as exc:  # pragma: no cover - defensive
        return {"error": f"Failed to parse feedback: {exc}"}


def _run_inference_bytes(
    file_bytes: bytes,
    filename: str,
    sensitivity: float,
    feedback_json: str,
    ckpt_url: Optional[str] = None,
):
    """Core inference routine used by the FastAPI endpoint."""
    sensitivity = _clamp_sensitivity(sensitivity)
    try:
        model = _load_model(ckpt_url)
    except Exception as exc:
        raise RuntimeError(f"Model load failed: {exc}") from exc

    tmp_dir = Path(tempfile.mkdtemp(prefix="patchcore_api_"))
    try:
        input_name = filename or "input.png"
        input_path = tmp_dir / input_name
        input_path.write_bytes(file_bytes)

        out_base = tmp_dir / "outputs"
        out_boxed = out_base / "boxed"
        out_mask = out_base / "masks"
        out_filtered = out_base / "filtered"
        for p in (out_boxed, out_mask, out_filtered):
            p.mkdir(parents=True, exist_ok=True)

        feedback_obj = _parse_feedback(feedback_json)
        with _model_lock:
            result = infer.run_pipeline_for_image(
                model,
                DEVICE,
                str(input_path),
                out_boxed_dir=str(out_boxed),
                out_mask_dir=str(out_mask),
                out_filtered_dir=str(out_filtered),
                infer_size=DEFAULT_INFER_SIZE,
                sensitivity=sensitivity,
                feedback=feedback_obj,
            )

        boxed_path = Path(result["boxed_path"])
        json_path = Path(result["json_path"])
        boxed_bytes = boxed_path.read_bytes()
        json_text = json_path.read_text(encoding="utf-8")

        return {
            "label": result.get("label"),
            "boxed_bytes": boxed_bytes,
            "boxed_image_ext": boxed_path.suffix or ".png",
            "json": json.loads(json_text),
            "json_text": json_text,
            "feedback_applied": bool(
                (result.get("feedback") or {}).get("applied", False)
            ),
        }
    finally:
        shutil.rmtree(tmp_dir, ignore_errors=True)


api = FastAPI(title="PatchCore Anomaly Detection API")


@api.get("/health")
def health():
    return {
        "status": "ok",
        "device": str(DEVICE),
        "loaded_ckpt": _loaded_ckpt,
    }


@api.post("/infer")
async def infer_endpoint(
    file: UploadFile = File(...),
    sensitivity: float = Form(1.0),
    feedback_json: str = Form(""),
    ckpt_url: str = Form("", description="Optional override for checkpoint URL"),
):
    if file is None:
        raise HTTPException(status_code=400, detail="file is required")

    file_bytes = await file.read()
    if not file_bytes:
        raise HTTPException(status_code=400, detail="Uploaded file is empty")

    started = time.perf_counter()
    try:
        inference = _run_inference_bytes(
            file_bytes,
            file.filename or "upload.png",
            sensitivity,
            feedback_json,
            ckpt_url or None,
        )
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc

    duration_ms = int((time.perf_counter() - started) * 1000)
    boxed_b64 = base64.b64encode(inference["boxed_bytes"]).decode("ascii")

    return {
        "label": inference.get("label"),
        "json": inference.get("json"),
        "json_text": inference.get("json_text"),
        "boxed_image_base64": boxed_b64,
        "boxed_image_ext": inference.get("boxed_image_ext", ".png"),
        "feedback_applied": inference.get("feedback_applied"),
        "duration_ms": duration_ms,
    }


@api.get("/", include_in_schema=False)
def root():
    return {
        "message": "PatchCore anomaly detection API is running. POST /infer with multipart form: file, sensitivity (float), feedback_json (string), optional ckpt_url."
    }


app = api


if __name__ == "__main__":  # pragma: no cover - manual run
    import uvicorn

    uvicorn.run(
        "app:app",
        host="0.0.0.0",
        port=int(os.environ.get("PORT", 7860)),
        log_level="info",
    )
