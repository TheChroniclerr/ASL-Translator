package com.group_nine.asl_translator.home;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.camera.core.CameraSelector;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.group_nine.asl_translator.R;

/** Home UI and contextual camera permission handling. */
public class HomeFragment extends Fragment {
    private static final String SPLIT = "home.split";
    private static final String SOURCE = "home.source";
    private static final String TARGET = "home.target";
    private static final String LENS = "home.lens";
    private static final String DENIED = "camera_denied";

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

        camera = new CameraPreviewController(requireContext(), getViewLifecycleOwner(),
                view.findViewById(R.id.camera_surface), session.lensFacing, this::renderCamera);
        view.findViewById(R.id.start_camera).setOnClickListener(button -> onCameraButton());
        view.findViewById(R.id.switch_camera).setOnClickListener(button -> camera.switchCamera());
        view.findViewById(R.id.microphone).setOnClickListener(button ->
                Snackbar.make(view, R.string.speech_soon, Snackbar.LENGTH_SHORT).show());
        panels = new ResizablePanelController(view, split);
        renderCamera(CameraPreviewController.State.IDLE, R.string.camera_placeholder,
                session.lensFacing, false);
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
        boolean starting = state == CameraPreviewController.State.STARTING;
        if (state == CameraPreviewController.State.ERROR
                || state == CameraPreviewController.State.UNAVAILABLE) {
            session.wanted = false;
        }
        view.findViewById(R.id.camera_surface).setVisibility(active || starting ? View.VISIBLE : View.INVISIBLE);
        view.findViewById(R.id.camera_placeholder).setVisibility(active ? View.GONE : View.VISIBLE);
        ((TextView) view.findViewById(R.id.camera_status)).setText(message);
        ((TextView) view.findViewById(R.id.live_caption)).setText(active
                ? message : R.string.camera_recognition_later);
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
        super.onSaveInstanceState(out);
    }

    @Override
    public void onDestroyView() {
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
