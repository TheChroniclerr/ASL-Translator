# Camera, recognition, typing, and voice integration

## Source revisions

- Updated master: `d89f9c4b1396f065898396e63de5daecacf38452` (GUI merge).
- Camera/vowel recognition: `25b62de` from the local `vowel-recognition` branch.
- Local integration branch: `camera-input-integration`. No remote branch is changed.

Master adds voice-to-text, an editable Home text area, a camera-above-text layout,
a fully collapsible camera panel, and drawer resources. Its Start Camera action
was a placeholder. This integration retains the master UI and input features
while connecting the working CameraX/MediaPipe camera and recognition pipeline.
The existing drawer menu, profile icon, and related strings are preserved.

## Behavior

- The camera panel stays above the editor and can be resized or closed completely.
  Closing it releases the camera, clears landmarks/results, and cancels pending
  session results. Start Camera expands a closed panel before binding use cases.
  The subsequent [preview resize fix](PREVIEW_RESIZE_FIX.md) keeps the full frame
  visible while resizing, including when the camera starts in a small panel.
- Live A/E/I/O/U recognition has a dedicated read-only result above the editor.
  Frame updates, camera errors, switches, and stops never replace editor text.
- The editor supports typing and the existing system speech-recognition activity.
  Voice success replaces editor text and places the cursor at the end. Cancellation
  or empty results preserve existing text. Feedback is separate from tracking status.
- Microphone releases the camera before launching speech input. Returning leaves
  the camera stopped until the user starts it again. Duplicate speech launches are
  prevented while a result is pending. Unsupported speech services show feedback
  and leave typing available.
- Text, selection, voice feedback, and split ratio are saved across recreation.
  View references and text watchers are released when the Home view is destroyed.
- The editor header reserves space during resizing; short windows scroll. Keyboard
  insets keep controls above the keyboard, including in landscape. Fullscreen IME
  extraction is disabled, and bottom navigation hides only while the keyboard is
  visible, restoring when it closes.
- The app continues to use Java, XML, the existing Android Studio/Gradle toolchain,
  and one camera-analysis worker. No new audio permission or speech backend is added.

## Recognition unchanged

The hand model, forest, feature extraction, stabilization, and thresholds are unchanged.
Vowel forest SHA-256:

```text
58af9deb90dc22bb821b0f94290e661b429c100771d9285910b0f4ed35430721
```

The previous vowel APK was reported by the user to work approximately 70% of the
time on a phone. This informal report is not a controlled accuracy measurement.
This integration does not retrain or claim an accuracy improvement. The existing
O detection limitation and evaluation caveats remain in the model documentation.

## Validation

- Debug build, unit tests, and lint pass; the 28 unit tests remain applicable.
- 43 Android emulator tests pass: 34 existing camera/model/tracking/permission
  checks and 9 Home input integration checks.
- New tests cover typing during recognition updates, voice result intent/options,
  voice cancellation/empty results, camera release before dictation, text and
  selection restoration, navigation/backgrounding, camera-panel collapse/reopen,
  and portrait/landscape keyboards.
- Voice tests stub the speech activity with Espresso-Intents. They verify result
  handling and state ownership, not live audio recognition or a particular provider.
- Existing tests exercise actual bundled detector/model fixtures, coordinate
  transforms, switching, rotation, backgrounding, logout, denied/blocked camera
  permissions, late results, and resource cleanup.
- Screenshots cover the combined camera/editor, typed text with the keyboard,
  landscape, and dark mode with larger fonts. Scripted hand results are UI fixtures.

The updated APK still needs a real-phone check of live camera alignment during
resizing, actual voice-service availability/transcription, and switching back to
camera use after dictation. Recognition quality improvements remain separate work.

## Delivery

The delivery includes a debug APK and a clean Android Studio source folder/ZIP.
The clean source excludes Git history, IDE state, local SDK paths, caches, and
generated build outputs, while retaining model assets, tests, and attribution.
GitHub publication is left to the repository collaborators.
