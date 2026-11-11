package com.group_nine.asl_translator.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.group_nine.asl_translator.MainActivity;
import com.group_nine.asl_translator.R;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicInteger;

/** Optional participant-assisted check; skipped unless the liveHands runner argument is supplied. */
@RunWith(AndroidJUnit4.class)
public class HandLiveCameraTest {
    @Test
    public void tracksLiveHands() throws Exception {
        String argument = InstrumentationRegistry.getArguments().getString("liveHands");
        assumeTrue("Supply -e liveHands 1 or 2 with hands in the real camera view", argument != null);
        int expected = Integer.parseInt(argument);
        assertTrue(expected == 1 || expected == 2);
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).executeShellCommand(
                "pm grant com.group_nine.asl_translator android.permission.CAMERA");
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> activity.findViewById(R.id.start_camera).performClick());
            CameraPreviewTest.await(scenario, CameraPreviewTest::active);
            long deadline = SystemClock.uptimeMillis() + 45000;
            AtomicInteger count = new AtomicInteger();
            int stable = 0;
            while (SystemClock.uptimeMillis() < deadline && stable < 5) {
                scenario.onActivity(activity -> count.set(
                        ((HandLandmarkOverlay) activity.findViewById(R.id.hand_landmarks)).getHandCount()));
                stable = count.get() == expected ? stable + 1 : 0;
                SystemClock.sleep(150);
            }
            assertEquals("Expected visible live hands", expected, count.get());
            assertTrue("Hand count should remain stable across several frames", stable >= 5);
            CameraPreviewTest.screenshot("live-" + expected + "-hands.png");
            scenario.onActivity(activity -> activity.findViewById(R.id.start_camera).performClick());
            CameraPreviewTest.await(scenario, CameraPreviewTest::stopped);
            scenario.onActivity(activity -> assertEquals(0,
                    ((HandLandmarkOverlay) activity.findViewById(R.id.hand_landmarks)).getHandCount()));
        }
    }
}
