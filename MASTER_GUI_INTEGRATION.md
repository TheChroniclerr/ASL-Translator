# Latest master GUI and camera integration

Sources: published `vowel-refinement` commit `1033134` and master commit
`4d75eed3caab4fb964b07a4646c387748c172312`. Integration is prepared on the local
`gui-integration` branch. Publication is left to the repository collaborators.

## Combined behavior

The master GUI, drawer routes, profile, settings, alphabet guide, bookmarks,
account updates, and handshape artwork are retained. Profile/settings/bookmarks/
alphabet/account Java source files are unchanged from the master revision above.

Home retains the camera-above-editor arrangement and the horizontal handshape
strip. Typed or dictated text updates that strip. Bookmarking uses the editor
text, as in master. Current recognized vowels have their own read-only result;
they do not overwrite editor text or accumulate words automatically.
The original hand drawings use the current text color so their outlines remain
readable in dark mode on Home, bookmarks, and the alphabet guide.

The placeholder camera is connected to CameraX, both hand overlays, and offline
vowel recognition. Front/rear switching, full-frame resizing, camera permissions,
release on navigation/backgrounding/logout, rotation, and error retry behavior
remain. The strip is included in the panel's minimum height so it does not hide
the text editor. Short windows scroll; IME insets keep controls reachable.

Debug builds keep the opt-in sample collection menu. Opening it releases the Home
camera; its private no-backup recordings and export format are unchanged. An APK
update preserves app data, including previously collected samples. Export those
samples before uninstalling or clearing app data.

## Model and scope

No retraining occurred. Vowel forest SHA-256 remains:

```text
58af9deb90dc22bb821b0f94290e661b429c100771d9285910b0f4ed35430721
```

E/O refinement still requires the participant's exported training/validation ZIP.
The original recognition limits apply. Master currently uses an in-memory bookmark
repository; this integration preserves that behavior and does not add database
persistence or complete unrelated placeholder features. The alphabet artwork and
handshape strip currently provide sign pictures for A/E/I/O/U.

## Validation and delivery

Validation includes debug/release builds, lint, Java/Python tests, the existing
camera/permission/model/collection regression scenarios, and focused checks for
bookmarks during camera use, drawer navigation, Home state restoration, and the
settings dark-mode switch. Emulator camera imagery and scripted hand fixtures are
not evidence of live-phone recognition accuracy. A final phone smoke check of
the integrated GUI remains recommended before merging the pull request.

Results: 59 unique Android scenarios passed (14 focused GUI/input/resize checks,
43 remaining regression checks, and two separately reset permission scenarios),
with 41 Java unit tests and 12 Python tests passing. Lint reports zero errors;
existing/advisory warnings remain. Final GUI checks also cover light artwork
outlines in dark mode. No phone-recognition accuracy improvement is claimed.

The delivery includes a tested debug APK, clean Android Studio source ZIP/folder,
and a Git bundle preserving both published branch ancestries. The bundle allows
the existing vowel-refinement branch to advance without overwriting source files
or rewriting history. Its merge commit includes the fetched master revision.
