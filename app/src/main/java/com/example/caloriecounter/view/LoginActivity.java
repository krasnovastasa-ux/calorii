package com.example.caloriecounter.view;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import com.example.caloriecounter.databinding.ActivityLoginBinding;
import com.example.caloriecounter.repository.SupabaseRepository;
import com.example.caloriecounter.model.AuthResponse;
import com.example.caloriecounter.model.ProfileData;

public class LoginActivity extends BaseActivity {
    private ActivityLoginBinding binding;
    private SupabaseRepository repo;
    private static final String TAG = "AUTH";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        repo = new SupabaseRepository(this);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        if (prefs.contains("user_id") && prefs.contains("token")) {
            Log.d(TAG, "Уже авторизован, проверяем профиль");
            checkProfileAndNavigate();
            return;
        }

        String savedEmail = prefs.getString("email", "");
        if (!savedEmail.isEmpty()) {
            binding.etEmail.setText(savedEmail);
            binding.etPassword.requestFocus();
        }

        binding.btnLogin.setOnClickListener(v -> {
            String email = binding.etEmail.getText().toString().trim();
            String password = binding.etPassword.getText().toString();
            if (!validateCredentials(email, password, false)) return;

            binding.progressBar.setVisibility(android.view.View.VISIBLE);
            binding.btnLogin.setEnabled(false);

            repo.signIn(email, password, new SupabaseRepository.SignInCallback() {
                @Override
                public void onSuccess(AuthResponse response) {
                    Log.d(TAG, "Вход: " + response.user.id);
                    saveAuthData(response);
                    ensureProfileExists(response.user.id, response.user.email, () -> checkProfileAndNavigate());
                }
                @Override
                public void onError(String msg) {
                    Log.e(TAG, "Ошибка входа: " + msg);
                    runOnUiThread(() -> {
                        binding.progressBar.setVisibility(android.view.View.GONE);
                        binding.btnLogin.setEnabled(true);
                        showAuthError(msg);
                    });
                }
            });
        });


        binding.tvAgreement.setOnClickListener(v -> {

            android.webkit.WebView webView = new android.webkit.WebView(this);

            webView.loadUrl("file:///android_asset/agreement.html");

            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Условия использования")
                    .setView(webView)
                    .setPositiveButton("Понятно", (dialog, which) -> {
                        webView.destroy();
                    })
                    .show();

        });


        binding.btnRegister.setOnClickListener(v -> {
            String email = binding.etEmail.getText().toString().trim();
            String password = binding.etPassword.getText().toString();
            if (!validateCredentials(email, password, true)) return;

            Log.d(TAG, "Регистрация: " + email);
            binding.progressBar.setVisibility(android.view.View.VISIBLE);
            binding.btnRegister.setEnabled(false);

            repo.signUp(email, password, new SupabaseRepository.SignUpCallback() {
                @Override
                public void onSuccess(AuthResponse response) {
                    if (response == null || response.user == null || response.user.id == null) {
                        binding.progressBar.setVisibility(android.view.View.GONE);
                        binding.btnRegister.setEnabled(true);
                        showAuthError("Не удалось создать аккаунт. Повторите попытку.");
                        return;
                    }
                    if (response.accessToken == null || response.accessToken.isEmpty()) {
                        binding.progressBar.setVisibility(android.view.View.GONE);
                        binding.btnRegister.setEnabled(true);
                        new androidx.appcompat.app.AlertDialog.Builder(LoginActivity.this)
                            .setTitle("Подтвердите email")
                            .setMessage("Проверьте почту и подтвердите адрес по ссылке, затем нажмите «Войти». Если аккаунт уже существует, попробуйте войти.")
                            .setPositiveButton("Понятно", null).show();
                        return;
                    }
                    Log.d(TAG, "Регистрация завершена");
                    saveAuthData(response);

                    ensureProfileExists(response.user.id, response.user.email, () -> {
                        startActivity(new Intent(LoginActivity.this, ProfileSetupActivity.class)
                                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                        finish();
                    });
                }
                @Override
                public void onError(String msg) {
                    Log.e(TAG, " Ошибка регистрации: " + msg);
                    runOnUiThread(() -> {
                        binding.progressBar.setVisibility(android.view.View.GONE);
                        binding.btnRegister.setEnabled(true);
                        showAuthError(msg);
                    });
                }
            });
        });
    }

