package com.group_nine.asl_translator.recognition;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Versioned portable forest: legacy hard votes or averaged leaf distributions. */
public final class ForestModel {
    private static final int MAGIC = 0x41534c56;
    public static final int FORMAT_VERSION = 1;
    public static final int DISTRIBUTION_FORMAT_VERSION = 2;
    public static final String[] LABELS = {"A", "E", "I", "O", "U", "OTHER"};

    public static final class Node {
        public final int feature, left, right, label;
        public final double threshold;
        private final double[] distribution;

        public Node(int feature, double threshold, int left, int right, int label) {
            this(feature, threshold, left, right, label, null);
        }

        public Node(int feature, double threshold, int left, int right, int label, double[] distribution) {
            this.feature = feature;
            this.threshold = threshold;
            this.left = left;
            this.right = right;
            this.label = label;
            this.distribution = distribution == null ? null : distribution.clone();
        }
    }

    public static final class Prediction {
        public final int label;
        /** Hard-vote fraction or mean leaf score; neither is a calibrated confidence. */
        public final double score, margin;
        private final double[] scores;
        private Prediction(int label, double score, double margin, double[] scores) {
            this.label = label; this.score = score; this.margin = margin; this.scores = scores;
        }
        public double[] scores() { return scores.clone(); }
    }

    private final Node[][] trees;
    public final String featureVersion;
    public final int featureCount;
    public final int formatVersion;
    public final double minimumScore, minimumMargin;

    public ForestModel(Node[][] trees, double minimumScore, double minimumMargin) {
        this(trees, minimumScore, minimumMargin, LandmarkFeatures.VERSION, FORMAT_VERSION);
    }

    public ForestModel(Node[][] trees, double minimumScore, double minimumMargin, String featureVersion, int formatVersion) {
        this.featureVersion = featureVersion;
        this.featureCount = LandmarkFeatures.count(featureVersion);
        this.formatVersion = formatVersion;
        if (trees == null || trees.length == 0 || trees.length > 512
                || (formatVersion != FORMAT_VERSION && formatVersion != DISTRIBUTION_FORMAT_VERSION)
                || (formatVersion == FORMAT_VERSION && !LandmarkFeatures.VERSION.equals(featureVersion))
                || !validThreshold(minimumScore) || !validThreshold(minimumMargin)) {
            throw new IllegalArgumentException("Invalid forest metadata");
        }
        this.trees = new Node[trees.length][];
        for (int t = 0; t < trees.length; t++) {
            Node[] nodes = trees[t];
            if (nodes == null || nodes.length == 0 || nodes.length > 16383) {
                throw new IllegalArgumentException("Invalid tree size");
            }
            this.trees[t] = nodes.clone();
            for (int i = 0; i < nodes.length; i++) {
                Node n = nodes[i];
                if (n == null || !Double.isFinite(n.threshold)
                        || (n.feature < 0 ? n.label < 0 || n.label >= LABELS.length
                        : n.feature >= featureCount || n.left <= i || n.right <= i
                        || n.left >= nodes.length || n.right >= nodes.length)) {
                    throw new IllegalArgumentException("Invalid tree node");
                }
                if (formatVersion == DISTRIBUTION_FORMAT_VERSION && n.feature < 0) {
                    if (n.distribution == null || n.distribution.length != LABELS.length) throw new IllegalArgumentException("Missing leaf scores");
                    double sum = 0;
                    for (double value : n.distribution) {
                        if (!validThreshold(value)) throw new IllegalArgumentException("Invalid leaf scores");
                        sum += value;
                    }
                    if (Math.abs(sum - 1) > 1e-6) throw new IllegalArgumentException("Leaf scores must sum to one");
                }
            }
        }
        this.minimumScore = minimumScore;
        this.minimumMargin = minimumMargin;
    }

    private static boolean validThreshold(double value) {
        return Double.isFinite(value) && value >= 0 && value <= 1;
    }

