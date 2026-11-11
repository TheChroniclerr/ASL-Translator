# Vowel training

The current refinement workflow uses **desktop Python training and Java Android
inference**. Follow [phone capture instructions](CAPTURE_GUIDE.md) first, then
[refinement training and export](REFINEMENT_TRAINING.md). Python is optional for
normal Android Studio builds and is never bundled in the app.

## Reproduce the original Java baseline

The Android app can be built and run immediately using the bundled model. These
steps are only for explicitly reproducing or replacing that model. All feature
extraction, training, exporting, and inference code in the baseline workflow below
is Java. No Python is required to reproduce that baseline.
Gradle configuration uses the project's existing Kotlin DSL.

## Requirements and data

Use the existing JDK 25 Gradle toolchain, Android SDK 37, and an Android emulator.
Open the root folder in Android Studio to load `app`, `recognition-core`, and
`recognition-training`. Normal `app` builds do not run the training module.

Download **SignAlphaSet.zip**, the static archive from
[SignAlphaSet v2](https://data.mendeley.com/datasets/8fmvr9m98w/2), into
`app/build/vowel-work/SignAlphaSet.zip`. Do not download `ASL_dynamic.zip` for this
milestone. The static archive is 445,404,175 bytes. Its expected SHA-256 is:

```text
1fb90e83a9d4d3548545e487b5c7b7be8ff6e6929a8751b79c447501d7bd408f
```

The Java preparer verifies the archive checksum, reads the 24 static-letter image
folders, excludes J/Z, detects conflicting exact labels, and creates a manifest.
Exact SHA-256 duplicates and connected dHash groups with Hamming distance <=2 stay
in the same split. Groups are deterministically assigned approximately 70% train,
15% validation, and 15% test per letter, with seed 470. Group sizes make the exact
image proportions vary. This avoids known duplicate leakage, but is not proof of
signer independence. The source has no signer/session IDs, and grouping by visual
similarity cannot recover them.

From the root folder's Android Studio terminal:

```powershell
.\gradlew.bat :recognition-training:run --args='prepare "app/build/vowel-work/SignAlphaSet.zip" "app/build/vowel-work/vowel-dataset"'
```

Inspect sample images against a reliable ASL reference before training new data.
The original public images for A/E/I/O/U were visually checked for this version.
Natural variants, especially E, are not exhaustively represented or validated.

## Extract landmarks on the emulator

Start an emulator in Android Studio Device Manager. The commands below explicitly
select `emulator-5554`; use `adb devices` to substitute its serial if necessary.
They install the project and its test APK on that emulator, not on your phone.

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $adb devices
& $adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
& $adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& $adb -s emulator-5554 shell mkdir -p /sdcard/Android/data/com.group_nine.asl_translator/files/vowel-dataset
& $adb -s emulator-5554 push app/build/vowel-work/vowel-dataset/. /sdcard/Android/data/com.group_nine.asl_translator/files/vowel-dataset/
& $adb -s emulator-5554 shell am instrument -w -r -e class com.group_nine.asl_translator.home.DatasetLandmarkExportTest -e extractDataset true com.group_nine.asl_translator.test/androidx.test.runner.AndroidJUnitRunner
& $adb -s emulator-5554 pull /sdcard/Android/data/com.group_nine.asl_translator/files/vowel-dataset/landmarks.csv app/build/vowel-work/landmarks.csv
& $adb -s emulator-5554 pull /sdcard/Android/data/com.group_nine.asl_translator/files/vowel-dataset/extraction.txt app/build/vowel-work/extraction.txt
```

The opt-in utility reads the existing local images, uses Hand Landmarker float16
version 1 in IMAGE mode on CPU with 0.5 thresholds, and writes 21 x/y/z landmarks
only for images with exactly one detected hand. It never opens the camera. It logs
progress every 500 images under the `VowelDataset` tag. Unreadable/no-hand/two-hand
images are counted and excluded. Extraction and transfer can take several minutes.

## Calibrate for tracked frames

The final model uses validation sequences in VIDEO mode as well as the IMAGE-mode
features used to train the forest. MediaPipe can produce slightly different
landmarks after it switches from palm detection to tracking. Calibrating only on
still-image features can reject a correctly tracked letter.

With the test APK installed and the prepared dataset still on the emulator:

```powershell
& $adb -s emulator-5554 shell am instrument -w -r -e class 'com.group_nine.asl_translator.home.DatasetLandmarkExportTest#exportVideoValidation' -e calibrateVideo true com.group_nine.asl_translator.test/androidx.test.runner.AndroidJUnitRunner
& $adb -s emulator-5554 pull /sdcard/Android/data/com.group_nine.asl_translator/files/vowel-dataset/video-validation.csv app/build/vowel-work/video-validation.csv
```

This explicitly selects 50 validation images per static letter by a seeded hash,
then repeats each image for eight frames 100 ms apart. A blank frame resets the
previous tracking region before each sequence. The 1,200 sequences exercise the
detector's VIDEO-mode geometry and the app's 500 ms stabilization. They are not
recorded videos of moving hands. Training/test images are forbidden by the Java
calibrator's original-manifest check.

## Train, validate, and export

```powershell
.\gradlew.bat :recognition-training:run --args='train-video "app/build/vowel-work/landmarks.csv" "app/build/vowel-work/model"'
```

The shared feature extractor converts normalized landmarks to aspect-correct
upright pixel coordinates (z is scaled by image width), subtracts the wrist, and
divides by the wrist-to-middle-MCP palm distance. It rejects non-finite, subpixel
palm, and implausible geometry. Feature version is `wrist-palm-aspect-v1`.

Training uses Tribuo 4.3.2: 128 hard-voting random-forest trees, maximum depth 12,
minimum child weight 5, eight of 63 features per split, and seed 470. Training data
is downsampled deterministically to equal A/E/I/O/U/Other class counts. Other is
sampled round-robin across its 19 static letters. Only training examples receive
mirrored augmentation; validation reflections are separate from training.

Validation selects a minimum vote fraction from 0.50..0.95 in 0.05 increments and
a winning margin from 0.05..0.30 in 0.05 increments. The final `train-video` command
uses VIDEO-mode validation sequences and the same stabilizer as the app. Any
incorrect stabilized vowel in a sequence counts as an error. Prefer candidates meeting all
per-vowel precision >=90% and recall >=80% targets, then maximize vowel macro F1;
otherwise maximize vowel macro F1 and clearly report unmet targets. Ties favor
lower thresholds for tracked-frame tolerance. No test examples influence this choice. Scores are tree-vote
fractions, not calibrated probabilities.

The optional `train` command reproduces the original still-image-only calibration
baseline (ties favor stricter thresholds). Its initial evaluation is preserved in
`third_party/vowel-model/evaluation-image-baseline.md`. The final test report clearly
states that the held-out test split was evaluated previously; it is not a fresh,
signer-independent external test set. Forest hyperparameters and training examples
are unchanged by temporal calibration.

Export writes a versioned binary forest, metadata/checksums, held-out landmark
fixtures, and an evaluation report. The Java predictor's class vote scores are
checked against Tribuo on validation and test examples, including reflections.
The binary contains feature IDs, thresholds, child links, and leaf labels; it does
not embed training images, serialized Java objects, or desktop library dependencies.

The report also audits all original validation/test images using the preparation
manifest next to the CSV (`vowel-dataset/manifest.tsv`). Missed detections count as
Unknown in this whole-image evaluation. Keep that manifest with the CSV; without
it, full pipeline recall cannot be measured. `audit` can refresh this report from
an existing model without retraining or changing its thresholds.

After reviewing the evaluation, explicitly copy the export into the app:

```powershell
Copy-Item app/build/vowel-work/model/vowel_forest.bin app/src/main/assets/
Copy-Item app/build/vowel-work/model/vowel_model.properties app/src/main/assets/
Copy-Item app/build/vowel-work/model/vowel_fixtures.csv app/src/androidTest/assets/
Copy-Item app/build/vowel-work/model/evaluation.md third_party/vowel-model/
Copy-Item app/build/vowel-work/model/reference_images.tsv third_party/vowel-model/
Copy-Item app/build/vowel-work/model/video-calibration.md third_party/vowel-model/
Import-Csv app/build/vowel-work/model/reference_images.tsv -Delimiter "`t" | ForEach-Object {
    Copy-Item (Join-Path 'app/build/vowel-work/vowel-dataset/images' $_.id) (Join-Path 'app/src/androidTest/assets/vowels' ($_.label + '.jpg'))
}
.\gradlew.bat :recognition-core:test :recognition-training:test :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Keep the source/model/dataset attribution and extraction counts current. Measure
real-phone behavior separately with all five vowels, each hand, multiple people,
unsupported signs, natural orientation, varied backgrounds, and good/poor lighting.
Do not report the public dataset score as real-world translation accuracy.
