package com.group_nine.asl_translator.home;

import android.content.Context;
import android.util.AtomicFile;
import com.group_nine.asl_translator.recognition.ForestModel;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** All files live under noBackupFilesDir. Call disk operations on the collection I/O worker. */
final class SampleStore {
    static final int SCHEMA = 1;
    final File root;
    private final Properties model = new Properties();

    static final class Session {
        final String id, participant, split;
        Session(String id, String participant, String split) { this.id = id; this.participant = participant; this.split = split; }
    }
    static final class Clip {
        final String id = UUID.randomUUID().toString();
        final Session session;
        final String label, requestedHand;
        final List<Frame> frames = new ArrayList<>();
        Clip(Session session, String label, String requestedHand) {
            this.session = session; this.label = label; this.requestedHand = requestedHand;
        }
    }
    static final class Frame {
        final long elapsedMs;
        final HandTrackingResult result;
        final String status;
        Frame(long elapsedMs, HandTrackingResult result, String status) {
            this.elapsedMs = elapsedMs; this.result = result; this.status = status;
        }
    }

    SampleStore(Context context) throws IOException {
        root = new File(context.getNoBackupFilesDir(), "vowel-samples");
        try (InputStream input = context.getAssets().open("vowel_model.properties")) { model.load(input); }
    }

    Session create(String participant, String split) throws Exception {
        if (!participant.matches("[A-Za-z0-9_-]{1,24}") || !Arrays.asList("train", "validation", "test").contains(split)) {
            throw new IOException("Use a short alias containing letters, digits, underscore, or dash.");
        }
        Session session = new Session(UUID.randomUUID().toString(), participant, split);
        File directory = directory(session); ensure(directory);
        JSONObject data = new JSONObject().put("schemaVersion", SCHEMA).put("sessionId", session.id)
                .put("participant", participant).put("split", split).put("createdUtcMs", System.currentTimeMillis())
                .put("modelSha256", model.getProperty("model.sha256", "unknown"))
                .put("featureVersion", model.getProperty("features", "unknown"))
                .put("detector", "MediaPipe Hand Landmarker float16 v1; Tasks Vision 1.0.0; CPU; VIDEO; thresholds 0.5")
                .put("recording", "landmarks and diagnostics only; no images, video, or audio");
        atomicJson(new File(directory, "session.json"), data);
        return session;
    }

    Session load(String id) throws Exception {
        if (!id.matches("[a-f0-9-]{36}")) throw new IOException("Invalid session ID");
        JSONObject data = readJson(new File(new File(root, id), "session.json"));
        return new Session(id, data.getString("participant"), data.getString("split"));
    }

    Session latestSession() throws Exception {
        Session latest = null; long created = -1;
        File[] directories = root.listFiles(File::isDirectory);
        if (directories != null) for (File directory : directories) {
            File manifest = new File(directory, "session.json");
            if (!manifest.isFile()) continue;
            JSONObject metadata = readJson(manifest);
            if (metadata.getLong("createdUtcMs") > created) { created = metadata.getLong("createdUtcMs"); latest = load(directory.getName()); }
        }
        return latest;
    }

    int count(Session session, String label, String hand) throws Exception {
        int total = 0;
        File[] files = directory(session).listFiles((dir, name) -> name.endsWith(".json") && !name.equals("session.json"));
        if (files != null) for (File file : files) {
            JSONObject data = readJson(file);
            if (data.optBoolean("complete") && label.equals(data.getString("label")) && hand.equals(data.getString("requestedHand"))) total++;
        }
        return total;
    }

    void begin(Clip clip) throws Exception { finish(clip, false, "capture interrupted before completion"); }

    void finish(Clip clip, boolean complete, String reason) throws Exception {
        File directory = directory(clip.session); ensure(directory);
        AtomicFile framesFile = new AtomicFile(new File(directory, clip.id + ".jsonl"));
        FileOutputStream output = framesFile.startWrite();
        try {
            for (Frame frame : clip.frames) {
                output.write((frameJson(frame).toString() + "\n").getBytes(StandardCharsets.UTF_8));
            }
            framesFile.finishWrite(output);
        } catch (Exception exception) { framesFile.failWrite(output); throw exception; }
        JSONObject metadata = new JSONObject().put("schemaVersion", SCHEMA).put("clipId", clip.id)
                .put("sessionId", clip.session.id).put("label", clip.label).put("requestedHand", clip.requestedHand)
                .put("complete", complete).put("reason", reason).put("frameCount", clip.frames.size())
                .put("durationMs", 2000).put("frames", clip.id + ".jsonl");
        atomicJson(new File(directory, clip.id + ".json"), metadata);
    }

