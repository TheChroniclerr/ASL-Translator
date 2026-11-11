package com.group_nine.asl_translator.profile;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.group_nine.asl_translator.R;
import com.group_nine.asl_translator.auth.LoginActivity;
import com.group_nine.asl_translator.bookmarks.BookmarksRepository;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ProfileFragment extends Fragment {
    private static final String PREFS_NAME = "ASL_User_Prefs";
    private static final String KEY_MEMBER_SINCE = "member_since";

    private TextView textAvatarInitials;
    private TextView textProfileName;
    private TextView textProfileEmail;
    private TextView textMemberSince;
    private TextView textRowName;
    private TextView textRowEmail;

    public ProfileFragment() {
        super(R.layout.fragment_profile);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        textAvatarInitials = view.findViewById(R.id.text_avatar_initials);
        textProfileName = view.findViewById(R.id.text_profile_name);
        textProfileEmail = view.findViewById(R.id.text_profile_email);
        textMemberSince = view.findViewById(R.id.text_member_since);
        textRowName = view.findViewById(R.id.text_row_name);
        textRowEmail = view.findViewById(R.id.text_row_email);

        loadUserData(prefs);

        view.findViewById(R.id.row_edit_profile_name).setOnClickListener(v -> showEditNameDialog(prefs));

        view.findViewById(R.id.row_change_password).setOnClickListener(v -> showChangePasswordDialog(prefs));

        view.findViewById(R.id.btn_profile_logout).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.log_out)
                        .setMessage("Are you sure you want to sign out?")
                        .setPositiveButton(R.string.log_out, (dialog, which) -> performLogout(prefs))
                        .setNegativeButton("Cancel", null)
                        .show());

        view.findViewById(R.id.btn_delete_account).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Delete Account")
                        .setMessage("Are you sure you want to delete your account? All saved bookmarks, logs, and settings will be permanently erased.")
                        .setPositiveButton("Delete Forever", (dialog, which) -> performAccountDeletion(prefs))
                        .setNegativeButton("Cancel", null)
                        .show());
    }

    private void loadUserData(SharedPreferences prefs) {
        String name = prefs.getString("user_name", prefs.getString("name", "ASL User"));
        String email = prefs.getString("user_email", prefs.getString("saved_email", "Signed in"));

        textProfileName.setText(name);
        textRowName.setText(name);
        textProfileEmail.setText(email);
        textRowEmail.setText(email);
        textAvatarInitials.setText(calculateInitials(name));

        long memberSinceTimestamp = prefs.getLong(KEY_MEMBER_SINCE, 0);
        if (memberSinceTimestamp == 0) {
            memberSinceTimestamp = System.currentTimeMillis();
            prefs.edit().putLong(KEY_MEMBER_SINCE, memberSinceTimestamp).apply();
        }
        SimpleDateFormat formatter = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        textMemberSince.setText("Member since " + formatter.format(new Date(memberSinceTimestamp)));
    }

    private String calculateInitials(String name) {
        if (name == null || name.trim().isEmpty()) return "U";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        } else {
            return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
        }
    }

    private void showEditNameDialog(SharedPreferences prefs) {
        EditText input = new EditText(requireContext());
        input.setSingleLine(true);
        input.setText(textProfileName.getText());
        input.setSelection(input.getText().length());

        int paddingPx = Math.round(20 * getResources().getDisplayMetrics().density);
        input.setPadding(paddingPx, paddingPx / 2, paddingPx, paddingPx / 2);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Edit Display Name")
                .setMessage("Enter your full name:")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        prefs.edit().putString("user_name", newName).apply();
                        textProfileName.setText(newName);
                        textRowName.setText(newName);
                        textAvatarInitials.setText(calculateInitials(newName));
                        Toast.makeText(requireContext(), "Name updated", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showChangePasswordDialog(SharedPreferences prefs) {
        Context context = requireContext();
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, pad / 2, pad, pad / 2);

        TextInputLayout oldLayout = new TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        oldLayout.setHint("Current Password");
        oldLayout.setPasswordVisibilityToggleEnabled(true);
        TextInputEditText oldInput = new TextInputEditText(context);
        oldInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        oldLayout.addView(oldInput);
        layout.addView(oldLayout);

        TextInputLayout newLayout = new TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        newLayout.setHint("New Password (min 6 characters)");
        newLayout.setPasswordVisibilityToggleEnabled(true);
        newLayout.setPadding(0, pad / 2, 0, 0);
        TextInputEditText newInput = new TextInputEditText(context);
        newInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        newLayout.addView(newInput);
        layout.addView(newLayout);

        new MaterialAlertDialogBuilder(context)
                .setTitle("Change Password")
                .setView(layout)
                .setPositiveButton("Update", (dialog, which) -> {
                    String oldPass = oldInput.getText() != null ? oldInput.getText().toString().trim() : "";
                    String newPass = newInput.getText() != null ? newInput.getText().toString().trim() : "";
                    String currentSavedPass = prefs.getString("user_password", prefs.getString("saved_password", ""));

                    if (oldPass.isEmpty()) {
                        Toast.makeText(context, "Enter your current password", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!currentSavedPass.equals(oldPass)) {
                        Toast.makeText(context, "Current password is incorrect", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (newPass.length() < 6) {
                        Toast.makeText(context, "New password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (newPass.equals(oldPass)) {
                        Toast.makeText(context, "New password must be different", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    prefs.edit()
                            .putString("user_password", newPass)
                            .putString("saved_password", newPass)
                            .apply();
                    Toast.makeText(context, "Password updated successfully", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performAccountDeletion(SharedPreferences prefs) {
        prefs.edit().clear().apply();
        BookmarksRepository.getInstance().clearAll();

        Toast.makeText(requireContext(), "Account deleted", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    private void performLogout(SharedPreferences prefs) {
        prefs.edit().putBoolean("is_logged_in", false).apply();
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }
}