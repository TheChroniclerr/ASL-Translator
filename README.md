# ASL Translator

Native Android app with Java application code and XML layouts. Open this repository's
root folder in Android Studio. The current milestone recognizes the individual
ASL fingerspelling vowels **A, E, I, O, U** offline, using the existing live hand
tracker. The updated Home screen also supports typing and voice-to-text input.
ASL word recognition and sentence translation are future work.

This debug version also provides **Collect vowel samples** in the overflow menu.
It is the first delivery of E/O refinement: the original model remains the default
until a replacement is trained and passes evaluation on your phone recordings.
See [capture instructions](CAPTURE_GUIDE.md), [Python training/export](REFINEMENT_TRAINING.md),
and [refinement milestone notes](VOWEL_REFINEMENT.md). Release builds omit collection.

This version combines the updated `master` UI (`d89f9c4`) with the camera/vowel
milestone (`25b62de`). The camera sits above a resizable text editor. Live vowel
results have a dedicated read-only area and never overwrite typed or dictated text.
See [integration notes](INTEGRATION_NOTES.md) for the merge and validation details.

## Run in Android Studio

1. Open the repository root and allow Gradle to sync.
2. Install Android SDK platform 37 if Android Studio requests it. Use the project's
   Gradle wrapper and Java 25 Gradle toolchain; application source targets Java 11.
3. Run the `app` configuration on an Android 7.0 / API 24 or newer device. For an
   emulator, configure front and rear cameras in its device settings.
4. Sign in or register using the existing local account flow, then tap **Start Camera**
   on Home and allow camera access.
5. Hold one hand clearly in view and form A, E, I, O, or U. Hold the shape steady
   for half a second. The output panel displays the recognized vowel, or an
   unknown-sign message when recognition is uncertain. Either hand is supported.
6. Both hands still receive landmark overlays; recognition asks for one visible
   hand at a time. Tracking and recognition start automatically with the camera.
7. Type in the editor or tap **Microphone** to open the speech recognizer on your
   phone. A successful voice result replaces the editor text; you can edit it.
   Cancelling or receiving no speech leaves existing text intact.

Login and registration currently use device-local preferences. Backend authentication
and account synchronization are separate future work.

## Camera controls

- **Start Camera / Stop Camera** opens or releases the live preview.
- **Switch Camera** changes between front and rear when both are available. Front is
  selected initially; a device with only a rear camera uses that camera instead.
- Leaving Home, backgrounding the app, or logging out releases the camera. Returning
  requires another tap on Start Camera. Rotation retains an active session and lens.
- If camera permission is denied, retry from Start Camera. After requests are blocked,
  use **Open Settings**, allow camera access, return, and tap Start Camera.
- Drag the divider to resize the camera-above-text panels; double-tap to reset.
  Drag fully upward to close the camera panel and stop tracking. **Start Camera**
  expands a closed panel. Short windows scroll so text and controls remain reachable.
- The preview fits the full shared camera frame at every panel size. Starting in a
  small panel and enlarging it keeps the same field of view without restarting the
  camera. Dark margins are normal when the panel and camera have different shapes.
  See [preview resize fix](PREVIEW_RESIZE_FIX.md) for implementation and test details.

This milestone does not record, save, or upload video. Recognition displays the
current letter only; it does not assemble text or translate words. Language dropdowns are retained from
the dashboard and do not change the supported ASL vowel vocabulary.

## Typing and voice input

The editor is independent of camera/recognition status and is never changed by
tracking updates, permission errors, camera switching, or stopping the camera.
Text, selection, voice feedback, and panel size survive rotation. Leaving Home
and returning preserves editor text while requiring a new camera start.

**Microphone** stops the camera before launching the external speech recognizer.
The device speech service supplies recognition and any microphone permission flow;
availability and offline support depend on that service. No Python or new speech
service is introduced. The camera model still works offline independently of voice.
Returning from voice input keeps the camera stopped until **Start Camera** is tapped.
Voice feedback is separate from the hand-count caption. Typing after dictation
clears the old voice feedback without altering live recognition.
While the keyboard is open, bottom navigation is hidden to leave room for editing
and camera controls. Dismiss the keyboard to restore navigation. Landscape typing
keeps the app visible instead of switching to a fullscreen extracted editor.