    private JSONObject frameJson(Frame frame) throws Exception {
        JSONObject data = new JSONObject().put("elapsedMs", frame.elapsedMs).put("trackingStatus", frame.status);
        HandTrackingResult result = frame.result;
        JSONArray hands = new JSONArray();
        if (result != null) {
            data.put("timestampMs", result.timestampMs).put("bufferWidth", result.bufferWidth).put("bufferHeight", result.bufferHeight)
                    .put("rotationDegrees", result.rotationDegrees)
                    .put("width", result.rotationDegrees % 180 == 0 ? result.bufferWidth : result.bufferHeight)
                    .put("height", result.rotationDegrees % 180 == 0 ? result.bufferHeight : result.bufferWidth)
                    .put("recognitionState", result.recognition.state.name()).put("displayedLetter", result.recognition.letter == null ? JSONObject.NULL : result.recognition.letter);
            for (HandTrackingResult.Hand hand : result.hands) {
                JSONArray points = new JSONArray();
                for (HandTrackingResult.Landmark p : hand.landmarks) points.put(jsonFloat(p.x)).put(jsonFloat(p.y)).put(jsonFloat(p.z));
                hands.put(new JSONObject().put("handedness", hand.handedness).put("handednessScore", jsonFloat(hand.confidence)).put("landmarks", points));
            }
            if (result.modelPrediction != null) {
                JSONArray scores = new JSONArray();
                for (double score : result.modelPrediction.scores()) scores.put(score);
                data.put("scores", scores).put("rawLabel", ForestModel.LABELS[result.modelPrediction.label])
                        .put("margin", result.modelPrediction.margin);
            }
        }
        return data.put("hands", hands);
    }

    private static Object jsonFloat(float value) { return Float.isFinite(value) ? (Object) value : JSONObject.NULL; }

    void export(OutputStream destination) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(destination)) {
            zip.putNextEntry(new ZipEntry("collection.json"));
            zip.write(new JSONObject().put("schemaVersion", SCHEMA).put("kind", "ASL vowel landmark collection").toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            addFiles(zip, root, "sessions/");
        }
    }

    private void addFiles(ZipOutputStream zip, File directory, String prefix) throws IOException {
        File[] files = directory.listFiles();
        if (files == null) return;
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File file : files) {
            if (file.isDirectory()) addFiles(zip, file, prefix + file.getName() + "/");
            else if (file.getName().endsWith(".json") || file.getName().endsWith(".jsonl")) {
                zip.putNextEntry(new ZipEntry(prefix + file.getName()));
                try (InputStream input = new FileInputStream(file)) {
                    byte[] buffer = new byte[8192]; int n;
                    while ((n = input.read(buffer)) != -1) zip.write(buffer, 0, n);
                }
                zip.closeEntry();
            }
        }
    }

    void deleteAll() throws IOException { delete(root); }
    private void delete(File file) throws IOException {
        if (!file.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator) && !file.getCanonicalFile().equals(root.getCanonicalFile())) {
            throw new IOException("Invalid collection path");
        }
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        if (file.exists() && !file.delete()) throw new IOException("Unable to delete local samples");
    }
    private File directory(Session session) { return new File(root, session.id); }
    private static void ensure(File directory) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Unable to create sample storage");
    }
    private static JSONObject readJson(File file) throws Exception {
        try (InputStream input = new FileInputStream(file); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int n;
            while ((n = input.read(buffer)) != -1) bytes.write(buffer, 0, n);
            return new JSONObject(bytes.toString("UTF-8"));
        }
    }
    private static void atomicJson(File file, JSONObject data) throws Exception {
        AtomicFile atomic = new AtomicFile(file); FileOutputStream output = atomic.startWrite();
        try { output.write(data.toString().getBytes(StandardCharsets.UTF_8)); atomic.finishWrite(output); }
        catch (Exception exception) { atomic.failWrite(output); throw exception; }
    }
}
