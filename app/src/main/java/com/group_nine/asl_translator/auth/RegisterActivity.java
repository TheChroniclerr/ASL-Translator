package com.group_nine.asl_translator.auth;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.group_nine.asl_translator.MainActivity;
import com.group_nine.asl_translator.R;

public class RegisterActivity extends AppCompatActivity {
    private TextInputEditText editName, editEmail, editPassword;
    private ProgressBar registerProgress;
    private MaterialButton btnCreateAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        editName = findViewById(R.id.edit_name);
        editEmail = findViewById(R.id.edit_register_email);
        editPassword = findViewById(R.id.edit_register_password);
        registerProgress = findViewById(R.id.register_progress);
        btnCreateAccount = findViewById(R.id.btn_create_account);
        ImageView btnBack = findViewById(R.id.btn_back_to_login);
        TextView btnLinkSignIn = findViewById(R.id.btn_link_sign_in);

        btnBack.setOnClickListener(v -> finish());
        btnLinkSignIn.setOnClickListener(v -> finish());

        btnCreateAccount.setOnClickListener(v -> handleRegistration());
    }

    private void handleRegistration() {
        String name = editName.getText() != null ? editName.getText().toString().trim() : "";
        String email = editEmail.getText() != null ? editEmail.getText().toString().trim() : "";
        String password = editPassword.getText() != null ? editPassword.getText().toString().trim() : "";

        if (name.isEmpty()) {
            editName.setError("Name is required");
            editName.requestFocus();
            return;
        }

        if (email.isEmpty() || !email.contains("@")) {
            editEmail.setError("Valid email required");
            editEmail.requestFocus();
            return;
        }

        if (password.length() < 6) {
            editPassword.setError("Minimum 6 characters");
            editPassword.requestFocus();
            return;
        }

        registerProgress.setVisibility(View.VISIBLE);
        btnCreateAccount.setEnabled(false);

        SharedPreferences prefs = getSharedPreferences("ASL_User_Prefs", Context.MODE_PRIVATE);
        prefs.edit()
                .putString("user_name", name)
                .putString("saved_email", email)
                .putString("user_email", email)
                .putString("saved_password", password)
                .putString("user_password", password)
                .putLong("member_since", System.currentTimeMillis())
                .putBoolean("is_logged_in", true)
                .apply();

        Snackbar.make(findViewById(android.R.id.content), "Welcome, " + name + "!", Snackbar.LENGTH_SHORT).show();

        findViewById(android.R.id.content).postDelayed(() -> {
            Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }, 700);
    }
}