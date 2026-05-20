package com.example.caloriecounter.view;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.caloriecounter.R;
import com.example.caloriecounter.databinding.ActivityProfileBinding;
import com.example.caloriecounter.repository.SupabaseRepository;
import com.example.caloriecounter.viewmodel.ProfileViewModel;

public class ProfileActivity extends BaseActivity {

    private static final String TAG = "PROFILE_ACTIVITY";
    private static final String ACTION_PROFILE_UPDATED = "com.example.caloriecounter.PROFILE_UPDATED";

    private ActivityProfileBinding binding;
    private ProfileViewModel viewModel;
    private SharedPreferences prefs;
    private String userId, email;

    private String[] gendersArr;
    private String[] goalsArr;
    private String[] lifestylesArr;

    private static final String[] ACCENT_COLORS = {
            "#6C63FF", "#b64949", "#b1c464", "#80cb9e",
            "#7bd1c7", "#659cc3", "#c365b0", "#edb0cf"
    };

    private String selectedAccent = "#6C63FF";
    private View selectedAccentView;

    private final BroadcastReceiver profileUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Profile update signal received, reloading data");
            if (userId != null && !userId.isEmpty()) {
                viewModel.loadProfile(userId);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
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
        viewModel.loadProfile(userId);
        observeProfileData();
        setupThemeUI();
        loadThemePrefs();
        setupObservers();
        setupClicks();
        applyThemeAndAccent();
        registerProfileUpdateReceiver();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (userId != null && !userId.isEmpty()) {
            viewModel.loadProfile(userId);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(profileUpdateReceiver);
        } catch (IllegalArgumentException e) {
        }
    }

    private void registerProfileUpdateReceiver() {
        IntentFilter filter = new IntentFilter(ACTION_PROFILE_UPDATED);
        ContextCompat.registerReceiver(
                this,
                profileUpdateReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
        Log.d(TAG, "Receiver registered");
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

        String savedGoal = prefs.getString("goal", "maintain");
        String goalDisplay = savedGoal.equals("lose") ? "Lose" :
                savedGoal.equals("gain") ? "Gain" : "Maintain";
        binding.spinnerGoal.setSelection(findIndex(goalsArr, goalDisplay));

        String savedLifestyle = prefs.getString("lifestyle", "moderate");
        String lifeDisplay = savedLifestyle.equals("sedentary") ? "Sedentary" :
                savedLifestyle.equals("light") ? "Light activity" :
                        savedLifestyle.equals("moderate") ? "Moderate activity" : "High activity";
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

    private void setupObservers() {
        viewModel.getErrorMessage().observe(this, m -> {
            if (m != null && !m.isEmpty()) {
                Log.e(TAG, "Error: " + m);
            }
        });

        viewModel.getProfileSaved().observe(this, saved -> {
            if (Boolean.TRUE.equals(saved)) {
                Log.d(TAG, "Profile saved successfully");

                prefs.edit()
                        .putString("accent_color", selectedAccent)
                        .apply();

                Intent intent = new Intent(ACTION_PROFILE_UPDATED);
                sendBroadcast(intent);
                Log.d(TAG, "Broadcast sent: " + ACTION_PROFILE_UPDATED);

                android.widget.Toast.makeText(this, "Settings saved",
                        android.widget.Toast.LENGTH_SHORT).show();

                finish();
            }
        });
    }

    private void observeProfileData() {
        viewModel.getProfileData().observe(this, profile -> {
            if (profile == null) {
                Log.w(TAG, "Profile is null");
                return;
            }

            Log.d(TAG, "Profile loaded: name='" + profile.name + "'");

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

            Log.d(TAG, "Profile data updated in SharedPreferences");
        });
    }

    private void setupClicks() {
        binding.btnSave.setOnClickListener(v -> {
            try {
                String name = binding.etName.getText().toString().trim();
                String h = binding.etHeight.getText().toString().trim();
                String w = binding.etWeight.getText().toString().trim();
                String a = binding.etAge.getText().toString().trim();

                if (name.isEmpty() || h.isEmpty() || w.isEmpty() || a.isEmpty()) {
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
                String lifeKey = lifeDisplay.equals("Sedentary") ? "sedentary" :
                        lifeDisplay.equals("Light activity") ? "light" :
                                lifeDisplay.equals("Moderate activity") ? "moderate" : "active";

                Log.d(TAG, "Saving: name=" + name + ", g=" + gender +
                        ", gl=" + goalKey + ", l=" + lifeKey + ", accent=" + selectedAccent);

                prefs.edit()
                        .putString("name", name)
                        .putInt("height", height)
                        .putInt("weight", weight)
                        .putInt("age", age)
                        .putString("gender", gender)
                        .putString("goal", goalKey)
                        .putString("lifestyle", lifeKey)
                        .putString("accent_color", selectedAccent)
                        .putLong("profile_updated_at", System.currentTimeMillis())
                        .apply();

                binding.progressBar.setVisibility(View.VISIBLE);
                binding.btnSave.setEnabled(false);

                viewModel.saveProfile(userId, email, name, height, weight, age,
                        gender, goalKey, lifeKey, "system", selectedAccent);

            } catch (NumberFormatException e) {
                Log.e(TAG, "Parse error: " + e.getMessage());
                android.widget.Toast.makeText(this, "Enter valid numbers",
                        android.widget.Toast.LENGTH_SHORT).show();
                binding.progressBar.setVisibility(View.GONE);
                binding.btnSave.setEnabled(true);
            }
        });

        binding.btnLogout.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Выход")
                .setMessage("Точно ввыйти из аккаунта?")
                .setPositiveButton("Выйти", (d, w) -> {
                    new SupabaseRepository(this).clearSession();
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Отмена", null)
                .show());
    }

    private void applyThemeAndAccent() {
        try {
            int color = Color.parseColor(selectedAccent);
            binding.btnSave.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(color));
            binding.btnLogout.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(color));
        } catch (Exception ignored) {}
    }

    private void setupThemeUI() {
        int size = (int) (40 * getResources().getDisplayMetrics().density);
        for (String hex : ACCENT_COLORS) {
            View circle = new View(this);
            circle.setLayoutParams(new LinearLayout.LayoutParams(size, size));
            circle.setBackground(getCircleDrawable(hex));
            circle.setClickable(true);
            circle.setTag(hex);
            circle.setOnClickListener(v -> {
                if (selectedAccentView != null) selectedAccentView.setAlpha(0.3f);
                v.setAlpha(1.0f);
                selectedAccentView = v;
                selectedAccent = (String) v.getTag();
                applyThemeAndAccent();
            });
            binding.llAccentColors.addView(circle);
        }
    }

    private GradientDrawable getCircleDrawable(String hex) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        try {
            d.setColor(Color.parseColor(hex));
        } catch(Exception e) {
            d.setColor(Color.parseColor("#6C63FF"));
        }
        d.setStroke(3, Color.parseColor("#EAEAEA"));
        return d;
    }

    private void loadThemePrefs() {
        selectedAccent = prefs.getString("accent_color", "#6C63FF");
        for (int i = 0; i < binding.llAccentColors.getChildCount(); i++) {
            View c = binding.llAccentColors.getChildAt(i);
            if (selectedAccent.equals(c.getTag())) {
                selectedAccentView = c;
                c.setAlpha(1.0f);
            } else {
                c.setAlpha(0.3f);
            }
        }
    }
}