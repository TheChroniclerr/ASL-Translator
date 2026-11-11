package com.group_nine.asl_translator.recognition;

/** The same aspect-correct, wrist-relative features are used in training and on Android. */
public final class LandmarkFeatures {
    public static final int COUNT = 63;
    public static final String VERSION = "wrist-palm-aspect-v1";
    public static final String GEOMETRY_VERSION = "wrist-palm-geometry-v2";
    public static final int GEOMETRY_COUNT = 93;

    private LandmarkFeatures() { }

    public static int count(String version) {
        if (VERSION.equals(version)) return COUNT;
        if (GEOMETRY_VERSION.equals(version)) return GEOMETRY_COUNT;
        throw new IllegalArgumentException("Unsupported feature version");
    }

    public static double[] extract(float[] xyz, int width, int height, String version) {
        double[] coordinates = extract(xyz, width, height);
        if (VERSION.equals(version)) return coordinates;
        if (!GEOMETRY_VERSION.equals(version)) throw new IllegalArgumentException("Unsupported feature version");
        double[] features = java.util.Arrays.copyOf(coordinates, GEOMETRY_COUNT);
        int next = COUNT;
        int[] bases = {1, 5, 9, 13, 17};
        for (int base : bases) {
            features[next++] = cosine(coordinates, 0, base, base + 1);
            features[next++] = cosine(coordinates, base, base + 1, base + 2);
            features[next++] = cosine(coordinates, base + 1, base + 2, base + 3);
        }
        int[] tips = {4, 8, 12, 16, 20};
        for (int i = 0; i < tips.length; i++) {
            for (int j = i + 1; j < tips.length; j++) features[next++] = distance(coordinates, tips[i], tips[j]);
        }
        for (int i = 0; i < tips.length; i++) features[next++] = distance(coordinates, tips[i], bases[i]);
        return features;
    }

    private static double distance(double[] p, int a, int b) {
        double sum = 0;
        for (int axis = 0; axis < 3; axis++) { double d = p[a * 3 + axis] - p[b * 3 + axis]; sum += d * d; }
        return Math.sqrt(sum);
    }

    private static double cosine(double[] p, int a, int joint, int b) {
        double dot = 0, left = 0, right = 0;
        for (int axis = 0; axis < 3; axis++) {
            double u = p[a * 3 + axis] - p[joint * 3 + axis];
            double v = p[b * 3 + axis] - p[joint * 3 + axis];
            dot += u * v; left += u * u; right += v * v;
        }
        if (left < 1e-12 || right < 1e-12) throw new IllegalArgumentException("Degenerate finger segment");
        return Math.max(-1, Math.min(1, dot / Math.sqrt(left * right)));
    }

    public static double[] extract(float[] xyz, int uprightWidth, int uprightHeight) {
        if (xyz == null || xyz.length != COUNT || uprightWidth <= 0 || uprightHeight <= 0) {
            throw new IllegalArgumentException("Expected 21 landmarks and positive frame dimensions");
        }
        for (float value : xyz) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("Non-finite landmark");
            }
        }
        double[] features = new double[COUNT];
        for (int i = 0; i < 21; i++) {
            features[i * 3] = (xyz[i * 3] - xyz[0]) * uprightWidth;
            features[i * 3 + 1] = (xyz[i * 3 + 1] - xyz[1]) * uprightHeight;
            features[i * 3 + 2] = (xyz[i * 3 + 2] - xyz[2]) * uprightWidth;
        }
        double scale = Math.sqrt(features[27] * features[27]
                + features[28] * features[28] + features[29] * features[29]);
        if (scale < 1.0) {
            throw new IllegalArgumentException("Degenerate palm geometry");
        }
        for (int i = 0; i < COUNT; i++) {
            features[i] /= scale;
            if (Math.abs(features[i]) > 10) {
                throw new IllegalArgumentException("Implausible palm geometry");
            }
        }
        return features;
    }

    public static double[] mirror(double[] features) {
        if (features.length != COUNT && features.length != GEOMETRY_COUNT) throw new IllegalArgumentException("Invalid feature length");
        double[] result = features.clone();
        for (int i = 0; i < COUNT; i += 3) result[i] = -result[i];
        return result;
    }
}
