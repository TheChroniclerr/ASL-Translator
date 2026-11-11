package com.group_nine.asl_translator.recognition;

import org.junit.Test;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;
import static com.group_nine.asl_translator.recognition.VowelRecognitionResult.State;

public class RecognitionTest {
    private static float[] hand() {
        float[] points = new float[63];
        for (int i = 0; i < 21; i++) {
            points[i * 3] = .4f + i * .006f;
            points[i * 3 + 1] = .8f - i * .02f;
            points[i * 3 + 2] = -i * .002f;
        }
        return points;
    }

    private static ForestModel constant(int label) {
        return new ForestModel(new ForestModel.Node[][]{{new ForestModel.Node(-1, 0, -1, -1, label)}}, .7, .1);
    }

    @Test public void normalizedFeaturesAreTranslationAndScaleInvariant() {
        float[] original = hand(), transformed = original.clone();
        for (int i = 0; i < 21; i++) {
            transformed[i * 3] = original[i * 3] * .7f + .1f;
            transformed[i * 3 + 1] = original[i * 3 + 1] * .7f + .05f;
            transformed[i * 3 + 2] = original[i * 3 + 2] * .7f;
        }
        assertArrayEquals(LandmarkFeatures.extract(original, 640, 480), LandmarkFeatures.extract(transformed, 640, 480), 1e-5);
    }

    @Test public void aspectRatioCorrectionMatchesEquivalentPixelGeometry() {
        float[] original = hand(), square = original.clone();
        for (int i = 0; i < 21; i++) square[i * 3 + 1] *= .75f;
        assertArrayEquals(LandmarkFeatures.extract(original, 640, 480), LandmarkFeatures.extract(square, 640, 640), 1e-5);
    }

    @Test public void mirroringMatchesReflectedLandmarksAndIsReversible() {
        float[] original = hand(), mirrored = original.clone();
        for (int i = 0; i < 21; i++) mirrored[i * 3] = 1 - original[i * 3];
        double[] features = LandmarkFeatures.extract(original, 640, 480);
        assertArrayEquals(LandmarkFeatures.mirror(features), LandmarkFeatures.extract(mirrored, 640, 480), 1e-5);
        assertArrayEquals(features, LandmarkFeatures.mirror(LandmarkFeatures.mirror(features)), 0);
    }

