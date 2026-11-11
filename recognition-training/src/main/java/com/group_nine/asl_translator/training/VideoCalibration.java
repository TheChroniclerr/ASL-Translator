package com.group_nine.asl_translator.training;

import com.group_nine.asl_translator.recognition.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Calibrates the fixed forest against repeated validation images in VIDEO mode. */
final class VideoCalibration {
    private static final class Frame { float[] points; int width, height, count; }
    private static final class Sequence {
        String id;
        int label;
        final List<Frame> frames = new ArrayList<>();
    }

    static ForestModel calibrate(ForestModel forest, Path csv, Path report) throws Exception {
        List<Sequence> sequences = load(csv);
        Metrics best = null;
        ForestModel selected = null;
        for (double score : new double[]{.50, .55, .60, .65, .70, .75, .80, .85, .90, .95}) {
            for (double margin : new double[]{.05, .10, .15, .20, .25, .30}) {
                ForestModel candidate = forest.withThresholds(score, margin);
                Metrics current = evaluate(candidate, sequences);
                if (best == null || current.betterThan(best)) { best = current; selected = candidate; }
            }
        }
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(report))) {
            out.println("# Video-mode validation calibration\n");
            out.println("50 original validation images per static letter, selected by SHA-256 of seed 470 and image ID. No training/test images are used. Each image is repeated for eight VIDEO-mode frames, 100 ms apart; a blank frame resets tracking before each sequence. This exercises tracked-frame geometry, not real hand movement.\n");
            out.println("Threshold evaluation uses the same Java VowelClassifier and 500 ms hold as the app. An incorrect stabilized vowel at any frame counts as an error; otherwise the final result is scored. Missing detections remain Unknown.\n");
            out.println("Dataset sequences: " + sequences.size() + ". CSV SHA-256: `" + DatasetPreparer.fileSha(csv) + "`.\n");
            out.println("Selected minimum tree-vote fraction: " + selected.minimumScore + "; winning margin: " + selected.minimumMargin + ".\n");
            out.println("Select candidates meeting all per-vowel precision >=90% and recall >=80% targets if available; otherwise maximize vowel macro F1. Ties favor lower thresholds to avoid rejecting small tracked-frame variations.\n");
            out.println("| Letter | Precision | Recall | Sequences |\n|---|---:|---:|---:|");
            for (int i = 0; i < 5; i++) out.printf(Locale.ROOT, "| %s | %.2f%% | %.2f%% | %d |%n", ForestModel.LABELS[i], best.precision(i) * 100, best.recall(i) * 100, best.actual(i));
            out.println("\nMatrix rows=A/E/I/O/U/Other; columns=displayed A/E/I/O/U/Unknown.\n\n```text");
            for (int[] row : best.matrix) out.println(Arrays.toString(row));
            out.println("```\n\nAll-vowel targets: " + (best.targets() ? "PASS" : "NOT MET") + ".\n");
            out.printf(Locale.ROOT, "Other falsely stabilized as a vowel: %.2f%% (%d/%d).%n", 100.0 * (best.actual(5) - best.matrix[5][5]) / best.actual(5), best.actual(5) - best.matrix[5][5], best.actual(5));
        }
        return selected;
    }

    private static List<Sequence> load(Path csv) throws Exception {
        Map<String, String[]> allowed = new HashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(csv.resolveSibling("vowel-dataset").resolve("manifest.tsv"))) {
            reader.readLine(); String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split("\t");
                if (fields.length == 6 && fields[3].equals("validation")) allowed.put(fields[0], fields);
            }
        }
        Map<String, Sequence> sequences = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(csv)) {
            String header = reader.readLine();
            if (header == null || !header.startsWith("id,letter,group,split,frame,width,height,handCount,p0,")) throw new IOException("Unexpected video CSV header");
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(",");
                if (fields.length != 71 || !fields[3].equals("validation")) throw new IOException("Video calibration accepts validation frames only");
                String[] original = allowed.get(fields[0]);
                if (original == null || !original[1].equals(fields[1]) || !original[2].equals(fields[2])) throw new IOException("Video image not in original validation manifest");
                Sequence sequence = sequences.computeIfAbsent(fields[0], ignored -> {
                    Sequence value = new Sequence(); value.id = fields[0];
                    value.label = Arrays.asList(ForestModel.LABELS).indexOf(fields[1]);
                    if (value.label < 0) value.label = 5;
                    return value;
                });
                if (Integer.parseInt(fields[4]) != sequence.frames.size()) throw new IOException("Video frame order differs");
                Frame frame = new Frame(); frame.width = Integer.parseInt(fields[5]); frame.height = Integer.parseInt(fields[6]);
                frame.count = Integer.parseInt(fields[7]);
                if (frame.count < 0 || frame.count > 2) throw new IOException("Invalid hand count");
                frame.points = new float[63];
                for (int i = 0; i < 63; i++) frame.points[i] = Float.parseFloat(fields[8 + i]);
                sequence.frames.add(frame);
            }
        }
        if (sequences.size() != 1200) throw new IOException("Expected 1200 validation sequences");
        for (Sequence sequence : sequences.values()) if (sequence.frames.size() != 8) throw new IOException("Expected eight frames per validation image");
        return new ArrayList<>(sequences.values());
    }

    private static Metrics evaluate(ForestModel forest, List<Sequence> sequences) {
        Metrics metrics = new Metrics(forest.minimumScore, forest.minimumMargin);
        for (Sequence sequence : sequences) {
            VowelClassifier classifier = new VowelClassifier(forest);
            int finalLabel = 5, wrongStable = -1;
            for (int f = 0; f < sequence.frames.size(); f++) {
                Frame frame = sequence.frames.get(f);
                List<float[]> hands = frame.count == 0 ? Collections.emptyList() : frame.count == 1
                        ? Collections.singletonList(frame.points) : Arrays.asList(frame.points, frame.points);
                VowelRecognitionResult result = classifier.classify(hands, frame.width, frame.height, f * 100L);
                finalLabel = result.letter == null ? 5 : Arrays.asList(ForestModel.LABELS).indexOf(result.letter);
                if (finalLabel < 5 && finalLabel != sequence.label) wrongStable = finalLabel;
            }
            metrics.matrix[sequence.label][wrongStable >= 0 ? wrongStable : finalLabel]++;
        }
        return metrics;
    }

    private static final class Metrics {
        final int[][] matrix = new int[6][6];
        final double score, margin;
        Metrics(double score, double margin) { this.score = score; this.margin = margin; }
        int actual(int i) { return Arrays.stream(matrix[i]).sum(); }
        double precision(int i) { int total = 0; for (int[] row : matrix) total += row[i]; return matrix[i][i] / (double) Math.max(1, total); }
        double recall(int i) { return matrix[i][i] / (double) Math.max(1, actual(i)); }
        double f1() { double sum = 0; for (int i = 0; i < 5; i++) { double p = precision(i), r = recall(i); sum += p + r == 0 ? 0 : 2 * p * r / (p + r); } return sum / 5; }
        boolean targets() { for (int i = 0; i < 5; i++) if (precision(i) < .9 || recall(i) < .8) return false; return true; }
        boolean betterThan(Metrics other) {
            if (targets() != other.targets()) return targets();
            if (Math.abs(f1() - other.f1()) > 1e-9) return f1() > other.f1();
            return score < other.score || (score == other.score && margin < other.margin);
        }
    }
}
