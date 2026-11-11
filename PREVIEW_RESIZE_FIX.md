# Full-frame camera preview during resizing

## Behavior

The preview shows the entire shared camera frame whether the camera starts in a
small or large panel. Dragging the divider scales that frame proportionally inside
the panel. Dark margins appear where its aspect ratio differs from the camera.
Resizing an open panel does not restart the camera, tracker, or recognition worker.
Closing the panel completely still stops the camera, as before.

## Cause and change

The previous preview used `FILL_CENTER`, with a shared CameraX viewport derived
from the panel dimensions at startup. This cropped the image to fill the original
box. Enlarging a panel that started small magnified the retained crop.

The preview now uses `FIT_CENTER`, and the Preview/Analysis use-case group uses
`ViewPort.FIT` to keep the maximum common sensor region. The viewport no longer
captures the panel's startup aspect ratio. Existing CameraX coordinate transforms
continue to handle rotation, display mirroring, and landmark positioning.

Camera resolution selection, analysis processing, model assets, recognition
thresholds, typed text, and speech input behavior are unchanged.

CameraX references: [PreviewView scale types](https://developer.android.com/reference/androidx/camera/view/PreviewView.ScaleType)
and [ViewPort FIT](https://developer.android.com/reference/androidx/camera/core/ViewPort).

## Validation

- Debug APK build, 28 Java unit tests, and lint pass (0 errors; 49 advisory warnings).
- 45 unique Android emulator tests pass: 43 camera/model/tracking/recognition/input
  checks and two permission scenarios run with permissions reset between cases.
- Both resize tests also pass after installing the final APK with updated Help.
- Emulator screenshots were inspected at small and enlarged portrait panel sizes
  and in landscape. They show artificial camera imagery and synthetic landmarks.

`PreviewResizeTest` fails against the previous cropped implementation and passes
with the fix. It checks actual CameraX output geometry, the full frame's four
corner landmarks, small and large startup, repeated small/large resizing, restart,
front/rear switching, and landscape. Resizing assertions also verify that the
tracker stays in the same session. Hand landmarks in this test are synthetic.

`HandProjectionTest` covers fitted and filled mappings across four rotations,
front/rear display mirroring, cropping, and three preview shapes (48 combinations).

A physical phone check is still needed: start the camera with a short panel, note
objects at the frame edges, then enlarge and shrink it several times. The same
objects should stay visible; landmarks should remain on the hand. Repeat with
both cameras and in landscape. Emulator geometry tests do not verify a particular
phone camera or live-hand accuracy.

## Delivery

Local branch: `preview-fit`, based on the completed input integration `a66da12`.
Remote branches are untouched. The APK and clean Android Studio source retain
the existing bundled models, tests, and required third-party attribution.
