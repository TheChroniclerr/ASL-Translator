package com.group_nine.asl_translator.home;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.pm.PackageManager;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.group_nine.asl_translator.MainActivity;
import com.group_nine.asl_translator.R;

import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

/** Run before CameraPreviewTest on a fresh install, or revoke CAMERA before starting instrumentation. */
@RunWith(AndroidJUnit4.class)
public class CameraPermissionTest {
    @Before
    public void resetCameraPermissionHistory() {
        // Reset only this feature's test history; leave the existing account data alone.
        InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getSharedPreferences("ASL_Camera_Prefs", 0).edit().clear().commit();
    }

    @Test
    public void denialAllowsRetryAndGrantStartsRealPreview() throws Exception {
        assumeTrue("Run with CAMERA permission reset to exercise the system permission dialog",
                ContextCompat.checkSelfPermission(InstrumentationRegistry.getInstrumentation()
                        .getTargetContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED);
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> activity.findViewById(R.id.start_camera).performClick());
            UiObject2 deny = device.wait(Until.findObject(By.res(
                    "com.android.permissioncontroller", "permission_deny_button")), 10000);
            assertNotNull("System camera permission dialog should appear", deny);
            deny.click();
            CameraPreviewTest.await(scenario, activity -> ((TextView) activity.findViewById(R.id.camera_status))
                    .getText().equals(activity.getString(R.string.camera_permission_denied)));
            CameraPreviewTest.screenshot("permission-denied.png");
            scenario.onActivity(activity -> activity.findViewById(R.id.start_camera).performClick());
            UiObject2 proceed = device.wait(Until.findObject(By.res("android", "button1")), 10000);
            assertNotNull("Permission rationale should offer Continue", proceed);
            proceed.click();
            UiObject2 allow = device.wait(Until.findObject(By.res(
                    "com.android.permissioncontroller", "permission_allow_foreground_only_button")), 10000);
            assertNotNull("Retry should request camera permission", allow);
            allow.click();
            CameraPreviewTest.await(scenario, CameraPreviewTest::active);
        }
    }

    @Test
    public void repeatedDenialOffersSettings() throws Exception {
        assumeTrue("Reset CAMERA permission before this test", ContextCompat.checkSelfPermission(
                InstrumentationRegistry.getInstrumentation().getTargetContext(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED);
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> activity.findViewById(R.id.start_camera).performClick());
            UiObject2 deny = device.wait(Until.findObject(By.res(
                    "com.android.permissioncontroller", "permission_deny_button")), 10000);
            assertNotNull(deny);
            deny.click();
            CameraPreviewTest.await(scenario, activity -> ((TextView) activity.findViewById(R.id.camera_status))
                    .getText().equals(activity.getString(R.string.camera_permission_denied)));
            scenario.onActivity(activity -> activity.findViewById(R.id.start_camera).performClick());
            UiObject2 proceed = device.wait(Until.findObject(By.res("android", "button1")), 10000);
            assertNotNull(proceed);
            proceed.click();
            deny = device.wait(Until.findObject(By.res(
                    Pattern.compile("com.android.permissioncontroller:id/permission_deny.*button"))), 10000);
            assertNotNull(deny);
            deny.click();
            CameraPreviewTest.await(scenario, activity -> ((TextView) activity.findViewById(R.id.start_camera))
                    .getText().equals(activity.getString(R.string.camera_open_settings)));
            CameraPreviewTest.screenshot("permission-blocked.png");
            scenario.onActivity(activity -> activity.findViewById(R.id.start_camera).performClick());
            assertTrue(device.wait(Until.hasObject(By.pkg("com.android.settings").depth(0)), 10000));
            device.pressBack();
        }
    }
}
