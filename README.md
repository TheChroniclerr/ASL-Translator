# ASL Translator

Native Android app with Java application code and XML layouts. Open this repository's
root folder in Android Studio. The current milestone adds live camera preview to
the existing Home screen; sign recognition and translation are future work.

## Run in Android Studio

1. Open the repository root and allow Gradle to sync.
2. Install Android SDK platform 37 if Android Studio requests it. Use the project's
   Gradle wrapper and Java 25 Gradle toolchain; application source targets Java 11.
3. Run the `app` configuration on an Android 7.0 / API 24 or newer device. For an
   emulator, configure front and rear cameras in its device settings.
4. Sign in or register using the existing local account flow, then tap **Start Camera**
   on Home and allow camera access.

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
- Drag the divider to resize the panels; double-tap to reset. Short windows scroll
  so the camera controls remain reachable.

This milestone does not record, save, or upload video. The translation areas remain
placeholders, and the microphone retains its existing informational behavior.

## Checks

From PowerShell in the repository root:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:installDebug :app:installDebugAndroidTest
adb shell am instrument -w -e class 'com.group_nine.asl_translator.home.CameraPreviewTest,com.group_nine.asl_translator.ExampleInstrumentedTest' com.group_nine.asl_translator.test/androidx.test.runner.AndroidJUnitRunner
```

The preview tests require an emulator with both cameras. They grant CAMERA for the
test app and check real CameraX stream state, switching, navigation, backgrounding,
logout, recreation, rotation, pending-start cancellation, and visible controls in
portrait and dark mode at 130% font size. Font and night-mode changes are restored.

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

See [Iteration 2](ITERATION_2.md) for implementation and validation notes.
