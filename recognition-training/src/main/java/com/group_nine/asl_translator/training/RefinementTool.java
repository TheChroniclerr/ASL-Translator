package com.group_nine.asl_translator.training;

import com.group_nine.asl_translator.recognition.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Java is the single source of feature preprocessing for desktop training and Android. */
public final class RefinementTool {
    private RefinementTool() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 6 && args[0].equals("prepare")) {
            prepare(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]), Path.of(args[4]), Path.of(args[5]));
        } else if (args.length == 4 && args[0].equals("verify")) {
            verify(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]));
        } else throw new IllegalArgumentException("refine prepare <public-landmarks.csv> <manifest.tsv> <phone.zip> <baseline.bin> <features.jsonl> OR refine verify <model.bin> <features.jsonl> <python-predictions.jsonl>");
    }

    static JSONArray features(float[] xyz, int width, int height, String version) {
        if (xyz == null) return null;
        try {
            JSONArray result = new JSONArray();
            for (double feature : LandmarkFeatures.extract(xyz, width, height, version)) result.put(feature);
            return result;
        } catch (IllegalArgumentException exception) { return null; }
    }

    private static int category(String letter) {
        for (int i = 0; i < 5; i++) if (ForestModel.LABELS[i].equals(letter)) return i;
        return 5;
    }

    private static JSONObject row(String id, String letter, String group, String split, String domain,
                                  String clip, long elapsed, int hands, float[] xyz, int width, int height, ForestModel baseline) {
        if (!Arrays.asList("train", "validation", "test").contains(split)) throw new IllegalArgumentException("Invalid split");
        JSONObject row = new JSONObject().put("id", id).put("letter", letter).put("category", category(letter))
                .put("group", group).put("split", split).put("domain", domain).put("clip", clip)
                .put("elapsedMs", elapsed).put("handCount", hands);
        JSONArray f63 = features(xyz, width, height, LandmarkFeatures.VERSION), f93 = features(xyz, width, height, LandmarkFeatures.GEOMETRY_VERSION);
        row.put("f63", f63 == null ? JSONObject.NULL : f63).put("f93", f93 == null ? JSONObject.NULL : f93);
        if (f63 != null) {
            double[] values = new double[63]; for (int i = 0; i < values.length; i++) values[i] = f63.getDouble(i);
            row.put("baselineScores", new JSONArray(baseline.predict(values).scores()));
        } else row.put("baselineScores", JSONObject.NULL);
        return row;
    }

    static void prepare(Path landmarks, Path manifest, Path phoneZip, Path baselineFile, Path output) throws Exception {
        ForestModel baseline;
        try (InputStream input = Files.newInputStream(baselineFile)) { baseline = ForestModel.read(input); }
        Map<String, String[]> publicLandmarks = new HashMap<>();
        try (BufferedReader input = Files.newBufferedReader(landmarks)) {
            String header = input.readLine();
            if (header == null || !header.startsWith("id,letter,group,split,width,height,p0,")) throw new IOException("Unexpected public landmark schema");
            String line;
            while ((line = input.readLine()) != null) {
                String[] fields = line.split(",", -1);
                if (fields.length != 69 || publicLandmarks.put(fields[0], fields) != null) throw new IOException("Invalid or duplicate public row");
            }
        }
        Path parent = output.toAbsolutePath().getParent(); Files.createDirectories(parent);
        Path temporary = output.resolveSibling(output.getFileName() + ".partial");
        int publicRows = 0, phoneRows = 0, completeClips = 0, incompleteClips = 0;
        Set<String> imageIds = new HashSet<>();
        Map<String, String> splits = new HashMap<>();
        try (BufferedWriter writer = Files.newBufferedWriter(temporary); ZipFile zip = new ZipFile(phoneZip.toFile())) {
            JSONObject header = new JSONObject().put("kind", "vowel-refinement-features").put("schemaVersion", 1)
                    .put("baselineScore", baseline.minimumScore).put("baselineMargin", baseline.minimumMargin)
                    .put("baselineSha256", DatasetPreparer.fileSha(baselineFile)).put("phoneZipSha256", DatasetPreparer.fileSha(phoneZip))
                    .put("publicLandmarksSha256", DatasetPreparer.fileSha(landmarks)).put("manifestSha256", DatasetPreparer.fileSha(manifest));
            writer.write(header.toString()); writer.newLine();
            try (BufferedReader input = Files.newBufferedReader(manifest)) {
                if (!"id\tletter\tgroup\tsplit\tsha256\tdhash".equals(input.readLine())) throw new IOException("Unexpected public manifest");
                String line;
                while ((line = input.readLine()) != null) {
                    String[] fields = line.split("\t", -1);
                    if (fields.length != 6 || !fields[1].matches("[A-Z]") || fields[1].matches("[JZ]") || !imageIds.add(fields[0])) throw new IOException("Invalid public manifest row");
                    enforceSplit(splits, "public:" + fields[2], fields[3]);
                    String[] found = publicLandmarks.remove(fields[0]); float[] points = null; int width = 0, height = 0;
                    if (found != null) {
                        if (!fields[1].equals(found[1]) || !fields[2].equals(found[2]) || !fields[3].equals(found[3])) throw new IOException("Manifest/landmark identity mismatch");
                        width = Integer.parseInt(found[4]); height = Integer.parseInt(found[5]); points = new float[63];
                        for (int i = 0; i < 63; i++) points[i] = Float.parseFloat(found[6 + i]);
                    }
                    writer.write(row("public:" + fields[0], fields[1], "public:" + fields[2], fields[3], "public", "public:" + fields[0], 0, points == null ? 0 : 1, points, width, height, baseline).toString()); writer.newLine(); publicRows++;
                }
            }
            if (!publicLandmarks.isEmpty()) throw new IOException("Landmarks absent from public manifest");
            Set<String> names = new HashSet<>();
            List<? extends ZipEntry> entries = Collections.list(zip.entries());
            for (ZipEntry entry : entries) {
                String name = entry.getName();
                if (!names.add(name) || name.contains("..") || name.startsWith("/") || name.contains("\\") || name.contains(":")) throw new IOException("Unsafe or repeated archive entry");
            }
            JSONObject collection = json(zip, "collection.json");
            if (collection.getInt("schemaVersion") != 1) throw new IOException("Unsupported collection schema");
            entries.sort(Comparator.comparing(ZipEntry::getName));
            for (ZipEntry entry : entries) {
                if (!entry.getName().matches("sessions/[a-f0-9-]{36}/session\\.json")) continue;
                JSONObject session = json(zip, entry.getName());
                String sessionId = session.getString("sessionId"), split = session.getString("split"), participant = session.getString("participant");
                String prefix = "sessions/" + sessionId + "/";
                if (session.getInt("schemaVersion") != 1 || !entry.getName().equals(prefix + "session.json") || !participant.matches("[A-Za-z0-9_-]{1,24}")) throw new IOException("Invalid session identity");
                enforceSplit(splits, sessionId, split);
                for (ZipEntry clipEntry : entries) {
                    String relative = clipEntry.getName().startsWith(prefix) ? clipEntry.getName().substring(prefix.length()) : "";
                    if (!relative.matches("[a-f0-9-]{36}\\.json")) continue;
                    JSONObject clip = json(zip, clipEntry.getName());
                    if (!clip.getString("sessionId").equals(sessionId) || !relative.equals(clip.getString("clipId") + ".json") || clip.getInt("schemaVersion") != 1) throw new IOException("Clip/session mismatch");
                    if (!clip.getBoolean("complete")) { incompleteClips++; continue; }
                    String letter = clip.getString("label"), id = sessionId + ":" + clip.getString("clipId");
                    if (!letter.matches("[AEIOUCSFHV]") || !Arrays.asList("Left", "Right").contains(clip.getString("requestedHand")) || !clip.getString("frames").equals(clip.getString("clipId") + ".jsonl")) throw new IOException("Invalid clip metadata");
                    ZipEntry frames = zip.getEntry(prefix + clip.getString("frames"));
                    if (frames == null) throw new IOException("Missing clip frames");
                    int index = 0; long previous = -1;
                    try (BufferedReader input = new BufferedReader(new InputStreamReader(zip.getInputStream(frames), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = input.readLine()) != null) {
                            JSONObject frame = new JSONObject(line); long elapsed = frame.getLong("elapsedMs");
                            if (elapsed < 0 || elapsed >= 2000 || elapsed <= previous) throw new IOException("Invalid frame timing");
                            previous = elapsed;
                            JSONArray hands = frame.getJSONArray("hands"); float[] points = null;
                            if (hands.length() == 1) {
                                JSONArray coordinates = hands.getJSONObject(0).getJSONArray("landmarks");
                                if (coordinates.length() != 63) throw new IOException("Expected 21 landmarks");
                                points = new float[63];
                                for (int p = 0; p < 63; p++) {
                                    if (coordinates.isNull(p)) { points = null; break; }
                                    points[p] = (float) coordinates.getDouble(p);
                                }
                            }
                            JSONObject record = row("phone:" + id + ":" + index, letter, sessionId, split, "phone", "phone:" + id, elapsed, hands.length(), points, frame.optInt("width"), frame.optInt("height"), baseline)
                                    .put("participant", participant).put("requestedHand", clip.getString("requestedHand"))
                                    .put("timestampMs", frame.optLong("timestampMs", elapsed));
                            writer.write(record.toString()); writer.newLine(); index++; phoneRows++;
                        }
                    }
                    if (index != clip.getInt("frameCount") || index == 0) throw new IOException("Incomplete/corrupt frame count");
                    completeClips++;
                }
            }
            if (completeClips == 0) throw new IOException("No complete phone clips in ZIP");
        }
        Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Prepared " + publicRows + " public images and " + phoneRows + " phone frames in " + completeClips + " complete clips. Incomplete clips excluded: " + incompleteClips);
    }

    private static void enforceSplit(Map<String, String> splits, String group, String split) throws IOException {
        if (!Arrays.asList("train", "validation", "test").contains(split)) throw new IOException("Invalid split");
        String prior = splits.putIfAbsent(group, split);
        if (prior != null && !prior.equals(split)) throw new IOException("Group leaked across splits");
    }
    private static JSONObject json(ZipFile zip, String name) throws Exception {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null || entry.getSize() > 1024 * 1024) throw new IOException("Missing/oversized metadata: " + name);
        try (InputStream input = zip.getInputStream(entry)) { return new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8)); }
    }

    static void verify(Path modelPath, Path featurePath, Path predictionsPath) throws Exception {
        ForestModel model; try (InputStream input = Files.newInputStream(modelPath)) { model = ForestModel.read(input); }
        String field = model.featureCount == 63 ? "f63" : "f93";
        int checked = 0, replayed = 0;
        Map<String, VowelClassifier> classifiers = new HashMap<>();
        try (BufferedReader data = Files.newBufferedReader(featurePath); BufferedReader expected = Files.newBufferedReader(predictionsPath)) {
            data.readLine(); String line, predicted;
            while ((line = data.readLine()) != null) {
                JSONObject row = new JSONObject(line); predicted = expected.readLine();
                if (predicted == null) throw new IOException("Prediction rows missing");
                JSONObject gold = new JSONObject(predicted);
                if (!row.getString("id").equals(gold.getString("id"))) throw new IOException("Prediction identity mismatch");
                double[] features = null;
                if (row.isNull(field)) {
                    if (!gold.isNull("scores")) throw new IOException("Invalid input was predicted");
                } else {
                    JSONArray values = row.getJSONArray(field), scores = gold.getJSONArray("scores");
                    features = new double[model.featureCount];
                    for (int i = 0; i < features.length; i++) features[i] = values.getDouble(i);
                    double[] actual = model.predict(features).scores();
                    for (int i = 0; i < actual.length; i++) if (!Double.isFinite(scores.getDouble(i)) || Math.abs(actual[i] - scores.getDouble(i)) > 1e-6) throw new IOException("Python/Java prediction mismatch: " + row.getString("id"));
                    checked++;
                }
                if (row.getString("domain").equals("phone")) {
                    VowelClassifier classifier = classifiers.computeIfAbsent(row.getString("clip"), key -> new VowelClassifier(model));
                    VowelRecognitionResult result = classifier.classifyFeatures(features, row.getInt("handCount"), row.optLong("timestampMs", row.getLong("elapsedMs")));
                    int displayed = result.letter == null ? -1 : category(result.letter);
                    if (displayed != gold.getInt("displayedLabel")) throw new IOException("Python/Java stabilization mismatch: " + row.getString("id"));
                    replayed++;
                }
            }
            if (expected.readLine() != null) throw new IOException("Extra prediction rows");
        }
        JSONObject verification = new JSONObject().put("schemaVersion", 1).put("modelSha256", DatasetPreparer.fileSha(modelPath))
                .put("featureDataSha256", DatasetPreparer.fileSha(featurePath)).put("predictionsSha256", DatasetPreparer.fileSha(predictionsPath))
                .put("scoreRows", checked).put("replayedPhoneFrames", replayed);
        Files.writeString(predictionsPath.resolveSibling("java-verification.json"), verification.toString(2));
        System.out.println("Verified all six Java/Python scores on " + checked + " valid examples within 1e-6 and displayed letters on " + replayed + " phone frames.");
    }
}
