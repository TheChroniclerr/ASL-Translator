package com.group_nine.asl_translator.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.io.*;
import java.util.*;
import org.junit.Test;
import org.junit.Assume;
import org.junit.runner.RunWith;

/** Opt-in offline extraction. Never opens a camera, downloads data, or runs in ordinary tests. */
@RunWith(AndroidJUnit4.class)
public class DatasetLandmarkExportTest {
    /** Validation only: records the detector's actual first-frame and tracked-frame geometry. */
    @Test public void exportVideoValidation() throws Exception {
        Assume.assumeTrue("Explicit -e calibrateVideo true is required",
                "true".equals(InstrumentationRegistry.getArguments().getString("calibrateVideo")));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File directory = new File(context.getExternalFilesDir(null), "vowel-dataset");
        Map<String, List<String[]>> letters = new TreeMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(new File(directory, "manifest.tsv")))) {
            reader.readLine(); String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split("\t");
                if (fields.length != 6) throw new IOException("Invalid manifest");
                if (fields[3].equals("validation")) letters.computeIfAbsent(fields[1], ignored -> new ArrayList<>()).add(fields);
            }
        }
        List<String[]> selected = new ArrayList<>();
        for (List<String[]> images : letters.values()) {
            images.sort(Comparator.comparing(fields -> hashBytes(("470:" + fields[0]).getBytes(java.nio.charset.StandardCharsets.UTF_8))));
            selected.addAll(images.subList(0, Math.min(50, images.size())));
        }
        if (selected.size() != 1200) throw new IOException("Expected 50 validation images for each static letter");
        HandLandmarker.HandLandmarkerOptions options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("hand_landmarker.task").setDelegate(Delegate.CPU).build())
                .setRunningMode(RunningMode.VIDEO).setNumHands(2).setMinHandDetectionConfidence(.5f)
                .setMinHandPresenceConfidence(.5f).setMinTrackingConfidence(.5f).build();
        Bitmap blank = Bitmap.createBitmap(296, 296, Bitmap.Config.ARGB_8888);
        long timestamp = 0; int processed = 0;
        try (HandLandmarker model = HandLandmarker.createFromOptions(context, options);
             PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(new File(directory, "video-validation.csv"))))) {
            out.print("id,letter,group,split,frame,width,height,handCount");
            for (int i = 0; i < 63; i++) out.print(",p" + i);
            out.println();
            for (String[] fields : selected) {
                // A blank frame clears the previous ROI without carrying state between sequences.
                MPImage resetImage = new BitmapImageBuilder(blank.copy(Bitmap.Config.ARGB_8888, false)).build();
                try { model.detectForVideo(resetImage, timestamp += 100); } finally { resetImage.close(); }
                Bitmap source = BitmapFactory.decodeFile(new File(directory, "images/" + fields[0]).getPath());
                if (source == null) throw new IOException("Unreadable validation image: " + fields[0]);
                try {
                    for (int frame = 0; frame < 8; frame++) {
                        MPImage image = new BitmapImageBuilder(source.copy(Bitmap.Config.ARGB_8888, false)).build();
                        try {
                            HandLandmarkerResult result = model.detectForVideo(image, timestamp += 100);
                            out.print(fields[0] + "," + fields[1] + "," + fields[2] + ",validation," + frame
                                    + "," + source.getWidth() + "," + source.getHeight() + "," + result.landmarks().size());
                            if (result.landmarks().isEmpty()) for (int i = 0; i < 63; i++) out.print(",0");
                            else for (NormalizedLandmark point : result.landmarks().get(0)) out.print("," + point.x() + "," + point.y() + "," + point.z());
                            out.println();
                        } finally { image.close(); }
                    }
                } finally { source.recycle(); }
                if (++processed % 50 == 0) {
                    out.flush(); android.util.Log.i("VowelDataset", "Video validation sequences: " + processed + "/1200");
                }
            }
            if (out.checkError()) throw new IOException("Could not write video validation");
        } finally { blank.recycle(); }
    }

    private static String hashBytes(byte[] bytes) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
            char[] hex = "0123456789abcdef".toCharArray(), output = new char[digest.length * 2];
            for (int i = 0; i < digest.length; i++) { output[i * 2] = hex[(digest[i] & 255) >>> 4]; output[i * 2 + 1] = hex[digest[i] & 15]; }
            return new String(output);
        } catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    @Test public void extractExplicitDataset() throws Exception {
        String requested = InstrumentationRegistry.getArguments().getString("extractDataset");
        Assume.assumeTrue("Explicit -e extractDataset true is required", "true".equals(requested));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File directory = new File(context.getExternalFilesDir(null), "vowel-dataset");
        File manifest = new File(directory, "manifest.tsv");
        if (!manifest.isFile()) throw new IOException("Push the prepared vowel-dataset folder first");
        HandLandmarker.HandLandmarkerOptions options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("hand_landmarker.task")
                        .setDelegate(Delegate.CPU).build())
                .setRunningMode(RunningMode.IMAGE).setNumHands(2)
                .setMinHandDetectionConfidence(.5f).setMinHandPresenceConfidence(.5f)
                .setMinTrackingConfidence(.5f).build();
        int processed = 0, accepted = 0, noHands = 0, multiple = 0, unreadable = 0;
        try (HandLandmarker model = HandLandmarker.createFromOptions(context, options);
             BufferedReader reader = new BufferedReader(new FileReader(manifest));
             PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(new File(directory, "landmarks.csv"))))) {
            String header = "id,letter,group,split,width,height";
            for (int i = 0; i < 63; i++) header += ",p" + i;
            out.println(header); reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split("\t");
                if (fields.length != 6 || !fields[0].matches("[A-Z]_[0-9]+\\.jpg")) throw new IOException("Invalid manifest row");
                Bitmap bitmap = BitmapFactory.decodeFile(new File(directory, "images/" + fields[0]).getPath());
                if (bitmap == null) { unreadable++; continue; }
                int width = bitmap.getWidth(), height = bitmap.getHeight();
                MPImage image = new BitmapImageBuilder(bitmap).build();
                try {
                    HandLandmarkerResult result = model.detect(image);
                    if (result.landmarks().size() == 1) {
                        out.print(fields[0] + "," + fields[1] + "," + fields[2] + "," + fields[3] + "," + width + "," + height);
                        for (NormalizedLandmark point : result.landmarks().get(0)) {
                            out.print("," + point.x() + "," + point.y() + "," + point.z());
                        }
                        out.println(); accepted++;
                    } else if (result.landmarks().isEmpty()) noHands++;
                    else multiple++;
                } finally {
                    image.close();
                    if (!bitmap.isRecycled()) bitmap.recycle();
                }
                processed++;
                if (processed % 500 == 0) {
                    out.flush();
                    android.util.Log.i("VowelDataset", "Processed " + processed + ", accepted " + accepted);
                }
            }
            if (out.checkError()) throw new IOException("Could not write landmarks");
        }
        try (PrintWriter summary = new PrintWriter(new File(directory, "extraction.txt"))) {
            summary.println("processed=" + processed + "\naccepted=" + accepted + "\nno_hand=" + noHands
                    + "\nmultiple_hands=" + multiple + "\nunreadable=" + unreadable);
            summary.println("Hand Landmarker IMAGE mode, CPU, default .5 thresholds, float16 version 1.");
        }
    }
}
