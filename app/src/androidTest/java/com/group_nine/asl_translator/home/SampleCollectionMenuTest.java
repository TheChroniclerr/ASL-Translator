package com.group_nine.asl_translator.home;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import com.group_nine.asl_translator.MainActivity;
import com.group_nine.asl_translator.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;

@RunWith(AndroidJUnit4.class)
public class SampleCollectionMenuTest {
    @Test public void overflowOpensCollectionAndReleasesHomeCamera() throws Exception {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).executeShellCommand("pm grant com.group_nine.asl_translator android.permission.CAMERA");
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.start_camera)).perform(scrollTo(), click()); CameraPreviewTest.await(scenario, CameraPreviewTest::active);
            onView(withContentDescription("More options")).perform(click());
            onView(withText(R.string.collect_samples)).perform(click());
            onView(withText(R.string.collection_title)).check(matches(isDisplayed()));
            androidx.test.espresso.Espresso.pressBack(); CameraPreviewTest.await(scenario, CameraPreviewTest::stopped);
        }
    }
}
