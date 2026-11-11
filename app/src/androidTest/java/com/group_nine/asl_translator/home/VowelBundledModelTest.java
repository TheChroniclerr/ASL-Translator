package com.group_nine.asl_translator.home;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.group_nine.asl_translator.recognition.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.util.*;
import static org.junit.Assert.*;

/** Held-out real dataset landmarks exercise the shipped model and predictor on Android. */
@RunWith(AndroidJUnit4.class)
public class VowelBundledModelTest {
    @Test public void shippedAssetMatchesExportedHeldOutPredictions() throws Exception {
        ForestModel model;
        try (InputStream input = InstrumentationRegistry.getInstrumentation().getTargetContext().getAssets().open("vowel_forest.bin")) {
            model = ForestModel.read(input);
        }
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("vowel_fixtures.csv")))) {
            reader.readLine(); String line;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",");
                double[] features = new double[63];
                for (int i = 0; i < 63; i++) features[i] = Double.parseDouble(values[5 + i]);
                ForestModel.Prediction prediction = model.predict(features);
                assertEquals(values[2], ForestModel.LABELS[prediction.label]);
                assertEquals(Double.parseDouble(values[3]), prediction.score, 1e-9);
                assertEquals(Double.parseDouble(values[4]), prediction.margin, 1e-9);
                assertEquals(Boolean.parseBoolean(values[1]), model.accepts(prediction)); count++;
            }
        }
        assertEquals(60, count);
    }

    @Test public void realDetectorAndClassifierRecognizeRepresentativeHeldOutImages() throws Exception {
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ForestModel diagnostic;
        try (InputStream input = context.getAssets().open("vowel_forest.bin")) { diagnostic = ForestModel.read(input); }
        List<String> failures = new ArrayList<>();
        for (String letter : new String[]{"A", "E", "I", "O", "U", "OTHER"}) {
            Bitmap source;
            try (InputStream input = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("vowels/" + letter + ".jpg")) {
                source = BitmapFactory.decodeStream(input);
            }
            assertNotNull(source);
            try (HandTracker.MediaPipeEngine detector = new HandTracker.MediaPipeEngine(context)) {
                VowelClassifier classifier = HandTracker.loadClassifier(context);
                VowelRecognitionResult recognized = null;
                StringBuilder trace = new StringBuilder(letter + ": ");
                for (long timestamp = 0; timestamp <= 700; timestamp += 100) {
                    Bitmap frame = source.copy(Bitmap.Config.ARGB_8888, false);
                    try {
                        List<HandTrackingResult.Hand> result = detector.detect(frame, timestamp);
                        assertEquals(1, result.size());
                        List<float[]> hands = new ArrayList<>();
                        for (HandTrackingResult.Hand hand : result) {
                            float[] points = new float[63];
                            for (int i = 0; i < 21; i++) {
                                HandTrackingResult.Landmark p = hand.landmarks.get(i);
                                points[i * 3] = p.x; points[i * 3 + 1] = p.y; points[i * 3 + 2] = p.z;
                            }
                            hands.add(points);
                        }
                        recognized = classifier.classify(hands, source.getWidth(), source.getHeight(), timestamp);
                        ForestModel.Prediction raw = diagnostic.predict(LandmarkFeatures.extract(hands.get(0), source.getWidth(), source.getHeight()));
                        trace.append(timestamp).append('=').append(ForestModel.LABELS[raw.label]).append('/')
                                .append(raw.score).append('/').append(raw.margin).append(' ');
                    } finally { if (!frame.isRecycled()) frame.recycle(); }
                }
                android.util.Log.i("VowelModelTest", trace.toString());
                if (letter.equals("OTHER")) {
                    if (recognized.state != VowelRecognitionResult.State.UNKNOWN) failures.add(trace.toString());
                } else if (recognized.state != VowelRecognitionResult.State.RECOGNIZED || !letter.equals(recognized.letter)) {
                    failures.add(trace.toString());
                }
            } finally { source.recycle(); }
        }
        assertTrue(String.join("\n", failures), failures.isEmpty());
    }
}
