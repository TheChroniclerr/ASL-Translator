package com.group_nine.asl_translator.recognition;

import java.util.List;
import static com.group_nine.asl_translator.recognition.VowelRecognitionResult.State;

/** Runs on the camera worker; a new instance is created for every camera session. */
public class VowelClassifier {
    private final ForestModel model;
    private int candidate = -1;
    private long since = -1, previous = -1;
    public static final long STABLE_MS = 500;
    public static final long MAX_GAP_MS = 250;
    private ForestModel.Prediction lastPrediction;

    public ForestModel.Prediction lastPrediction() { return lastPrediction; }

    public VowelClassifier(ForestModel model) { this.model = model; }

    public VowelRecognitionResult classify(List<float[]> hands, int width, int height, long timestampMs) {
        double[] features = null;
        if (hands.size() == 1) {
            try { features = LandmarkFeatures.extract(hands.get(0), width, height, model.featureVersion); }
            catch (IllegalArgumentException ignored) { /* Invalid geometry remains Unknown. */ }
        }
        return classifyFeatures(features, hands.size(), timestampMs);
    }

    /** Desktop replay uses features produced by the same Java extractor. */
    public VowelRecognitionResult classifyFeatures(double[] features, int handCount, long timestampMs) {
        lastPrediction = null;
        if (previous >= 0 && (timestampMs <= previous || timestampMs - previous > MAX_GAP_MS)) reset();
        previous = timestampMs;
        if (handCount != 1) {
            resetCandidate();
            return VowelRecognitionResult.status(timestampMs, handCount == 0 ? State.NO_HANDS : State.TWO_HANDS);
        }
        if (features == null) {
            resetCandidate();
            return VowelRecognitionResult.status(timestampMs, State.UNKNOWN);
        }
        ForestModel.Prediction prediction = model.predict(features);
        lastPrediction = prediction;
        if (!model.accepts(prediction)) {
            resetCandidate();
            return VowelRecognitionResult.status(timestampMs, State.UNKNOWN);
        }
        if (candidate != prediction.label) {
            candidate = prediction.label;
            since = timestampMs;
        }
        return timestampMs - since >= STABLE_MS
                ? VowelRecognitionResult.recognized(timestampMs, candidate, prediction.score)
                : VowelRecognitionResult.status(timestampMs, State.HOLD_STEADY);
    }

    private void resetCandidate() { candidate = -1; since = -1; }
    public void reset() { resetCandidate(); previous = -1; lastPrediction = null; }
}
