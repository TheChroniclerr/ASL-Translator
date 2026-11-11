package com.group_nine.asl_translator.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.view.View;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import com.group_nine.asl_translator.MainActivity;
import com.group_nine.asl_translator.R;
import com.group_nine.asl_translator.bookmarks.BookmarksRepository;
import org.junit.*;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MasterGuiIntegrationTest {
    private ActivityScenario<MainActivity> scenario;
    private SharedPreferences preferences;
    private boolean hadDark, oldDark;
    private int oldMode;
    @Before public void launch() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).executeShellCommand("pm grant com.group_nine.asl_translator android.permission.CAMERA");
        preferences = context.getSharedPreferences("ASL_User_Prefs", 0);
        hadDark = preferences.contains("dark_mode"); oldDark = preferences.getBoolean("dark_mode", false);
        oldMode = AppCompatDelegate.getDefaultNightMode();
        preferences.edit().remove("dark_mode").commit();
        BookmarksRepository.getInstance().clearAll();
        scenario = ActivityScenario.launch(MainActivity.class);
    }
    @After public void close() {
        if (scenario != null) scenario.close();
        if (hadDark) preferences.edit().putBoolean("dark_mode", oldDark).commit();
        else preferences.edit().remove("dark_mode").commit();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> AppCompatDelegate.setDefaultNightMode(oldMode));
        BookmarksRepository.getInstance().clearAll();
    }
    private void destination(int id) {
        scenario.onActivity(activity -> ((BottomNavigationView) activity.findViewById(R.id.bottom_navigation)).setSelectedItemId(id));
    }
    private void enterText() {
        onView(withId(R.id.translation_output)).perform(scrollTo(), replaceText("AEIOU x"), closeSoftKeyboard());
        CameraPreviewTest.await(scenario, activity -> ((RecyclerView) activity.findViewById(R.id.recycler_handshapes)).getAdapter().getItemCount() == 6);
    }
    @Test public void typedHandshapesAndBookmarksSurviveCameraAndNavigation() throws Exception {
        enterText();
        onView(withId(R.id.start_camera)).perform(scrollTo(), click()); CameraPreviewTest.await(scenario, CameraPreviewTest::active);
        scenario.onActivity(activity -> activity.findViewById(R.id.add_button).performClick());
        assertEquals("AEIOU x", BookmarksRepository.getInstance().getBookmarks().get(0).getWord());
        destination(R.id.nav_dictionary);
        CameraPreviewTest.await(scenario, activity -> activity.findViewById(R.id.recycler_bookmarks) != null);
        scenario.onActivity(activity -> assertEquals(1, ((RecyclerView) activity.findViewById(R.id.recycler_bookmarks)).getAdapter().getItemCount()));
        destination(R.id.nav_home); CameraPreviewTest.await(scenario, CameraPreviewTest::stopped);
        scenario.onActivity(activity -> assertEquals("AEIOU x", ((EditText) activity.findViewById(R.id.translation_output)).getText().toString()));
        CameraPreviewTest.screenshot("master-home-handshapes.png");
    }
    @Test public void profileAndAlphabetDrawerRoutesPreserveHomeAndReleaseCamera() {
        enterText();
        onView(withId(R.id.start_camera)).perform(scrollTo(), click()); CameraPreviewTest.await(scenario, CameraPreviewTest::active);
        scenario.onActivity(activity -> ((NavigationView) activity.findViewById(R.id.drawer_navigation)).getMenu().performIdentifierAction(R.id.nav_drawer_profile, 0));
        CameraPreviewTest.await(scenario, activity -> activity.findViewById(R.id.text_profile_name) != null);
        scenario.onActivity(activity -> ((NavigationView) activity.findViewById(R.id.drawer_navigation)).getMenu().performIdentifierAction(R.id.nav_drawer_alphabet, 0));
        CameraPreviewTest.await(scenario, activity -> activity.findViewById(R.id.recycler_alphabet) != null);
        scenario.onActivity(activity -> assertEquals(5, ((RecyclerView) activity.findViewById(R.id.recycler_alphabet)).getAdapter().getItemCount()));
        destination(R.id.nav_home); CameraPreviewTest.await(scenario, CameraPreviewTest::stopped);
        scenario.onActivity(activity -> assertEquals("AEIOU x", ((EditText) activity.findViewById(R.id.translation_output)).getText().toString()));
    }
    @Test public void settingsDarkModeRecreationKeepsEditorAndHandshapeStrip() throws Exception {
        enterText(); destination(R.id.nav_settings);
        CameraPreviewTest.await(scenario, activity -> activity.findViewById(R.id.switch_dark_mode) != null);
        scenario.onActivity(activity -> {
            com.google.android.material.materialswitch.MaterialSwitch toggle = activity.findViewById(R.id.switch_dark_mode);
            if (!toggle.isChecked()) toggle.performClick();
        });
        CameraPreviewTest.await(scenario, activity -> (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
        destination(R.id.nav_home);
        CameraPreviewTest.await(scenario, activity -> activity.findViewById(R.id.translation_output) != null
                && ((RecyclerView) activity.findViewById(R.id.recycler_handshapes)).getAdapter().getItemCount() == 6);
        scenario.onActivity(activity -> assertEquals("AEIOU x", ((EditText) activity.findViewById(R.id.translation_output)).getText().toString()));
        scenario.onActivity(activity -> {
            android.graphics.drawable.Drawable icon = androidx.core.content.ContextCompat.getDrawable(activity, R.drawable.ic_asl_a);
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(72, 72, android.graphics.Bitmap.Config.ARGB_8888);
            try {
                icon.setBounds(0, 0, 72, 72); icon.draw(new android.graphics.Canvas(bitmap));
                boolean lightOutline = false;
                for (int y = 0; y < 72; y++) for (int x = 0; x < 72; x++) {
                    int pixel = bitmap.getPixel(x, y);
                    if (android.graphics.Color.alpha(pixel) > 220 && android.graphics.Color.red(pixel) > 200) lightOutline = true;
                }
                assertTrue("Hand artwork must stay readable on dark cards", lightOutline);
            } finally { bitmap.recycle(); }
        });
        CameraPreviewTest.screenshot("master-home-dark.png");
    }
}
