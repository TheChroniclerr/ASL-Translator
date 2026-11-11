# Vowel recognition evaluation

Classifier tables are conditional on successful single-hand landmark extraction and valid geometry. Whole-image pipeline evaluation, including missed detections, is reported separately below.

Dataset: SignAlphaSet v2 (CC BY 4.0), DOI 10.17632/8fmvr9m98w.2.

Split before mirrored augmentation; exact/near-duplicate groups stay together. Signer/session IDs are not supplied. This is not a signer-independent or live-phone evaluation.

- train: 16575 original images; class counts A/E/I/O/U/Other: [700, 700, 700, 454, 700, 13321]
- validation: 3522 original images; class counts A/E/I/O/U/Other: [150, 150, 153, 125, 150, 2794]
- test: 3470 original images; class counts A/E/I/O/U/Other: [150, 150, 147, 108, 150, 2765]
- Training after class balancing and mirrored augmentation: 5448
- Export verification: 13984 validation/test examples, all six vote scores agree with Tribuo within 1e-6.
- Validation-selected minimum tree-vote fraction: 0.75; minimum winning margin: 0.30.

Validation threshold selection includes each validation image and its reflection. Tree-vote fractions are not calibrated probabilities. Test images are used only for final evaluation and export verification.

## Validation

| Letter | Precision | Recall | Test images |
|---|---:|---:|---:|
| A | 100.00% | 100.00% | 300 |
| E | 100.00% | 100.00% | 300 |
| I | 100.00% | 99.35% | 306 |
| O | 100.00% | 100.00% | 250 |
| U | 99.00% | 99.33% | 300 |

Vowel macro F1: 0.9977. Overall rejection: 79.34%. Vowel rejection: 0.27%. Other falsely accepted as a vowel: 0.05% (3/5588).

Confusion matrix: rows=actual A/E/I/O/U/Other; columns=displayed A/E/I/O/U/Unknown.

```text
[300, 0, 0, 0, 0, 0]
[0, 300, 0, 0, 0, 0]
[0, 0, 304, 0, 0, 2]
[0, 0, 0, 250, 0, 0]
[0, 0, 0, 0, 298, 2]
[0, 0, 0, 0, 3, 5585]
```

## Untouched test split

| Letter | Precision | Recall | Test images |
|---|---:|---:|---:|
| A | 100.00% | 98.67% | 150 |
| E | 100.00% | 99.33% | 150 |
| I | 100.00% | 100.00% | 147 |
| O | 100.00% | 100.00% | 108 |
| U | 100.00% | 98.00% | 150 |

Vowel macro F1: 0.9960. Overall rejection: 79.86%. Vowel rejection: 0.85%. Other falsely accepted as a vowel: 0.00% (0/2765).

Confusion matrix: rows=actual A/E/I/O/U/Other; columns=displayed A/E/I/O/U/Unknown.

```text
[148, 0, 0, 0, 0, 2]
[0, 149, 0, 0, 0, 1]
[0, 0, 147, 0, 0, 0]
[0, 0, 0, 108, 0, 0]
[0, 0, 0, 0, 147, 3]
[0, 0, 0, 0, 0, 2765]
```

## Test split including reflected views

| Letter | Precision | Recall | Test images |
|---|---:|---:|---:|
| A | 100.00% | 99.00% | 300 |
| E | 100.00% | 99.33% | 300 |
| I | 100.00% | 100.00% | 294 |
| O | 100.00% | 100.00% | 216 |
| U | 100.00% | 98.00% | 300 |

Vowel macro F1: 0.9963. Overall rejection: 79.84%. Vowel rejection: 0.78%. Other falsely accepted as a vowel: 0.00% (0/5530).

Confusion matrix: rows=actual A/E/I/O/U/Other; columns=displayed A/E/I/O/U/Unknown.

```text
[297, 0, 0, 0, 0, 3]
[0, 298, 0, 0, 0, 2]
[0, 0, 294, 0, 0, 0]
[0, 0, 0, 216, 0, 0]
[0, 0, 0, 0, 294, 6]
[0, 0, 0, 0, 0, 5530]
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
| A | 100.00% | 100.00% | 150 |
| E | 100.00% | 100.00% | 150 |
| I | 100.00% | 99.35% | 153 |
| O | 100.00% | 81.70% | 153 |
| U | 99.33% | 99.33% | 150 |

Vowel macro F1: 0.9779. Overall rejection: 79.64%. Vowel rejection: 3.97%. Other falsely accepted as a vowel: 0.04% (1/2814).

Confusion matrix: rows=actual A/E/I/O/U/Other; columns=displayed A/E/I/O/U/Unknown.

```text
[150, 0, 0, 0, 0, 0]
[0, 150, 0, 0, 0, 0]
[0, 0, 152, 0, 0, 1]
[0, 0, 0, 125, 0, 28]
[0, 0, 0, 0, 149, 1]
[0, 0, 0, 0, 1, 2813]
```

### Whole-image test

| Letter | Precision | Recall | Test images |
|---|---:|---:|---:|
| A | 100.00% | 98.67% | 150 |
| E | 100.00% | 99.33% | 150 |
| I | 100.00% | 100.00% | 147 |
| O | 100.00% | 73.47% | 147 |
| U | 100.00% | 98.00% | 150 |

Vowel macro F1: 0.9654. Overall rejection: 80.14%. Vowel rejection: 6.05%. Other falsely accepted as a vowel: 0.00% (0/2775).

Confusion matrix: rows=actual A/E/I/O/U/Other; columns=displayed A/E/I/O/U/Unknown.

```text
[148, 0, 0, 0, 0, 2]
[0, 149, 0, 0, 0, 1]
[0, 0, 147, 0, 0, 0]
[0, 0, 0, 108, 0, 39]
[0, 0, 0, 0, 147, 3]
[0, 0, 0, 0, 0, 2775]
```

Whole-image test targets (precision >=90%, recall >=80% per vowel): NOT MET.
- O misses the target: precision 100.00%, recall 73.47%. Detector misses are included in recall.

These are still public-image results, not measurements of live-phone stabilization, motion, or unseen signers.
