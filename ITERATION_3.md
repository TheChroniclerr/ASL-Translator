# Iteration 3: Offline hand tracking

Based on the published `camera-preview` commit `1719f66`. Java application code,
XML layouts, SDK levels, local account flow, and existing camera controls are retained.

## Behavior

Starting the camera initializes the bundled MediaPipe Hand Landmarker on a worker.
Up to two hands are drawn with 21 landmarks and their connections. The caption
shows initialization, no hands, one hand, two hands, or tracking unavailable.
Repeated frames with the same status do not repeat accessibility announcements.
Tracking failure leaves preview usable and can be retried by stopping and restarting.

The camera and analyzer share a CameraX viewport. Analysis requests 640 x 480 with
supported-resolution fallback, RGBA frames, and keep-only-latest backpressure.
Video-mode CPU inference, model initialization, and model closing use a single
ExecutorService for each session. Input timestamps are strictly increasing.
MediaPipe owns its MPImage bitmap after wrapping; inputs are not reused after
release. Frame and temporary-image cleanup occurs even when detection fails.

Detector input is rotated upright without horizontal mirroring. Landmarks are
mapped back to buffer coordinates and then through CameraX's preview transform,
which accounts for crop and display mirroring. The current transform is used when
drawing so resizing does not retain an outdated screen-space projection.

Stopping, switching lenses, navigation, backgrounding, and logout clear overlays
and invalidate pending results. Rotation retains session intent while replacing
camera/tracker resources. Result delivery retains only the latest pending frame.

## Internal components

- HandTracker: worker-owned detector lifecycle, frame conversion, inference,
  error handling, and bounded result delivery. A package-private engine factory
  permits deterministic instrumentation tests without a user-facing test mode.
- HandTrackingResult: immutable hand/landmark lists, handedness information,
  timestamps, frame geometry, and source transform.
- HandLandmarkOverlay and LandmarkCoordinates: camera-to-view projection and
  clipped landmark rendering. CameraX transform APIs have explicit opt-in annotations.

## Verification

- Debug build and lint passed; lint reports zero errors and 34 advisory warnings.
- Five new coordinate/data unit tests passed, along with the existing template unit test.
- 24 device tests passed in one emulator run: four real-model tests, one projection
  test covering 24 geometry combinations, three worker/resource tests, six Espresso
  UI tests, nine camera regressions, and the existing app-context test.
- Both permission tests passed separately after resetting camera permission:
  denial/retry/grant and repeated denial with Open Settings recovery.
- Portrait, rear-camera recreation, landscape scrolling, and dark mode at 130%
  font size were inspected using screenshots. Landmark UI screenshots use scripted
  test output; real-model correctness is tested separately against official images.
- Live webcam checks were offered but skipped. No live hand-motion, real-world
  overlay alignment, physical-phone, TalkBack, or 60 FPS performance claim is made.

## Remaining work

The model localizes hands; it does not recognize ASL words or translate phrases.
Recognition training, translation, audio, history/database features, and account
improvements remain separate milestones. Model sources, hashes, and upstream
license information are in `third_party/mediapipe`.
