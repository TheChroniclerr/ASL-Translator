package com.group_nine.asl_translator.training;

import com.group_nine.asl_translator.recognition.*;
import org.tribuo.*;
import org.tribuo.classification.Label;
import org.tribuo.classification.LabelFactory;
import org.tribuo.classification.dtree.CARTClassificationTrainer;
import org.tribuo.classification.dtree.impurity.GiniIndex;
import org.tribuo.classification.ensemble.VotingCombiner;
import org.tribuo.common.tree.*;
import org.tribuo.ensemble.EnsembleModel;
import org.tribuo.impl.ArrayExample;
import org.tribuo.provenance.SimpleDataSourceProvenance;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Explicit prepare/train commands. Both training and Android inference are Java. */
public final class TrainingTool {
    private static final String[] FEATURES = new String[LandmarkFeatures.COUNT];
    static { for (int i = 0; i < FEATURES.length; i++) FEATURES[i] = String.format(Locale.ROOT, "p%02d", i); }
    private static final class Row {
        String id, letter, group, split;
        int label;
        double[] features;
    }

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        if (args.length > 0 && args[0].equals("refine")) {
            RefinementTool.main(java.util.Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        if (args.length == 3 && args[0].equals("prepare")) {
            DatasetPreparer.prepare(Path.of(args[1]), Path.of(args[2]));
        } else if (args.length == 3 && args[0].equals("train")) {
            train(Path.of(args[1]), Path.of(args[2]), false);
        } else if (args.length == 3 && args[0].equals("train-video")) {
            train(Path.of(args[1]), Path.of(args[2]), true);
        } else if (args.length == 3 && args[0].equals("audit")) {
            Path input = Path.of(args[1]), output = Path.of(args[2]);
            try (InputStream stream = Files.newInputStream(output.resolve("vowel_forest.bin"))) {
                appendPipelineReport(input, output, load(input), ForestModel.read(stream));
            }
        } else {
            throw new IllegalArgumentException("Usage: prepare <SignAlphaSet.zip> <dataset-directory> OR train|train-video|audit <landmarks.csv> <output-directory>");
        }
    }

    private static int label(String letter) {
        for (int i = 0; i < 5; i++) if (ForestModel.LABELS[i].equals(letter)) return i;
        return 5;
    }

    private static List<Row> load(Path input) throws IOException {
        List<Row> rows = new ArrayList<>();
        Map<String, String> splitByGroup = new HashMap<>();
        Set<String> ids = new HashSet<>();
        int invalid = 0;
        try (BufferedReader reader = Files.newBufferedReader(input)) {
            String header = reader.readLine();
            if (header == null || !header.startsWith("id,letter,group,split,width,height,p0,")) throw new IOException("Unexpected landmark header");
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(",");
                if (fields.length != 69) throw new IOException("Unexpected landmark column count");
                Row row = new Row();
                row.id = fields[0]; row.letter = fields[1]; row.group = fields[2]; row.split = fields[3];
                if (!ids.add(row.id)) throw new IOException("Repeated image ID");
                if (!row.letter.matches("[A-Z]") || row.letter.equals("J") || row.letter.equals("Z")) throw new IOException("Invalid static label");
                if (!Arrays.asList("train", "validation", "test").contains(row.split)) throw new IOException("Invalid split");
                String previous = splitByGroup.putIfAbsent(row.group, row.split);
                if (previous != null && !previous.equals(row.split)) throw new IOException("Duplicate group leaked between splits");
                float[] points = new float[63];
                for (int i = 0; i < 63; i++) points[i] = Float.parseFloat(fields[6 + i]);
                try { row.features = LandmarkFeatures.extract(points, Integer.parseInt(fields[4]), Integer.parseInt(fields[5])); }
                catch (IllegalArgumentException exception) { invalid++; continue; }
                row.label = label(row.letter); rows.add(row);
            }
        }
        System.out.println("Loaded " + rows.size() + " valid examples; rejected degenerate geometry: " + invalid);
        return rows;
    }

