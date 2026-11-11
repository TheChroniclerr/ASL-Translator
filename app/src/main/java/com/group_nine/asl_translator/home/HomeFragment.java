package com.group_nine.asl_translator.home;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
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
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.group_nine.asl_translator.R;

import java.util.ArrayList;
import java.util.Locale;

/** Home screen with full-range resizable camera preview and editable translation output. */
public class HomeFragment extends Fragment {
    private static final String SPLIT = "home.split";
    private static final String SOURCE = "home.source";
    private static final String TARGET = "home.target";
    private static final String CAMERA = "home.camera";
    private static final String TRANSLATED_OUTPUT = "home.translated_output";

    private float split = ResizablePanelController.DEFAULT_SPLIT;
    private int sourceIndex = 0;
    private int targetIndex = 1;
    private boolean cameraTapped;
    private ResizablePanelController panels;

    private String translatedOutputText = "";
    private EditText translationOutputView;
    private TextView liveCaptionView;

    private final ActivityResultLauncher<Intent> speechLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    ArrayList<String> matches = result.getData()
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (matches != null && !matches.isEmpty()) {
                        translatedOutputText = matches.get(0);
                        if (translationOutputView != null) {
                            translationOutputView.setText(translatedOutputText);
                            translationOutputView.setSelection(translatedOutputText.length());
                        }
                        if (liveCaptionView != null) {
                            liveCaptionView.setText("Voice captured: \"" + translatedOutputText + "\"");
                        }
                    }
                }
            });

    public HomeFragment() {
        super(R.layout.fragment_home);
    }

    @Override
    public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        if (state != null) {
            split = state.getFloat(SPLIT, split);
            sourceIndex = state.getInt(SOURCE, 0);
            targetIndex = state.getInt(TARGET, 1);
            cameraTapped = state.getBoolean(CAMERA);
            translatedOutputText = state.getString(TRANSLATED_OUTPUT, "");
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

        translationOutputView = view.findViewById(R.id.translation_output);
        liveCaptionView = view.findViewById(R.id.live_caption);

        if (!translatedOutputText.isEmpty()) {
            translationOutputView.setText(translatedOutputText);
            liveCaptionView.setText("Voice captured: \"" + translatedOutputText + "\"");
        }

        translationOutputView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                translatedOutputText = s.toString();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        TextView status = view.findViewById(R.id.camera_status);
        status.setText(cameraTapped ? R.string.camera_unavailable : R.string.camera_placeholder);
        view.findViewById(R.id.start_camera).setOnClickListener(button -> {
            cameraTapped = true;
            status.setText(R.string.camera_unavailable);
        });

        view.findViewById(R.id.microphone).setOnClickListener(button -> startVoiceToText(view));

        panels = new ResizablePanelController(view, split);
    }

    private void startVoiceToText(View rootView) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now to translate...");

        try {
            speechLauncher.launch(intent);
        } catch (ActivityNotFoundException e) {
            Snackbar.make(rootView, "Speech recognition is not supported on this device.",
                    Snackbar.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        if (panels != null) {
            split = panels.getSplit();
        }
        out.putFloat(SPLIT, split);
        out.putInt(SOURCE, sourceIndex);
        out.putInt(TARGET, targetIndex);
        out.putBoolean(CAMERA, cameraTapped);
        out.putString(TRANSLATED_OUTPUT, translatedOutputText);
        super.onSaveInstanceState(out);
    }

    @Override
    public void onDestroyView() {
        if (panels != null) {
            split = panels.getSplit();
            panels.dispose();
            panels = null;
        }
        translationOutputView = null;
        liveCaptionView = null;
        super.onDestroyView();
    }
}