package com.group_nine.asl_translator.home;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.speech.RecognizerIntent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.intent.Intents;
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
import java.util.concurrent.atomic.AtomicInteger;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.intent.Intents.*;
import static androidx.test.espresso.intent.matcher.IntentMatchers.*;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.*;

/** Tests input ownership and camera lifecycle; speech results are stubbed, not live audio. */
@RunWith(AndroidJUnit4.class)
public class HomeInputIntegrationTest {
    private ActivityScenario<MainActivity> scenario;
    private final AtomicInteger count = new AtomicInteger(1);

    @Before public void launch() throws Exception {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).executeShellCommand(
                "pm grant com.group_nine.asl_translator android.permission.CAMERA");
        Intents.init();
        HandTracker.engineFactory = context -> new HandTracker.Engine() {
            @Override public List<HandTrackingResult.Hand> detect(Bitmap bitmap, long timestamp) {
                List<HandTrackingResult.Hand> hands = new ArrayList<>();
                for (int h = 0; h < count.get(); h++) {
                    List<HandTrackingResult.Landmark> points = new ArrayList<>();
                    for (int i = 0; i < 21; i++) points.add(new HandTrackingResult.Landmark(.4f + i * .006f, .8f - i * .02f, -i * .002f));
                    hands.add(new HandTrackingResult.Hand(points, "Left", .99f));
                }
                return hands;
            }
            @Override public void close() { }
        };
        HandTracker.classifierFactory = context -> new VowelClassifier(new ForestModel(
                new ForestModel.Node[][]{{new ForestModel.Node(-1, 0, -1, -1, 0)}}, .8, .2));
        scenario = ActivityScenario.launch(MainActivity.class);
    }

    @After public void close() {
        if (scenario != null) scenario.close();
        HandTracker.engineFactory = HandTracker.MediaPipeEngine::new;
        HandTracker.classifierFactory = HandTracker::loadClassifier;
        Intents.release();
    }

    private void startAndRecognize() {
        onView(withId(R.id.start_camera)).perform(scrollTo(), click());
        CameraPreviewTest.await(scenario, activity -> CameraPreviewTest.active(activity)
                && ((TextView) activity.findViewById(R.id.vowel_result)).getText().toString()
                .equals(activity.getString(R.string.vowel_recognized, "A")));
    }

    private void voiceResult(int resultCode, String... text) {
        Intent data = new Intent();
        data.putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, new ArrayList<>(Arrays.asList(text)));
        intending(hasAction(RecognizerIntent.ACTION_RECOGNIZE_SPEECH))
                .respondWith(new Instrumentation.ActivityResult(resultCode, data));
    }

    @Test public void typingAndCameraUpdatesDoNotOverwriteEachOther() {
        startAndRecognize();
        onView(withId(R.id.translation_output)).perform(scrollTo(), replaceText("Meet at three"), closeSoftKeyboard());
        count.set(0);
        CameraPreviewTest.await(scenario, activity -> ((TextView) activity.findViewById(R.id.vowel_result))
                .getText().toString().equals(activity.getString(R.string.vowel_no_hands)));
        onView(withId(R.id.translation_output)).check(matches(withText("Meet at three")));
        count.set(1);
        CameraPreviewTest.await(scenario, activity -> ((TextView) activity.findViewById(R.id.vowel_result))
                .getText().toString().equals(activity.getString(R.string.vowel_recognized, "A")));
        onView(withId(R.id.translation_output)).check(matches(withText("Meet at three")));
    }

    @Test public void voiceReplacesOnlyTheEditorAndStopsTheCamera() throws Exception {
        onView(withId(R.id.translation_output)).perform(scrollTo(), replaceText("Earlier text"), closeSoftKeyboard());
        startAndRecognize(); voiceResult(Activity.RESULT_OK, "Hello from voice");
        onView(withId(R.id.microphone)).perform(scrollTo(), click());
        intended(allOf(hasAction(RecognizerIntent.ACTION_RECOGNIZE_SPEECH),
                hasExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM),
                hasExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())));
        onView(withId(R.id.translation_output)).check(matches(withText("Hello from voice")));
        onView(withId(R.id.speech_status)).check(matches(withText(R.string.speech_captured)));
        CameraPreviewTest.await(scenario, CameraPreviewTest::stopped);
        scenario.onActivity(activity -> assertEquals(0, ((HandLandmarkOverlay) activity.findViewById(R.id.hand_landmarks)).getHandCount()));
        startAndRecognize();
        onView(withId(R.id.translation_output)).check(matches(withText("Hello from voice")));
        CameraPreviewTest.screenshot("combined-camera-and-voice-text.png");
    }

    @Test public void cancellingVoiceKeepsTypedText() {
        onView(withId(R.id.translation_output)).perform(scrollTo(), replaceText("Keep this"), closeSoftKeyboard());
        voiceResult(Activity.RESULT_CANCELED);
        onView(withId(R.id.microphone)).perform(scrollTo(), click());
        onView(withId(R.id.translation_output)).check(matches(withText("Keep this")));
        onView(withId(R.id.speech_status)).check(matches(withText(R.string.speech_cancelled)));
        onView(withId(R.id.microphone)).check(matches(isEnabled()));
    }

    @Test public void emptyVoiceResultKeepsTypedText() {
        onView(withId(R.id.translation_output)).perform(scrollTo(), replaceText("Keep this too"), closeSoftKeyboard());
        voiceResult(Activity.RESULT_OK);
        onView(withId(R.id.microphone)).perform(scrollTo(), click());
        onView(withId(R.id.translation_output)).check(matches(withText("Keep this too")));
        onView(withId(R.id.speech_status)).check(matches(withText(R.string.speech_no_result)));
    }

    @Test public void textSelectionSurvivesRecreationNavigationAndBackground() {
        onView(withId(R.id.translation_output)).perform(scrollTo(), replaceText("Saved typed text"), closeSoftKeyboard());
        scenario.onActivity(activity -> ((EditText) activity.findViewById(R.id.translation_output)).setSelection(2, 7));
        scenario.recreate();
        onView(withId(R.id.translation_output)).check(matches(withText("Saved typed text")));
        scenario.onActivity(activity -> {
            EditText editor = activity.findViewById(R.id.translation_output);
            assertEquals(2, editor.getSelectionStart()); assertEquals(7, editor.getSelectionEnd());
            ((BottomNavigationView) activity.findViewById(R.id.bottom_navigation)).setSelectedItemId(R.id.nav_recents);
        });
        scenario.onActivity(activity -> ((BottomNavigationView) activity.findViewById(R.id.bottom_navigation)).setSelectedItemId(R.id.nav_home));
        scenario.moveToState(Lifecycle.State.CREATED); scenario.moveToState(Lifecycle.State.RESUMED);
        onView(withId(R.id.translation_output)).check(matches(withText("Saved typed text")));
    }

    @Test public void voiceTextAndFeedbackSurviveRecreation() {
        voiceResult(Activity.RESULT_OK, "Saved speech");
        onView(withId(R.id.microphone)).perform(scrollTo(), click()); scenario.recreate();
        onView(withId(R.id.translation_output)).check(matches(withText("Saved speech")));
        onView(withId(R.id.speech_status)).check(matches(withText(R.string.speech_captured)));
        onView(withId(R.id.translation_output)).perform(scrollTo(), replaceText("Edited speech"), closeSoftKeyboard());
        onView(withId(R.id.speech_status)).check(matches(withEffectiveVisibility(Visibility.GONE)));
    }

    @Test public void collapsingStopsTrackingAndStartReopensThePanel() {
        onView(withId(R.id.translation_output)).perform(scrollTo(), replaceText("Still here"), closeSoftKeyboard());
        startAndRecognize();
        scenario.onActivity(activity -> {
            for (int i = 0; i < 12; i++) activity.findViewById(R.id.resize_divider)
                    .performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, null);
        });
        CameraPreviewTest.await(scenario, activity -> CameraPreviewTest.stopped(activity)
                && activity.findViewById(R.id.camera_panel).getVisibility() == View.GONE);
        onView(withId(R.id.translation_output)).check(matches(withText("Still here")));
        onView(withId(R.id.vowel_result)).check(matches(withText(R.string.vowel_idle)));
        startAndRecognize();
        onView(withId(R.id.translation_output)).check(matches(withText("Still here")));
    }

    @Test public void keyboardDoesNotCoverTheEditorOrCameraButtons() throws Exception {
        checkKeyboard("combined-keyboard.png");
    }

    @Test public void landscapeKeyboardKeepsTypingAndButtonsReachable() throws Exception {
        try {
            scenario.onActivity(activity -> activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
            CameraPreviewTest.await(scenario, activity -> activity.getResources().getConfiguration().orientation
                    == android.content.res.Configuration.ORIENTATION_LANDSCAPE);
            checkKeyboard("combined-keyboard-landscape.png");
        } finally {
            scenario.onActivity(activity -> activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
            CameraPreviewTest.await(scenario, activity -> activity.getResources().getConfiguration().orientation
                    == android.content.res.Configuration.ORIENTATION_PORTRAIT);
        }
    }

    private void checkKeyboard(String screenshot) throws Exception {
        onView(withId(R.id.translation_output)).perform(scrollTo(), click(), typeText("Keyboard text"));
        CameraPreviewTest.await(scenario, activity -> {
            WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(activity.getWindow().getDecorView());
            return insets != null && insets.isVisible(WindowInsetsCompat.Type.ime());
        });
        onView(withId(R.id.microphone)).perform(scrollTo());
        scenario.onActivity(activity -> {
            android.view.inputmethod.InputMethodManager keyboard = (android.view.inputmethod.InputMethodManager)
                    activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            assertFalse("The keyboard must not replace the app with fullscreen extraction", keyboard.isFullscreenMode());
            assertEquals(View.GONE, activity.findViewById(R.id.bottom_chrome).getVisibility());
            View controls = activity.findViewById(R.id.camera_controls);
            Rect visible = new Rect(); assertTrue(controls.getGlobalVisibleRect(visible));
            assertEquals(controls.getHeight(), visible.height());
            WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(activity.getWindow().getDecorView());
            int keyboardTop = activity.getWindow().getDecorView().getHeight() - insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            assertTrue(visible.bottom <= keyboardTop);
        });
        CameraPreviewTest.screenshot(screenshot);
        onView(withId(R.id.translation_output)).perform(closeSoftKeyboard());
        CameraPreviewTest.await(scenario, activity -> activity.findViewById(R.id.bottom_chrome).getVisibility() == View.VISIBLE);
        onView(withId(R.id.translation_output)).check(matches(withText("Keyboard text")));
    }
}
