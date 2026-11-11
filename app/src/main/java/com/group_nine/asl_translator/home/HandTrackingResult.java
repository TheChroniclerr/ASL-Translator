package com.group_nine.asl_translator.home;

import androidx.camera.view.transform.OutputTransform;
import androidx.annotation.OptIn;
import androidx.camera.view.TransformExperimental;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.group_nine.asl_translator.recognition.VowelRecognitionResult;
import com.group_nine.asl_translator.recognition.ForestModel;

/** Immutable detector data and the geometry of the frame that produced it. */
@OptIn(markerClass = TransformExperimental.class)
final class HandTrackingResult {
    static final class Landmark {
        final float x;
        final float y;
        final float z;

        Landmark(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    static final class Hand {
        final List<Landmark> landmarks;
        final String handedness;
        final float confidence;

        Hand(List<Landmark> landmarks, String handedness, float confidence) {
            if (landmarks.size() != 21) {
                throw new IllegalArgumentException("A detected hand must have 21 landmarks");
            }
            this.landmarks = Collections.unmodifiableList(new ArrayList<>(landmarks));
            this.handedness = handedness;
            this.confidence = confidence;
        }
    }

    final long timestampMs;
    final int bufferWidth;
    final int bufferHeight;
    final int rotationDegrees;
    final List<Hand> hands;
    final VowelRecognitionResult recognition;
    final ForestModel.Prediction modelPrediction;
    private final OutputTransform sourceTransform;

    HandTrackingResult(long timestampMs, int bufferWidth, int bufferHeight, int rotationDegrees,
                       OutputTransform sourceTransform, List<Hand> hands) {
        this(timestampMs, bufferWidth, bufferHeight, rotationDegrees, sourceTransform, hands,
                VowelRecognitionResult.status(timestampMs, VowelRecognitionResult.State.INITIALIZING));
    }

    HandTrackingResult(long timestampMs, int bufferWidth, int bufferHeight, int rotationDegrees,
                       OutputTransform sourceTransform, List<Hand> hands, VowelRecognitionResult recognition) {
        this(timestampMs, bufferWidth, bufferHeight, rotationDegrees, sourceTransform, hands, recognition, null);
    }

    HandTrackingResult(long timestampMs, int bufferWidth, int bufferHeight, int rotationDegrees,
                       OutputTransform sourceTransform, List<Hand> hands, VowelRecognitionResult recognition,
                       ForestModel.Prediction modelPrediction) {
        this.timestampMs = timestampMs;
        this.bufferWidth = bufferWidth;
        this.bufferHeight = bufferHeight;
        this.rotationDegrees = rotationDegrees;
        this.sourceTransform = sourceTransform;
        this.hands = Collections.unmodifiableList(new ArrayList<>(hands));
        this.recognition = recognition;
        this.modelPrediction = modelPrediction;
    }

    OutputTransform sourceTransform() {
        return sourceTransform;
    }
}
