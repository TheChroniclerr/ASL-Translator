package com.group_nine.asl_translator.training;

import com.group_nine.asl_translator.recognition.ForestModel;
import java.io.IOException;
import java.nio.file.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class VideoCalibrationTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private void calibration(String split, String group, int frame, String expected) throws Exception {
        Path root = temporary.getRoot().toPath(), prepared = Files.createDirectory(root.resolve("vowel-dataset"));
        Files.writeString(prepared.resolve("manifest.tsv"), "id\tletter\tgroup\tsplit\tsha256\tdhash\nA_0.jpg\tA\tvalid-group\tvalidation\tunused\t0\n");
        StringBuilder header = new StringBuilder("id,letter,group,split,frame,width,height,handCount");
        StringBuilder row = new StringBuilder("A_0.jpg,A," + group + "," + split + "," + frame + ",296,296,0");
        for (int i = 0; i < 63; i++) { header.append(",p").append(i); row.append(",0"); }
        Path csv = root.resolve("video-validation.csv"); Files.writeString(csv, header + "\n" + row + "\n");
        ForestModel forest = new ForestModel(new ForestModel.Node[][]{{new ForestModel.Node(-1, 0, -1, -1, 0)}}, .8, .2);
        try { VideoCalibration.calibrate(forest, csv, root.resolve("report.md")); fail("Expected invalid calibration data to be rejected"); }
        catch (IOException exception) { assertTrue(exception.getMessage(), exception.getMessage().contains(expected)); }
    }

    @Test public void neverAcceptsTestSplitFramesForCalibration() throws Exception { calibration("test", "valid-group", 0, "validation frames only"); }
    @Test public void checksTheOriginalValidationGroup() throws Exception { calibration("validation", "wrong-group", 0, "original validation manifest"); }
    @Test public void rejectsMissingOrReorderedFrames() throws Exception { calibration("validation", "valid-group", 2, "frame order"); }
}
