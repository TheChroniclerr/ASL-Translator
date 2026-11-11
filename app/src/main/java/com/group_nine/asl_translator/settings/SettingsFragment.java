package com.group_nine.asl_translator.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.group_nine.asl_translator.R;
import com.group_nine.asl_translator.auth.LoginActivity;
import com.group_nine.asl_translator.bookmarks.BookmarksRepository;

import java.io.File;

public class SettingsFragment extends Fragment {
    private static final String PREFS_NAME = "ASL_User_Prefs";
    private static final String KEY_DARK_MODE = "dark_mode";

    private TextView textCacheSize;
    private TextView textBookmarksCount;
    private TextView textCurrentLanguage;

    public SettingsFragment() {
        super(R.layout.fragment_settings);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        TextView textAccountName = view.findViewById(R.id.text_account_name);
        TextView textAccountEmail = view.findViewById(R.id.text_account_email);

        String savedName = prefs.getString("user_name", prefs.getString("name", "ASL Translator User"));
        String savedEmail = prefs.getString("user_email", prefs.getString("email", "Signed in"));
        textAccountName.setText(savedName);
        textAccountEmail.setText(savedEmail);

        view.findViewById(R.id.btn_settings_logout).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.log_out)
                        .setMessage("Are you sure you want to sign out of your account?")
                        .setPositiveButton(R.string.log_out, (dialog, which) -> performLogout(prefs))
                        .setNegativeButton("Cancel", null)
                        .show());

        MaterialSwitch switchDarkMode = view.findViewById(R.id.switch_dark_mode);
        boolean isNightCurrently = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        switchDarkMode.setChecked(prefs.getBoolean(KEY_DARK_MODE, isNightCurrently));

        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply();
            AppCompatDelegate.setDefaultNightMode(
                    isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        });

        textCurrentLanguage = view.findViewById(R.id.text_current_language);
        updateLanguageLabel();
        view.findViewById(R.id.row_app_language).setOnClickListener(v -> showLanguageDialog());

        textCacheSize = view.findViewById(R.id.text_cache_size);
        updateCacheSizeDisplay();
        view.findViewById(R.id.row_clear_cache).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Clear App Cache")
                        .setMessage("Clear temporary cached files?")
                        .setPositiveButton("Clear", (dialog, which) -> {
                            clearDir(requireContext().getCacheDir());
                            if (requireContext().getExternalCacheDir() != null) {
                                clearDir(requireContext().getExternalCacheDir());
                            }
                            updateCacheSizeDisplay();
                            Toast.makeText(requireContext(), "Cache cleared", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show());

        textBookmarksCount = view.findViewById(R.id.text_bookmarks_count);
        updateBookmarksCountDisplay();
        view.findViewById(R.id.row_clear_bookmarks).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Clear All Bookmarks")
                        .setMessage("This will permanently remove all saved sign bookmarks.")
                        .setPositiveButton("Clear All", (dialog, which) -> {
                            BookmarksRepository.getInstance().clearAll();
                            updateBookmarksCountDisplay();
                            Toast.makeText(requireContext(), "All bookmarks cleared", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show());

        view.findViewById(R.id.row_clear_history).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Clear Translation History")
                        .setMessage("Delete all recent translation history?")
                        .setPositiveButton("Clear All", (dialog, which) -> {
                            // Will connect to HistoryRepository once Recents is built
                            Toast.makeText(requireContext(), "History cleared", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show());

        TextView textVersion = view.findViewById(R.id.text_app_version);
        textVersion.setText(getAppVersionText());
    }

    @Override
    public void onResume() {
        super.onResume();
        updateCacheSizeDisplay();
        updateBookmarksCountDisplay();
        updateLanguageLabel();
    }

    private void showLanguageDialog() {
        final String[] labels = {"System Default", "English", "Español (Spanish)", "Français (French)"};
        final String[] tags = {"", "en", "es", "fr"};

        LocaleListCompat currentLocales = AppCompatDelegate.getApplicationLocales();
        String currentTag = currentLocales.isEmpty() ? "" : currentLocales.toLanguageTags();

        int checkedIndex = 0;
        for (int i = 0; i < tags.length; i++) {
            if (!tags[i].isEmpty() && currentTag.startsWith(tags[i])) {
                checkedIndex = i;
                break;
            }
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("App Language")
                .setSingleChoiceItems(labels, checkedIndex, (dialog, which) -> {
                    LocaleListCompat appLocale = tags[which].isEmpty()
                            ? LocaleListCompat.getEmptyLocaleList()
                            : LocaleListCompat.forLanguageTags(tags[which]);
                    AppCompatDelegate.setApplicationLocales(appLocale);
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateLanguageLabel() {
        if (textCurrentLanguage == null) return;
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        if (locales.isEmpty() || locales.get(0) == null) {
            textCurrentLanguage.setText("System Default");
        } else {
            String display = locales.get(0).getDisplayLanguage(locales.get(0));
            if (!display.isEmpty()) {
                display = display.substring(0, 1).toUpperCase() + display.substring(1);
            }
            textCurrentLanguage.setText(display);
        }
    }

    private void updateCacheSizeDisplay() {
        if (textCacheSize == null) return;
        long bytes = getDirSize(requireContext().getCacheDir());
        if (requireContext().getExternalCacheDir() != null) {
            bytes += getDirSize(requireContext().getExternalCacheDir());
        }
        textCacheSize.setText(Formatter.formatShortFileSize(requireContext(), bytes));
    }

    private void updateBookmarksCountDisplay() {
        if (textBookmarksCount == null) return;
        int count = BookmarksRepository.getInstance().getBookmarks().size();
        textBookmarksCount.setText(count == 1
                ? "1 saved bookmark"
                : count + " saved bookmarks");
    }

    private long getDirSize(File dir) {
        if (dir == null || !dir.exists()) return 0;
        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                size += file.isDirectory() ? getDirSize(file) : file.length();
            }
        }
        return size;
    }

    private void clearDir(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    clearDir(file);
                }
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    private String getAppVersionText() {
        try {
            PackageInfo info = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0);
            long buildNumber = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? info.getLongVersionCode()
                    : info.versionCode;
            return "Version " + info.versionName + " (Build " + buildNumber + ")";
        } catch (PackageManager.NameNotFoundException e) {
            return "Version 1.0 (Build 1)";
        }
    }

    private void performLogout(SharedPreferences prefs) {
        prefs.edit().putBoolean("is_logged_in", false).apply();
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }
}
