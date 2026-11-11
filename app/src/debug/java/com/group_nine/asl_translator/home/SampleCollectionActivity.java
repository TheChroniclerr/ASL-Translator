package com.group_nine.asl_translator.home;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.group_nine.asl_translator.R;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Explicit debug-only collection. No image or audio resources leave the existing tracker. */
public final class SampleCollectionActivity extends AppCompatActivity {
    private static final String[] LABELS = {"A", "E", "I", "O", "U", "C (Other)", "S (Other)", "F (Other)", "H (Other)", "V (Other)"};
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private SampleStore store;
    private SampleStore.Session session;
    private SampleStore.Clip clip;
    private CameraPreviewController camera;
    private final Object recordingLock = new Object();
    private SampleStore.Clip workerClip;
    private long recordStart;
    private boolean active, busy, wanted, bothCameras;
    private int lens = CameraSelector.LENS_FACING_FRONT;
    private Spinner label, hand, split;
    private TextView prompt, status, progress;
    private final int[][] counts = new int[LABELS.length][2];
    private static final String[] SPLITS = {"train", "validation", "test"};

    private final ActivityResultLauncher<String> permission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
        if (granted && wanted && getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) camera.start();
        else { wanted = false; status.setText("Camera access is needed. Allow it in Android Settings, then tap Start camera."); }
    });
    private final ActivityResultLauncher<String> export = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/zip"), uri -> {
        if (uri == null) { busy = false; controls(); return; }
        busy = true; controls();
        io.execute(() -> {
            try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                if (output == null) throw new java.io.IOException("Unable to open export destination");
                store.export(output);
                ui(() -> { busy = false; status.setText("Samples exported. Keep the ZIP private until you choose to share it for training."); controls(); });
            } catch (Exception exception) { error("Export failed", exception); }
        });
    });

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); androidx.activity.EdgeToEdge.enable(this); setContentView(R.layout.activity_sample_collection);
        View root = findViewById(R.id.collection_scroll);
        ((NestedScrollView) root).setSmoothScrollingEnabled(false);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            androidx.core.graphics.Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(safe.left, safe.top, safe.right, Math.max(safe.bottom, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom));
            return WindowInsetsCompat.CONSUMED;
        });
        label = findViewById(R.id.collection_label); hand = findViewById(R.id.collection_hand); split = findViewById(R.id.collection_split);
        adapter(label, LABELS); adapter(hand, new String[]{"Left hand", "Right hand"});
        adapter(split, new String[]{"Training session", "Validation session", "Final test session"});
        prompt = findViewById(R.id.collection_prompt); status = findViewById(R.id.collection_status); progress = findViewById(R.id.collection_progress);
        try { store = new SampleStore(this); }
        catch (Exception exception) { status.setText("Sample storage unavailable. Return to Home to use the camera."); busy = true; }
        if (state != null) lens = state.getInt("lens", lens);
        String savedId = state == null ? null : state.getString("session");
        if (store != null) {
            busy = true;
            io.execute(() -> {
                try {
                    SampleStore.Session saved = savedId == null ? store.latestSession() : store.load(savedId);
                    ui(() -> { busy = false; session = saved; if (saved != null) {
                        ((EditText) findViewById(R.id.collection_alias)).setText(saved.participant);
                        split.setSelection(java.util.Arrays.asList(SPLITS).indexOf(saved.split)); reloadCounts();
                    } else controls(); });
                } catch (Exception exception) { error("Cannot restore session", exception); }
            });
        }
        PreviewView preview = findViewById(R.id.collection_preview);
        camera = new CameraPreviewController(this, this, preview, lens, (cameraState, message, facing, canSwitch) -> {
            active = cameraState == CameraPreviewController.State.ACTIVE; lens = facing; bothCameras = canSwitch;
            ((Button) findViewById(R.id.collection_camera)).setText(active || cameraState == CameraPreviewController.State.STARTING ? R.string.collection_stop : R.string.collection_start);
            if (clip == null) status.setText(message);
            if (cameraState == CameraPreviewController.State.ERROR || cameraState == CameraPreviewController.State.UNAVAILABLE) abort("camera failure");
            controls();
        }, (stateValue, result) -> {
            ((HandLandmarkOverlay) findViewById(R.id.collection_overlay)).showResult(result, preview);
            if (stateValue == HandTracker.State.UNAVAILABLE) synchronized (recordingLock) {
                long elapsed = SystemClock.elapsedRealtime() - recordStart;
                if (workerClip != null && elapsed >= 0 && elapsed < 2000) workerClip.frames.add(new SampleStore.Frame(elapsed, null, "UNAVAILABLE"));
            }
        });
        camera.setFrameObserver(result -> {
            synchronized (recordingLock) {
                long elapsed = SystemClock.elapsedRealtime() - recordStart;
                if (workerClip != null && elapsed >= 0 && elapsed < 2000) {
                    String stateValue = result.hands.isEmpty() ? "NO_HANDS" : result.hands.size() == 1 ? "ONE_HAND" : "TWO_HANDS";
                    workerClip.frames.add(new SampleStore.Frame(elapsed, result, stateValue));
                }
            }
        });
        findViewById(R.id.collection_camera).setOnClickListener(view -> {
            if (wanted) { abort("camera stopped"); wanted = false; camera.stop(); }
            else { wanted = true; if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) camera.start(); else permission.launch(Manifest.permission.CAMERA); }
        });
        findViewById(R.id.collection_switch).setOnClickListener(view -> camera.switchCamera());
        findViewById(R.id.collection_new).setOnClickListener(view -> createSession());
        findViewById(R.id.collection_capture).setOnClickListener(view -> capture());
        findViewById(R.id.collection_cancel).setOnClickListener(view -> abort("cancelled by participant"));
        findViewById(R.id.collection_export).setOnClickListener(view -> { busy = true; controls(); export.launch("ASL-vowel-samples.zip"); });
        findViewById(R.id.collection_delete).setOnClickListener(view -> new MaterialAlertDialogBuilder(this)
                .setMessage("Delete all local landmark samples? Exported ZIP files are kept at the location you selected.")
                .setNegativeButton(android.R.string.cancel, null).setPositiveButton("Delete", (dialog, which) -> deleteSamples()).show());
        findViewById(R.id.collection_reference).setOnClickListener(view -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.lifeprint.com/asl101/fingerspelling/abc.htm"))); }
            catch (android.content.ActivityNotFoundException exception) { status.setText("No browser available. Reference: lifeprint.com/asl101/fingerspelling/abc.htm"); }
        });
        findViewById(R.id.collection_close).setOnClickListener(view -> finish());
        android.widget.AdapterView.OnItemSelectedListener selection = new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { updatePrompt(); controls(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        };
        label.setOnItemSelectedListener(selection); hand.setOnItemSelectedListener(selection);
        updatePrompt(); controls();
    }

    private void createSession() {
        if (busy || clip != null) return;
        String alias = ((EditText) findViewById(R.id.collection_alias)).getText().toString().trim();
        findViewById(R.id.collection_scroll).requestFocus();
        androidx.core.view.WindowInsetsControllerCompat insets = ViewCompat.getWindowInsetsController(findViewById(R.id.collection_scroll));
        if (insets != null) insets.hide(WindowInsetsCompat.Type.ime());
        String role = SPLITS[split.getSelectedItemPosition()]; busy = true; controls();
        io.execute(() -> {
            try { SampleStore.Session created = store.create(alias, role); ui(() -> { session = created; reloadCounts(); }); }
            catch (Exception exception) { error("Unable to start session", exception); }
        });
    }

    private void reloadCounts() {
        SampleStore.Session selected = session; busy = true; controls();
        io.execute(() -> {
            try {
                int[][] loaded = new int[LABELS.length][2];
                for (int l = 0; l < LABELS.length; l++) for (int h = 0; h < 2; h++) loaded[l][h] = store.count(selected, LABELS[l].substring(0, 1), h == 0 ? "Left" : "Right");
                ui(() -> {
                    for (int l = 0; l < counts.length; l++) System.arraycopy(loaded[l], 0, counts[l], 0, 2);
                    busy = false; chooseNext(); updatePrompt(); controls();
                    ((NestedScrollView) findViewById(R.id.collection_scroll)).fullScroll(View.FOCUS_UP);
                });
            } catch (Exception exception) { error("Unable to read session", exception); }
        });
    }

    private static int target(int l) { return l == 1 || l == 3 ? 10 : l < 5 ? 5 : 1; }
    private void chooseNext() {
        for (int h = 0; h < 2; h++) for (int l = 0; l < LABELS.length; l++) if (counts[l][h] < target(l)) {
            label.setSelection(l); hand.setSelection(h); return;
        }
    }

    private void updatePrompt() {
        if (clip != null || label == null || prompt == null) return;
        int l = label.getSelectedItemPosition(), h = hand.getSelectedItemPosition(), total = 0;
        for (int[] count : counts) for (int value : count) total += value;
        progress.setText(session == null ? "Start a session before capturing. Use the same alias across all three sessions." : session.participant + " · " + session.split + " · " + total + "/80 holds saved");
        prompt.setText(total == 80 ? "Session complete. Export the samples ZIP. Use a different background and lighting for the next session."
                : "Form " + LABELS[l] + " with your " + (h == 0 ? "left" : "right") + " hand only. " + counts[l][h] + "/" + target(l) + " saved for this shape/hand. Relax between holds.");
    }

    private void capture() {
        if (!active || busy || session == null || clip != null) return;
        clip = new SampleStore.Clip(session, LABELS[label.getSelectedItemPosition()].substring(0, 1), hand.getSelectedItemPosition() == 0 ? "Left" : "Right");
        SampleStore.Clip recording = clip;
        // Persist incomplete metadata first, so process death cannot masquerade as a complete hold.
        busy = true; controls(); prompt.setText("Get ready… Predictions are hidden.");
        io.execute(() -> {
            try { store.begin(recording); ui(() -> {
                busy = false;
                if (clip != recording) { controls(); return; }
                main.postDelayed(() -> { if (clip == recording) {
                    synchronized (recordingLock) { recordStart = SystemClock.elapsedRealtime(); workerClip = recording; }
                    tick.run();
                } }, 1000);
                controls();
            }); } catch (Exception exception) { ui(() -> clip = null); error("Unable to begin hold", exception); }
        });
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (clip == null) return;
            long elapsed = SystemClock.elapsedRealtime() - recordStart;
            if (elapsed >= 2000) { finishClip(true, "completed"); return; }
            prompt.setText("Recording " + clip.label + " · keep your sign natural · " + (2 - elapsed / 1000) + "s");
            main.postDelayed(this, 100);
        }
    };

    private void abort(String reason) { if (clip != null) finishClip(false, reason); }
    private void finishClip(boolean complete, String reason) {
        main.removeCallbacks(tick); SampleStore.Clip recording = clip; clip = null;
        synchronized (recordingLock) { workerClip = null; }
        if (recording == null) return;
        if (complete && recording.frames.isEmpty()) recording.frames.add(new SampleStore.Frame(0, null, "NO_FRAMES"));
        busy = true; controls();
        io.execute(() -> {
            try { store.finish(recording, complete, reason); ui(() -> {
                busy = false;
                if (complete) {
                    int l = 0; while (!LABELS[l].startsWith(recording.label)) l++;
                    counts[l][recording.requestedHand.equals("Left") ? 0 : 1]++; chooseNext();
                }
                status.setText(complete ? "Hold saved. Relax your hand before the next capture." : "Hold marked incomplete: " + reason);
                updatePrompt(); controls();
            }); } catch (Exception exception) { error("Unable to save hold", exception); }
        });
    }

    private void deleteSamples() {
        busy = true; controls(); io.execute(() -> {
            try { store.deleteAll(); ui(() -> { session = null; for (int[] count : counts) java.util.Arrays.fill(count, 0); busy = false; updatePrompt(); controls(); status.setText("Local samples deleted."); }); }
            catch (Exception exception) { error("Delete failed", exception); }
        });
    }
    private void error(String message, Exception exception) { ui(() -> { busy = false; status.setText(message + ": " + exception.getMessage()); controls(); }); }
    private void controls() {
        if (label == null) return;
        boolean idle = clip == null && !busy;
        label.setEnabled(idle); hand.setEnabled(idle); split.setEnabled(idle);
        findViewById(R.id.collection_alias).setEnabled(idle);
        for (int id : new int[]{R.id.collection_new, R.id.collection_export, R.id.collection_delete, R.id.collection_reference}) findViewById(id).setEnabled(idle && store != null);
        findViewById(R.id.collection_capture).setEnabled(idle && active && session != null && counts[label.getSelectedItemPosition()][hand.getSelectedItemPosition()] < target(label.getSelectedItemPosition()));
        findViewById(R.id.collection_cancel).setEnabled(clip != null);
        findViewById(R.id.collection_camera).setEnabled(!busy);
        findViewById(R.id.collection_switch).setEnabled(active && bothCameras && idle);
    }
    private void adapter(Spinner spinner, String[] values) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinner.setAdapter(adapter);
    }
    private void ui(Runnable task) { main.post(() -> { if (!isDestroyed()) task.run(); }); }
    @Override protected void onResume() { super.onResume(); if (wanted) camera.start(); }
    @Override protected void onPause() { abort("screen left or rotated"); wanted = false; camera.stop(); super.onPause(); }
    @Override protected void onStop() { if (!isChangingConfigurations()) wanted = false; super.onStop(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("wanted", wanted); state.putInt("lens", lens);
        if (session != null) state.putString("session", session.id);
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() { camera.dispose(); main.removeCallbacks(tick); io.shutdown(); super.onDestroy(); }
}
