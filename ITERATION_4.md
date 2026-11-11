# Iteration 4: offline ASL vowel recognition

## Implemented

- Java recognition of the isolated fingerspelling letters A, E, I, O, U, plus an
  Other/rejection category. The current letter appears in the existing output panel.
- The existing CameraX/MediaPipe pipeline remains on one worker and retains its
  two-hand landmarks. Classification operates on one hand at a time and asks for
  one hand when two are visible. No separate toggle is needed.
- A 500 ms continuous hold stabilizes output. Missing hands, ambiguous predictions,
  stopping/switching cameras, navigation, backgrounding, logout, and recreation
  clear the current letter. Gaps over 250 ms restart stabilization.
- Classifier initialization/inference failures show retry feedback while preserving
  tracking/preview when possible. Camera-session generation checks discard late
  results. Model loading/inference/reset occur on the existing worker.
- Shared `recognition-core` Java library and an explicitly run desktop Java trainer.
  No Python, remote inference service, second camera analyzer, or new native runtime.
- Versioned binary model with checksum/provenance, required third-party attribution,
  Java reproduction instructions, Help updates, and evaluation reports.

## Data and evaluation

SignAlphaSet v2's static ZIP matches its published SHA-256. Its 24,000 static-letter
images exclude J/Z. Exact and near-duplicate groups are assigned to approximately
70/15/15 train/validation/test partitions before mirrored training augmentation.
The source has no signer/session IDs, so this split is not signer-independent.

The bundled Hand Landmarker produced usable single-hand landmarks for 23,567
images; 433 had no detected hand, with no unreadable or multi-hand exclusions.
All extracted landmarks passed geometry validation. O accounts for 313 of the
missed detections; only 687/1,000 source O images provided landmarks.

Training uses Tribuo 4.3.2, 128 hard-voting trees, depth 12, eight features per split,
seed 470, and equal class counts. The smallest valid training class has 454 images;
balancing and mirrored augmentation produce 5,448 training examples.

The initial still-image calibration was too strict for some VIDEO-mode tracked
landmarks. Final calibration uses 1,200 validation-only repeated-image sequences
(50 images/static letter, eight frames 100 ms apart), with the app's stabilizer.
Selected minimum tree-vote fraction is 0.50 and winning margin is 0.05. Scores are
not calibrated probabilities. The forest trees are byte-for-byte identical before
and after recalibration; only thresholds changed.

The original test evaluation is retained in `evaluation-image-baseline.md`. Final
evaluation clearly discloses that the training-held-out test set was evaluated
previously. The same split is not presented as a fresh external test set.

Whole-image test results, including missed detections:

| Vowel | Precision | Recall |
|---|---:|---:|
| A | 99.34% | 100.00% |
| E | 100.00% | 100.00% |
| I | 100.00% | 100.00% |
| O | 99.08% | 73.47% |
| U | 91.36% | 98.67% |

**The overall target is not met for O:** recall remains below 80% because of
missed hand detections. Other falsely accepted as a vowel: 16/2,775 (0.58%) in
single-image evaluation. This does not quantify errors after live stabilization.
See [full evaluation](third_party/vowel-model/evaluation.md) and
[temporal validation](third_party/vowel-model/video-calibration.md) for confusion
matrices, rejection rates, and the separate conditional-classifier results.

## Validation

- Debug APK and test APK assemble successfully; Android lint reports 0 errors and
  36 advisory warnings.
- 28 unit tests pass: 15 recognition, 7 data/calibration guards, and 6 existing app
  and coordinate-mapping tests.
- 34 Android emulator tests pass: 24 existing detector/camera/tracking/projection
  checks, 8 new recognition checks, and 2 independently reset permission tests.
- A final 17-test camera/layout/recognition run also passes after removing obsolete
  captions. The packaged APK's native libraries pass 16 KiB ZIP alignment checks.
- New real-model tests check 60 held-out landmark fixtures and six representative
  reference images through VIDEO-mode detection and the 500 ms stabilizer. These
  selected examples are integration checks, not an accuracy estimate.
- Espresso tests inject deterministic detector/classifier results to check status,
  unknown signs, two hands, initialization/inference failure, restart, blocked late
  results, camera switching, recreation, navigation, and backgrounding.
- Exported class vote scores match Tribuo within 1e-6 for 13,984 original/reflected
  validation/test examples. Binary model validation rejects invalid formats/nodes.
- Both explicit extraction utilities completed successfully. They skip in ordinary
  instrumentation runs without their required arguments.
- Screenshot inspection covers portrait, landscape, dark mode at 130% font size,
  and the recognized-vowel layout. Scripted UI screenshots contain simulated
  landmarks; they are not real-phone recognition evidence.

The previous hand-tracking milestone was reported working on a physical phone.
This vowel APK still needs live-phone checks of all five letters, either hand,
natural signing variants, unsupported shapes, movement, distance, and lighting.
No real-phone recognition accuracy, TalkBack session, or frame-rate claim is made.

## Boundaries and delivery

Current letter only; no accumulated text, word/phrase recognition, sentence
translation, speech, history/database, training on-device, or account changes.
The local branch is `vowel-recognition`, based on the completed hand-tracking
commit. Remote branches are unchanged. The delivery contains a debug APK and a
clean Android Studio project folder/ZIP, including model assets and required
attribution, with machine-specific configuration and generated outputs excluded.
