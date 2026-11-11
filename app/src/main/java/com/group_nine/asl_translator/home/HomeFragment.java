package com.group_nine.asl_translator.home;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.camera.core.CameraSelector;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.group_nine.asl_translator.R;
import com.group_nine.asl_translator.recognition.VowelRecognitionResult;
import java.util.ArrayList;
import java.util.Locale;

/** Home combines live signing, editable text, and the system voice-to-text activity. */
public class HomeFragment extends Fragment {
    private static final String SPLIT = "home.split";
    private static final String SOURCE = "home.source";
    private static final String TARGET = "home.target";
    private static final String LENS = "home.lens";
    private static final String DENIED = "camera_denied";
    private static final String TRANSLATED_OUTPUT = "home.translated_output";
    private static final String SPEECH_STATUS = "home.speech_status";
    private static final String SELECTION_START = "home.selection_start";
    private static final String SELECTION_END = "home.selection_end";
    private static final String SPEECH_PENDING = "home.speech_pending";

    /** Memory-only camera intent survives rotation, but cannot restart after process death. */
    public static class CameraSession extends ViewModel {
        boolean wanted;
        boolean permissionPending;
        boolean denied;
        int lensFacing = CameraSelector.LENS_FACING_FRONT;
    }

    private float split = ResizablePanelController.DEFAULT_SPLIT;
    private int sourceIndex;
    private int targetIndex = 1;
    private CameraSession session;
    private SharedPreferences permissionPreferences;
    private ResizablePanelController panels;
    private CameraPreviewController camera;
    private AlertDialog rationale;
    private boolean settingsRequired;
    private boolean cameraActive;
    private HandTracker.State trackingState = HandTracker.State.IDLE;
    private HandTrackingResult trackingResult;
    private String translatedOutputText = "";
    private String speechStatus = "";
    private int selectionStart = -1, selectionEnd = -1;
    private EditText translationOutputView;
    private TextWatcher editorWatcher;
    private boolean speechPending;
    private HandshapeAdapter handshapeAdapter;

