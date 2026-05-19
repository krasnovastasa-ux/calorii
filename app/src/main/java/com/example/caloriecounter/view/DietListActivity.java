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
import androidx.appcompat.app.AppCompatActivity;
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
    private List<Diet> allDiets = new ArrayList<>();
    private List<Diet> goalFilteredDiets = new ArrayList<>();
    private String userGoal;

    @Override protected void onCreate(Bundle savedInstanceState) {
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
            if (id == R.id.nav_food) {
                startActivity(new Intent(this, MainActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                return true;
            } else if (id == R.id.nav_water) {
                startActivity(new Intent(this, WaterTrackerActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                return true;
            } else if (id == R.id.nav_diets) {
                return true;
            }else if (id == R.id.nav_weight) {
                startActivity(new Intent(this, WeightTrackerActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                return true;
            }
            return false;
        });
    }

    private void loadDiets() {
        repo.fetchDiets(new SupabaseRepository.DietListCallback() {
            @Override public void onSuccess(List<Diet> diets) {
                allDiets.clear();
                allDiets.addAll(diets != null ? diets : Collections.emptyList());
                filterByGoal();
                renderList(goalFilteredDiets);
            }
            @Override public void onError(String m) { addEmpty("Ошибка загрузки: " + m); }
        });
    }

    private void filterByGoal() {
        goalFilteredDiets.clear();
        boolean foundAny = false;
        for (Diet d : allDiets) {
            if (d.goal == null || d.goal.equals(userGoal)) {
                goalFilteredDiets.add(d);
                foundAny = true;
            }
        }
        if (!foundAny) goalFilteredDiets.addAll(allDiets);
    }

    private void setupSearch() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String q = s.toString().toLowerCase(Locale.getDefault()).trim();
                List<Diet> filtered = new ArrayList<>();
                for (Diet d : goalFilteredDiets) {
                    if (d.name != null && d.name.toLowerCase(Locale.getDefault()).contains(q)) filtered.add(d);
                }
                renderList(filtered);
            }
        });
    }

    private void renderList(List<Diet> diets) {
        binding.llDietsList.removeAllViews();
        if (diets.isEmpty()) { addEmpty("Ничего не найдено"); return; }
        for (Diet d : diets) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL); row.setPadding(16, 12, 16, 12);
            row.setBackgroundResource(R.drawable.bg_card);
            row.setClickable(true);

            TextView name = new TextView(this);
            name.setText(d.name != null ? d.name : "Без названия");
            name.setTextSize(16); name.setTextColor(0xFF212121);
            name.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            row.addView(name);
            row.setOnClickListener(v -> {
                Intent i = new Intent(DietListActivity.this, DietDetailActivity.class);
                i.putExtra("diet_id", d.id);
                startActivity(i);
            });
            binding.llDietsList.addView(row);
        }
    }

    private void addEmpty(String text) {
        TextView tv = new TextView(this); tv.setText(text); tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, 40, 0, 40); tv.setTextColor(0xFF757575);
        binding.llDietsList.addView(tv);
    }
}