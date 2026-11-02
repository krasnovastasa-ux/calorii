package com.example.caloriecounter.view;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.caloriecounter.R;
import com.example.caloriecounter.databinding.ActivityDietListBinding;
import com.example.caloriecounter.model.Diet;
import com.example.caloriecounter.repository.SupabaseRepository;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class DietListActivity extends BaseActivity {

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
        userGoal = prefs.getString("goal", "maintain");

        loadDiets();
        setupSearch();
        setupBottomNavigation();
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
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(16, 12, 16, 12);
        row.setBackgroundResource(R.drawable.bg_card);
        row.setClickable(true);

        TextView name = new TextView(this);
        name.setText(diet.name != null ? diet.name : "Без названия");
        name.setTextSize(16);
        name.setTextColor(0xFF212121);
        name.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(name);

        row.setOnClickListener(v -> {
            Intent intent = new Intent(DietListActivity.this, DietDetailActivity.class);
            intent.putExtra("diet_id", diet.id);
            startActivity(intent);
        });

        return row;
    }

    private void addEmpty(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, 40, 0, 40);
        view.setTextColor(0xFF757575);
        binding.llDietsList.addView(view);
    }
}