    private final ActivityResultLauncher<Intent> speechLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                speechPending = false;
                if (getView() != null) requireView().findViewById(R.id.microphone).setEnabled(true);
                ArrayList<String> matches = result.getData() == null ? null
                        : result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (result.getResultCode() == Activity.RESULT_OK && matches != null && !matches.isEmpty()
                        && matches.get(0) != null && !matches.get(0).trim().isEmpty()) {
                    translatedOutputText = matches.get(0);
                    selectionStart = selectionEnd = translatedOutputText.length();
                    if (translationOutputView != null) {
                        translationOutputView.setText(translatedOutputText);
                        translationOutputView.setSelection(selectionStart);
                    }
                    setSpeechStatus(R.string.speech_captured);
                } else {
                    setSpeechStatus(result.getResultCode() == Activity.RESULT_CANCELED
                            ? R.string.speech_cancelled : R.string.speech_no_result);
                }
            });

    private final ActivityResultLauncher<String> cameraPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                session.permissionPending = false;
                permissionPreferences.edit().putBoolean(DENIED, !granted).apply();
                session.denied = !granted;
                if (!granted) {
                    session.wanted = false;
                    showPermissionDenied();
                } else if (session.wanted && isResumed() && !isHidden() && camera != null) {
                    camera.start();
                }
            });

    public HomeFragment() {
        super(R.layout.fragment_home);
    }

    @Override
    public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        session = new ViewModelProvider(this).get(CameraSession.class);
        permissionPreferences = requireContext().getSharedPreferences("ASL_Camera_Prefs", 0);
        if (state != null) {
            split = state.getFloat(SPLIT, split);
            sourceIndex = state.getInt(SOURCE, 0);
            targetIndex = state.getInt(TARGET, 1);
            session.lensFacing = state.getInt(LENS, session.lensFacing);
            translatedOutputText = state.getString(TRANSLATED_OUTPUT, "");
            speechStatus = state.getString(SPEECH_STATUS, "");
            selectionStart = state.getInt(SELECTION_START, -1);
            selectionEnd = state.getInt(SELECTION_END, -1);
            speechPending = state.getBoolean(SPEECH_PENDING, false);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        String[] languages = getResources().getStringArray(R.array.language_options);
        MaterialAutoCompleteTextView source = view.findViewById(R.id.source_language);
        MaterialAutoCompleteTextView target = view.findViewById(R.id.target_language);
        source.setSimpleItems(languages);
        target.setSimpleItems(languages);
        sourceIndex = Math.max(0, Math.min(sourceIndex, languages.length - 1));
        targetIndex = Math.max(0, Math.min(targetIndex, languages.length - 1));
        source.setText(languages[sourceIndex], false);
        target.setText(languages[targetIndex], false);
        source.setOnItemClickListener((parent, item, position, id) -> sourceIndex = position);
        target.setOnItemClickListener((parent, item, position, id) -> targetIndex = position);

        androidx.recyclerview.widget.RecyclerView strip = view.findViewById(R.id.recycler_handshapes);
        strip.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(requireContext(),
                androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false));
        handshapeAdapter = new HandshapeAdapter();
        strip.setAdapter(handshapeAdapter);
        translationOutputView = view.findViewById(R.id.translation_output);
        translationOutputView.setText(translatedOutputText);
        int length = translatedOutputText.length();
        translationOutputView.setSelection(selectionStart < 0 ? length : Math.min(selectionStart, length),
                selectionEnd < 0 ? length : Math.min(selectionEnd, length));
        editorWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                translatedOutputText = text.toString();
                updateHandshapes(translatedOutputText);
                if (!speechStatus.isEmpty()) {
                    speechStatus = "";
                    renderSpeechStatus();
                }
            }
            @Override public void afterTextChanged(Editable text) { }
        };
        translationOutputView.addTextChangedListener(editorWatcher);
        updateHandshapes(translatedOutputText);
        renderSpeechStatus();

        camera = new CameraPreviewController(requireContext(), getViewLifecycleOwner(),
                view.findViewById(R.id.camera_surface), session.lensFacing,
                this::renderCamera, this::renderTracking);
        view.findViewById(R.id.start_camera).setOnClickListener(button -> onCameraButton());
        view.findViewById(R.id.switch_camera).setOnClickListener(button -> camera.switchCamera());
        view.findViewById(R.id.microphone).setOnClickListener(button -> startVoiceToText());
        view.findViewById(R.id.microphone).setEnabled(!speechPending);
        panels = new ResizablePanelController(view, split, () -> {
            if (panels != null && !panels.isCameraVisible()) stopSession();
        });
        renderCamera(CameraPreviewController.State.IDLE, R.string.camera_placeholder,
                session.lensFacing, false);
    }

    private void updateHandshapes(String text) {
        if (handshapeAdapter == null) return;
        java.util.List<HandshapeItem> items = new ArrayList<>();
        for (char c : text.toCharArray()) {
            if (!Character.isLetter(c)) continue;
            char letter = Character.toUpperCase(c);
            int drawable;
            switch (letter) {
                case 'A': drawable = R.drawable.ic_asl_a; break;
                case 'E': drawable = R.drawable.ic_asl_e; break;
                case 'I': drawable = R.drawable.ic_asl_i; break;
                case 'O': drawable = R.drawable.ic_asl_o; break;
                case 'U': drawable = R.drawable.ic_asl_u; break;
                default: drawable = 0;
            }
            items.add(new HandshapeItem(letter, drawable));
        }
        handshapeAdapter.setItems(items);
        if (panels != null) panels.refresh();
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void onCameraButton() {
        if (session.wanted || session.permissionPending) {
            stopSession();
            return;
        }
        translationOutputView.clearFocus();
        WindowInsetsControllerCompat insets = ViewCompat.getWindowInsetsController(requireView());
        if (insets != null) insets.hide(WindowInsetsCompat.Type.ime());
        panels.showCamera();
        ((androidx.core.widget.NestedScrollView) requireView()).fullScroll(View.FOCUS_UP);
        if (hasCameraPermission()) {
            session.denied = false;
            settingsRequired = false;
            permissionPreferences.edit().putBoolean(DENIED, false).apply();
            session.wanted = true;
            camera.start();
        } else if (settingsRequired || (permissionPreferences.getBoolean(DENIED, false)
                && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA))) {
            session.wanted = false;
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + requireContext().getPackageName())));
            } catch (ActivityNotFoundException exception) {
                showPermissionDenied();
            }
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            rationale = new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.camera_permission_title)
                    .setMessage(R.string.camera_permission_rationale)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.camera_permission_continue, (dialog, which) -> requestCamera())
                    .show();
        } else {
            requestCamera();
        }
    }

    private void requestCamera() {
        if (!isResumed() || isHidden()) {
            return;
        }
        session.wanted = true;
        session.permissionPending = true;
        cameraPermission.launch(Manifest.permission.CAMERA);
    }

    private void showPermissionDenied() {
        if (getView() == null || isHidden()) {
            return;
        }
        settingsRequired = !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA);
        renderCamera(CameraPreviewController.State.IDLE, settingsRequired
                        ? R.string.camera_permission_blocked : R.string.camera_permission_denied,
                session.lensFacing, false);
    }

    private void renderCamera(CameraPreviewController.State state, int message,
                              int lensFacing, boolean canSwitch) {
        View view = getView();
        if (view == null) {
            return;
        }
        session.lensFacing = lensFacing;
        boolean active = state == CameraPreviewController.State.ACTIVE;
        cameraActive = active;
        boolean starting = state == CameraPreviewController.State.STARTING;
        if (state == CameraPreviewController.State.ERROR
                || state == CameraPreviewController.State.UNAVAILABLE) {
            session.wanted = false;
        }
        view.findViewById(R.id.camera_surface).setVisibility(active || starting ? View.VISIBLE : View.INVISIBLE);
        view.findViewById(R.id.camera_placeholder).setVisibility(active ? View.GONE : View.VISIBLE);
        ((TextView) view.findViewById(R.id.camera_status)).setText(message);
        renderTracking(trackingState, trackingResult);
        MaterialButton start = view.findViewById(R.id.start_camera);
        start.setText(active || starting ? R.string.stop_camera
                : settingsRequired ? R.string.camera_open_settings : R.string.start_camera);
        start.setEnabled(!session.permissionPending);
        View switchCamera = view.findViewById(R.id.switch_camera);
        switchCamera.setEnabled(active && canSwitch);
        switchCamera.setContentDescription(getString(lensFacing == CameraSelector.LENS_FACING_FRONT
                ? R.string.switch_to_rear_camera : R.string.switch_to_front_camera));
        if (panels != null) {
            panels.setCameraPreviewActive(active || starting);
        }
    }

    private void renderTracking(HandTracker.State state, @Nullable HandTrackingResult result) {
        trackingState = state;
        trackingResult = result;
        View view = getView();
        if (view == null) {
            return;
        }
        HandLandmarkOverlay overlay = view.findViewById(R.id.hand_landmarks);
        PreviewView surface = view.findViewById(R.id.camera_surface);
        if (cameraActive && result != null) {
            overlay.showResult(result, surface);
        } else {
            overlay.clear();
        }
        int message = R.string.camera_recognition_later;
        if (cameraActive) {
            switch (state) {
                case NO_HANDS: message = R.string.tracking_no_hands; break;
                case ONE_HAND: message = R.string.tracking_one_hand; break;
                case TWO_HANDS: message = R.string.tracking_two_hands; break;
                case UNAVAILABLE: message = R.string.tracking_unavailable; break;
                default: message = R.string.tracking_initializing; break;
            }
        }
        TextView caption = view.findViewById(R.id.live_caption);
        String text = getString(message);
        // Avoid repeated announcements and relayouts for every processed frame.
        if (!text.contentEquals(caption.getText())) {
            caption.setText(text);
            if (panels != null) {
                panels.setCameraPreviewActive(cameraActive);
            }
        }
        renderRecognition(view, state, result);
    }

    private void renderRecognition(View view, HandTracker.State state, @Nullable HandTrackingResult result) {
        int message = R.string.vowel_idle;
        String letter = null;
        if (cameraActive) {
            if (state == HandTracker.State.UNAVAILABLE) {
                message = R.string.vowel_unavailable;
            } else if (result == null) {
                message = R.string.vowel_initializing;
            } else {
                VowelRecognitionResult recognition = result.recognition;
                switch (recognition.state) {
                    case NO_HANDS: message = R.string.vowel_no_hands; break;
                    case TWO_HANDS: message = R.string.vowel_two_hands; break;
                    case HOLD_STEADY: message = R.string.vowel_hold_steady; break;
                    case RECOGNIZED: letter = recognition.letter; break;
                    case UNKNOWN: message = R.string.vowel_unknown; break;
                    case UNAVAILABLE: message = R.string.vowel_unavailable; break;
                    default: message = R.string.vowel_initializing; break;
                }
            }
        }
        TextView output = view.findViewById(R.id.vowel_result);
        String text = letter == null ? getString(message) : getString(R.string.vowel_recognized, letter);
        if (!text.contentEquals(output.getText())) {
            output.setText(text);
            if (panels != null) panels.refresh();
        }
    }

    private void startVoiceToText() {
        if (speechPending) return;
        // The external recognizer owns its microphone; release our camera before handing over.
        stopSession();
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.speech_prompt));
        setSpeechStatus(R.string.speech_listening);
        speechPending = true;
        requireView().findViewById(R.id.microphone).setEnabled(false);
        try { speechLauncher.launch(intent); }
        catch (ActivityNotFoundException | SecurityException exception) {
            speechPending = false;
            requireView().findViewById(R.id.microphone).setEnabled(true);
            setSpeechStatus(R.string.speech_unavailable);
            if (getView() != null) Snackbar.make(requireView(), R.string.speech_unavailable, Snackbar.LENGTH_LONG).show();
        }
    }

    private void setSpeechStatus(int message) {
        speechStatus = getString(message);
        renderSpeechStatus();
    }

    private void renderSpeechStatus() {
        if (getView() == null) return;
        TextView status = requireView().findViewById(R.id.speech_status);
        status.setText(speechStatus);
        status.setVisibility(speechStatus.isEmpty() ? View.GONE : View.VISIBLE);
        if (panels != null) panels.refresh();
    }

    /** Navigation to a separate input tool must clear intent before an activity transition. */
    public void stopForExternalInput() { stopSession(); }

    private void stopSession() {
        session.wanted = false;
        if (camera != null) {
            camera.stop();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isHidden()) {
            return;
        }
        if (hasCameraPermission()) {
            session.denied = false;
            settingsRequired = false;
            if (permissionPreferences.getBoolean(DENIED, false)) {
                permissionPreferences.edit().putBoolean(DENIED, false).apply();
            }
            if (session.wanted && !session.permissionPending) {
                // Fragment view lifecycle reaches RESUMED after this callback returns.
                requireView().post(() -> {
                    if (camera != null && isResumed() && !isHidden() && session.wanted
                            && hasCameraPermission()) {
                        camera.start();
                    }
                });
            } else {
                renderCamera(CameraPreviewController.State.IDLE, R.string.camera_placeholder,
                        session.lensFacing, false);
            }
        } else if (session.denied) {
            showPermissionDenied();
        } else if (!session.permissionPending) {
            session.wanted = false;
        }
    }

    @Override
    public void onPause() {
        if (camera != null) {
            camera.stop();
        }
        super.onPause();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) {
            stopSession();
            if (rationale != null) {
                rationale.dismiss();
            }
        }
    }

    @Override
    public void onStop() {
        // Unlike onPause, this distinguishes a real departure from Activity recreation.
        if (!requireActivity().isChangingConfigurations()) {
            stopSession();
        }
        super.onStop();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        if (panels != null) {
            split = panels.getSplit();
        }
        out.putFloat(SPLIT, split);
        out.putInt(SOURCE, sourceIndex);
        out.putInt(TARGET, targetIndex);
        out.putInt(LENS, session.lensFacing);
        if (translationOutputView != null) {
            translatedOutputText = translationOutputView.getText().toString();
            selectionStart = translationOutputView.getSelectionStart();
            selectionEnd = translationOutputView.getSelectionEnd();
        }
        out.putString(TRANSLATED_OUTPUT, translatedOutputText);
        out.putString(SPEECH_STATUS, speechStatus);
        out.putInt(SELECTION_START, selectionStart);
        out.putInt(SELECTION_END, selectionEnd);
        out.putBoolean(SPEECH_PENDING, speechPending);
        super.onSaveInstanceState(out);
    }

    @Override
    public void onDestroyView() {
        ((androidx.recyclerview.widget.RecyclerView) requireView().findViewById(R.id.recycler_handshapes)).setAdapter(null);
        handshapeAdapter = null;
        if (translationOutputView != null) {
            translatedOutputText = translationOutputView.getText().toString();
            selectionStart = translationOutputView.getSelectionStart();
            selectionEnd = translationOutputView.getSelectionEnd();
            translationOutputView.removeTextChangedListener(editorWatcher);
            translationOutputView = null;
            editorWatcher = null;
        }
        if (rationale != null) {
            rationale.dismiss();
            rationale = null;
        }
        if (camera != null) {
            camera.dispose();
            camera = null;
        }
        if (panels != null) {
            split = panels.getSplit();
            panels.dispose();
            panels = null;
        }
        super.onDestroyView();
    }
}
