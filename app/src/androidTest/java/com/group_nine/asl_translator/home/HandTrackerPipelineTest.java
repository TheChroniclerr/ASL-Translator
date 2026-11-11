package com.group_nine.asl_translator.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;

import androidx.camera.core.ImageInfo;
import androidx.camera.core.ImageProxy;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@RunWith(AndroidJUnit4.class)
public class HandTrackerPipelineTest {
    private HandTracker tracker;
    private CountDownLatch unblock = new CountDownLatch(0);

    @After
    public void reset() {
        unblock.countDown();
        if (tracker != null) { tracker.close(); }
        HandTracker.engineFactory = HandTracker.MediaPipeEngine::new;
    }

    @Test
    public void timestampsIncreaseAndAllImagesCloseOnTheSameWorker() throws Exception {
        AtomicLong workerThread = new AtomicLong();
        AtomicInteger imageCloses = new AtomicInteger();
        CountDownLatch processed = new CountDownLatch(3);
        CountDownLatch engineClosed = new CountDownLatch(1);
        List<Long> timestamps = Collections.synchronizedList(new ArrayList<>());
        HandTracker.engineFactory = context -> {
            workerThread.set(Thread.currentThread().getId());
            return new HandTracker.Engine() {
                @Override public List<HandTrackingResult.Hand> detect(Bitmap bitmap, long timestamp) {
                    assertEquals(workerThread.get(), Thread.currentThread().getId());
                    assertEquals(48, bitmap.getWidth());
                    assertEquals(64, bitmap.getHeight());
                    timestamps.add(timestamp);
                    return Collections.emptyList();
                }
                @Override public void close() {
                    assertEquals(workerThread.get(), Thread.currentThread().getId());
                    engineClosed.countDown();
                }
            };
        };
        tracker = new HandTracker(InstrumentationRegistry.getInstrumentation().getTargetContext(), (state, result) -> { });
        for (int i = 0; i < 3; i++) {
            ImageProxy image = frame(90, imageCloses);
            tracker.executor().execute(() -> { tracker.analyze(image); processed.countDown(); });
        }
        assertTrue(processed.await(10, TimeUnit.SECONDS));
        tracker.close();
        assertTrue(engineClosed.await(10, TimeUnit.SECONDS));
        assertEquals(3, imageCloses.get());
        assertEquals(3, timestamps.size());
        assertTrue(timestamps.get(0) < timestamps.get(1) && timestamps.get(1) < timestamps.get(2));
    }

    @Test
    public void stopDuringInferenceDiscardsResultAndReleasesFrameAndEngine() throws Exception {
        unblock = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch engineClosed = new CountDownLatch(1);
        AtomicInteger callbacks = new AtomicInteger();
        AtomicInteger imageCloses = new AtomicInteger();
        HandTracker.engineFactory = context -> new HandTracker.Engine() {
            @Override public List<HandTrackingResult.Hand> detect(Bitmap bitmap, long timestamp) {
                entered.countDown();
                try { assertTrue(unblock.await(10, TimeUnit.SECONDS)); }
                catch (InterruptedException exception) { throw new RuntimeException(exception); }
                return Collections.emptyList();
            }
            @Override public void close() { engineClosed.countDown(); }
        };
        tracker = new HandTracker(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                (state, result) -> callbacks.incrementAndGet());
        tracker.executor().execute(() -> tracker.analyze(frame(0, imageCloses)));
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        tracker.close();
        unblock.countDown();
        assertTrue(engineClosed.await(10, TimeUnit.SECONDS));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertEquals(1, imageCloses.get());
        assertEquals(0, callbacks.get());
    }

    @Test
    public void inferenceFailureReleasesImageAndReportsUnavailable() throws Exception {
        AtomicInteger imageCloses = new AtomicInteger();
        CountDownLatch unavailable = new CountDownLatch(1);
        CountDownLatch engineClosed = new CountDownLatch(1);
        HandTracker.engineFactory = context -> new HandTracker.Engine() {
            @Override public List<HandTrackingResult.Hand> detect(Bitmap bitmap, long timestamp) {
                throw new IllegalStateException("Test inference failure");
            }
            @Override public void close() { engineClosed.countDown(); }
        };
        tracker = new HandTracker(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                (state, result) -> { if (state == HandTracker.State.UNAVAILABLE) { unavailable.countDown(); } });
        tracker.executor().execute(() -> tracker.analyze(frame(0, imageCloses)));
        assertTrue(unavailable.await(10, TimeUnit.SECONDS));
        assertTrue(engineClosed.await(10, TimeUnit.SECONDS));
        assertEquals(1, imageCloses.get());
    }

    private static ImageProxy frame(int rotation, AtomicInteger closes) {
        ImageInfo info = (ImageInfo) Proxy.newProxyInstance(ImageInfo.class.getClassLoader(),
                new Class<?>[]{ImageInfo.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getRotationDegrees": return rotation;
                        case "getTimestamp": return 1000000L;
                        case "getSensorToBufferTransformMatrix": return new Matrix();
                        default: return null;
                    }
                });
        return (ImageProxy) Proxy.newProxyInstance(ImageProxy.class.getClassLoader(),
                new Class<?>[]{ImageProxy.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getWidth": return 64;
                        case "getHeight": return 48;
                        case "getCropRect": return new Rect(0, 0, 64, 48);
                        case "getImageInfo": return info;
                        case "toBitmap": return Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888);
                        case "close": closes.incrementAndGet(); return null;
                        default: return null;
                    }
                });
    }
}
