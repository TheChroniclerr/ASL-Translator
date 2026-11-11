package com.group_nine.asl_translator.training;

import com.group_nine.asl_translator.recognition.*;
import org.json.*;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;

public class RefinementInputTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private static final String SESSION = "00000000-0000-0000-0000-000000000001";
    private static final String CLIP = "00000000-0000-0000-0000-000000000002";
    private boolean invalidCoordinates;
    private Path phone(boolean complete, boolean unsafe) throws Exception {
        Path path = folder.newFile().toPath();
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(path))) {
            entry(out, "collection.json", "{\"schemaVersion\":1}");
            entry(out, "sessions/" + SESSION + "/session.json", new JSONObject().put("schemaVersion", 1).put("sessionId", SESSION).put("participant", "P001").put("split", "train").toString());
            entry(out, "sessions/" + SESSION + "/" + CLIP + ".json", new JSONObject().put("schemaVersion", 1).put("sessionId", SESSION).put("clipId", CLIP)
                    .put("label", "O").put("requestedHand", "Left").put("complete", complete).put("frameCount", 1).put("frames", CLIP + ".jsonl").toString());
            JSONObject frame = new JSONObject().put("elapsedMs", 0).put("width", 480).put("height", 640);
            JSONArray hands = new JSONArray();
            if (invalidCoordinates) {
                JSONArray coordinates = new JSONArray(); for (int i = 0; i < 63; i++) coordinates.put(i == 0 ? JSONObject.NULL : .5);
                hands.put(new JSONObject().put("landmarks", coordinates));
            }
            entry(out, "sessions/" + SESSION + "/" + CLIP + ".jsonl", frame.put("hands", hands).toString());
            if (unsafe) entry(out, "../outside.json", "{}");
        }
        return path;
    }
    private void entry(ZipOutputStream out, String name, String text) throws IOException {
        out.putNextEntry(new ZipEntry(name)); out.write(text.getBytes(StandardCharsets.UTF_8)); out.closeEntry();
    }
    private Path prepare(boolean complete, boolean unsafe) throws Exception {
        Path csv = folder.newFile().toPath(), manifest = folder.newFile().toPath(), baseline = folder.newFile().toPath(), output = folder.getRoot().toPath().resolve("features.jsonl");
        Files.writeString(csv, "id,letter,group,split,width,height,p0,\n");
        Files.writeString(manifest, "id\tletter\tgroup\tsplit\tsha256\tdhash\nO_1.jpg\tO\tgroup\ttest\thash\t0\n");
        try (OutputStream stream = Files.newOutputStream(baseline)) {
            new ForestModel(new ForestModel.Node[][]{{new ForestModel.Node(-1, 0, -1, -1, 0)}}, .5, .05).write(stream);
        }
        RefinementTool.prepare(csv, manifest, phone(complete, unsafe), baseline, output); return output;
    }
    @Test public void missedPublicAndPhoneFramesRemainInFeatures() throws Exception {
        List<String> lines = Files.readAllLines(prepare(true, false)); assertEquals(3, lines.size());
        for (String line : lines.subList(1, 3)) {
            JSONObject record = new JSONObject(line); assertTrue(record.isNull("f63")); assertTrue(record.isNull("f93")); assertEquals(0, record.getInt("handCount"));
        }
        assertEquals("test", new JSONObject(lines.get(1)).getString("split"));
        assertEquals("train", new JSONObject(lines.get(2)).getString("split"));
    }
    @Test(expected = IOException.class) public void unsafeArchiveIsRejected() throws Exception { prepare(true, true); }
    @Test(expected = IOException.class) public void incompleteOnlyArchiveCannotTrain() throws Exception { prepare(false, false); }
    @Test public void badGeometryDoesNotBecomeTrainingData() {
        assertNull(RefinementTool.features(new float[63], 480, 640, LandmarkFeatures.GEOMETRY_VERSION));
    }
    @Test public void nullPhoneCoordinatesAreRetainedAsUnknown() throws Exception {
        invalidCoordinates = true;
        JSONObject row = new JSONObject(Files.readAllLines(prepare(true, false)).get(2));
        assertEquals(1, row.getInt("handCount")); assertTrue(row.isNull("f63")); assertTrue(row.isNull("f93"));
    }
}
