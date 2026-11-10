package com.group_nine.asl_translator.home;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.group_nine.asl_translator.R;

/** UI-only home screen; no permissions or recognition engines are connected. */
public class HomeFragment extends Fragment {
    private static final String SPLIT = "home.split";
    private static final String SOURCE = "home.source";
    private static final String TARGET = "home.target";
    private static final String CAMERA = "home.camera";

    private float split = ResizablePanelController.DEFAULT_SPLIT;
    private int sourceIndex = 0;
    private int targetIndex = 1;
    private boolean cameraTapped;
    private ResizablePanelController panels;

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

        TextView status = view.findViewById(R.id.camera_status);
        status.setText(cameraTapped ? R.string.camera_unavailable : R.string.camera_placeholder);
        view.findViewById(R.id.start_camera).setOnClickListener(button -> {
            cameraTapped = true;
            status.setText(R.string.camera_unavailable);
        });
        view.findViewById(R.id.microphone).setOnClickListener(button ->
                Snackbar.make(view, R.string.speech_soon, Snackbar.LENGTH_SHORT).show());
        panels = new ResizablePanelController(view, split);
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
        super.onSaveInstanceState(out);
    }

    @Override
    public void onDestroyView() {
        if (panels != null) {
            split = panels.getSplit();
            panels.dispose();
            panels = null;
        }
        super.onDestroyView();
    }
}
