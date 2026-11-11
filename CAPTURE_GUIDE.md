# Collect E/O refinement samples on your phone

This APK collects training examples. It still uses the original recognition model;
its E/O predictions have not improved yet. Do not reshape your signs to satisfy
that model. Use natural, deliberate ASL fingerspelling and the linked ASL reference.

## First: training and validation

1. Install the collection debug APK over the previous debug version. Sign in and
   open the top-right overflow menu, then **Collect vowel samples**. Home releases
   its camera before this screen opens. Returning to Home requires Start Camera.
2. Scroll down to the session controls. Read the collection notice and use an alias
   such as **P001**, not your name. Keep the same alias in all sessions.
3. Choose **Training session**, then **Start selected session**. The screen returns
   to the camera controls. Tap **Start camera** and allow camera access if needed.
4. Follow the letter and left/right-hand prompt. Keep your entire hand in view,
   with only that hand visible. **Capture next hold** gives one second to prepare,
   then records two seconds. It advances the guidance after saving. Relax and form
   the sign again between captures. You can switch camera while no hold is running.
5. Complete the guided 80 holds: per hand, A/I/U five times each and E/O ten times
   each; also C/S/F/H/V once per hand as Other. Predictions stay hidden during
   capture. Landmark overlays remain visible. Missing detections are saved too.
6. Scroll down and tap **Export samples ZIP**. Use the Android file picker to save
   a file such as `ASL-vowel-samples-training.zip` in Downloads. Export is manual;
   nothing is uploaded automatically.
7. Take a break, change background and lighting, then choose **Validation session**
   and **Start selected session**. Repeat the 80 holds, keeping the same alias.
   Export again as `ASL-vowel-samples-training-validation.zip`.

The second ZIP contains all locally saved sessions, so only the latest ZIP is needed
for training. Keep the originals as a private backup. Each session takes about four
minutes of countdown/recording time, plus time to reposition and relax your hand.
Portrait is convenient for keeping the preview and capture controls visible together.

## Later: final testing

Reserve **Final test session** until a candidate has been trained and frozen.
Use another recording session with different lighting/background, repeat all 80
holds, then export again. These recordings evaluate the candidate and must not be
used to tune that same candidate. Only your sessions are represented initially;
results cannot establish accuracy for new people.

## Interruptions and storage

- Cancelled holds, rotation, leaving the screen, and backgrounding mark the current
  hold incomplete. They do not advance the guided count. Restart the camera and
  repeat that hold. A process interruption also leaves incomplete metadata.
- Your latest session and saved counts are restored when reopening collection.
  Start selected session creates a new session; it does not erase older recordings.
- Samples contain timestamps, labels, landmarks, frame geometry, handedness,
  tracking status, model scores, and displayed predictions. No photos, video, or
  audio are saved. The alias identifies your own sessions without using your name.
- Samples live in the app's private no-backup directory. Export before uninstalling
  or clearing app data. Exported ZIP files remain wherever you saved them.
- Delete local samples requires confirmation and does not delete exported ZIP files.
- If saving/export fails, the screen shows the error. Keep enough phone storage
  available and retry the export; do not use a partially written ZIP.

For sharing with the training workflow, provide the exported ZIP itself. Screenshots
of predictions cannot replace the labeled landmark recordings.
