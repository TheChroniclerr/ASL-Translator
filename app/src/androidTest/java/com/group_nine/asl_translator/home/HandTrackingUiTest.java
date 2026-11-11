package com.group_nine.asl_translator.home;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.group_nine.asl_translator.MainActivity;
import com.group_nine.asl_translator.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Scripted detector output tests UI behavior; HandModelTest separately verifies the real model. */
@RunWith(AndroidJUnit4.class)
public class HandTrackingUiTest {
    private ActivityScenario<MainActivity> scenario;
    private final AtomicInteger count = new AtomicInteger();
    private CountDownLatch unblock = new CountDownLatch(0);

    @Before
    public void launch() throws Exception {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).executeShellCommand(
                "pm grant com.group_nine.asl_translator android.permission.CAMERA");
        HandTracker.engineFactory = context -> engine();
        scenario = ActivityScenario.launch(MainActivity.class);
    }

    @After
    public void close() {
        unblock.countDown();
        if (scenario != null) { scenario.close(); }
        HandTracker.engineFactory = HandTracker.MediaPipeEngine::new;
    }

    @Test
    public void showsZeroOneAndTwoHandsThenClearsLostHands() throws Exception {
        onView(withId(R.id.start_camera)).perform(click());
        awaitCaption(R.string.tracking_no_hands);
        count.set(1);
        awaitCaption(R.string.tracking_one_hand);
        assertHands(1);
        count.set(2);
        awaitCaption(R.string.tracking_two_hands);
        assertHands(2);
        CameraPreviewTest.screenshot("tracking-ui-two-hands.png");
        count.set(0);
        awaitCaption(R.string.tracking_no_hands);
        assertHands(0);
    }

    @Test
    public void modelFailureKeepsPreviewAndCanRetry() {
        HandTracker.engineFactory = context -> { throw new IllegalStateException("Test model initialization failure"); };
        onView(withId(R.id.start_camera)).perform(click());
        awaitCaption(R.string.tracking_unavailable);
        scenario.onActivity(activity -> assertTrue(CameraPreviewTest.active(activity)));
        assertHands(0);
        onView(withId(R.id.start_camera)).perform(click());
        HandTracker.engineFactory = context -> engine();
        count.set(1);
        onView(withId(R.id.start_camera)).perform(click());
        awaitCaption(R.string.tracking_one_hand);
        assertHands(1);
    }

    @Test
    public void initializingModelDoesNotBlockCameraOrUi() throws Exception {
        unblock = new CountDownLatch(1);
        HandTracker.engineFactory = context -> {
            assertTrue(unblock.await(10, TimeUnit.SECONDS));
            return engine();
        };
        onView(withId(R.id.start_camera)).perform(click());
        awaitCaption(R.string.tracking_initializing);
        scenario.onActivity(activity -> assertTrue(CameraPreviewTest.active(activity)));
        unblock.countDown();
        awaitCaption(R.string.tracking_no_hands);
    }

    @Test
    public void stoppingDropsInFlightResultAndClosesEngine() throws Exception {
        unblock = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        HandTracker.engineFactory = context -> new HandTracker.Engine() {
            @Override public List<HandTrackingResult.Hand> detect(Bitmap image, long timestamp) {
                entered.countDown();
                try { assertTrue(unblock.await(10, TimeUnit.SECONDS)); }
                catch (InterruptedException exception) { throw new RuntimeException(exception); }
                return hands(2);
            }
            @Override public void close() { closed.countDown(); }
        };
        onView(withId(R.id.start_camera)).perform(click());
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        CameraPreviewTest.await(scenario, CameraPreviewTest::active);
        onView(withId(R.id.start_camera)).perform(click());
        unblock.countDown();
        assertTrue(closed.await(10, TimeUnit.SECONDS));
        CameraPreviewTest.await(scenario, CameraPreviewTest::stopped);
        assertHands(0);
        onView(withId(R.id.live_caption)).check(matches(withText(R.string.camera_recognition_later)));
    }

    @Test
    public void navigationClearsLandmarksAndDoesNotResumeTracking() {
        count.set(2);
        onView(withId(R.id.start_camera)).perform(click());
        awaitCaption(R.string.tracking_two_hands);
        scenario.onActivity(activity -> ((BottomNavigationView) activity.findViewById(R.id.bottom_navigation))
                .setSelectedItemId(R.id.nav_recents));
        assertHands(0);
        scenario.onActivity(activity -> ((BottomNavigationView) activity.findViewById(R.id.bottom_navigation))
                .setSelectedItemId(R.id.nav_home));
        CameraPreviewTest.await(scenario, CameraPreviewTest::stopped);
        assertHands(0);
    }

    @Test
    public void cameraSwitchAndRecreationRestartTracking() throws Exception {
        count.set(2);
        onView(withId(R.id.start_camera)).perform(click());
        awaitCaption(R.string.tracking_two_hands);
        onView(withId(R.id.switch_camera)).perform(click());
        awaitCaption(R.string.tracking_two_hands);
        scenario.recreate();
        awaitCaption(R.string.tracking_two_hands);
        assertHands(2);
        CameraPreviewTest.screenshot("tracking-ui-rear-recreated.png");
    }

    private void awaitCaption(int message) {
        CameraPreviewTest.await(scenario, activity -> CameraPreviewTest.active(activity)
                && ((android.widget.TextView) activity.findViewById(R.id.live_caption))
                .getText().equals(activity.getString(message)));
        onView(withId(R.id.live_caption)).check(matches(withText(message)));
    }

    private void assertHands(int expected) {
        scenario.onActivity(activity -> assertEquals(expected,
                ((HandLandmarkOverlay) activity.findViewById(R.id.hand_landmarks)).getHandCount()));
    }

    private HandTracker.Engine engine() {
        return new HandTracker.Engine() {
            @Override public List<HandTrackingResult.Hand> detect(Bitmap image, long timestamp) { return hands(count.get()); }
            @Override public void close() { }
        };
    }

    private static List<HandTrackingResult.Hand> hands(int count) {
        List<HandTrackingResult.Hand> hands = new ArrayList<>();
        for (int hand = 0; hand < count; hand++) {
            List<HandTrackingResult.Landmark> points = new ArrayList<>();
            for (int i = 0; i < 21; i++) {
                points.add(new HandTrackingResult.Landmark(.25f + hand * .4f + (i % 4) * .025f,
                        .3f + (i / 4) * .04f, 0));
            }
            hands.add(new HandTrackingResult.Hand(points, hand == 0 ? "Left" : "Right", 1));
        }
        return Collections.unmodifiableList(hands);
    }
}
