package com.example.caloriecounter.view;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.caloriecounter.databinding.ActivityDietDetailBinding;
import com.example.caloriecounter.model.Diet;
import com.example.caloriecounter.repository.SupabaseRepository;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.List;

public class DietDetailActivity extends AppCompatActivity {
    private ActivityDietDetailBinding binding;
    private SupabaseRepository repo;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDietDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        repo = new SupabaseRepository(this);
        String id = getIntent().getStringExtra("diet_id");
        if (id != null) loadDiet(id);
    }

    private void loadDiet(String id) {
        repo.fetchDietById(id, new SupabaseRepository.DietCallback() {
            @Override public void onSuccess(Diet diet) { renderDiet(diet); }
            @Override public void onError(String m) { showError("Не удалось загрузить диету"); }
        });
    }

    private void renderDiet(Diet d) {
        addTitle(d.name);
        addSection("Преимущества", d.benefits);
        addSection(" ⚠ Противопоказания", d.contraindications);
        addListSection("Можно есть", d.allowedFoods);
        addListSection("Нельзя есть", d.forbiddenFoods);
        addSection("Рацион на 2 недели", d.mealPlan2Weeks);
        addSection("Ожидаемый результат", d.expectedResults);
        addSection("Повторяемость", d.frequency);
    }

    private void addTitle(String text) {
        TextView tv = new TextView(this); tv.setText(text); tv.setTextSize(24);
        tv.setTypeface(null, android.graphics.Typeface.BOLD); tv.setTextColor(Color.parseColor("#1A1A2E"));
        tv.setPadding(0, 0, 0, 24); binding.llContent.addView(tv);
    }

    private void addSection(String title, String content) {
        if (content == null || content.isEmpty()) return;
        TextView t = new TextView(this); t.setText(title); t.setTextSize(18);
        t.setTypeface(null, android.graphics.Typeface.BOLD); t.setTextColor(Color.parseColor("#6C63FF"));
        t.setPadding(0, 16, 0, 8); binding.llContent.addView(t);
        TextView c = new TextView(this); c.setText(content); c.setTextSize(15);
        c.setTextColor(0xFF333333); c.setPadding(8, 0, 8, 16); binding.llContent.addView(c);
        addDivider();
    }

    private void addListSection(String title, String json) {
        if (json == null || json.isEmpty()) return;
        TextView t = new TextView(this); t.setText(title); t.setTextSize(18);
        t.setTypeface(null, android.graphics.Typeface.BOLD); t.setTextColor(Color.parseColor("#6C63FF"));
        t.setPadding(0, 16, 0, 8); binding.llContent.addView(t);
        try {
            List<String> items = new Gson().fromJson(json, new TypeToken<List<String>>() {}.getType());
            for (String item : items) {
                TextView c = new TextView(this); c.setText("• " + item); c.setTextSize(15);
                c.setTextColor(0xFF333333); c.setPadding(16, 4, 8, 4); binding.llContent.addView(c);
            }
        } catch (Exception e) {
            TextView err = new TextView(this); err.setText(json); err.setTextSize(15);
            err.setPadding(16, 0, 8, 16); binding.llContent.addView(err);
        }
        addDivider();
    }

    private void addDivider() {
        android.view.View v = new android.view.View(this);
        v.setBackgroundColor(0xFFEAEAEA); v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2)); binding.llContent.addView(v);
    }

    private void showError(String msg) {
        TextView err = new TextView(this); err.setText(msg); err.setGravity(Gravity.CENTER);
        err.setPadding(0, 100, 0, 0); binding.llContent.addView(err);
    }
}