    private static void train(Path input, Path output, boolean videoCalibration) throws Exception {
        Files.createDirectories(output);
        List<Row> rows = load(input);
        List<Row> validation = withMirrors(split(rows, "validation")), test = split(rows, "test");
        List<Row> balanced = balance(split(rows, "train"));
        LabelFactory factory = new LabelFactory();
        MutableDataset<Label> training = new MutableDataset<>(
                new SimpleDataSourceProvenance("SignAlphaSet v2, grouped split, seed 470, " + DatasetPreparer.fileSha(input), factory), factory);
        for (Row row : balanced) {
            training.add(example(row.label, row.features));
            training.add(example(row.label, LandmarkFeatures.mirror(row.features)));
        }
        CARTClassificationTrainer cart = new CARTClassificationTrainer(12, 5f, 0f,
                8f / 63f, false, new GiniIndex(), 470L);
        RandomForestTrainer<Label> trainer = new RandomForestTrainer<>(cart, new VotingCombiner(), 128, 470L);
        System.out.println("Training 128 trees on " + training.size() + " class-balanced originals/mirrors");
        EnsembleModel<Label> trained = trainer.train(training);
        ForestModel model = export(trained);
        // Threshold selection cannot inspect the test split.
        Metrics best = null;
        for (double score : new double[]{.50, .55, .60, .65, .70, .75, .80, .85, .90, .95}) {
            for (double margin : new double[]{.05, .10, .15, .20, .25, .30}) {
                ForestModel candidate = model.withThresholds(score, margin);
                Metrics metrics = evaluate(candidate, validation);
                if (best == null || metrics.betterThan(best)) {
                    best = metrics; model = candidate;
                }
            }
        }
        if (videoCalibration) {
            model = VideoCalibration.calibrate(model, input.resolveSibling("video-validation.csv"), output.resolve("video-calibration.md"));
            best = evaluate(model, validation);
        }
        try (OutputStream stream = Files.newOutputStream(output.resolve("vowel_forest.bin"))) { model.write(stream); }
        ForestModel reloaded;
        try (InputStream stream = Files.newInputStream(output.resolve("vowel_forest.bin"))) { reloaded = ForestModel.read(stream); }
        List<Row> mirroredTest = withMirrors(test);
        int verified = verifyExport(trained, reloaded, validation) + verifyExport(trained, reloaded, mirroredTest);
        Metrics heldOut = evaluate(reloaded, test);
        Metrics bothHandViews = evaluate(reloaded, mirroredTest);
        try (PrintWriter report = new PrintWriter(Files.newBufferedWriter(output.resolve("evaluation.md")))) {
            report.println("# Vowel recognition evaluation\n");
            report.println("Dataset: SignAlphaSet v2 (CC BY 4.0), DOI 10.17632/8fmvr9m98w.2.\n");
            report.println("Split before mirrored augmentation; exact/near-duplicate groups stay together. Signer/session IDs are not supplied. This is not a signer-independent or live-phone evaluation.\n");
            report.println("Classifier tables are conditional on successful single-hand landmark extraction and valid geometry. Whole-image pipeline evaluation, including missed detections, is reported separately below.\n");
            for (String name : new String[]{"train", "validation", "test"}) {
                List<Row> part = split(rows, name);
                report.println("- " + name + ": " + part.size() + " original images; class counts A/E/I/O/U/Other: " + Arrays.toString(counts(part)));
            }
            report.println("- Training after class balancing and mirrored augmentation: " + training.size());
            report.println("- Export verification: " + verified + " validation/test examples, all six vote scores agree with Tribuo within 1e-6.");
            report.printf("- Validation-selected minimum tree-vote fraction: %.2f; minimum winning margin: %.2f.%n%n", model.minimumScore, model.minimumMargin);
            report.println(videoCalibration
                    ? "Thresholds use validation-only VIDEO-mode sequences and the app's 500 ms stabilization. See video-calibration.md. The same forest configuration and training split are retained from the initial still-image baseline; the test split is re-evaluated after this validation-only calibration. It is held out from training, but has been evaluated previously.\n"
                    : "Validation threshold selection includes each validation image and its reflection. Test images are used only for final evaluation and export verification.\n");
            report.println("Tree-vote fractions are not calibrated probabilities.\n");
            report.println("## Validation\n"); best.write(report);
            report.println("\n## Held-out test split\n"); heldOut.write(report);
            report.println("\n## Test split including reflected views\n"); bothHandViews.write(report);
            report.println("\nConditional classifier targets: precision >=90% and recall >=80% for each vowel. Result: " + (heldOut.targets() ? "PASS" : "NOT MET") + ".");
            report.println("\nOther contains the 19 remaining static letters; it does not cover every possible unknown hand shape. Movement letters J/Z are excluded. Dataset collection conditions differ from phone use; test with additional people and backgrounds before claiming generalization.");
        }
        Properties metadata = new Properties();
        metadata.setProperty("dataset", "SignAlphaSet v2; DOI 10.17632/8fmvr9m98w.2; CC BY 4.0");
        metadata.setProperty("archive.sha256", DatasetPreparer.ARCHIVE_SHA256);
        metadata.setProperty("landmarks.sha256", DatasetPreparer.fileSha(input));
        metadata.setProperty("model.sha256", DatasetPreparer.fileSha(output.resolve("vowel_forest.bin")));
        metadata.setProperty("features", LandmarkFeatures.VERSION);
        metadata.setProperty("labels", String.join(",", ForestModel.LABELS));
        metadata.setProperty("trainer", "Tribuo 4.3.2; trees=128; depth=12; minChildWeight=5; featuresPerSplit=8; seed=470");
        metadata.setProperty("minimumVoteFraction", Double.toString(model.minimumScore));
        metadata.setProperty("minimumWinningMargin", Double.toString(model.minimumMargin));
        metadata.setProperty("test.classifierTargetsMet", Boolean.toString(heldOut.targets()));
        metadata.setProperty("calibration", videoCalibration ? "validation-only VIDEO sequences; 50 images/static letter; 8 frames; 100 ms/frame; 500 ms hold" : "validation IMAGE landmarks and reflections");
        if (videoCalibration) metadata.setProperty("videoValidation.sha256", DatasetPreparer.fileSha(input.resolveSibling("video-validation.csv")));
        try (Writer writer = Files.newBufferedWriter(output.resolve("vowel_model.properties"))) {
            metadata.store(writer, "Offline ASL vowel recognition model");
        }
        // Representative held-out landmark fixtures verify the actual shipped asset on Android.
        try (PrintWriter fixtures = new PrintWriter(Files.newBufferedWriter(output.resolve("vowel_fixtures.csv")))) {
            fixtures.println("label,accepted,predicted,score,margin,features...");
            for (int label = 0; label < 6; label++) {
                final int currentLabel = label;
                int written = 0;
                for (Row row : test) {
                    if (row.label != currentLabel) continue;
                    ForestModel.Prediction p = model.predict(row.features);
                    fixtures.print(ForestModel.LABELS[label] + "," + model.accepts(p) + "," + ForestModel.LABELS[p.label] + "," + p.score + "," + p.margin);
                    for (double f : row.features) fixtures.print("," + f);
                    fixtures.println();
                    if (++written == 10) break;
                }
            }
        }
        try (PrintWriter images = new PrintWriter(Files.newBufferedWriter(output.resolve("reference_images.tsv")))) {
            images.println("label\tid\tgroup\tsplit");
            for (int label = 0; label < 6; label++) {
                for (Row row : test) {
                    ForestModel.Prediction prediction = model.predict(row.features);
                    if (row.label == label && (label == 5 ? !model.accepts(prediction)
                            : model.accepts(prediction) && prediction.label == label)) {
                        images.println(ForestModel.LABELS[label] + "\t" + row.id + "\t" + row.group + "\t" + row.split);
                        break;
                    }
                }
            }
        }
        appendPipelineReport(input, output, rows, model);
        System.out.println(Files.readString(output.resolve("evaluation.md")));
    }

