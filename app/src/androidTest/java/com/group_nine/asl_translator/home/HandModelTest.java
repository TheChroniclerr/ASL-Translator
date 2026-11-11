package com.group_nine.asl_translator.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Uses the real model and official images; no detector substitution. */
@RunWith(AndroidJUnit4.class)
public class HandModelTest {
    @Test public void detectsOneHand() throws Exception { check("thumb_up.jpg", 1); }
    @Test public void detectsTwoHands() throws Exception { check("right_hands.jpg", 2); }
    @Test public void detectsNoHands() throws Exception { check("cats_and_dogs.jpg", 0); }

    @Test
    public void bundledModelWorksAcrossSuccessiveVideoFrames() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            worker.submit(() -> {
                Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
                try (HandTracker.MediaPipeEngine engine = new HandTracker.MediaPipeEngine(context)) {
                    Bitmap bitmap = load("thumb_up.jpg");
                    try {
                        for (long timestamp : new long[]{1, 34, 67}) {
                            // Closing an MPImage also releases its Bitmap container.
                            assertFalse(engine.detect(bitmap.copy(Bitmap.Config.ARGB_8888, false), timestamp).isEmpty());
                        }
                    } finally { bitmap.recycle(); }
                } catch (Exception exception) { throw new RuntimeException(exception); }
            }).get(30, TimeUnit.SECONDS);
        } finally { worker.shutdownNow(); }
    }

    private void check(String name, int expected) throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            worker.submit(() -> {
                Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
                try (HandTracker.MediaPipeEngine engine = new HandTracker.MediaPipeEngine(context)) {
                    Bitmap bitmap = load(name);
                    try {
                        List<HandTrackingResult.Hand> hands = engine.detect(bitmap, 1);
                        assertEquals(expected, hands.size());
                        for (HandTrackingResult.Hand hand : hands) {
                            assertEquals(21, hand.landmarks.size());
                            assertTrue(hand.confidence >= .5f);
                            assertTrue(hand.handedness.equals("Left") || hand.handedness.equals("Right"));
                            for (HandTrackingResult.Landmark point : hand.landmarks) {
                                assertTrue(Float.isFinite(point.x) && Float.isFinite(point.y) && Float.isFinite(point.z));
                            }
                        }
                    } finally { bitmap.recycle(); }
                } catch (Exception exception) { throw new RuntimeException(exception); }
            }).get(30, TimeUnit.SECONDS);
        } finally { worker.shutdownNow(); }
    }

    private Bitmap load(String name) throws Exception {
        try (InputStream stream = InstrumentationRegistry.getInstrumentation().getContext()
                .getAssets().open("hand_tracking/" + name)) {
            return BitmapFactory.decodeStream(stream);
        }
    }
}