    public Prediction predict(double[] features) {
        if (features.length != featureCount) throw new IllegalArgumentException("Invalid features");
        for (double f : features) if (!Double.isFinite(f) || (formatVersion == DISTRIBUTION_FORMAT_VERSION && !Float.isFinite((float) f))) {
            throw new IllegalArgumentException("Non-finite features");
        }
        double[] votes = new double[LABELS.length];
        for (Node[] tree : trees) {
            int current = 0;
            while (tree[current].feature >= 0) {
                Node n = tree[current];
                double value = formatVersion == DISTRIBUTION_FORMAT_VERSION ? (double) (float) features[n.feature] : features[n.feature];
                current = value <= n.threshold ? n.left : n.right;
            }
            if (formatVersion == FORMAT_VERSION) votes[tree[current].label]++;
            else for (int label = 0; label < LABELS.length; label++) votes[label] += tree[current].distribution[label];
        }
        int best = 0;
        double second = 0;
        for (int i = 1; i < votes.length; i++) if (votes[i] > votes[best]) best = i;
        for (int i = 0; i < votes.length; i++) if (i != best) second = Math.max(second, votes[i]);
        double[] scores = new double[LABELS.length];
        for (int i = 0; i < votes.length; i++) scores[i] = votes[i] / (double) trees.length;
        return new Prediction(best, scores[best], (votes[best] - second) / (double) trees.length, scores);
    }

    public boolean accepts(Prediction prediction) {
        return prediction.label < 5 && prediction.score >= minimumScore && prediction.margin >= minimumMargin;
    }

    public ForestModel withThresholds(double score, double margin) {
        return new ForestModel(trees, score, margin, featureVersion, formatVersion);
    }

    public void write(OutputStream output) throws IOException {
        DataOutputStream out = new DataOutputStream(output);
        out.writeInt(MAGIC); out.writeInt(formatVersion);
        out.writeUTF(featureVersion); out.writeInt(featureCount);
        for (String label : LABELS) out.writeUTF(label);
        out.writeDouble(minimumScore); out.writeDouble(minimumMargin); out.writeInt(trees.length);
        for (Node[] tree : trees) {
            out.writeInt(tree.length);
            for (Node n : tree) {
                out.writeInt(n.feature); out.writeDouble(n.threshold);
                out.writeInt(n.left); out.writeInt(n.right); out.writeInt(n.label);
                if (formatVersion == DISTRIBUTION_FORMAT_VERSION && n.feature < 0) {
                    for (double value : n.distribution) out.writeDouble(value);
                }
            }
        }
        out.flush();
    }

    public static ForestModel read(InputStream input) throws IOException {
        DataInputStream in = new DataInputStream(input);
        if (in.readInt() != MAGIC) throw new IOException("Invalid vowel model magic");
        int version = in.readInt();
        String features = in.readUTF();
        int featureCount = in.readInt();
        if ((version != FORMAT_VERSION && version != DISTRIBUTION_FORMAT_VERSION)
                || !(LandmarkFeatures.VERSION.equals(features) || LandmarkFeatures.GEOMETRY_VERSION.equals(features))
                || featureCount != LandmarkFeatures.count(features)) {
            throw new IOException("Unsupported vowel model format");
        }
        for (String label : LABELS) if (!in.readUTF().equals(label)) throw new IOException("Invalid label order");
        double score = in.readDouble(), margin = in.readDouble();
        int count = in.readInt();
        if (count < 1 || count > 512) throw new IOException("Invalid tree count");
        Node[][] trees = new Node[count][];
        for (int t = 0; t < count; t++) {
            int size = in.readInt();
            if (size < 1 || size > 16383) throw new IOException("Invalid tree size");
            trees[t] = new Node[size];
            for (int i = 0; i < size; i++) {
                int feature = in.readInt(); double threshold = in.readDouble();
                int left = in.readInt(), right = in.readInt(), label = in.readInt();
                double[] distribution = null;
                if (version == DISTRIBUTION_FORMAT_VERSION && feature < 0) {
                    distribution = new double[LABELS.length];
                    for (int c = 0; c < distribution.length; c++) distribution[c] = in.readDouble();
                }
                trees[t][i] = new Node(feature, threshold, left, right, label, distribution);
            }
        }
        try { return new ForestModel(trees, score, margin, features, version); }
        catch (IllegalArgumentException exception) { throw new IOException("Invalid vowel model", exception); }
    }
}
