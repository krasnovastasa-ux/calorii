package com.example.caloriecounter.view;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.example.caloriecounter.R;
import com.example.caloriecounter.databinding.ActivityProfileSetupBinding;
import com.example.caloriecounter.model.ProfileData;
import com.example.caloriecounter.repository.SupabaseRepository;
import com.example.caloriecounter.viewmodel.ProfileViewModel;

public class ProfileSetupActivity extends AppCompatActivity {
    private ActivityProfileSetupBinding binding;
    private ProfileViewModel viewModel;
    private SharedPreferences prefs;
    private String userId, email;
    private String[] gendersArr, goalsArr, lifestylesArr;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileSetupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        gendersArr = getResources().getStringArray(R.array.genders);
        goalsArr = getResources().getStringArray(R.array.goals);
        lifestylesArr = getResources().getStringArray(R.array.lifestyles);

        userId = prefs.getString("user_id", "");
        email = prefs.getString("email", "");
        if (userId.isEmpty()) {
            new SupabaseRepository(this).clearSession();
            startActivity(new Intent(this, LoginActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish(); return;
        }
        binding.tvEmail.setText(email);
        setupSpinners();
        loadFromPrefs();
        setupObservers();

        String savedName = prefs.getString("name", "");
        if (savedName != null && !savedName.isEmpty()) {
            viewModel.loadProfile(userId);
        }
        observeProfileData();
        setupClicks();
    }

    private void setupSpinners() {
        ArrayAdapter<String> g = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, gendersArr);
        g.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerGender.setAdapter(g);
        ArrayAdapter<String> go = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, goalsArr);
        go.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerGoal.setAdapter(go);
        ArrayAdapter<String> l = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, lifestylesArr);
        l.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerLifestyle.setAdapter(l);
    }

    private void setupClicks() {
        binding.btnSaveSetup.setOnClickListener(v -> {
            try {
                String name = binding.etName.getText().toString().trim();
                if (name.isEmpty()) {
                    binding.etName.setError("Введите имя");
                    binding.etName.requestFocus();
                    return;
                }
                String h = binding.etHeight.getText().toString().trim();
                String w = binding.etWeight.getText().toString().trim();
                String a = binding.etAge.getText().toString().trim();

                if (name.isEmpty() || h.isEmpty() || w.isEmpty() || a.isEmpty()) return;

                int height = Integer.parseInt(h);
                int weight = Integer.parseInt(w);
                int age = Integer.parseInt(a);
                if (height < 100 || height > 250 || weight < 20 || weight > 300 || age < 10 || age > 100) return;

                int gPos = binding.spinnerGender.getSelectedItemPosition();
                int goalPos = binding.spinnerGoal.getSelectedItemPosition();
                int lifePos = binding.spinnerLifestyle.getSelectedItemPosition();
                String gender = gendersArr[gPos];
                String goalDisplay = goalsArr[goalPos];
                String lifeDisplay = lifestylesArr[lifePos];

                String goalKey = goalDisplay.equals("Похудеть") ? "lose" : goalDisplay.equals("Набрать") ? "gain" : "maintain";
                String lifeKey = lifeDisplay.equals("Сидячий") ? "sedentary"
                        : lifeDisplay.equals("Лёгкая активность") ? "light"
                        : lifeDisplay.equals("Средняя активность") ? "moderate"
                        : lifeDisplay.equals("Высокая активность") ? "active"
                        : "very_active";

                prefs.edit()
                        .putString("name", name).putInt("height", height).putInt("weight", weight)
                        .putInt("age", age).putString("gender", gender).putString("goal", goalKey)
                        .putString("lifestyle", lifeKey).putLong("profile_updated_at", System.currentTimeMillis())
                        .apply();

                binding.progressBar.setVisibility(View.VISIBLE);

                viewModel.saveProfile(userId, email, name, height, weight, age, gender, goalKey, lifeKey);

                Intent intent = new Intent(ProfileSetupActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();

            } catch (NumberFormatException e) {
                Log.e("PROFILE", " Ошибка парсинга: " + e.getMessage());
                binding.progressBar.setVisibility(View.GONE);
            }
        });
    }

    private void setupObservers() {

        viewModel.getErrorMessage().observe(this, m -> {
        });
    }

    private void loadFromPrefs() {
        binding.etName.setText(prefs.getString("name", ""));
        int h = prefs.getInt("height", 0), w = prefs.getInt("weight", 0), a = prefs.getInt("age", 0);
        if (h > 0) binding.etHeight.setText(String.valueOf(h));
        if (w > 0) binding.etWeight.setText(String.valueOf(w));
        if (a > 0) binding.etAge.setText(String.valueOf(a));

        binding.spinnerGender.setSelection(findIndex(gendersArr, prefs.getString("gender", "Женский")));
        String goalPref = prefs.getString("goal", "maintain");
        String goalDisplay = goalPref.equals("lose") ? "Похудеть" : goalPref.equals("gain") ? "Набрать" : "Поддерживать";
        binding.spinnerGoal.setSelection(findIndex(goalsArr, goalDisplay));

        String lifePref = prefs.getString("lifestyle", "moderate");
        String lifeDisplay = lifePref.equals("sedentary") ? "Сидячий" : lifePref.equals("light") ? "Лёгкая активность" : lifePref.equals("moderate") ? "Средняя активность" : "Высокая активность";
        binding.spinnerLifestyle.setSelection(findIndex(lifestylesArr, lifeDisplay));
    }

    private int findIndex(String[] arr, String val) {
        if (val == null) return 0;
        for (int i = 0; i < arr.length; i++) if (arr[i] != null && arr[i].trim().equalsIgnoreCase(val.trim())) return i;
        return 0;
    }

    private void observeProfileData() {
        viewModel.getProfileData().observe(this, profile -> {
            if (profile == null) return;
            if (profile.name != null && !profile.name.isEmpty()) binding.etName.setText(profile.name);
            if (profile.height != null && profile.height > 0) binding.etHeight.setText(String.valueOf(profile.height));
            if (profile.weight != null && profile.weight > 0) binding.etWeight.setText(String.valueOf(profile.weight));
            if (profile.age != null && profile.age > 0) binding.etAge.setText(String.valueOf(profile.age));
            if (profile.gender != null) binding.spinnerGender.setSelection(findIndex(gendersArr, profile.gender));
            if (profile.goal != null) {
                String goalDisplay = profile.goal.equals("lose") ? "Похудеть" : profile.goal.equals("gain") ? "Набрать" : "Поддерживать";
                binding.spinnerGoal.setSelection(findIndex(goalsArr, goalDisplay));
            }
            if (profile.lifestyle != null) {
                String lifeDisplay = profile.lifestyle.equals("sedentary") ? "Сидячий" : profile.lifestyle.equals("light") ? "Лёгкая активность" : profile.lifestyle.equals("moderate") ? "Средняя активность" : "Высокая активность";
                binding.spinnerLifestyle.setSelection(findIndex(lifestylesArr, lifeDisplay));
            }
            prefs.edit()
                    .putString("name", profile.name)
                    .putInt("height", profile.height != null ? profile.height : 0)
                    .putInt("weight", profile.weight != null ? profile.weight : 0)
                    .putInt("age", profile.age != null ? profile.age : 0)
                    .putString("gender", profile.gender)
                    .putString("goal", profile.goal)
                    .putString("lifestyle", profile.lifestyle)
                    .apply();
        });
    }
}