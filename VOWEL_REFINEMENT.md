# E/O refinement: collection and training foundation

Local branch `vowel-refinement` starts at the preview resize fix `60e51e7`.
Remote branches are untouched. This is delivery one: collect labeled phone samples
and exercise the desktop training/export path. A refined recognition APK requires
the participant's recordings and successful final evaluation.

The original bundled forest remains the default, with SHA-256:

```text
58af9deb90dc22bb821b0f94290e661b429c100771d9285910b0f4ed35430721
```

The debug-only collection activity uses the existing CameraX/MediaPipe pipeline,
saves landmarks/diagnostics in no-backup storage, and exports manually through the
Android document picker. Its 80-hold protocol covers both hands, more E/O examples,
and confusable Other shapes. Interrupted holds stay incomplete; actual tracker
timing gaps are retained rather than inventing no-hand detections. Release builds
omit the activity, menu entry, storage implementation, and collection resources.
Every analyzed result is recorded before UI coalescing, using the same worker and
inference timestamps as live recognition; no additional detector/analyzer is added.

The shared Java library supports legacy 63-feature hard-vote forests and versioned
63/93-feature averaged-leaf forests exported by the Python trainer. Java remains
the only feature implementation. The existing camera framing, hand overlays,
one-hand recognition rule, 500 ms stabilization, editor, and speech input remain.

Initial software checks use synthetic fixture recordings repeating public
landmarks. All eight training configurations, validation selection, binary export,
held-out evaluation code, mirrored-score checks, and temporal Java/Python replay
are exercised. These are software checks, not evidence that E/O accuracy improved.
The synthetic candidate fails acceptance and is not bundled or promoted.

## Validation for delivery one

- Debug and release APK builds pass; lint reports 0 errors and 65 advisory warnings.
- 41 Java unit tests and 12 Python tests pass.
- 56 unique Android emulator checks pass across the full regression run and final
  collection checks: the existing 45 scenarios plus 10 collection scenarios and
  the overflow/camera-handoff scenario. The final APK's 11 collection/menu tests
  pass, including successful ZIP export, cancellation, deletion, raw diagnostic
  fields, invalid coordinates, recreation, backgrounding, and tracker cleanup.
- Python/Java agree within `1e-6` on all six scores for 756 original/mirrored
  synthetic examples, with matching displayed letters on 720 sequence frames.
  An additional 240 synthetic vectors exercise branches in the new geometry
  feature range. All eight training configurations run in the end-to-end check.
- APK inspection confirms collection code is absent from release and no Python
  source/package is present in either variant. The baseline model checksum is
  unchanged. Captured images and training data are absent from the delivery.
- Emulator screenshots were inspected. Camera imagery and hand results in
  collection UI tests are artificial fixtures. Real phone collection and any
  accuracy improvement remain unverified until participant recordings are supplied.

For the next step, follow [capture instructions](CAPTURE_GUIDE.md) and export the
training/validation sessions. See [desktop workflow](REFINEMENT_TRAINING.md) for
frozen-candidate evaluation, checksum verification, promotion gates, and limits.

The delivery source excludes Git history, machine SDK paths, generated outputs,
Python environments, personal recordings, and training artifacts. It preserves
required model/dataset attribution and can be opened directly in Android Studio.
