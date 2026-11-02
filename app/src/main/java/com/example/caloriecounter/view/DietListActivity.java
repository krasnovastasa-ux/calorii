package com.example.caloriecounter.view;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.caloriecounter.R;
import com.example.caloriecounter.databinding.ActivityDietListBinding;
import com.example.caloriecounter.model.Diet;
import com.example.caloriecounter.repository.SupabaseRepository;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DietListActivity extends BaseActivity {

    private static final String GOAL_DEFAULT = "maintain";

    private ActivityDietListBinding binding;
    private SupabaseRepository repo;

    private final List<Diet> allDiets = new ArrayList<>();
    private final List<Diet> goalFilteredDiets = new ArrayList<>();

    private String userGoal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDietListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        repo = new SupabaseRepository(this);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        userGoal = prefs.getString("goal", GOAL_DEFAULT);

        loadDiets();
        setupSearch();
        setupBottomNavigation();
        findViewById(R.id.dietBack).setOnClickListener(v -> finish());
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = binding.bottomNavigation;
        bottomNav.setSelectedItemId(R.id.nav_diets);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_training) {
                startActivity(new Intent(this, TrainingActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                return false;
            }
            if (id == R.id.nav_food) {
                startActivity(new Intent(this, MainActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                return true;
            }
            if (id == R.id.nav_water) {
                startActivity(new Intent(this, WaterTrackerActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                return true;
            }
            if (id == R.id.nav_diets) {
                return true;
            }
            if (id == R.id.nav_weight) {
                startActivity(new Intent(this, WeightTrackerActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                return true;
            }
            return false;
        });
    }

    private void loadDiets() {
        repo.fetchDiets(new SupabaseRepository.DietListCallback() {
            @Override
            public void onSuccess(List<Diet> diets) {
                allDiets.clear();
                if (diets != null) {
                    allDiets.addAll(diets);
                }
                filterByGoal();
                renderList(goalFilteredDiets);
            }

            @Override
            public void onError(String message) {
                addEmpty("Ошибка загрузки: " + message);
            }
        });
    }

    private void filterByGoal() {
        goalFilteredDiets.clear();

        boolean foundAny = false;
        for (Diet diet : allDiets) {
            if (diet.goal == null || diet.goal.equals(userGoal)) {
                goalFilteredDiets.add(diet);
                foundAny = true;
            }
        }

        if (!foundAny) {
            goalFilteredDiets.addAll(allDiets);
        }
    }

    private void setupSearch() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String query = s.toString().toLowerCase(Locale.getDefault()).trim();
                List<Diet> filtered = new ArrayList<>();

                for (Diet diet : goalFilteredDiets) {
                    if (diet.name != null
                            && diet.name.toLowerCase(Locale.getDefault()).contains(query)) {
                        filtered.add(diet);
                    }
                }

                renderList(filtered);
            }
        });
    }

    private void renderList(List<Diet> diets) {
        binding.llDietsList.removeAllViews();

        if (diets.isEmpty()) {
            addEmpty("Ничего не найдено");
            return;
        }

        for (Diet diet : diets) {
            binding.llDietsList.addView(dietRow(diet));
        }
    }

    private LinearLayout dietRow(Diet diet) {
        int padding = ActivityUi.dp(this, 12);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(padding, padding, padding, padding);
        row.setBackground(ActivityUi.shape(
                ActivityPalette.surface(this), padding * 3 / 2));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.setMargins(0, 0, 0, padding / 2);
        row.setLayoutParams(rowParams);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);

        TextView name = new TextView(this);
        name.setText(diet.name == null ? "Без названия" : diet.name);
        name.setTextSize(14);
        name.setTextColor(ActivityPalette.text(this));
        labels.addView(name);

        String subtitle = diet.benefits == null
                ? "Подробнее о питании"
                : diet.benefits.replace('\n', ' ');

        TextView description = new TextView(this);
        description.setText(subtitle);
        description.setTextSize(12);
        description.setMaxLines(1);
        description.setEllipsize(TextUtils.TruncateAt.END);
        description.setTextColor(ActivityPalette.muted(this));
        labels.addView(description);

        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(28);
        arrow.setGravity(Gravity.CENTER);
        arrow.setTextColor(ActivityPalette.accent(this));
        arrow.setTag("accent");
        row.addView(arrow, new LinearLayout.LayoutParams(padding * 2, padding * 3));

        row.setOnClickListener(v -> startActivity(
                new Intent(this, DietDetailActivity.class)
                        .putExtra("diet_id", diet.id)));

        return row;
    }

    private void addEmpty(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, 40, 0, 40);
        view.setTextColor(ActivityPalette.muted(this));
        binding.llDietsList.addView(view);
    }
}