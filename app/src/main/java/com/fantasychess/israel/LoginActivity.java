package com.fantasychess.israel;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.fantasychess.israel.data.repo.FantasyRepository;

/**
 * Local login/registration gate. There is no game server yet, so the account
 * lives on this device only: registering creates it, logging in verifies the
 * stored SHA-256 password hash. Replace with real authentication when a
 * backend exists.
 */
public class LoginActivity extends AppCompatActivity {

    private FantasyRepository repository;
    private boolean registerMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = ((FantasyChessApplication) getApplication()).getRepository();

        if (repository.isLoggedIn() && getIntent().getBooleanExtra(EXTRA_AUTO_SKIP, true)) {
            proceed();
            return;
        }

        setContentView(R.layout.activity_login);
        registerMode = !repository.isLoggedIn();
        bindMode();

        Button action = findViewById(R.id.login_btn_action);
        action.setOnClickListener(v -> submit());

        TextView toggle = findViewById(R.id.login_toggle);
        toggle.setOnClickListener(v -> {
            registerMode = !registerMode;
            bindMode();
        });
    }

    private void bindMode() {
        ((Button) findViewById(R.id.login_btn_action)).setText(
                registerMode ? R.string.login_register : R.string.login_sign_in);
        ((TextView) findViewById(R.id.login_toggle)).setText(
                registerMode ? R.string.login_have_account : R.string.login_no_account);
        findViewById(R.id.login_error).setVisibility(View.INVISIBLE);
    }

    private void submit() {
        String username = ((EditText) findViewById(R.id.login_username)).getText()
                .toString().trim();
        String password = ((EditText) findViewById(R.id.login_password)).getText().toString();
        TextView error = findViewById(R.id.login_error);

        if (username.length() < 2 || password.length() < 4) {
            error.setText(R.string.login_error_invalid);
            error.setVisibility(View.VISIBLE);
            return;
        }
        boolean ok = registerMode
                ? repository.register(username, password)
                : repository.login(username, password);
        if (!ok) {
            error.setText(registerMode
                    ? R.string.login_error_exists : R.string.login_error_wrong);
            error.setVisibility(View.VISIBLE);
            return;
        }
        proceed();
    }

    private void proceed() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    public static final String EXTRA_AUTO_SKIP = "auto_skip";
}
