# Feedback Integration for Model Improvement

## Overview

This module enables human-in-the-loop feedback to continuously refine anomaly detection accuracy.  
It uses user annotations to compute small, explainable confidence “nudges” for each fault label, allowing the AI to gradually align with expert feedback during real-world operation.

---

## How It Works

### 1. User Annotations (Frontend)

After the anomaly detection runs, users can add, edit, resize, or delete bounding boxes on the image viewer.

The React UI maintains two parallel JSON structures:

- `originalResultJson` → AI detections from the model  
- `modifiedResultJson` → human-edited annotations  

Each edit automatically triggers a PATCH or PUT request to persist the change in the backend.

Whenever the user makes a change (add/delete/resize/etc.), the frontend automatically sends a PATCH or PUT request to the backend with the updated JSON and metadata:

```json
{
  "image_id": 12,
  "user_id": 7,
  "originalResultJson": { ... },
  "modifiedResultJson": { ... },
  "timestamp": "2025-10-22T11:00:00Z"
}
```
The backend writes this into the annotation table so every edit is permanently stored with:
- who made the change
- when it was made
- which image it belongs to
- both the AI prediction and the human correction

### 2. Backend Aggregation (ModelFeedbackService)

- The `ModelFeedbackService` periodically (or when requested) scans all annotations and compares each `originalResultJson` vs. `modifiedResultJson`, grouped by label.  
- For every label (for example, *“Loose Joint”* or *“Point Overload”*), it computes three key deltas that quantify how humans corrected the model output.


#### a. Count Change
- Measures how many boxes humans added or deleted.  
- A value of **+1** means the model missed one anomaly; **–1** means the model detected one incorrectly.


#### b. Area Change
- Measures how much the total annotated area grew or shrank.  
- For example, **+0.4** means users drew larger boxes compared to the model’s predictions.  
- Normalized so that small labels are not overshadowed by large ones.


#### c. Confidence Change
- Measures how humans adjusted the model’s confidence.  
- Only considers the AI’s own detections; user-added boxes have no AI confidence value.  

---

1. It then blends these three using weights (typically 0.5, 0.3, 0.2) to produce a single adjustment signal per label.
2. Scale by learningRate (e.g., 0.0001 = 0.01%)
3. Accumulate into the stored per-label bias

