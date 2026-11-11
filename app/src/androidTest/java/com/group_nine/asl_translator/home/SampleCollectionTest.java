package com.group_nine.asl_translator.home;

import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import com.group_nine.asl_translator.R;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.zip.*;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SampleCollectionTest {
    private ActivityScenario<SampleCollectionActivity> scenario;
    private SampleStore store;
    private final AtomicInteger opened = new AtomicInteger(), closed = new AtomicInteger();
    private boolean emitHand;

    @Before public void launch() throws Exception {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).executeShellCommand("pm grant com.group_nine.asl_translator android.permission.CAMERA");
        store = new SampleStore(InstrumentationRegistry.getInstrumentation().getTargetContext()); store.deleteAll();
        HandTracker.engineFactory = context -> {
            opened.incrementAndGet();
            return new HandTracker.Engine() {
                @Override public List<HandTrackingResult.Hand> detect(android.graphics.Bitmap image, long timestamp) {
                    if (!emitHand) return Collections.emptyList();
                    List<HandTrackingResult.Landmark> points = new ArrayList<>();
                    for (int i = 0; i < 21; i++) points.add(new HandTrackingResult.Landmark(.4f + i * .006f, .8f - i * .02f, -i * .002f));
                    return Collections.singletonList(new HandTrackingResult.Hand(points, "Left", .99f));
                }
                @Override public void close() { closed.incrementAndGet(); }
            };
        };
        scenario = ActivityScenario.launch(SampleCollectionActivity.class);
    }
    @After public void cleanup() throws Exception {
        if (scenario != null) {
            File screenshot = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null), "collection-screen.png");
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(screenshot);
        }
        if (scenario != null) scenario.close();
        HandTracker.engineFactory = HandTracker.MediaPipeEngine::new;
        long deadline = SystemClock.elapsedRealtime() + 5000;
        while (closed.get() < opened.get() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(30);
        assertEquals("Collection must release every tracker", opened.get(), closed.get());
        store.deleteAll();
    }
    private void await(Predicate<SampleCollectionActivity> condition) {
        long deadline = SystemClock.elapsedRealtime() + 15000;
        while (SystemClock.elapsedRealtime() < deadline) {
            boolean[] done = {false}; scenario.onActivity(activity -> done[0] = condition.test(activity));
            if (done[0]) return; SystemClock.sleep(50);
        }
        fail("Collection state did not settle");
    }
    private void start() {
        await(activity -> activity.findViewById(R.id.collection_new).isEnabled());
        onView(withId(R.id.collection_new)).perform(scrollTo(), click());
        await(activity -> activity.findViewById(R.id.collection_new).isEnabled()
                && ((TextView) activity.findViewById(R.id.collection_progress)).getText().toString().contains("P001"));
        onView(withId(R.id.collection_camera)).perform(scrollTo(), click());
        await(activity -> activity.findViewById(R.id.collection_capture).isEnabled());
    }
    private List<JSONObject> metadata() throws Exception {
        List<JSONObject> output = new ArrayList<>();
        File[] sessions = store.root.listFiles();
        if (sessions != null) for (File session : sessions) {
            File[] files = session.listFiles((dir, name) -> name.endsWith(".json") && !name.equals("session.json"));
            if (files != null) for (File file : files) try (InputStream input = new FileInputStream(file)) {
                ByteArrayOutputStream data = new ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int count;
                while ((count = input.read(buffer)) != -1) data.write(buffer, 0, count);
                output.add(new JSONObject(data.toString("UTF-8")));
            }
        }
        return output;
    }
    private void awaitSaved(int count) {
        await(activity -> { try { return metadata().size() == count && !activity.findViewById(R.id.collection_cancel).isEnabled()
                && activity.findViewById(R.id.collection_new).isEnabled(); } catch (Exception e) { return false; } });
    }


    @Test public void completeHoldRetainsNoHandFramesAndExportsOnlyLandmarkFiles() throws Exception {
        start(); onView(withId(R.id.collection_capture)).perform(scrollTo(), click());
        await(activity -> ((TextView) activity.findViewById(R.id.collection_prompt)).getText().toString().contains("Predictions are hidden"));
        awaitSaved(1);
        JSONObject clip = metadata().get(0); assertTrue(clip.getBoolean("complete")); assertTrue(clip.getInt("frameCount") >= 8);
        ByteArrayOutputStream output = new ByteArrayOutputStream(); store.export(output);
        int frames = 0, entries = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(output.toByteArray()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries++; assertTrue(entry.getName().endsWith(".json") || entry.getName().endsWith(".jsonl"));
                if (entry.getName().endsWith(".jsonl")) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(zip, StandardCharsets.UTF_8)); String line;
                    while ((line = reader.readLine()) != null) { assertEquals(0, new JSONObject(line).getJSONArray("hands").length()); frames++; }
                }
            }
        }
        assertEquals(4, entries); assertEquals(clip.getInt("frameCount"), frames);
    }
    @Test public void cancelledCountdownIsIncompleteAndDoesNotAdvanceGuidance() throws Exception {
        start(); onView(withId(R.id.collection_capture)).perform(scrollTo(), click());
        scenario.onActivity(activity -> activity.findViewById(R.id.collection_cancel).performClick());
        awaitSaved(1); assertFalse(metadata().get(0).getBoolean("complete"));
        await(activity -> ((TextView) activity.findViewById(R.id.collection_progress)).getText().toString().contains("0/80"));
    }
    @Test public void recreationInterruptsHoldRestoresSessionAndClearsCamera() throws Exception {
        start(); onView(withId(R.id.collection_capture)).perform(scrollTo(), click()); scenario.recreate();
        awaitSaved(1); assertFalse(metadata().get(0).getBoolean("complete"));
        await(activity -> ((TextView) activity.findViewById(R.id.collection_progress)).getText().toString().contains("P001"));
        scenario.onActivity(activity -> {
            assertFalse(activity.findViewById(R.id.collection_capture).isEnabled());
            assertEquals(0, ((HandLandmarkOverlay) activity.findViewById(R.id.collection_overlay)).getHandCount());
        });
    }
    @Test public void backgroundingInterruptsAndRequiresExplicitRestart() throws Exception {
        start(); onView(withId(R.id.collection_capture)).perform(scrollTo(), click());
        scenario.moveToState(Lifecycle.State.CREATED); scenario.moveToState(Lifecycle.State.RESUMED);
        awaitSaved(1); assertFalse(metadata().get(0).getBoolean("complete"));
        scenario.onActivity(activity -> assertFalse(activity.findViewById(R.id.collection_capture).isEnabled()));
    }
    @Test public void deleteRequiresConfirmationAndResetsSession() throws Exception {
        start(); onView(withId(R.id.collection_capture)).perform(scrollTo(), click()); awaitSaved(1);
        scenario.onActivity(activity -> {
            androidx.core.widget.NestedScrollView scroll = activity.findViewById(R.id.collection_scroll);
            scroll.fullScroll(View.FOCUS_DOWN);
        });
        onView(withId(R.id.collection_delete)).perform(scrollTo(), click()); onView(withText(android.R.string.cancel)).perform(click());
        assertEquals(1, metadata().size());
        onView(withId(R.id.collection_delete)).perform(scrollTo(), click()); onView(withText("Delete")).perform(click());
        await(activity -> ((TextView) activity.findViewById(R.id.collection_status)).getText().toString().equals("Local samples deleted."));
        assertFalse(store.root.exists());
    }
    @Test public void sampleStorageIsExcludedFromBackupDirectory() throws Exception {
        assertTrue(store.root.getCanonicalPath().startsWith(InstrumentationRegistry.getInstrumentation().getTargetContext().getNoBackupFilesDir().getCanonicalPath() + File.separator));
        start(); onView(withId(R.id.collection_capture)).perform(scrollTo(), click()); awaitSaved(1);
        assertEquals("A", metadata().get(0).getString("label"));
        assertEquals("Left", metadata().get(0).getString("requestedHand"));
    }

    @Test public void detectedHandExportContainsGeometryAndAllSixDiagnosticScores() throws Exception {
        emitHand = true; start(); onView(withId(R.id.collection_capture)).perform(scrollTo(), click()); awaitSaved(1);
        File[] sessions = store.root.listFiles(File::isDirectory); assertNotNull(sessions);
        File[] frames = sessions[0].listFiles((directory, name) -> name.endsWith(".jsonl")); assertNotNull(frames);
        try (BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(frames[0]), StandardCharsets.UTF_8))) {
            JSONObject frame = new JSONObject(input.readLine());
            assertTrue(frame.getInt("width") > 0); assertTrue(frame.getInt("height") > 0); assertTrue(frame.getLong("timestampMs") > 0);
            assertEquals(63, frame.getJSONArray("hands").getJSONObject(0).getJSONArray("landmarks").length());
            assertEquals("Left", frame.getJSONArray("hands").getJSONObject(0).getString("handedness"));
            assertEquals(6, frame.getJSONArray("scores").length()); assertTrue(frame.has("displayedLetter"));
        }
    }

    @Test public void exportUsesZipDocumentPickerAndCancellationKeepsSamples() throws Exception {
        start(); onView(withId(R.id.collection_capture)).perform(scrollTo(), click()); awaitSaved(1);
        androidx.test.espresso.intent.Intents.init();
        try {
            androidx.test.espresso.intent.Intents.intending(androidx.test.espresso.intent.matcher.IntentMatchers.hasAction(Intent.ACTION_CREATE_DOCUMENT))
                    .respondWith(new android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED, null));
            scenario.onActivity(activity -> ((androidx.core.widget.NestedScrollView) activity.findViewById(R.id.collection_scroll)).fullScroll(View.FOCUS_DOWN));
            onView(withId(R.id.collection_export)).perform(scrollTo(), click());
            androidx.test.espresso.intent.Intents.intended(org.hamcrest.Matchers.allOf(
                    androidx.test.espresso.intent.matcher.IntentMatchers.hasAction(Intent.ACTION_CREATE_DOCUMENT),
                    androidx.test.espresso.intent.matcher.IntentMatchers.hasType("application/zip")));
            assertEquals(1, metadata().size());
            await(activity -> activity.findViewById(R.id.collection_new).isEnabled());
        } finally { androidx.test.espresso.intent.Intents.release(); }
    }

    @Test public void documentPickerSuccessWritesReadableZip() throws Exception {
        start(); onView(withId(R.id.collection_capture)).perform(scrollTo(), click()); awaitSaved(1);
        File destination = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(), "collection-export-test.zip");
        androidx.test.espresso.intent.Intents.init();
        try {
            Intent result = new Intent().setData(android.net.Uri.fromFile(destination));
            androidx.test.espresso.intent.Intents.intending(androidx.test.espresso.intent.matcher.IntentMatchers.hasAction(Intent.ACTION_CREATE_DOCUMENT))
                    .respondWith(new android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_OK, result));
            scenario.onActivity(activity -> ((androidx.core.widget.NestedScrollView) activity.findViewById(R.id.collection_scroll)).fullScroll(View.FOCUS_DOWN));
            onView(withId(R.id.collection_export)).perform(scrollTo(), click());
            await(activity -> ((TextView) activity.findViewById(R.id.collection_status)).getText().toString().startsWith("Samples exported."));
            try (ZipFile zip = new ZipFile(destination)) { assertEquals(4, zip.size()); assertNotNull(zip.getEntry("collection.json")); }
            onView(withId(R.id.collection_delete)).perform(scrollTo(), click()); onView(withText("Delete")).perform(click());
            await(activity -> ((TextView) activity.findViewById(R.id.collection_status)).getText().toString().equals("Local samples deleted."));
            assertTrue("Deleting local samples must keep an exported ZIP", destination.isFile());
        } finally { androidx.test.espresso.intent.Intents.release(); if (destination.exists()) assertTrue(destination.delete()); }
    }

    @Test public void invalidLandmarksRemainInACompleteClipAsNullCoordinates() throws Exception {
        SampleStore.Session session = store.create("P001", "train");
        SampleStore.Clip clip = new SampleStore.Clip(session, "O", "Left");
        List<HandTrackingResult.Landmark> points = new ArrayList<>();
        for (int i = 0; i < 21; i++) points.add(new HandTrackingResult.Landmark(i == 0 ? Float.NaN : .5f, .5f, 0));
        HandTrackingResult result = new HandTrackingResult(100, 640, 480, 0, null, Collections.singletonList(new HandTrackingResult.Hand(points, "Left", .99f)));
        clip.frames.add(new SampleStore.Frame(0, result, "ONE_HAND"));
        store.begin(clip); store.finish(clip, true, "completed");
        assertTrue(metadata().get(0).getBoolean("complete"));
        File file = new File(new File(store.root, session.id), clip.id + ".jsonl");
        try (BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            JSONObject frame = new JSONObject(input.readLine());
            assertTrue(frame.getJSONArray("hands").getJSONObject(0).getJSONArray("landmarks").isNull(0));
        }
    }
}
