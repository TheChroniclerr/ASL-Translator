package com.group_nine.asl_translator.recognition;

import org.junit.Test;
import java.io.*;
import java.util.Arrays;
import static org.junit.Assert.*;

public class RefinementModelTest {
    private float[] hand() {
        float[] p = new float[63];
        for (int i = 0; i < 21; i++) { p[i * 3] = .2f + i * .01f; p[i * 3 + 1] = .8f - i * .015f; p[i * 3 + 2] = -.01f * (i % 4); }
        return p;
    }
    @Test public void geometryMirrorPreservesDistancesAndBends() {
        float[] original = hand(), reflected = original.clone();
        for (int i = 0; i < 63; i += 3) reflected[i] = 1 - reflected[i];
        double[] features = LandmarkFeatures.extract(original, 480, 640, LandmarkFeatures.GEOMETRY_VERSION);
        assertEquals(93, features.length);
        assertArrayEquals(LandmarkFeatures.mirror(features), LandmarkFeatures.extract(reflected, 480, 640, LandmarkFeatures.GEOMETRY_VERSION), 1e-5);
        assertArrayEquals(Arrays.copyOfRange(features, 63, 93), Arrays.copyOfRange(LandmarkFeatures.mirror(features), 63, 93), 0);
    }
    @Test public void geometryIsScaleAndTranslationInvariant() {
        float[] original = hand(), changed = original.clone();
        for (int i = 0; i < 21; i++) {
            changed[i * 3] = original[i * 3] * .7f + .1f;
            changed[i * 3 + 1] = original[i * 3 + 1] * .7f + .1f;
            changed[i * 3 + 2] = original[i * 3 + 2] * .7f;
        }
        assertArrayEquals(LandmarkFeatures.extract(original, 480, 640, LandmarkFeatures.GEOMETRY_VERSION), LandmarkFeatures.extract(changed, 480, 640, LandmarkFeatures.GEOMETRY_VERSION), 2e-5);
    }
    @Test(expected = IllegalArgumentException.class) public void zeroFingerSegmentIsRejected() {
        float[] p = hand(); System.arraycopy(p, 3, p, 6, 3);
        LandmarkFeatures.extract(p, 640, 480, LandmarkFeatures.GEOMETRY_VERSION);
    }
    @Test public void distributionForestAveragesLeafScoresRatherThanHardVotes() throws Exception {
        ForestModel model = new ForestModel(new ForestModel.Node[][]{
                {new ForestModel.Node(-1, 0, -1, -1, 0, new double[]{.6, .4, 0, 0, 0, 0})},
                {new ForestModel.Node(-1, 0, -1, -1, 1, new double[]{.1, .9, 0, 0, 0, 0})}}, .5, .05, LandmarkFeatures.GEOMETRY_VERSION, 2);
        ByteArrayOutputStream out = new ByteArrayOutputStream(); model.write(out);
        ForestModel copy = ForestModel.read(new ByteArrayInputStream(out.toByteArray()));
        ForestModel.Prediction prediction = copy.predict(new double[93]);
        assertEquals(1, prediction.label); assertEquals(.65, prediction.score, 1e-12);
        assertEquals(93, copy.featureCount); assertEquals(LandmarkFeatures.GEOMETRY_VERSION, copy.featureVersion);
    }
    @Test public void distributionForestUsesFloat32AtBranches() {
        ForestModel model = new ForestModel(new ForestModel.Node[][]{{
                new ForestModel.Node(0, .25, 1, 2, 0),
                new ForestModel.Node(-1, 0, -1, -1, 0, new double[]{1, 0, 0, 0, 0, 0}),
                new ForestModel.Node(-1, 0, -1, -1, 1, new double[]{0, 1, 0, 0, 0, 0})}}, .5, .05, LandmarkFeatures.VERSION, 2);
        double[] p = new double[63]; p[0] = Math.nextUp(.25);
        assertEquals(0, model.predict(p).label);
        p[0] = Math.nextUp(.25f); assertEquals(1, model.predict(p).label);
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsInvalidLeafDistribution() {
        new ForestModel(new ForestModel.Node[][]{{new ForestModel.Node(-1, 0, -1, -1, 0, new double[]{.2, .2, 0, 0, 0, 0})}}, .5, .05, LandmarkFeatures.VERSION, 2);
    }
    @Test(expected = IOException.class) public void rejectsTruncatedDistributionModel() throws Exception {
        ForestModel model = new ForestModel(new ForestModel.Node[][]{{new ForestModel.Node(-1, 0, -1, -1, 0, new double[]{1, 0, 0, 0, 0, 0})}}, .5, .05, LandmarkFeatures.VERSION, 2);
        ByteArrayOutputStream out = new ByteArrayOutputStream(); model.write(out);
        ForestModel.read(new ByteArrayInputStream(Arrays.copyOf(out.toByteArray(), out.size() - 3)));
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsFloat32Overflow() {
        ForestModel model = new ForestModel(new ForestModel.Node[][]{{new ForestModel.Node(-1, 0, -1, -1, 0, new double[]{1, 0, 0, 0, 0, 0})}}, .5, .05, LandmarkFeatures.VERSION, 2);
        double[] values = new double[63]; values[0] = Double.MAX_VALUE; model.predict(values);
    }
}
