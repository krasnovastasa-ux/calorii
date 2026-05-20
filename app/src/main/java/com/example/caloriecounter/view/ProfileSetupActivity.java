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

public class ProfileSetupActivity extends BaseActivity {

    private static final String TAG = "PROFILE_SETUP";
    private static final String ACTION_PROFILE_UPDATED = "com.example.caloriecounter.PROFILE_UPDATED";

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
            finish();
            return;
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
        ArrayAdapter<String> g = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, gendersArr);
        g.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerGender.setAdapter(g);

        ArrayAdapter<String> go = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, goalsArr);
        go.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerGoal.setAdapter(go);

        ArrayAdapter<String> l = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, lifestylesArr);
        l.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerLifestyle.setAdapter(l);
    }

    private void setupClicks() {
        binding.btnSaveSetup.setOnClickListener(v -> {
            try {
                String name = binding.etName.getText().toString().trim();
                if (name.isEmpty()) {
                    binding.etName.setError("Enter name");
                    binding.etName.requestFocus();
                    return;
                }

                String h = binding.etHeight.getText().toString().trim();
                String w = binding.etWeight.getText().toString().trim();
                String a = binding.etAge.getText().toString().trim();

                if (h.isEmpty() || w.isEmpty() || a.isEmpty()) {
                    android.widget.Toast.makeText(this, "Fill all fields",
                            android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }

                int height = Integer.parseInt(h);
                int weight = Integer.parseInt(w);
                int age = Integer.parseInt(a);

                if (height < 100 || height > 250 ||
                        weight < 20 || weight > 300 ||
                        age < 10 || age > 100) {
                    android.widget.Toast.makeText(this, "Invalid values",
                            android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }

                int gPos = binding.spinnerGender.getSelectedItemPosition();
                int goalPos = binding.spinnerGoal.getSelectedItemPosition();
                int lifePos = binding.spinnerLifestyle.getSelectedItemPosition();

                String gender = gendersArr[gPos];
                String goalDisplay = goalsArr[goalPos];
                String lifeDisplay = lifestylesArr[lifePos];

                String goalKey = goalDisplay.equals("Lose") ? "lose" :
                        goalDisplay.equals("Gain") ? "gain" : "maintain";
                String lifeKey = lifeDisplay.equals("Sedentary") ? "sedentary"
                        : lifeDisplay.equals("Light activity") ? "light"
                        : lifeDisplay.equals("Moderate activity") ? "moderate"
                        : lifeDisplay.equals("High activity") ? "active"
                        : "very_active";

                Log.d(TAG, "Saving profile: " + name + ", " + weight + "kg, goal=" + goalKey);

                prefs.edit()
                        .putString("name", name)
                        .putInt("height", height)
                        .putInt("weight", weight)
                        .putInt("age", age)
                        .putString("gender", gender)
                        .putString("goal", goalKey)
                        .putString("lifestyle", lifeKey)
                        .putString("accent_color", "#6C63FF")
                        .putLong("profile_updated_at", System.currentTimeMillis())
                        .apply();

                binding.progressBar.setVisibility(View.VISIBLE);
                binding.btnSaveSetup.setEnabled(false);

                viewModel.saveProfile(
                        userId, email, name, height, weight, age,
                        gender, goalKey, lifeKey,
                        "system", "#6C63FF"
                );

                Intent intent = new Intent(ACTION_PROFILE_UPDATED);
                sendBroadcast(intent);
                Log.d(TAG, "Broadcast sent: " + ACTION_PROFILE_UPDATED);

                Intent mainIntent = new Intent(ProfileSetupActivity.this, MainActivity.class);
                mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(mainIntent);
                finish();

            } catch (NumberFormatException e) {
                Log.e(TAG, "Parse error: " + e.getMessage());
                android.widget.Toast.makeText(this, "Enter valid numbers",
                        android.widget.Toast.LENGTH_SHORT).show();
                binding.progressBar.setVisibility(View.GONE);
                binding.btnSaveSetup.setEnabled(true);
            }
        });
    }

    private void setupObservers() {
        viewModel.getErrorMessage().observe(this, m -> {
            if (m != null && !m.isEmpty()) {
                Log.e(TAG, "Error: " + m);
                binding.progressBar.setVisibility(View.GONE);
                binding.btnSaveSetup.setEnabled(true);
                android.widget.Toast.makeText(this, "Error: " + m,
                        android.widget.Toast.LENGTH_LONG).show();
            }
        });
    }

    private void loadFromPrefs() {
        binding.etName.setText(prefs.getString("name", ""));

        int h = prefs.getInt("height", 0);
        int w = prefs.getInt("weight", 0);
        int a = prefs.getInt("age", 0);

        if (h > 0) binding.etHeight.setText(String.valueOf(h));
        if (w > 0) binding.etWeight.setText(String.valueOf(w));
        if (a > 0) binding.etAge.setText(String.valueOf(a));

        binding.spinnerGender.setSelection(findIndex(gendersArr,
                prefs.getString("gender", "Female")));

        String goalPref = prefs.getString("goal", "maintain");
        String goalDisplay = goalPref.equals("lose") ? "Lose" :
                goalPref.equals("gain") ? "Gain" : "Maintain";
        binding.spinnerGoal.setSelection(findIndex(goalsArr, goalDisplay));

        String lifePref = prefs.getString("lifestyle", "moderate");
        String lifeDisplay = lifePref.equals("sedentary") ? "Sedentary" :
                lifePref.equals("light") ? "Light activity" :
                        lifePref.equals("moderate") ? "Moderate activity" : "High activity";
        binding.spinnerLifestyle.setSelection(findIndex(lifestylesArr, lifeDisplay));
    }

    private int findIndex(String[] arr, String val) {
        if (val == null) return 0;
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] != null && arr[i].trim().equalsIgnoreCase(val.trim())) {
                return i;
            }
        }
        return 0;
    }

    private void observeProfileData() {
        viewModel.getProfileData().observe(this, profile -> {
            if (profile == null) return;

            Log.d(TAG, "Profile from DB: name='" + profile.name + "'");

            if (profile.name != null && !profile.name.isEmpty()) {
                binding.etName.setText(profile.name);
            }
            if (profile.height != null && profile.height > 0) {
                binding.etHeight.setText(String.valueOf(profile.height));
            }
            if (profile.weight != null && profile.weight > 0) {
                binding.etWeight.setText(String.valueOf(profile.weight));
            }
            if (profile.age != null && profile.age > 0) {
                binding.etAge.setText(String.valueOf(profile.age));
            }
            if (profile.gender != null) {
                binding.spinnerGender.setSelection(findIndex(gendersArr, profile.gender));
            }
            if (profile.goal != null) {
                String goalDisplay = profile.goal.equals("lose") ? "Lose" :
                        profile.goal.equals("gain") ? "Gain" : "Maintain";
                binding.spinnerGoal.setSelection(findIndex(goalsArr, goalDisplay));
            }
            if (profile.lifestyle != null) {
                String lifeDisplay = profile.lifestyle.equals("sedentary") ? "Sedentary" :
                        profile.lifestyle.equals("light") ? "Light activity" :
                                profile.lifestyle.equals("moderate") ? "Moderate activity" : "High activity";
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