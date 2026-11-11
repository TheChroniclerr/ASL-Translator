# Iteration 2: Camera preview

Based on the `GUI` branch, retaining its Java/XML UI and local login flow.

## Implementation

- CameraX 1.6.2 supplies the Camera2 backend, lifecycle binding, and `PreviewView`.
  The compatible rendering mode supports the existing resizable, rounded preview.
- `CameraPreviewController` owns a single Preview use case, camera selection, a
  startup timeout, stream/error observation, and cleanup. It unbinds only its own
  use case. Request generations discard asynchronous work after stop or disposal.
- `HomeFragment` requests CAMERA in context through the Activity Result API and
  provides denial, rationale, blocked-permission settings, missing-camera, and
  recoverable-error states. No microphone or storage permission is requested.
- Front camera is preferred, with rear fallback. Switching is available only while
  streaming and when both cameras exist. Stop remains available during startup.
- A Fragment ViewModel retains session intent across rotation. Session intent is
  not saved to disk or saved-instance state, so process restoration cannot reopen
  the camera. Lens preference, panel split, and language selection are restored.
- Preview is released on pause. Navigation explicitly clears session intent because
  hidden destinations remain STARTED. Backgrounding and logout clear it as well;
  returning requires Start Camera. Configuration changes retain the intent.
- The split area is bounded to its scroll viewport. This prevents TextureView's
  natural measurement from expanding the content and pushing controls offscreen.
  Short windows expand only enough to keep measured content reachable by scrolling.
- Fixed the two existing Home XML attributes that appeared as child text instead
  of attributes, and added camera-control instructions to Help.

## Boundaries

No hand tracking, recognition model, recording, video upload, speech recognition,
translation service, or authentication backend is included. The reference Python
project recognizes alphabet letters; signed-word recognition will need its own
defined vocabulary, training data, and validation.

## Validation

Build, unit-test, lint, and device-test commands are in [README](README.md).
Device checks use the Pixel 8 API 37 emulator with simulated front and rear cameras.
The debug build, one unit test, ten preview/app-context device tests, and two
separately reset permission tests passed. Lint completed with zero errors and
33 advisory warnings. Portrait, landscape scroll fallback, and dark mode at 130%
font size were inspected using emulator screenshots.
Physical-device and TalkBack verification remain outstanding. Camera contention,
missing hardware, and system-disabled access have user-facing error handling but
have not been certified on physical hardware.

Historical UI-only notes in [Iteration 1](ITERATION_1.md) describe that earlier
milestone; the camera integration documented here supersedes its camera placeholders.