## Vowel recognition

The bundled random forest runs in Java on the existing camera worker after hand
landmark extraction. No second camera stream, Python installation, runtime model
download, or network access is needed. Training dependencies are confined to the
separate desktop training tools and are not packaged in the APK. The original
Java/Tribuo pipeline remains available; the new Python workflow exports a portable
binary for Java inference. Android Studio builds do not invoke Python training.

Keep the whole hand visible in good lighting, with the sign's natural orientation.
Form a deliberate isolated vowel rather than rapidly fingerspelling a word. E and
O have similar shapes; consult a reliable ASL fingerspelling reference when testing.
The app accepts a letter only after a consistent prediction lasts 500 ms. Unknown
shapes and ambiguous predictions are rejected, although the model cannot guarantee
rejection of every unsupported sign.

Removing the hand, showing two hands, stopping or switching cameras, leaving Home,
backgrounding, logout, and session recreation clear the current result. Rotation
retains camera intent and restarts the half-second recognition check. A classifier
failure leaves tracking/preview usable when possible; stop and restart to retry.

The first model uses public SignAlphaSet v2 images. Read the
[model evaluation](third_party/vowel-model/evaluation.md) for measured results and
limitations. Its grouped dataset split is **not signer-independent**: the source
does not supply signer/session IDs. Real-phone testing with additional people and
backgrounds remains necessary. Training and inference are Java; see
[Java training instructions](TRAINING.md) and [attribution](third_party/vowel-model/README.md).

Known limitation: whole-image held-out O recall is **73.47%**, below the 80% target,
because some O poses do not produce hand landmarks. A/E/I/U meet the held-out
precision/recall targets. Recognition of unsupported shapes is imperfect. The
evaluation includes missed detections and is separate from real-phone testing.

## Hand tracking

MediaPipe Tasks Vision 1.0.0 and its bundled Hand Landmarker model run on the CPU
without a runtime download or network connection. Each detected hand has 21
landmarks. CameraX analysis uses a single worker and keeps only the latest frame
when inference cannot keep up; it does not queue an increasing backlog.

The detector processes upright, unmirrored images. CameraX maps its output to the
preview, including crop, rotation, and front-camera display mirroring. Stopping,
switching cameras, or leaving Home immediately clears landmarks and invalidates
pending results. If tracking fails, preview stays usable; stop and restart to retry.

For a live webcam test, edit the emulator's advanced camera settings to use Webcam0
for the front camera. Emulated and VirtualScene cameras produce artificial images,
so a **No hands detected** caption is expected unless a hand is actually present.
You can also run on a physical Android phone using USB debugging.

Model provenance, license, and SHA-256 checksums are in
[third-party notices](third_party/mediapipe/README.md). Do not omit the bundled
`app/src/main/assets/hand_landmarker.task` when copying the project.

## Checks

