# Video-mode validation calibration

50 original validation images per static letter, selected by SHA-256 of seed 470 and image ID. No training/test images are used. Each image is repeated for eight VIDEO-mode frames, 100 ms apart; a blank frame resets tracking before each sequence. This exercises tracked-frame geometry, not real hand movement.

Threshold evaluation uses the same Java VowelClassifier and 500 ms hold as the app. An incorrect stabilized vowel at any frame counts as an error; otherwise the final result is scored. Missing detections remain Unknown.

Dataset sequences: 1200. CSV SHA-256: `15d34748e098097ea311435d491edb067f38b9c0757cfef143e3413245160876`.

Selected minimum tree-vote fraction: 0.5; winning margin: 0.05.

Select candidates meeting all per-vowel precision >=90% and recall >=80% targets if available; otherwise maximize vowel macro F1. Ties favor lower thresholds to avoid rejecting small tracked-frame variations.

| Letter | Precision | Recall | Sequences |
|---|---:|---:|---:|
| A | 100.00% | 96.00% | 50 |
| E | 100.00% | 100.00% | 50 |
| I | 100.00% | 100.00% | 50 |
| O | 100.00% | 86.00% | 50 |
| U | 90.91% | 80.00% | 50 |

Matrix rows=A/E/I/O/U/Other; columns=displayed A/E/I/O/U/Unknown.

```text
[48, 0, 0, 0, 0, 2]
[0, 50, 0, 0, 0, 0]
[0, 0, 50, 0, 0, 0]
[0, 0, 0, 43, 0, 7]
[0, 0, 0, 0, 40, 10]
[0, 0, 0, 0, 4, 946]
```

All-vowel targets: PASS.

Other falsely stabilized as a vowel: 0.42% (4/950).
