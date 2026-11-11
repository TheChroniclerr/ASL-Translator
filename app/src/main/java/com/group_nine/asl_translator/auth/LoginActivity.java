package com.group_nine.asl_translator.auth;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.group_nine.asl_translator.MainActivity;
import com.group_nine.asl_translator.R;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText editEmail, editPassword;
    private ProgressBar loginProgress;
    private MaterialButton btnLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = getSharedPreferences("ASL_User_Prefs", Context.MODE_PRIVATE);
        if (prefs.getBoolean("is_logged_in", false)) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        editEmail = findViewById(R.id.edit_login_email);
        editPassword = findViewById(R.id.edit_login_password);
        loginProgress = findViewById(R.id.login_progress);
        btnLogin = findViewById(R.id.btn_login);
        TextView btnLinkSignUp = findViewById(R.id.btn_link_sign_up);

        btnLinkSignUp.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });

        btnLogin.setOnClickListener(v -> handleLogin());

    }

    private void handleLogin() {
        String email = editEmail.getText() != null ? editEmail.getText().toString().trim() : "";
        String password = editPassword.getText() != null ? editPassword.getText().toString().trim() : "";

        if (email.isEmpty()) {
            editEmail.setError("Email required");
            editEmail.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            editPassword.setError("Password required");
            editPassword.requestFocus();
            return;
        }

        loginProgress.setVisibility(View.VISIBLE);
        btnLogin.setEnabled(false);

        SharedPreferences prefs = getSharedPreferences("ASL_User_Prefs", Context.MODE_PRIVATE);
        String savedEmail = prefs.getString("saved_email", "");
        String savedPassword = prefs.getString("saved_password", "");

        if (savedEmail.isEmpty() || (email.equals(savedEmail) && password.equals(savedPassword))) {
            prefs.edit().putBoolean("is_logged_in", true).putString("saved_email", email).apply();

            Snackbar.make(findViewById(android.R.id.content), "Signed in successfully!", Snackbar.LENGTH_SHORT).show();
            findViewById(android.R.id.content).postDelayed(() -> {
                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                finish();
            }, 600);
        } else {
            loginProgress.setVisibility(View.GONE);
            btnLogin.setEnabled(true);
            Snackbar.make(findViewById(android.R.id.content), "Invalid email or password", Snackbar.LENGTH_SHORT).show();
        }
    }
}





