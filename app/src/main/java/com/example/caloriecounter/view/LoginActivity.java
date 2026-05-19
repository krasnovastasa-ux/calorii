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

public class LoginActivity extends AppCompatActivity {
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
            String password = binding.etPassword.getText().toString().trim();
            if (email.isEmpty() || password.isEmpty()) return;

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
                    });
                }
            });
        });

        binding.btnRegister.setOnClickListener(v -> {
            String email = binding.etEmail.getText().toString().trim();
            String password = binding.etPassword.getText().toString().trim();
            if (email.isEmpty() || password.isEmpty()) return;
            if (password.length() < 6) return;

            Log.d(TAG, "Регистрация: " + email);
            binding.progressBar.setVisibility(android.view.View.VISIBLE);
            binding.btnRegister.setEnabled(false);

            repo.signUp(email, password, new SupabaseRepository.SignUpCallback() {
                @Override
                public void onSuccess(AuthResponse response) {
                    Log.d(TAG, "Регистрация: " + response.user.id);
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


}