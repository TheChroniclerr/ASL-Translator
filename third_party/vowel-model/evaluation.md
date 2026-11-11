# Vowel recognition evaluation

Dataset: SignAlphaSet v2 (CC BY 4.0), DOI 10.17632/8fmvr9m98w.2.

Split before mirrored augmentation; exact/near-duplicate groups stay together. Signer/session IDs are not supplied. This is not a signer-independent or live-phone evaluation.

Classifier tables are conditional on successful single-hand landmark extraction and valid geometry. Whole-image pipeline evaluation, including missed detections, is reported separately below.

- train: 16575 original images; class counts A/E/I/O/U/Other: [700, 700, 700, 454, 700, 13321]
- validation: 3522 original images; class counts A/E/I/O/U/Other: [150, 150, 153, 125, 150, 2794]
- test: 3470 original images; class counts A/E/I/O/U/Other: [150, 150, 147, 108, 150, 2765]
- Training after class balancing and mirrored augmentation: 5448
- Export verification: 13984 validation/test examples, all six vote scores agree with Tribuo within 1e-6.
- Validation-selected minimum tree-vote fraction: 0.50; minimum winning margin: 0.05.

Thresholds use validation-only VIDEO-mode sequences and the app's 500 ms stabilization. See video-calibration.md. The same forest configuration and training split are retained from the initial still-image baseline; the test split is re-evaluated after this validation-only calibration. It is held out from training, but has been evaluated previously.

Tree-vote fractions are not calibrated probabilities.

## Validation

| Letter | Precision | Recall | Test images |
|---|---:|---:|---:|
| A | 99.34% | 100.00% | 300 |
| E | 100.00% | 100.00% | 300 |
| I | 100.00% | 100.00% | 306 |
| O | 100.00% | 100.00% | 250 |
| U | 84.75% | 100.00% | 300 |

Vowel macro F1: 0.9828. Overall rejection: 78.53%. Vowel rejection: 0.00%. Other falsely accepted as a vowel: 1.00% (56/5588).

Confusion matrix: rows=actual A/E/I/O/U/Other; columns=displayed A/E/I/O/U/Unknown.

```text
[300, 0, 0, 0, 0, 0]
[0, 300, 0, 0, 0, 0]
[0, 0, 306, 0, 0, 0]
[0, 0, 0, 250, 0, 0]
[0, 0, 0, 0, 300, 0]
[2, 0, 0, 0, 54, 5532]
```

## Held-out test split

| Letter | Precision | Recall | Test images |
|---|---:|---:|---:|
| A | 99.34% | 100.00% | 150 |
| E | 100.00% | 100.00% | 150 |
| I | 100.00% | 100.00% | 147 |
| O | 99.08% | 100.00% | 108 |
| U | 91.36% | 98.67% | 150 |

Vowel macro F1: 0.9882. Overall rejection: 79.28%. Vowel rejection: 0.28%. Other falsely accepted as a vowel: 0.58% (16/2765).

Confusion matrix: rows=actual A/E/I/O/U/Other; columns=displayed A/E/I/O/U/Unknown.

```text
[150, 0, 0, 0, 0, 0]
[0, 150, 0, 0, 0, 0]
[0, 0, 147, 0, 0, 0]
[0, 0, 0, 108, 0, 0]
[0, 0, 0, 0, 148, 2]
[1, 0, 0, 1, 14, 2749]
```

## Test split including reflected views

| Letter | Precision | Recall | Test images |
|---|---:|---:|---:|
| A | 99.34% | 100.00% | 300 |
| E | 100.00% | 100.00% | 300 |
| I | 100.00% | 100.00% | 294 |
| O | 99.08% | 100.00% | 216 |
| U | 89.43% | 98.67% | 300 |

Vowel macro F1: 0.9861. Overall rejection: 79.18%. Vowel rejection: 0.28%. Other falsely accepted as a vowel: 0.71% (39/5530).

Confusion matrix: rows=actual A/E/I/O/U/Other; columns=displayed A/E/I/O/U/Unknown.

```text
[300, 0, 0, 0, 0, 0]
[0, 300, 0, 0, 0, 0]
[0, 0, 294, 0, 0, 0]
[0, 0, 0, 216, 0, 0]
[0, 0, 0, 0, 296, 4]
[2, 0, 0, 2, 35, 5491]
```

Conditional classifier targets: precision >=90% and recall >=80% for each vowel. Result: PASS.

Other contains the 19 remaining static letters; it does not cover every possible unknown hand shape. Movement letters J/Z are excluded. Dataset collection conditions differ from phone use; test with additional people and backgrounds before claiming generalization.

## Whole-image pipeline evaluation

All original held-out images are included here. Images with no usable single-hand landmarks count as Unknown, rather than being silently dropped. Thresholds remain those chosen using validation classifier results; this audit does not retrain or tune the model.

Vowel extraction coverage across all original splits:

| Letter | Usable landmarks | Original images | Coverage |
|---|---:|---:|---:|
| A | 1000 | 1000 | 100.00% |
| E | 1000 | 1000 | 100.00% |
| I | 1000 | 1000 | 100.00% |
| O | 687 | 1000 | 68.70% |
| U | 1000 | 1000 | 100.00% |

### Whole-image validation

| Letter | Precision | Recall | Test images |
|---|---:|---:|---:|
| A | 99.34% | 100.00% | 150 |
| E | 100.00% | 100.00% | 150 |
| I | 100.00% | 100.00% | 153 |
| O | 100.00% | 81.70% | 153 |
| U | 86.21% | 100.00% | 150 |

Vowel macro F1: 0.9644. Overall rejection: 78.91%. Vowel rejection: 3.70%. Other falsely accepted as a vowel: 0.89% (25/2814).

Confusion matrix: rows=actual A/E/I/O/U/Other; columns=displayed A/E/I/O/U/Unknown.

```text
[150, 0, 0, 0, 0, 0]
[0, 150, 0, 0, 0, 0]
[0, 0, 153, 0, 0, 0]
[0, 0, 0, 125, 0, 28]
[0, 0, 0, 0, 150, 0]
[1, 0, 0, 0, 24, 2789]
```

### Whole-image test

| Letter | Precision | Recall | Test images |
|---|---:|---:|---:|
| A | 99.34% | 100.00% | 150 |
| E | 100.00% | 100.00% | 150 |
| I | 100.00% | 100.00% | 147 |
| O | 99.08% | 73.47% | 147 |
| U | 91.36% | 98.67% | 150 |

Vowel macro F1: 0.9578. Overall rejection: 79.57%. Vowel rejection: 5.51%. Other falsely accepted as a vowel: 0.58% (16/2775).

Confusion matrix: rows=actual A/E/I/O/U/Other; columns=displayed A/E/I/O/U/Unknown.

```text
[150, 0, 0, 0, 0, 0]
[0, 150, 0, 0, 0, 0]
[0, 0, 147, 0, 0, 0]
[0, 0, 0, 108, 0, 39]
[0, 0, 0, 0, 148, 2]
[1, 0, 0, 1, 14, 2759]
```

Whole-image test targets (precision >=90%, recall >=80% per vowel): NOT MET.
- O misses the target: precision 99.08%, recall 73.47%. Detector misses are included in recall.

These are still public-image results, not measurements of live-phone stabilization, motion, or unseen signers.