    private static void appendPipelineReport(Path input, Path output, List<Row> rows, ForestModel model) throws Exception {
        Path manifest = input.resolveSibling("vowel-dataset").resolve("manifest.tsv");
        Path report = output.resolve("evaluation.md");
        if (!Files.isRegularFile(manifest)) {
            Files.writeString(report, "\nWhole-image pipeline evaluation unavailable: the original preparation manifest was not supplied alongside the CSV. Classifier recall excludes missed detections.\n", StandardOpenOption.APPEND);
            return;
        }
        Map<String, Row> detected = new HashMap<>();
        for (Row row : rows) detected.put(row.id, row);
        Metrics validation = new Metrics(model.minimumScore, model.minimumMargin), test = new Metrics(model.minimumScore, model.minimumMargin);
        int[] total = new int[26], valid = new int[26];
        try (BufferedReader reader = Files.newBufferedReader(manifest)) {
            reader.readLine(); String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split("\t");
                if (fields.length != 6) throw new IOException("Invalid preparation manifest");
                int letter = fields[1].charAt(0) - 'A'; total[letter]++;
                Row row = detected.get(fields[0]);
                int prediction = 5;
                if (row != null) {
                    if (!row.group.equals(fields[2]) || !row.split.equals(fields[3])) throw new IOException("CSV and preparation groups differ");
                    valid[letter]++;
                    ForestModel.Prediction p = model.predict(row.features);
                    if (model.accepts(p)) prediction = p.label;
                }
                if (fields[3].equals("validation")) validation.matrix[label(fields[1])][prediction]++;
                if (fields[3].equals("test")) test.matrix[label(fields[1])][prediction]++;
            }
        }
        String original = Files.readString(report);
        original = original.replace("\nTargets:", "\nConditional classifier targets:");
        if (!original.contains("Classifier tables are conditional")) {
            original = original.replace("# Vowel recognition evaluation\n", "# Vowel recognition evaluation\n\n"
                    + "Classifier tables are conditional on successful single-hand landmark extraction and valid geometry. Whole-image pipeline evaluation, including missed detections, is reported separately below.\n");
        }
        String marker = "\n## Whole-image pipeline evaluation";
        int existing = original.indexOf(marker);
        if (existing >= 0) original = original.substring(0, existing);
        Files.writeString(report, original);
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(report, StandardOpenOption.APPEND))) {
            out.println(marker + "\n");
            out.println("All original held-out images are included here. Images with no usable single-hand landmarks count as Unknown, rather than being silently dropped. Thresholds remain those chosen using validation classifier results; this audit does not retrain or tune the model.\n");
            out.println("Vowel extraction coverage across all original splits:\n\n| Letter | Usable landmarks | Original images | Coverage |\n|---|---:|---:|---:|");
            for (String vowel : new String[]{"A", "E", "I", "O", "U"}) {
                int i = vowel.charAt(0) - 'A';
                out.printf("| %s | %d | %d | %.2f%% |%n", vowel, valid[i], total[i], 100.0 * valid[i] / Math.max(1, total[i]));
            }
            out.println("\n### Whole-image validation\n"); validation.write(out);
            out.println("\n### Whole-image test\n"); test.write(out);
            out.println("\nWhole-image test targets (precision >=90%, recall >=80% per vowel): " + (test.targets() ? "PASS" : "NOT MET") + ".");
            for (int i = 0; i < 5; i++) if (test.precision(i) < .9 || test.recall(i) < .8) {
                out.printf("- %s misses the target: precision %.2f%%, recall %.2f%%. Detector misses are included in recall.%n", ForestModel.LABELS[i], test.precision(i) * 100, test.recall(i) * 100);
            }
            out.println("\nThese are still public-image results, not measurements of live-phone stabilization, motion, or unseen signers.");
        }
        Properties properties = new Properties();
        Path metadata = output.resolve("vowel_model.properties");
        try (Reader reader = Files.newBufferedReader(metadata)) { properties.load(reader); }
        // Keep the meaning explicit: overall success includes the detector, not just the classifier.
        properties.remove("test.targetsMet");
        properties.setProperty("test.classifierTargetsMet", Boolean.toString(evaluate(model, split(rows, "test")).targets()));
        properties.setProperty("test.pipelineTargetsMet", Boolean.toString(test.targets()));
        try (Writer writer = Files.newBufferedWriter(metadata)) { properties.store(writer, "Offline ASL vowel recognition model"); }
    }

    private static ArrayExample<Label> example(int label, double[] features) {
        return new ArrayExample<>(new Label(ForestModel.LABELS[label]), FEATURES, features);
    }

    private static List<Row> split(List<Row> rows, String split) {
        List<Row> selected = new ArrayList<>();
        for (Row row : rows) if (row.split.equals(split)) selected.add(row);
        return selected;
    }

    private static List<Row> withMirrors(List<Row> rows) {
        List<Row> result = new ArrayList<>();
        for (Row row : rows) {
            result.add(row);
            Row mirror = new Row();
            mirror.id = row.id + "-reflected"; mirror.label = row.label; mirror.letter = row.letter;
            mirror.group = row.group; mirror.split = row.split;
            mirror.features = LandmarkFeatures.mirror(row.features); result.add(mirror);
        }
        return result;
    }

    private static int[] counts(List<Row> rows) {
        int[] counts = new int[6];
        for (Row row : rows) counts[row.label]++;
        return counts;
    }

    private static List<Row> balance(List<Row> rows) {
        int[] counts = counts(rows);
        int cap = Arrays.stream(counts).min().orElse(0);
        if (cap < 100) throw new IllegalArgumentException("Need >=100 valid training images per class: " + Arrays.toString(counts));
        // Round-robin negative letters instead of allowing one letter to dominate Other.
        Map<String, List<Row>> byLetter = new TreeMap<>();
        for (Row row : rows) byLetter.computeIfAbsent(row.letter, ignored -> new ArrayList<>()).add(row);
        for (List<Row> list : byLetter.values()) list.sort(Comparator.comparing(r -> DatasetPreparer.sha(("470:" + r.id).getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        int[] used = new int[6];
        List<Row> result = new ArrayList<>();
        for (int offset = 0; ; offset++) {
            boolean added = false;
            for (List<Row> letter : byLetter.values()) {
                if (offset < letter.size()) {
                    Row row = letter.get(offset);
                    if (used[row.label] < cap) { result.add(row); used[row.label]++; added = true; }
                }
            }
            if (!added) break;
        }
        return result;
    }

    private static ForestModel export(EnsembleModel<Label> model) {
        List<Model<Label>> members = model.getModels();
        ForestModel.Node[][] trees = new ForestModel.Node[members.size()][];
        for (int t = 0; t < trees.length; t++) {
            TreeModel<Label> tree = (TreeModel<Label>) members.get(t);
            List<ForestModel.Node> nodes = new ArrayList<>();
            flatten(tree.getRoot(), tree, nodes);
            trees[t] = nodes.toArray(new ForestModel.Node[0]);
        }
        return new ForestModel(trees, .5, .05);
    }

    private static int flatten(Node<Label> node, TreeModel<Label> tree, List<ForestModel.Node> nodes) {
        int index = nodes.size(); nodes.add(null);
        if (node.isLeaf()) {
            String value = ((LeafNode<Label>) node).getOutput().getLabel();
            int label = Arrays.asList(ForestModel.LABELS).indexOf(value);
            if (label < 0) throw new IllegalArgumentException("Unexpected leaf label");
            nodes.set(index, new ForestModel.Node(-1, 0, -1, -1, label));
        } else {
            SplitNode<Label> split = (SplitNode<Label>) node;
            String feature = tree.getFeatureIDMap().get(split.getFeatureID()).getName();
            int featureIndex = Arrays.asList(FEATURES).indexOf(feature);
            int left = flatten(split.getLessThanOrEqual(), tree, nodes);
            int right = flatten(split.getGreaterThan(), tree, nodes);
            nodes.set(index, new ForestModel.Node(featureIndex, split.splitValue(), left, right, -1));
        }
        return index;
    }

    private static int verifyExport(EnsembleModel<Label> trained, ForestModel exported, List<Row> rows) {
        for (Row row : rows) {
            // Compare the winning score/margin, allowing different tied winners (ties are rejected).
            Prediction<Label> original = trained.predict(example(row.label, row.features));
            ForestModel.Prediction actual = exported.predict(row.features);
            double[] actualScores = actual.scores();
            for (int i = 0; i < ForestModel.LABELS.length; i++) {
                if (Math.abs(original.getOutputScores().get(ForestModel.LABELS[i]).getScore() - actualScores[i]) > 1e-6) {
                    throw new IllegalStateException("Exported vote distribution differs from Tribuo: " + row.id);
                }
            }
            List<Double> scores = new ArrayList<>();
            for (String label : ForestModel.LABELS) scores.add(original.getOutputScores().get(label).getScore());
            scores.sort(Comparator.reverseOrder());
            if (Math.abs(scores.get(0) - actual.score) > 1e-6 || Math.abs(scores.get(0) - scores.get(1) - actual.margin) > 1e-6
                    || (actual.margin > 0 && !original.getOutput().getLabel().equals(ForestModel.LABELS[actual.label]))) {
                throw new IllegalStateException("Exported prediction differs from Tribuo: " + row.id);
            }
        }
        return rows.size();
    }

    private static Metrics evaluate(ForestModel model, List<Row> rows) {
        Metrics metrics = new Metrics(model.minimumScore, model.minimumMargin);
        for (Row row : rows) {
            ForestModel.Prediction prediction = model.predict(row.features);
            int output = model.accepts(prediction) ? prediction.label : 5;
            metrics.matrix[row.label][output]++;
        }
        return metrics;
    }

    private static final class Metrics {
        final int[][] matrix = new int[6][6];
        final double score, margin;
        Metrics(double score, double margin) { this.score = score; this.margin = margin; }
        int actual(int label) { return Arrays.stream(matrix[label]).sum(); }
        int predicted(int label) { int total = 0; for (int[] row : matrix) total += row[label]; return total; }
        double precision(int label) { return matrix[label][label] / (double) Math.max(1, predicted(label)); }
        double recall(int label) { return matrix[label][label] / (double) Math.max(1, actual(label)); }
        double f1() {
            double total = 0;
            for (int i = 0; i < 5; i++) {
                double p = precision(i), r = recall(i);
                total += p + r == 0 ? 0 : 2 * p * r / (p + r);
            }
            return total / 5;
        }
        boolean targets() { for (int i = 0; i < 5; i++) if (precision(i) < .9 || recall(i) < .8) return false; return true; }
        boolean betterThan(Metrics other) {
            if (targets() != other.targets()) return targets();
            if (Math.abs(f1() - other.f1()) > 1e-9) return f1() > other.f1();
            return score > other.score || (score == other.score && margin > other.margin);
        }
        void write(PrintWriter out) {
            out.println("| Letter | Precision | Recall | Test images |\n|---|---:|---:|---:|");
            for (int i = 0; i < 5; i++) out.printf("| %s | %.2f%% | %.2f%% | %d |%n", ForestModel.LABELS[i], precision(i) * 100, recall(i) * 100, actual(i));
            int rejected = 0, total = 0, vowelsRejected = 0, vowelCount = 0;
            for (int i = 0; i < 6; i++) { rejected += matrix[i][5]; total += actual(i); if (i < 5) { vowelsRejected += matrix[i][5]; vowelCount += actual(i); } }
            out.printf("%nVowel macro F1: %.4f. Overall rejection: %.2f%%. Vowel rejection: %.2f%%. Other falsely accepted as a vowel: %.2f%% (%d/%d).%n%n",
                    f1(), 100.0 * rejected / Math.max(1, total), 100.0 * vowelsRejected / Math.max(1, vowelCount),
                    100.0 * (actual(5) - matrix[5][5]) / Math.max(1, actual(5)), actual(5) - matrix[5][5], actual(5));
            out.println("Confusion matrix: rows=actual A/E/I/O/U/Other; columns=displayed A/E/I/O/U/Unknown.\n\n```text");
            for (int[] row : matrix) out.println(Arrays.toString(row));
            out.println("```");
        }
    }
}
