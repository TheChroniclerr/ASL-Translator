package com.group_nine.asl_translator.home;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.group_nine.asl_translator.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment {
    private static final String KEY_SPLIT = "panel_split";
    private static final String KEY_CAMERA_ACTIVE = "camera_active";

    private ResizablePanelController panelController;
    private MaterialAutoCompleteTextView sourceLanguage;
    private MaterialAutoCompleteTextView targetLanguage;
    private TextView cameraStatus;
    private TextView liveCaption;
    private EditText translationOutput;
    private MaterialButton startCameraButton;
    private MaterialButton microphoneButton;
    private RecyclerView recyclerHandshapes;
    private HandshapeAdapter handshapeAdapter;

    private boolean cameraActive = false;

    private final ActivityResultLauncher<Intent> speechLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    ArrayList<String> matches = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (matches != null && !matches.isEmpty()) {
                        String spokenText = matches.get(0);
                        translationOutput.setText(spokenText);
                        translationOutput.setSelection(spokenText.length());
                    }
                }
            });

    public HomeFragment() {
        super(R.layout.fragment_home);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        float restoredSplit = savedInstanceState != null
                ? savedInstanceState.getFloat(KEY_SPLIT, ResizablePanelController.DEFAULT_SPLIT)
                : ResizablePanelController.DEFAULT_SPLIT;
        cameraActive = savedInstanceState != null && savedInstanceState.getBoolean(KEY_CAMERA_ACTIVE, false);

        panelController = new ResizablePanelController(view, restoredSplit);

        sourceLanguage = view.findViewById(R.id.source_language);
        targetLanguage = view.findViewById(R.id.target_language);
        cameraStatus = view.findViewById(R.id.camera_status);
        liveCaption = view.findViewById(R.id.live_caption);
        translationOutput = view.findViewById(R.id.translation_output);
        startCameraButton = view.findViewById(R.id.start_camera);
        microphoneButton = view.findViewById(R.id.microphone);
        recyclerHandshapes = view.findViewById(R.id.recycler_handshapes);

        setupLanguageDropdowns();
        setupHandshapeStrip();
        setupControls();
        updateCameraUi();
    }

    private void setupLanguageDropdowns() {
        String[] languages = new String[]{"ASL", "English", "French", "Spanish"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                languages
        );
        sourceLanguage.setAdapter(adapter);
        targetLanguage.setAdapter(adapter);

        if (sourceLanguage.getText().toString().isEmpty()) {
            sourceLanguage.setText(languages[0], false);
        }
        if (targetLanguage.getText().toString().isEmpty()) {
            targetLanguage.setText(languages[1], false);
        }
    }

    private void setupHandshapeStrip() {
        handshapeAdapter = new HandshapeAdapter();
        recyclerHandshapes.setLayoutManager(
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        );
        recyclerHandshapes.setAdapter(handshapeAdapter);

        translationOutput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateHandshapes(s != null ? s.toString() : "");
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        updateHandshapes(translationOutput.getText().toString());
    }

    private void setupControls() {
        startCameraButton.setOnClickListener(v -> {
            cameraActive = !cameraActive;
            updateCameraUi();
        });

        microphoneButton.setOnClickListener(v -> startVoiceInput());
    }

    private void updateCameraUi() {
        if (cameraActive) {
            cameraStatus.setText("Camera active — detecting signs...");
            liveCaption.setText("Listening for ASL gestures...");
            startCameraButton.setText("Stop Camera");
        } else {
            cameraStatus.setText(R.string.camera_placeholder);
            liveCaption.setText(R.string.live_translation);
            startCameraButton.setText(R.string.start_camera);
        }
    }

    private void startVoiceInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.microphone));
        try {
            speechLauncher.launch(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(requireContext(), "Speech recognition not supported on this device", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateHandshapes(String text) {
        List<HandshapeItem> list = new ArrayList<>();
        for (char c : text.toCharArray()) {
            if (Character.isLetter(c)) {
                char upper = Character.toUpperCase(c);
                list.add(new HandshapeItem(upper, getVowelDrawable(upper)));
            }
        }
        handshapeAdapter.setItems(list);
    }

    private int getVowelDrawable(char c) {
        switch (c) {
            case 'A':
                return R.drawable.ic_asl_a;
            case 'E':
                return R.drawable.ic_asl_e;
            case 'I':
                return R.drawable.ic_asl_i;
            case 'O':
                return R.drawable.ic_asl_o;
            case 'U':
                return R.drawable.ic_asl_u;
            default:
                return 0;
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (panelController != null) {
            outState.putFloat(KEY_SPLIT, panelController.getSplit());
        }
        outState.putBoolean(KEY_CAMERA_ACTIVE, cameraActive);
    }

    @Override
    public void onDestroyView() {
        if (panelController != null) {
            panelController.dispose();
            panelController = null;
        }
        super.onDestroyView();
    }
}