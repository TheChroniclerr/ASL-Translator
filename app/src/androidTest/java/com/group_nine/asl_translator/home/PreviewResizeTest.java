package com.group_nine.asl_translator.home;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.annotation.OptIn;
import androidx.camera.view.PreviewView;
import androidx.camera.view.TransformExperimental;
import androidx.camera.view.transform.OutputTransform;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import com.group_nine.asl_translator.MainActivity;
import com.group_nine.asl_translator.R;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.*;

/** Real CameraX geometry with synthetic corner landmarks; does not use live hands. */
@RunWith(AndroidJUnit4.class)
@OptIn(markerClass = TransformExperimental.class)
public class PreviewResizeTest {
    private ActivityScenario<MainActivity> scenario;
    private final AtomicInteger engineStarts = new AtomicInteger();
    private final AtomicInteger frameWidth = new AtomicInteger(), frameHeight = new AtomicInteger();
    private final AtomicInteger frames = new AtomicInteger();

    @Before public void launch() throws Exception {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).executeShellCommand(
                "pm grant com.group_nine.asl_translator android.permission.CAMERA");
        HandTracker.engineFactory = context -> {
            engineStarts.incrementAndGet();
            return new HandTracker.Engine() {
                @Override public List<HandTrackingResult.Hand> detect(Bitmap bitmap, long timestamp) {
                    frameWidth.set(bitmap.getWidth()); frameHeight.set(bitmap.getHeight()); frames.incrementAndGet();
                    List<HandTrackingResult.Landmark> points = new ArrayList<>();
                    for (float[] corner : new float[][]{{0, 0}, {1, 0}, {1, 1}, {0, 1}}) {
                        points.add(new HandTrackingResult.Landmark(corner[0], corner[1], 0));
                    }
                    while (points.size() < 21) points.add(new HandTrackingResult.Landmark(.5f, .5f, 0));
                    return Collections.singletonList(new HandTrackingResult.Hand(points, "Left", 1));
                }
                @Override public void close() { }
            };
        };
        scenario = ActivityScenario.launch(MainActivity.class);
    }

    @After public void close() {
        if (scenario != null) scenario.close();
        HandTracker.engineFactory = HandTracker.MediaPipeEngine::new;
    }

    private void resize(int steps) {
        scenario.onActivity(activity -> {
            View divider = activity.findViewById(R.id.resize_divider);
            for (int i = 0; i < Math.abs(steps); i++) divider.performAccessibilityAction(steps > 0
                    ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, null);
        });
    }

    private void start() {
        onView(withId(R.id.start_camera)).perform(scrollTo(), click());
        CameraPreviewTest.await(scenario, CameraPreviewTest::active);
    }

    private void assertFullFrame(String description) {
        int before = frames.get();
        CameraPreviewTest.await(scenario, activity -> frames.get() >= before + 3
                && ((HandLandmarkOverlay) activity.findViewById(R.id.hand_landmarks)).getHandCount() == 1
                && ((PreviewView) activity.findViewById(R.id.camera_surface)).getOutputTransform() != null);
        scenario.onActivity(activity -> {
            PreviewView preview = activity.findViewById(R.id.camera_surface);
            OutputTransform transform = preview.getOutputTransform();
            assertNotNull(transform);
            RectF content = new RectF(-1, -1, 1, 1);
            transform.getMatrix().mapRect(content);
            float scale = Math.min(preview.getWidth() / (float) frameWidth.get(), preview.getHeight() / (float) frameHeight.get());
            String geometry = description + ": content=" + content + " view=" + preview.getWidth() + "x" + preview.getHeight()
                    + " analysis=" + frameWidth.get() + "x" + frameHeight.get();
            assertTrue(geometry, content.left >= -2 && content.top >= -2
                    && content.right <= preview.getWidth() + 2 && content.bottom <= preview.getHeight() + 2);
            assertEquals(geometry, frameWidth.get() * scale, content.width(), 2f);
            assertEquals(geometry, frameHeight.get() * scale, content.height(), 2f);
            assertEquals(geometry, preview.getWidth() / 2f, content.centerX(), 1f);
            assertEquals(geometry, preview.getHeight() / 2f, content.centerY(), 1f);
            HandLandmarkOverlay overlay = activity.findViewById(R.id.hand_landmarks);
            Bitmap drawing = Bitmap.createBitmap(overlay.getWidth(), overlay.getHeight(), Bitmap.Config.ARGB_8888);
            try {
                overlay.draw(new Canvas(drawing));
                for (float[] corner : new float[][]{{content.left, content.top}, {content.right, content.top},
                        {content.right, content.bottom}, {content.left, content.bottom}}) {
                    assertTrue(geometry + " missing corner landmark " + Arrays.toString(corner), whiteNear(drawing, corner[0], corner[1]));
                }
            } finally { drawing.recycle(); }
        });
    }

    private static boolean whiteNear(Bitmap bitmap, float x, float y) {
        for (int py = Math.max(0, Math.round(y) - 7); py < Math.min(bitmap.getHeight(), Math.round(y) + 8); py++) {
            for (int px = Math.max(0, Math.round(x) - 7); px < Math.min(bitmap.getWidth(), Math.round(x) + 8); px++) {
                int color = bitmap.getPixel(px, py);
                if (Color.alpha(color) > 220 && Color.red(color) > 240 && Color.green(color) > 240 && Color.blue(color) > 240) return true;
            }
        }
        return false;
    }

    @Test public void smallStartKeepsTheEntireFrameThroughResizingAndRestart() throws Exception {
        resize(-4); start(); assertFullFrame("small startup");
        int session = engineStarts.get();
        resize(7); assertFullFrame("enlarged from small");
        CameraPreviewTest.screenshot("fit-enlarged-from-small.png");
        resize(-7); assertFullFrame("shrunk again");
        CameraPreviewTest.screenshot("fit-small.png");
        resize(7); assertFullFrame("enlarged a second time");
        assertEquals("Resizing must not restart the camera/tracker", session, engineStarts.get());
        onView(withId(R.id.start_camera)).perform(scrollTo(), click());
        CameraPreviewTest.await(scenario, CameraPreviewTest::stopped);
        start(); assertFullFrame("large startup after restart");
    }

    @Test public void largeStartupRearCameraAndLandscapePreserveFullFrame() throws Exception {
        resize(3); start(); assertFullFrame("large startup");
        int session = engineStarts.get();
        resize(-7); assertFullFrame("small after large startup");
        resize(7); assertFullFrame("large again");
        assertEquals(session, engineStarts.get());
        scenario.onActivity(activity -> activity.findViewById(R.id.switch_camera).performClick());
        CameraPreviewTest.await(scenario, activity -> CameraPreviewTest.active(activity)
                && activity.findViewById(R.id.switch_camera).getContentDescription().equals(activity.getString(R.string.switch_to_front_camera)));
        assertFullFrame("rear camera");
        try {
            scenario.onActivity(activity -> activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
            CameraPreviewTest.await(scenario, activity -> CameraPreviewTest.active(activity)
                    && activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);
            assertFullFrame("landscape");
            CameraPreviewTest.screenshot("fit-landscape.png");
        } finally {
            scenario.onActivity(activity -> activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
            CameraPreviewTest.await(scenario, activity -> activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT);
        }
    }
}
