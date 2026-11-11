package com.group_nine.asl_translator.home;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.widget.TextView;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.group_nine.asl_translator.MainActivity;
import com.group_nine.asl_translator.R;
import com.group_nine.asl_translator.recognition.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Scripted predictions exercise UI/lifecycle behavior; bundled-model tests use real held-out data. */
@RunWith(AndroidJUnit4.class)
public class VowelRecognitionUiTest {
    private ActivityScenario<MainActivity> scenario;
    private final AtomicInteger handCount = new AtomicInteger(1);
    private final AtomicBoolean inferenceFails = new AtomicBoolean();
    private final AtomicBoolean invalidGeometry = new AtomicBoolean();
    private CountDownLatch unblock = new CountDownLatch(0);
    private CountDownLatch entered = new CountDownLatch(0);

    @Before public void launch() throws Exception {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).executeShellCommand(
                "pm grant com.group_nine.asl_translator android.permission.CAMERA");
        HandTracker.engineFactory = context -> new HandTracker.Engine() {
            @Override public List<HandTrackingResult.Hand> detect(Bitmap bitmap, long timestamp) {
                List<HandTrackingResult.Hand> result = new ArrayList<>();
                for (int h = 0; h < handCount.get(); h++) {
                    List<HandTrackingResult.Landmark> landmarks = new ArrayList<>();
                    for (int i = 0; i < 21; i++) landmarks.add(new HandTrackingResult.Landmark(
                            .4f + i * .006f, invalidGeometry.get() ? Float.NaN : .8f - i * .02f, -i * .002f));
                    result.add(new HandTrackingResult.Hand(landmarks, h == 0 ? "Left" : "Right", .99f));
                }
                return result;
            }
            @Override public void close() { }
        };
        HandTracker.classifierFactory = context -> scriptedClassifier();
        scenario = ActivityScenario.launch(MainActivity.class);
    }

    private VowelClassifier scriptedClassifier() {
        ForestModel model = new ForestModel(new ForestModel.Node[][]{{new ForestModel.Node(-1, 0, -1, -1, 0)}}, .8, .2);
        return new VowelClassifier(model) {
            @Override public VowelRecognitionResult classify(List<float[]> hands, int width, int height, long timestamp) {
                entered.countDown();
                try { if (!unblock.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for test"); }
                catch (InterruptedException exception) { throw new IllegalStateException(exception); }
                if (inferenceFails.get()) throw new IllegalStateException("Scripted classifier failure");
                return super.classify(hands, width, height, timestamp);
            }
        };
    }

    @After public void close() {
        unblock.countDown();
        if (scenario != null) scenario.close();
        HandTracker.engineFactory = HandTracker.MediaPipeEngine::new;
        HandTracker.classifierFactory = HandTracker::loadClassifier;
    }

    private void awaitText(int resource) {
        CameraPreviewTest.await(scenario, activity -> ((TextView) activity.findViewById(R.id.vowel_result))
                .getText().toString().equals(activity.getString(resource)));
    }

    private void awaitLetter() {
        CameraPreviewTest.await(scenario, activity -> CameraPreviewTest.active(activity)
                && ((TextView) activity.findViewById(R.id.vowel_result)).getText().toString()
                .equals(activity.getString(R.string.vowel_recognized, "A")));
    }

    @Test public void stableVowelUnknownMissingAndTwoHandsAreDisplayed() throws Exception {
        handCount.set(0);
        onView(withId(R.id.start_camera)).perform(click()); awaitText(R.string.vowel_no_hands);
        handCount.set(1); awaitLetter();
        CameraPreviewTest.screenshot("vowel-recognized.png");
        handCount.set(2); awaitText(R.string.vowel_two_hands);
        onView(withId(R.id.live_caption)).check(matches(withText(R.string.tracking_two_hands)));
        handCount.set(1); invalidGeometry.set(true); awaitText(R.string.vowel_unknown);
        invalidGeometry.set(false); awaitLetter();
        handCount.set(0); awaitText(R.string.vowel_no_hands);
    }

    @Test public void inferenceFailurePreservesTrackingAndRestartRetries() {
        onView(withId(R.id.start_camera)).perform(click()); awaitLetter();
        inferenceFails.set(true); awaitText(R.string.vowel_unavailable);
        scenario.onActivity(activity -> assertTrue(CameraPreviewTest.active(activity)));
        onView(withId(R.id.live_caption)).check(matches(withText(R.string.tracking_one_hand)));
        onView(withId(R.id.start_camera)).perform(click()); awaitText(R.string.vowel_idle);
        inferenceFails.set(false);
        onView(withId(R.id.start_camera)).perform(click()); awaitLetter();
    }

    @Test public void initializationFailurePreservesHandTracking() {
        HandTracker.classifierFactory = context -> { throw new java.io.IOException("Scripted missing model"); };
        onView(withId(R.id.start_camera)).perform(click()); awaitText(R.string.vowel_unavailable);
        onView(withId(R.id.live_caption)).check(matches(withText(R.string.tracking_one_hand)));
        scenario.onActivity(activity -> assertTrue(CameraPreviewTest.active(activity)));
    }

    @Test public void stopDiscardsBlockedRecognitionAndRestartWorks() throws Exception {
        unblock = new CountDownLatch(1); entered = new CountDownLatch(1);
        onView(withId(R.id.start_camera)).perform(click());
        assertTrue(entered.await(15, TimeUnit.SECONDS));
        CameraPreviewTest.await(scenario, CameraPreviewTest::active);
        onView(withId(R.id.start_camera)).perform(click());
        unblock.countDown(); awaitText(R.string.vowel_idle);
        handCount.set(0);
        onView(withId(R.id.start_camera)).perform(click()); awaitText(R.string.vowel_no_hands);
        handCount.set(1); awaitLetter();
    }

    @Test public void navigationAndBackgroundClearTheRecognizedLetter() {
        onView(withId(R.id.start_camera)).perform(click()); awaitLetter();
        scenario.onActivity(activity -> ((BottomNavigationView) activity.findViewById(R.id.bottom_navigation)).setSelectedItemId(R.id.nav_recents));
        scenario.onActivity(activity -> ((BottomNavigationView) activity.findViewById(R.id.bottom_navigation)).setSelectedItemId(R.id.nav_home));
        awaitText(R.string.vowel_idle);
        onView(withId(R.id.start_camera)).perform(click()); awaitLetter();
        scenario.moveToState(Lifecycle.State.CREATED); scenario.moveToState(Lifecycle.State.RESUMED);
        awaitText(R.string.vowel_idle);
    }

    @Test public void switchingAndRecreationClearTheOldLetter() {
        onView(withId(R.id.start_camera)).perform(click()); awaitLetter();
        handCount.set(0); onView(withId(R.id.switch_camera)).perform(click()); awaitText(R.string.vowel_no_hands);
        handCount.set(1); awaitLetter(); handCount.set(0); scenario.recreate(); awaitText(R.string.vowel_no_hands);
        onView(withId(R.id.start_camera)).perform(click()); awaitText(R.string.vowel_idle);
    }
}
