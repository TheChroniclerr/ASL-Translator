package com.group_nine.asl_translator;

import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.group_nine.asl_translator.home.SampleCollectionActivity;
import com.group_nine.asl_translator.home.HomeFragment;

/** Compile-time debug variant of the optional collection entry point. */
final class CollectionTools {
    private static final int COLLECT = 0x470001;
    static void install(AppCompatActivity activity, MaterialToolbar toolbar) {
        toolbar.getMenu().add(Menu.NONE, COLLECT, Menu.NONE, R.string.collect_samples)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
    }
    static boolean handle(AppCompatActivity activity, MenuItem item) {
        if (item.getItemId() != COLLECT) return false;
        androidx.fragment.app.Fragment home = activity.getSupportFragmentManager().findFragmentByTag("home");
        if (home instanceof HomeFragment) ((HomeFragment) home).stopForExternalInput();
        activity.startActivity(new Intent(activity, SampleCollectionActivity.class));
        return true;
    }
}
