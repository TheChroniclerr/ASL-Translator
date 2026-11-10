# Iteration 1: UI foundation

Native Java application logic and XML layouts. The existing namespace, SDK levels,
Gradle conventions, and dependencies are preserved.

## Structure

- `MainActivity`: shared toolbar, drawer, system insets, bottom navigation, and dialogs.
  Destination Fragments are added once and shown/hidden; only the selected destination
  is resumed. Back closes the drawer, returns to Home, then uses normal Android behavior.
- `home/HomeFragment`: language selections and local camera placeholder state.
- `home/ResizablePanelController`: touch handling, measured size limits, split restoration,
  double-tap reset, keyboard Up/Down resizing, and accessibility actions.
- `history/HistoryFragment`, `dictionary/DictionaryFragment`, and
  `settings/SettingsFragment`: minimal destination screens using separate XML layouts.
- `res/menu`: drawer, overflow, and bottom navigation menus.
- `res/values` and `res/values-night`: shared dimensions, strings, and light/dark colors.

## Resizing and saved state

The initial split is 45% translation / 55% camera, excluding the divider and fixed
language row. Both translation text areas share the panel height equally and can
scroll individually when needed. Minimum sizes account for text line heights,
padding, caption height, preview text, and measured camera controls.

The compact camera preview hides its decorative icon before essential text.
The caption and buttons remain reserved at the camera panel minimum. The preferred
split, language selections, camera placeholder state, and selected destination survive
Activity recreation. Switching destinations retains each Fragment.

On exceptionally short windows, including phone landscape, the Home content scrolls
to keep both panels and controls reachable. The toolbar and bottom navigation remain
fixed. No fullscreen or panel-collapse behavior is implemented.

## UI work for the next iteration

- Decide the roles and labels of the two translation areas and supported language pairs.
- Define the Add workflow and replace its informational dialog when agreed.
- Design the Recents list, empty/error states, and session-detail flow.
- Define Dictionary sign content, search/filtering, and entry details.
- Replace the Settings placeholder with agreed preferences and accessible controls.
- Finalize drawer destinations, author information, Help instructions, About copy,
  and application launcher branding.
- Refine compact landscape/tablet layouts and validate larger fonts, TalkBack,
  keyboard focus, and physical devices.
- When camera/speech work is approved, design permission, denied, loading, active,
  stopped, and error states; keep progressive captions visible during sessions.

Login, authentication, and session management remain deferred and are not implemented
or tested. Add them only after a separate approval.

## Future integration points

- Replace the camera preview container's placeholder content with a CameraX preview.
  Add permission handling only with that real feature.
- Publish progressive recognized words into `live_caption`; no predictions are generated.
- Populate `language_options` and the dropdown adapters when language support is decided.
- Connect real session clearing and LoginActivity at the documented Log Out handler.
- Update Help/About strings with the author, Activity version, and final instructions.

No camera, microphone, recognition, authentication, translation API, or Firebase
implementation/dependency has been added. All placeholders are intentional scope
for this milestone.

## Verification

Executed successfully on the existing Pixel 10 emulator:

```
:app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug
```

- 1 existing unit test passed.
- 5 device tests passed, including the existing context test.
- The local-only `HomeScreenTest` is excluded from this commit. It verifies
  divider drag limits, double-tap reset, stationary chrome,
  visible camera controls/captions, navigation and Activity recreation, system-safe
  bounds, dropdown selection without translation, exactly five drawer items, Help,
  About, Log Out, microphone feedback, and Add.
- Light/dark portrait layouts and landscape scroll fallback were visually checked.
- Lint completed without errors; advisory warnings remain (including nested layout
  weights, unused resources, and dependency/toolchain notices).

No TalkBack or physical-device certification is claimed.
