# MediaPipe third-party notices and asset provenance

## Library and model

- Library: `com.google.mediapipe:tasks-vision:1.0.0`, including its transitive dependencies.
- Upstream project: https://github.com/google-ai-edge/mediapipe
- Copyright 2022 The MediaPipe Authors. The upstream Apache License 2.0, including
  its appended notice, is reproduced in [LICENSE](LICENSE).
- Bundled model: `app/src/main/assets/hand_landmarker.task`, unchanged Hand Landmarker
  float16 version 1, containing a palm detector and hand landmark detector.
- Download: https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task
- SHA-256: `fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1`
- Official model card (Apache License 2.0 and intended-use/performance details):
  https://storage.googleapis.com/mediapipe-assets/Model%20Card%20Hand%20Tracking%20(Lite_Full)%20with%20Fairness%20Oct%202021.pdf

The model is loaded from APK assets; no runtime asset download is performed.
This application uses the model for hand landmark localization, not identification
or ASL classification. The model is redistributed unchanged.

## Instrumentation-test images

These unchanged files are included only in the test APK under
`app/src/androidTest/assets/hand_tracking`. They are the fixtures used by upstream
MediaPipe HandLandmarkerTest; no additional authorship is asserted.

| File | Original URL | SHA-256 |
|---|---|---|
| thumb_up.jpg | https://storage.googleapis.com/mediapipe-assets/thumb_up.jpg | `5d673c081ab13b8a1812269ff57047066f9c33c07db5f4178089e8cb3fdc0291` |
| right_hands.jpg | https://storage.googleapis.com/mediapipe-assets/right_hands.jpg | `4b5134daa4cb60465535239535f9f74c2842aba3aa5fd30bf04ef5678f93d87f` |
| cats_and_dogs.jpg | https://storage.googleapis.com/mediapipe-assets/cats_and_dogs.jpg | `a2eaa7ad3a1aae4e623dd362a5f737e8a88d122597ecd1a02b3e1444db56df9c` |

Upstream test source:
https://github.com/google-ai-edge/mediapipe/blob/master/mediapipe/tasks/javatests/com/google/mediapipe/tasks/vision/handlandmarker/HandLandmarkerTest.java

Preserve these notices and the original license when redistributing the project.
