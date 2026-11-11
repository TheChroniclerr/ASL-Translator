package com.group_nine.asl_translator;

import android.view.MenuItem;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;

/** Release builds have no collection menu or collection activity. */
final class CollectionTools {
    static void install(AppCompatActivity activity, MaterialToolbar toolbar) { }
    static boolean handle(AppCompatActivity activity, MenuItem item) { return false; }
}
