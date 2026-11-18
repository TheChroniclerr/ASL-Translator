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
import com.group_nine.asl_translator.database.ASLDataSource;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText editEmail, editPassword;
    private ProgressBar loginProgress;
    private MaterialButton btnLogin;
    private ASLDataSource datasource;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();

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

        datasource = new ASLDataSource(this);

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

    @Override
    protected void onDestroy() {
        databaseExecutor.shutdown();
        if (datasource != null) {
            datasource.close();
        }
        super.onDestroy();
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

        databaseExecutor.execute(() -> {
            datasource.open();
            ASLDataSource.UserAccount user = datasource.authenticateUser(email, password);

            SharedPreferences prefs = getSharedPreferences("ASL_User_Prefs", Context.MODE_PRIVATE);
            String savedEmail = prefs.getString("saved_email", "");
            String savedPassword = prefs.getString("saved_password", "");

            boolean migrated = false;
            if (user == null && email.equals(savedEmail) && password.equals(savedPassword) && !savedEmail.isEmpty()) {
                String savedName = prefs.getString("user_name", "ASL User");
                long memberSince = prefs.getLong("member_since", System.currentTimeMillis());
                datasource.registerUser(savedName, email, password, memberSince);
                migrated = true;
            }
            datasource.close();

            final boolean isMigrated = migrated;
            runOnUiThread(() -> {
                if (user != null) {
                    prefs.edit()
                            .putBoolean("is_logged_in", true)
                            .putString("user_name", user.name)
                            .putString("saved_email", user.email)
                            .putString("user_email", user.email)
                            .putString("saved_password", user.password)
                            .putString("user_password", user.password)
                            .putLong("member_since", user.memberSince)
                            .apply();

                    Snackbar.make(findViewById(android.R.id.content), "Signed in successfully!", Snackbar.LENGTH_SHORT).show();
                    findViewById(android.R.id.content).postDelayed(() -> {
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    }, 600);
                } else if (isMigrated) {
                    prefs.edit().putBoolean("is_logged_in", true).apply();
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
            });
        });
    }
}