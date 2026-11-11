package com.group_nine.asl_translator.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;

import androidx.camera.view.PreviewView;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Until;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.group_nine.asl_translator.MainActivity;
import com.group_nine.asl_translator.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/** Device tests use actual CameraX streaming, rather than only checking button labels. */
@RunWith(AndroidJUnit4.class)
public class CameraPreviewTest {
    private ActivityScenario<MainActivity> scenario;

    @Before
    public void launch() throws Exception {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).executeShellCommand(
                "pm grant com.group_nine.asl_translator android.permission.CAMERA");
        scenario = ActivityScenario.launch(MainActivity.class);
    }

    @After
    public void close() {
        if (scenario != null) {
            scenario.close();
        }
    }

    @Test
    public void previewStreamsSwitchesAndStops() throws Exception {
        start();
        screenshot("preview-portrait.png");
        scenario.onActivity(activity -> {
            View controls = activity.findViewById(R.id.camera_controls);
            android.graphics.Rect visible = new android.graphics.Rect();
            View space = activity.findViewById(R.id.resizable_space);
            View translation = activity.findViewById(R.id.translation_panel);
            View camera = activity.findViewById(R.id.camera_panel);
            assertTrue("Controls must fit in portrait. space=" + space.getHeight()
                    + " min=" + space.getMinimumHeight() + " translation=" + translation.getHeight()
                    + " camera=" + camera.getHeight() + " controls=" + controls.getHeight(),
                    controls.getGlobalVisibleRect(visible) && visible.height() == controls.getHeight());
        });
        scenario.onActivity(activity -> {
            assertTrue(activity.findViewById(R.id.switch_camera).isEnabled());
            activity.findViewById(R.id.switch_camera).performClick();
        });
        await(scenario, activity -> active(activity)
                && activity.findViewById(R.id.switch_camera).getContentDescription()
                .equals(activity.getString(R.string.switch_to_front_camera)));
        scenario.onActivity(activity -> activity.findViewById(R.id.start_camera).performClick());
        await(scenario, CameraPreviewTest::stopped);
    }

    @Test
    public void navigationReleasesCameraAndRequiresAnotherStart() {
        start();
        scenario.onActivity(activity -> ((BottomNavigationView) activity.findViewById(R.id.bottom_navigation))
                .setSelectedItemId(R.id.nav_recents));
        await(scenario, CameraPreviewTest::stopped);
        scenario.onActivity(activity -> ((BottomNavigationView) activity.findViewById(R.id.bottom_navigation))
                .setSelectedItemId(R.id.nav_home));
        await(scenario, CameraPreviewTest::stopped);
        start();
    }

    @Test
    public void backgroundingRequiresAnotherStart() {
        start();
        scenario.moveToState(Lifecycle.State.CREATED);
        scenario.moveToState(Lifecycle.State.RESUMED);
        await(scenario, CameraPreviewTest::stopped);
        start();
    }

    @Test
    public void recreationKeepsSessionAndSelectedCamera() {
        start();
        scenario.onActivity(activity -> activity.findViewById(R.id.switch_camera).performClick());
        await(scenario, activity -> active(activity)
                && activity.findViewById(R.id.switch_camera).getContentDescription()
                .equals(activity.getString(R.string.switch_to_front_camera)));
        scenario.recreate();
        await(scenario, activity -> active(activity)
                && activity.findViewById(R.id.switch_camera).getContentDescription()
                .equals(activity.getString(R.string.switch_to_front_camera)));
    }

    @Test
    public void rapidStartStopDoesNotReopenFromLateCallback() {
        scenario.onActivity(activity -> {
            activity.findViewById(R.id.start_camera).performClick();
            activity.findViewById(R.id.start_camera).performClick();
        });
        await(scenario, CameraPreviewTest::stopped);
        SystemClock.sleep(750);
        scenario.onActivity(activity -> assertTrue(stopped(activity)));
        start();
    }

    @Test
    public void leavingDuringStartDoesNotActivateHiddenHome() {
        scenario.onActivity(activity -> {
            activity.findViewById(R.id.start_camera).performClick();
            ((BottomNavigationView) activity.findViewById(R.id.bottom_navigation))
                    .setSelectedItemId(R.id.nav_dictionary);
        });
        await(scenario, CameraPreviewTest::stopped);
        SystemClock.sleep(750);
        scenario.onActivity(activity -> {
            assertTrue(stopped(activity));
            ((BottomNavigationView) activity.findViewById(R.id.bottom_navigation))
                    .setSelectedItemId(R.id.nav_home);
        });
        await(scenario, CameraPreviewTest::stopped);
    }

    @Test
    public void rotationAndPanelResizingKeepPreviewUsable() throws Exception {
        start();
        scenario.onActivity(activity -> activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
        await(scenario, activity -> active(activity) && activity.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE);
        scenario.onActivity(activity -> {
            activity.findViewById(R.id.resize_divider).performClick();
            assertTrue(activity.findViewById(R.id.camera_surface).getHeight() > 0);
            assertEquals(View.GONE, activity.findViewById(R.id.preview_icon).getVisibility());
            ((androidx.core.widget.NestedScrollView) activity.findViewById(R.id.home_scroll))
                    .fullScroll(View.FOCUS_DOWN);
        });
        await(scenario, activity -> {
            View controls = activity.findViewById(R.id.camera_controls);
            android.graphics.Rect bounds = new android.graphics.Rect();
            return controls.getGlobalVisibleRect(bounds) && bounds.height() == controls.getHeight();
        });
        screenshot("preview-landscape.png");
        scenario.onActivity(activity -> activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
        await(scenario, activity -> active(activity) && activity.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT);
    }

    @Test
    public void darkModeAndLargeFontsKeepCameraControlsUsable() throws Exception {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        String originalScale = device.executeShellCommand("settings get system font_scale").trim();
        int originalNightMode = AppCompatDelegate.getDefaultNightMode();
        try {
            start();
            scenario.onActivity(activity -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES));
            device.executeShellCommand("settings put system font_scale 1.3");
            await(scenario, activity -> active(activity)
                    && activity.getResources().getConfiguration().fontScale > 1.29f
                    && (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES);
            scenario.onActivity(activity -> {
                assertTrue(activity.findViewById(R.id.start_camera).isShown());
                assertTrue(activity.findViewById(R.id.switch_camera).isEnabled());
                assertTrue(activity.findViewById(R.id.camera_surface).getHeight() > 0);
                View controls = activity.findViewById(R.id.camera_controls);
                android.graphics.Rect bounds = new android.graphics.Rect();
                assertTrue(controls.getGlobalVisibleRect(bounds) && bounds.height() == controls.getHeight());
            });
            screenshot("preview-dark-large-font.png");
        } finally {
            if (originalScale.equals("null")) {
                device.executeShellCommand("settings delete system font_scale");
            } else {
                device.executeShellCommand("settings put system font_scale " + Float.parseFloat(originalScale));
            }
            scenario.onActivity(activity -> AppCompatDelegate.setDefaultNightMode(
                    originalNightMode == AppCompatDelegate.MODE_NIGHT_UNSPECIFIED
                            ? AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM : originalNightMode));
        }
    }

    @Test
    public void logoutReturnsToLoginAndReleasesCamera() {
        start();
        java.util.concurrent.atomic.AtomicReference<PreviewView> surface = new java.util.concurrent.atomic.AtomicReference<>();
        scenario.onActivity(activity -> {
            surface.set(activity.findViewById(R.id.camera_surface));
            ((com.google.android.material.appbar.MaterialToolbar) activity.findViewById(R.id.toolbar))
                    .getMenu().performIdentifierAction(R.id.action_logout, 0);
        });
        assertTrue(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).wait(
                Until.hasObject(By.res("com.group_nine.asl_translator", "edit_login_email")), 10000));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                assertEquals(PreviewView.StreamState.IDLE, surface.get().getPreviewStreamState().getValue()));
    }

    private void start() {
        scenario.onActivity(activity -> activity.findViewById(R.id.start_camera).performClick());
        await(scenario, CameraPreviewTest::active);
    }

    static boolean active(MainActivity activity) {
        PreviewView preview = activity.findViewById(R.id.camera_surface);
        return preview != null && preview.getPreviewStreamState().getValue() == PreviewView.StreamState.STREAMING
                && activity.findViewById(R.id.camera_placeholder).getVisibility() == View.GONE;
    }

    static boolean stopped(MainActivity activity) {
        PreviewView preview = activity.findViewById(R.id.camera_surface);
        return preview != null && preview.getVisibility() == View.INVISIBLE
                && preview.getPreviewStreamState().getValue() == PreviewView.StreamState.IDLE
                && ((TextView) activity.findViewById(R.id.start_camera)).getText()
                .equals(activity.getString(R.string.start_camera));
    }

    static void await(ActivityScenario<MainActivity> scenario, Predicate<MainActivity> condition) {
        long deadline = SystemClock.uptimeMillis() + 20000;
        AtomicBoolean ready = new AtomicBoolean();
        do {
            scenario.onActivity(activity -> ready.set(condition.test(activity)));
            if (ready.get()) {
                return;
            }
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < deadline);
        scenario.onActivity(activity -> {
            String status = ((TextView) activity.findViewById(R.id.camera_status)).getText().toString();
            assertTrue("Camera condition timed out. Status: " + status, condition.test(activity));
        });
    }

    static void screenshot(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle(1000);
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File folder = new File(InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir(null), "camera-qa");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        try (FileOutputStream stream = new FileOutputStream(new File(folder, name))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        }
        bitmap.recycle();
    }
}
