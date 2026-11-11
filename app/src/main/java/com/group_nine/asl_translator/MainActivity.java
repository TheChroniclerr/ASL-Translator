package com.group_nine.asl_translator;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.group_nine.asl_translator.auth.LoginActivity;
import com.group_nine.asl_translator.dictionary.DictionaryFragment;
import com.group_nine.asl_translator.history.HistoryFragment;
import com.group_nine.asl_translator.home.HomeFragment;
import com.group_nine.asl_translator.settings.SettingsFragment;

/** Shared chrome and destination switching. Feature UI belongs to Fragments. */
public class MainActivity extends AppCompatActivity {
    private static final String STATE_DESTINATION = "destination";

    private int destination = R.id.nav_home;
    private DrawerLayout drawer;
    private BottomNavigationView navigation;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        drawer = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(drawer, (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            // DrawerLayout positions its children without honoring its own padding.
            // Apply safe areas to the content shell and drawer contents instead.
            int keyboardBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            findViewById(R.id.main_shell).setPadding(safe.left, safe.top, safe.right, Math.max(safe.bottom, keyboardBottom));
            findViewById(R.id.bottom_chrome).setVisibility(insets.isVisible(WindowInsetsCompat.Type.ime())
                    ? android.view.View.GONE : android.view.View.VISIBLE);
            findViewById(R.id.drawer_navigation).setPadding(safe.left, safe.top, safe.right, safe.bottom);
            // Shell owns insets; prevent Material children from applying them again.
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(drawer);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        CollectionTools.install(this, toolbar);
        toolbar.setNavigationOnClickListener(view -> drawer.openDrawer(GravityCompat.START));
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_help) {
                showInfo(R.string.help, R.string.help_message);
            } else if (id == R.id.action_about) {
                showInfo(R.string.about, R.string.about_message);
            } else if (id == R.id.action_logout) {
                performLogout();
            } else {
                return CollectionTools.handle(this, item);
            }
            return true;
        });

        NavigationView drawerNavigation = findViewById(R.id.drawer_navigation);
        drawerNavigation.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_logout) {
                performLogout();
                return true;
            }
            drawer.closeDrawer(GravityCompat.START);
            return true;
        });

        navigation = findViewById(R.id.bottom_navigation);
        navigation.setSaveEnabled(false);
        navigation.setItemActiveIndicatorEnabled(false);
        if (state != null) {
            destination = state.getInt(STATE_DESTINATION, R.id.nav_home);
        }
        showDestination(destination);
        navigation.setSelectedItemId(destination);
        navigation.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_add) {
                showInfo(R.string.add, R.string.add_message);
                return false;
            }
            showDestination(item.getItemId());
            return true;
        });
        findViewById(R.id.add_button).setOnClickListener(view ->
                showInfo(R.string.add, R.string.add_message));

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawer.isDrawerOpen(GravityCompat.START)) {
                    drawer.closeDrawer(GravityCompat.START);
                } else if (destination != R.id.nav_home) {
                    navigation.setSelectedItemId(R.id.nav_home);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });
    }

    private void performLogout() {
        SharedPreferences prefs = getSharedPreferences("ASL_User_Prefs", Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean("is_logged_in", false)
                .apply();

        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showDestination(int id) {
        String tag;
        if (id == R.id.nav_recents) {
            tag = "recents";
        } else if (id == R.id.nav_dictionary) {
            tag = "dictionary";
        } else if (id == R.id.nav_settings) {
            tag = "settings";
        } else {
            id = R.id.nav_home;
            tag = "home";
        }

        Fragment target = getSupportFragmentManager().findFragmentByTag(tag);
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            transaction.hide(fragment).setMaxLifecycle(fragment, Lifecycle.State.STARTED);
        }
        if (target == null) {
            switch (tag) {
                case "recents":
                    target = new HistoryFragment();
                    break;
                case "dictionary":
                    target = new DictionaryFragment();
                    break;
                case "settings":
                    target = new SettingsFragment();
                    break;
                default:
                    target = new HomeFragment();
            }
            transaction.add(R.id.content, target, tag);
        } else {
            transaction.show(target);
        }
        transaction.setMaxLifecycle(target, Lifecycle.State.RESUMED);
        transaction.setPrimaryNavigationFragment(target);
        transaction.commitNow();
        destination = id;
    }

    private void showInfo(int title, int message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        out.putInt(STATE_DESTINATION, destination);
        super.onSaveInstanceState(out);
    }
}