From PowerShell in the repository root:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :recognition-core:test
.\gradlew.bat :app:installDebug :app:installDebugAndroidTest
adb shell am instrument -w -e class 'com.group_nine.asl_translator.home.HandModelTest,com.group_nine.asl_translator.home.HandProjectionTest,com.group_nine.asl_translator.home.HandTrackerPipelineTest,com.group_nine.asl_translator.home.HandTrackingUiTest,com.group_nine.asl_translator.home.CameraPreviewTest,com.group_nine.asl_translator.ExampleInstrumentedTest' com.group_nine.asl_translator.test/androidx.test.runner.AndroidJUnitRunner
```

The preview tests require an emulator with both cameras. They grant CAMERA for the
test app and check real CameraX stream state, switching, navigation, backgrounding,
logout, recreation, rotation, pending-start cancellation, and visible controls in
portrait and dark mode at 130% font size. Font and night-mode changes are restored.

`HandModelTest` exercises the actual bundled detector with MediaPipe's one-hand,
two-hand, and no-hand images and successive video frames. `HandProjectionTest`
covers rotations, cropping, mirrored/nonmirrored output, and preview sizes.
`PreviewResizeTest` checks the full frame and corner-landmark alignment using a real
CameraX stream, including small/large startup, repeated resizing, restart, rear camera,
and landscape. Its landmarks are synthetic fixtures, not a live-hand evaluation.
`HandTrackerPipelineTest` checks monotonic timestamps, worker-thread ownership,
late-result rejection, and frame/engine cleanup. The Espresso tests in
`HandTrackingUiTest` use a package-private detector seam for predictable statuses
and failures; their screenshots are UI test fixtures, not live detection evidence.

`HandLiveCameraTest` is optional and skips without the `liveHands` runner argument.
With a webcam or phone camera configured and a participant ready, run:

```powershell
adb shell am instrument -w -e liveHands 1 -e class com.group_nine.asl_translator.home.HandLiveCameraTest com.group_nine.asl_translator.test/androidx.test.runner.AndroidJUnitRunner
```

Repeat with `-e liveHands 2` for both hands. Keep hands visible for the test's short
stable-count check. This participant-assisted test was not run during validation.

For each permission test, force-stop the test app and reset its Android camera
permission **before** starting instrumentation. These commands affect only this
app on the selected test device:

```powershell
adb shell am force-stop com.group_nine.asl_translator
adb shell pm revoke com.group_nine.asl_translator android.permission.CAMERA
adb shell pm clear-permission-flags com.group_nine.asl_translator android.permission.CAMERA user-set user-fixed
adb shell am instrument -w -e class 'com.group_nine.asl_translator.home.CameraPermissionTest#denialAllowsRetryAndGrantStartsRealPreview' com.group_nine.asl_translator.test/androidx.test.runner.AndroidJUnitRunner
```

Repeat the three reset commands, then run the same instrumentation command with
`CameraPermissionTest#repeatedDenialOffersSettings`. These tests reset only the
camera feature's permission history, preserving local account preferences. They
skip if CAMERA is already granted, so run them separately before the preview tests.

Device screenshots are written to the test app's external files directory under
`camera-qa`. Pull them before uninstalling the app:

```powershell
adb pull /sdcard/Android/data/com.group_nine.asl_translator/files/camera-qa
```

`VowelBundledModelTest` exercises the shipped forest using held-out public dataset
landmarks. `VowelRecognitionUiTest` uses deterministic classifier injection for
status, failure, and lifecycle checks. Run these alongside the existing tests:

```powershell
adb shell am instrument -w -e class 'com.group_nine.asl_translator.home.VowelBundledModelTest,com.group_nine.asl_translator.home.VowelRecognitionUiTest' com.group_nine.asl_translator.test/androidx.test.runner.AndroidJUnitRunner
```

`HomeInputIntegrationTest` adds typing, voice-intent result/cancellation, editor
ownership, text/selection restoration, camera-panel collapse/reopen, and portrait/
landscape keyboard checks. It stubs the external speech activity through
Espresso-Intents; it does not claim live microphone transcription accuracy.

```powershell
adb shell settings put secure show_ime_with_hard_keyboard 1
adb shell am instrument -w -e class com.group_nine.asl_translator.home.HomeInputIntegrationTest com.group_nine.asl_translator.test/androidx.test.runner.AndroidJUnitRunner
```

Use these keyboard settings only on a test emulator and restore its prior value
afterward. On a phone, test the actual speech service, cancellation, and returning
to camera use manually.

`DatasetLandmarkExportTest` skips unless explicitly requested with
`-e extractDataset true` or `-e calibrateVideo true`; it is not part of normal app startup or training-free
builds. See [Iteration 4](ITERATION_4.md) for this milestone's validation notes and
[Iteration 3](ITERATION_3.md) for the previous tracking milestone.
