# Desktop refinement training notices

The optional desktop trainer uses scikit-learn 1.9.1, licensed under BSD 3-Clause.
Its source and license are available at https://github.com/scikit-learn/scikit-learn.
The installed wheel's license is preserved in `SCIKIT_LEARN_COPYING.txt`.

The locked desktop environment also uses NumPy, SciPy, joblib, cloudpickle,
narwhals, and threadpoolctl. Their package versions are recorded in
`tools/vowel-training/requirements-lock.txt`. These dependencies are installed
separately for training and are not redistributed in the source ZIP or APK.

The Java feature/import/verification utility additionally uses org.json:json
20240303 (JSON-java); its Public Domain notice is preserved in `JSON_JAVA_LICENSE.txt`.
It is a desktop training dependency, not an Android runtime dependency.

Public training data and the current bundled model retain their existing notices
in `third_party/vowel-model`, `third_party/mediapipe`, and `third_party/tribuo`.
Phone samples are opt-in personal recordings and are not published or delivered
with the project. A future trained model must retain its input checksums,
evaluation report, and the applicable public dataset attribution.