    private void saveAuthData(AuthResponse response) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit()
                .clear()
                .putString("user_id", response.user.id)
                .putString("email", response.user.email)
                .putString("token", response.accessToken)
                .putString("refresh_token", response.refreshToken)
                .putBoolean("is_logged_in", true)
                .apply();
    }

    private void ensureProfileExists(String userId, String email, Runnable onComplete) {
        repo.fetchProfile(new SupabaseRepository.ProfileLoadedCallback() {
            @Override
            public void onProfileLoaded(ProfileData profile) {
                Log.d(TAG, "Профиль найден в БД: " + (profile.name != null ? profile.name : "пустой"));
                if (profile.name != null && !profile.name.isEmpty()) {
                    saveProfileToPrefs(profile);
                }
                onComplete.run();
            }
            @Override
            public void onError(String m) {
                Log.d(TAG, " Профиль не найден, создаём минимальный");
                ProfileData newProfile = new ProfileData();
                newProfile.id = userId;
                newProfile.userId = userId;
                newProfile.email = email;
                newProfile.name = "";
                newProfile.height = 170;
                newProfile.weight = 60;
                newProfile.age = 25;
                newProfile.gender = "Женский";
                newProfile.goal = "maintain";
                newProfile.lifestyle = "moderate";

                repo.updateProfile(newProfile, new SupabaseRepository.VoidCallback() {
                    @Override public void onSuccess() {
                        Log.d(TAG, "Минимальный профиль создан в БД");
                        onComplete.run();
                    }
                    @Override public void onError(String err) {
                        Log.e(TAG, "Не удалось создать профиль: " + err);
                        onComplete.run();
                    }
                });
            }
        });
    }

    private void saveProfileToPrefs(ProfileData p) {
        if (p == null || p.name == null || p.name.isEmpty()) return;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit()
                .putString("name", p.name)
                .putInt("height", p.height != null ? p.height : 0)
                .putInt("weight", p.weight != null ? p.weight : 0)
                .putInt("age", p.age != null ? p.age : 0)
                .putString("gender", p.gender)
                .putString("goal", p.goal)
                .putString("lifestyle", p.lifestyle)
                .apply();
        Log.d(TAG, "Профиль сохранён в кэш: " + p.name);
    }

    private void checkProfileAndNavigate() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String name = prefs.getString("name", null);

        Log.d(TAG, "checkProfileAndNavigate: name из кэша = '" + name + "'");
        if (name != null && !name.isEmpty()) {
            Log.d(TAG, "Профиль в кэше, идём в MainActivity");
            startActivity(new Intent(this, MainActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
            return;
        }

        Log.d(TAG, "кэш пуст или имя пустое, загружаю из БД");
        repo.fetchProfile(new SupabaseRepository.ProfileLoadedCallback() {
            @Override
            public void onProfileLoaded(ProfileData profile) {
                Log.d(TAG, "onProfileLoaded: profile=" + (profile != null ? "OK" : "null") +
                        ", name='" + (profile != null ? profile.name : "N/A") + "'");

                if (profile != null && profile.name != null && !profile.name.isEmpty()) {
                    Log.d(TAG, "Профиль из БД заполнен: " + profile.name);
                    saveProfileToPrefs(profile);
                    startActivity(new Intent(LoginActivity.this, MainActivity.class)
                            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                } else {
                    Log.d(TAG, "⚠Профиль из БД пустой (name='" + (profile != null ? profile.name : "null") + "'), идём на онбординг");
                    startActivity(new Intent(LoginActivity.this, ProfileSetupActivity.class)
                            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                }
                finish();
            }
            @Override
            public void onError(String m) {
                Log.e(TAG, "Ошибка загрузки профиля: " + m);
                startActivity(new Intent(LoginActivity.this, ProfileSetupActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                finish();
            }
        });
    }


    private boolean validateCredentials(String email, String password, boolean registration) {
        binding.etEmail.setError(null);
        binding.etPassword.setError(null);
        boolean valid = true;
        if (email.isEmpty()) {
            binding.etEmail.setError("Введите email, например name@example.com"); valid=false;
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.setError("Нужен email с @ и доменом, например name@example.com"); valid=false;
        }
        if (password.isEmpty()) {
            binding.etPassword.setError("Введите пароль"); valid=false;
        } else if (registration && password.length()<6) {
            binding.etPassword.setError("Пароль должен содержать минимум 6 символов"); valid=false;
        }
        if (!valid) {
            if (binding.etEmail.getError()!=null) binding.etEmail.requestFocus();
            else binding.etPassword.requestFocus();
        }
        return valid;
    }
    private void showAuthError(String message) {
        String raw=message==null?"":message;
        String lower=raw.toLowerCase(java.util.Locale.ROOT);
        String text;
        if(lower.contains("weak")||lower.contains("password")) text="Пароль не соответствует требованиям сервера. Минимум 6 символов; сервер может требовать более сложный пароль.";
        else if(lower.contains("already")||lower.contains("registered")) text="Этот email уже зарегистрирован. Нажмите «Войти».";
        else if(lower.contains("invalid login")||lower.contains("credentials")) text="Неверный email или пароль.";
        else if(lower.contains("confirm")) text="Подтвердите email по ссылке из письма перед входом.";
        else if(lower.contains("rate")||lower.contains("too many")) text="Слишком много попыток. Подождите и попробуйте снова.";
        else if(lower.contains("email")) text="Сервер не принял email. Проверьте адрес почты.";
        else if(lower.contains("network")||lower.contains("connect")) text="Нет связи с сервером. Проверьте интернет.";
        else text=raw.isEmpty()?"Не удалось выполнить запрос. Попробуйте снова.":raw;
        new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Не получилось")
            .setMessage(text).setPositiveButton("Понятно",null).show();
    }
}