    @Test(expected = IllegalArgumentException.class) public void rejectsDegeneratePalm() { LandmarkFeatures.extract(new float[63], 640, 480); }
    @Test(expected = IllegalArgumentException.class) public void rejectsNonFiniteCoordinates() {
        float[] points = hand(); points[10] = Float.NaN; LandmarkFeatures.extract(points, 640, 480);
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsInvalidDimensions() { LandmarkFeatures.extract(hand(), 0, 480); }

    @Test public void binaryRoundTripPreservesThresholdBranchesAndVotes() throws Exception {
        ForestModel.Node[] tree = {new ForestModel.Node(3, .25, 1, 2, -1), new ForestModel.Node(-1, 0, -1, -1, 0), new ForestModel.Node(-1, 0, -1, -1, 4)};
        ForestModel model = new ForestModel(new ForestModel.Node[][]{tree}, .8, .2);
        ByteArrayOutputStream output = new ByteArrayOutputStream(); model.write(output);
        ForestModel loaded = ForestModel.read(new ByteArrayInputStream(output.toByteArray()));
        double[] features = new double[63]; features[3] = .25;
        assertEquals(0, loaded.predict(features).label);
        features[3] = Math.nextUp(.25); assertEquals(4, loaded.predict(features).label);
        assertEquals(.8, loaded.minimumScore, 0); assertEquals(.2, loaded.minimumMargin, 0);
    }

    @Test(expected = IOException.class) public void rejectsCorruptModel() throws Exception { ForestModel.read(new ByteArrayInputStream(new byte[64])); }
    @Test(expected = IllegalArgumentException.class) public void rejectsCyclicTree() {
        new ForestModel(new ForestModel.Node[][]{{new ForestModel.Node(0, .5, 0, 0, -1)}}, .8, .2);
    }

    @Test public void stableLetterNeedsFiveHundredContinuousMilliseconds() {
        VowelClassifier classifier = new VowelClassifier(constant(0));
        List<float[]> hands = Collections.singletonList(hand());
        for (long t = 0; t < 500; t += 100) assertEquals(State.HOLD_STEADY, classifier.classify(hands, 640, 480, t).state);
        VowelRecognitionResult result = classifier.classify(hands, 640, 480, 500);
        assertEquals(State.RECOGNIZED, result.state); assertEquals("A", result.letter);
    }

    @Test public void missingAndMultipleHandsClearStability() {
        VowelClassifier classifier = new VowelClassifier(constant(2));
        List<float[]> hands = Collections.singletonList(hand());
        for (long t = 0; t <= 500; t += 100) classifier.classify(hands, 640, 480, t);
        VowelRecognitionResult result = classifier.classify(Collections.emptyList(), 640, 480, 600);
        assertEquals(State.NO_HANDS, result.state); assertNull(result.letter);
        assertEquals(State.HOLD_STEADY, classifier.classify(hands, 640, 480, 700).state);
        assertEquals(State.TWO_HANDS, classifier.classify(Arrays.asList(hand(), hand()), 640, 480, 800).state);
        assertEquals(State.HOLD_STEADY, classifier.classify(hands, 640, 480, 900).state);
    }

    @Test public void timingGapsLateFramesAndExplicitResetRestartStability() {
        VowelClassifier classifier = new VowelClassifier(constant(1));
        List<float[]> hands = Collections.singletonList(hand());
        for (long t = 0; t <= 500; t += 100) classifier.classify(hands, 640, 480, t);
        assertEquals(State.HOLD_STEADY, classifier.classify(hands, 640, 480, 1000).state);
        assertEquals(State.HOLD_STEADY, classifier.classify(hands, 640, 480, 900).state);
        classifier.reset();
        assertEquals(State.HOLD_STEADY, classifier.classify(hands, 640, 480, 1500).state);
    }

    @Test public void otherAndInvalidGeometryProduceUnknown() {
        VowelClassifier classifier = new VowelClassifier(constant(5));
        assertEquals(State.UNKNOWN, classifier.classify(Collections.singletonList(hand()), 640, 480, 0).state);
        assertEquals(State.UNKNOWN, classifier.classify(Collections.singletonList(new float[63]), 640, 480, 100).state);
    }

    @Test public void ambiguousForestVotesAreRejected() {
        ForestModel model = new ForestModel(new ForestModel.Node[][]{
                {new ForestModel.Node(-1, 0, -1, -1, 0)}, {new ForestModel.Node(-1, 0, -1, -1, 1)}}, .5, .1);
        assertFalse(model.accepts(model.predict(new double[63])));
    }

    @Test public void changingTheLetterRestartsItsHoldTime() {
        ForestModel.Node[] tree = {new ForestModel.Node(3, 0, 1, 2, -1),
                new ForestModel.Node(-1, 0, -1, -1, 0), new ForestModel.Node(-1, 0, -1, -1, 1)};
        VowelClassifier classifier = new VowelClassifier(new ForestModel(new ForestModel.Node[][]{tree}, .8, .2));
        float[] points = hand();
        for (long t = 0; t <= 500; t += 100) classifier.classify(Collections.singletonList(points), 640, 480, t);
        assertEquals("E", classifier.classify(Collections.singletonList(points), 640, 480, 600).letter);
        for (int i = 0; i < 21; i++) points[i * 3] = 1 - points[i * 3];
        assertEquals(State.HOLD_STEADY, classifier.classify(Collections.singletonList(points), 640, 480, 700).state);
        for (long t = 800; t <= 1100; t += 100) classifier.classify(Collections.singletonList(points), 640, 480, t);
        assertEquals("A", classifier.classify(Collections.singletonList(points), 640, 480, 1200).letter);
    }
}
