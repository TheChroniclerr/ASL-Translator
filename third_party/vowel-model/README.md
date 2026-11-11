# ASL vowel model provenance

Training images: **SignAlphaSet**, version 2, published 7 May 2025.
Creators: Bindu Garg, Manisha Kasar, Achyut Kashyap, Amber Vats, Gunjan Sharma,
and Aditya Hange. Mendeley Data. DOI: [10.17632/8fmvr9m98w.2](https://doi.org/10.17632/8fmvr9m98w.2).
License: [Creative Commons Attribution 4.0 International](https://creativecommons.org/licenses/by/4.0/).

Static archive: `SignAlphaSet.zip`, 445,404,175 bytes, file ID
`a12e2b20-74de-4301-858f-35ab312d77b7`.

```text
SHA-256: 1fb90e83a9d4d3548545e487b5c7b7be8ff6e6929a8751b79c447501d7bd408f
```

Changes: static images were converted to MediaPipe hand landmarks, normalized,
grouped for splitting, and used to train a six-category hard-voting forest.
Training uses mirrored augmentation and class balancing. Output categories are
A/E/I/O/U/Other. Movement-based J/Z and the dynamic archive are excluded.
No-hand, multi-hand, unreadable, and invalid-geometry inputs are excluded and
counted in the extraction/evaluation records. Raw images are not bundled in the app.
Small derived landmark fixtures are included only in the instrumentation test APK.
Six representative, correctly classified held-out source images are also included
in that test APK, renamed to their category names without editing. Their original
IDs and split groups are recorded in `reference_images.tsv`; these six examples
exercise integration and do not measure overall accuracy. They retain the same
CC BY 4.0 attribution given above.

The model uses the bundled MediaPipe Hand Landmarker; see
[its provenance and license](../mediapipe/README.md). Training uses
[Tribuo 4.3.2](../tribuo/README.md); Android inference uses the project's Java
predictor and does not package Tribuo.

Model checksum, training-data checksum, feature version, training settings, and
validation-selected thresholds are in `app/src/main/assets/vowel_model.properties`.
See [evaluation](evaluation.md) for measured held-out results. These results are
not signer-independent or real-phone performance measurements.
The final thresholds additionally use 1,200 validation-only repeated-image sequences
in VIDEO mode; see `video-calibration.md`. The original still-image baseline report
is preserved as `evaluation-image-baseline.md`. The test split remains held out
from model fitting but has been evaluated before and after validation calibration.
