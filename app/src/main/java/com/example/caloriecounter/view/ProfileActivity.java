package com.example.caloriecounter.view;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ArrayAdapter;
import android.os.Build;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatDelegate;
import android.annotation.SuppressLint;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import com.example.caloriecounter.R;
import com.example.caloriecounter.databinding.ActivityProfileBinding;
import com.example.caloriecounter.repository.SupabaseRepository;
import com.example.caloriecounter.viewmodel.ProfileViewModel;

public class ProfileActivity extends BaseActivity {

    private ActivityProfileBinding binding;
    private ProfileViewModel viewModel;
    private SharedPreferences prefs;
    private String userId, email;

    private String[] gendersArr;
    private String[] goalsArr;
    private String[] lifestylesArr;
    private static final String[] ACCENT_COLORS = {
            "#6C63FF", "#b64949", "#b1c464", "#80cb9e", "#7bd1c7", "#659cc3", "#c365b0", "#edb0cf"
    };
    private String selectedTheme = "system";
    private String selectedAccent = "#6C63FF";
    private View selectedAccentView;
    private final BroadcastReceiver weightUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            viewModel.loadProfile(userId);
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
        applyThemeAndAccent();
        registerWeightUpdateReceiver();
    }

    @SuppressLint("UnprotectedRegisterReceiver")
    private void registerWeightUpdateReceiver() {
        androidx.core.content.ContextCompat.registerReceiver(
                this,
                weightUpdateReceiver,
                new IntentFilter("com.example.caloriecounter.WEIGHT_UPDATED"),
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        );
    }



    @Override
    protected void onResume() {
        super.onResume();
        if (userId != null && !userId.isEmpty()) {
            viewModel.loadProfile(userId);
        }
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
                prefs.getString("gender", "Женский")));

        String savedGoal = prefs.getString("goal", "maintain");
        String goalDisplay = savedGoal.equals("lose") ? "Похудеть" :
                savedGoal.equals("gain") ? "Набрать" : "Поддерживать";
        binding.spinnerGoal.setSelection(findIndex(goalsArr, goalDisplay));

        String savedLifestyle = prefs.getString("lifestyle", "moderate");
        String lifeDisplay = savedLifestyle.equals("sedentary") ? "Сидячий" :
                savedLifestyle.equals("light") ? "Лёгкая активность" :
                        savedLifestyle.equals("moderate") ? "Средняя активность" : "Высокая активность";
        binding.spinnerLifestyle.setSelection(findIndex(lifestylesArr, lifeDisplay));
    }

    private int findIndex(String[] arr, String val) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].trim().equalsIgnoreCase(val.trim())) return i;
        }
        return 0;
    }

    private void setupObservers() {
        viewModel.getErrorMessage().observe(this, m -> {});
        viewModel.getProfileSaved().observe(this, saved -> {
            if (saved) {
                prefs.edit()
                        .putString("theme_mode", selectedTheme)
                        .putString("accent_color", selectedAccent)
                        .apply();
                android.widget.Toast.makeText(this, "Настройки сохранены!", android.widget.Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void observeProfileData() {
        viewModel.getProfileData().observe(this, profile -> {
            if (profile != null) {
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
                    String goalDisplay = profile.goal.equals("lose") ? "Похудеть" :
                            profile.goal.equals("gain") ? "Набрать" : "Поддерживать";
                    binding.spinnerGoal.setSelection(findIndex(goalsArr, goalDisplay));
                }
                if (profile.lifestyle != null) {
                    String lifeDisplay = profile.lifestyle.equals("sedentary") ? "Сидячий" :
                            profile.lifestyle.equals("light") ? "Лёгкая активность" :
                                    profile.lifestyle.equals("moderate") ? "Средняя активность" : "Высокая активность";
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

                Log.d("PROFILE", " Данные профиля загружены из БД");
            }
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
                    return;
                }

                int height = Integer.parseInt(h);
                int weight = Integer.parseInt(w);
                int age = Integer.parseInt(a);

                if (height < 100 || height > 250 ||
                        weight < 20 || weight > 300 ||
                        age < 10 || age > 100) {
                    return;
                }

                int gPos = binding.spinnerGender.getSelectedItemPosition();
                int goalPos = binding.spinnerGoal.getSelectedItemPosition();
                int lifePos = binding.spinnerLifestyle.getSelectedItemPosition();

                String gender = gendersArr[gPos];
                String goalDisplay = goalsArr[goalPos];
                String lifeDisplay = lifestylesArr[lifePos];

                String goalKey = goalDisplay.equals("Похудеть") ? "lose" :
                        goalDisplay.equals("Набрать") ? "gain" : "maintain";
                String lifeKey = lifeDisplay.equals("Сидячий") ? "sedentary" :
                        lifeDisplay.equals("Лёгкая активность") ? "light" :
                                lifeDisplay.equals("Средняя активность") ? "moderate" : "active";

                Log.d("PROFILE_SAVE", " Сохраняю: g=" + gender + ", gl=" + goalKey + ", l=" + lifeKey);

                prefs.edit()
                        .putString("name", name)
                        .putInt("height", height)
                        .putInt("weight", weight)
                        .putInt("age", age)
                        .putString("gender", gender)
                        .putString("goal", goalKey)
                        .putString("lifestyle", lifeKey)
                        .putLong("profile_updated_at", System.currentTimeMillis())
                        .putString("theme_mode", selectedTheme)
                        .putString("accent_color", selectedAccent)
                        .apply();

                binding.progressBar.setVisibility(View.VISIBLE);
                viewModel.saveProfile(userId, email, name, height, weight, age, gender, goalKey, lifeKey, selectedTheme, selectedAccent);
                binding.progressBar.postDelayed(() -> binding.progressBar.setVisibility(View.GONE), 400);

            } catch (NumberFormatException e) {
            }
        });

        binding.btnLogout.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Выход")
                .setMessage("Точно выйти?")
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
            int color = android.graphics.Color.parseColor(selectedAccent);
            binding.btnSave.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
            binding.btnLogout.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        } catch (Exception ignored) {}
    }




    private void setupThemeUI() {

        binding.rgTheme.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbLight) selectedTheme = "light";
            else if (checkedId == R.id.rbDark) selectedTheme = "dark";
            else selectedTheme = "system";
        });

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
            });
            binding.llAccentColors.addView(circle);
        }
    }

    private android.graphics.drawable.GradientDrawable getCircleDrawable(String hex) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        try { d.setColor(android.graphics.Color.parseColor(hex)); } catch(Exception e){}
        d.setStroke(3, android.graphics.Color.parseColor("#EAEAEA"));
        return d;
    }

    private void loadThemePrefs() {
        selectedTheme = prefs.getString("theme_mode", "system");
        selectedAccent = prefs.getString("accent_color", "#6C63FF");

        if ("light".equals(selectedTheme)) binding.rgTheme.check(R.id.rbLight);
        else if ("dark".equals(selectedTheme)) binding.rgTheme.check(R.id.rbDark);
        else binding.rgTheme.check(R.id.rbSystem);

        for (int i = 0; i < binding.llAccentColors.getChildCount(); i++) {
            View c = binding.llAccentColors.getChildAt(i);
            if (selectedAccent.equals(c.getTag())) {
                selectedAccentView = c;
                c.setAlpha(1.0f);
            } else c.setAlpha(0.3f);
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(weightUpdateReceiver);
    }
}