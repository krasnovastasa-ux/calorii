package com.example.caloriecounter.view;

import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.caloriecounter.databinding.ActivityDayDetailBinding;
import com.example.caloriecounter.model.FoodLog;
import com.example.caloriecounter.repository.SupabaseRepository;
import java.util.List;

public class DayDetailActivity extends BaseActivity {
    private ActivityDayDetailBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDayDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String date = getIntent().getStringExtra("selected_date");
        if (date == null) finish();

        binding.tvTitle.setText("Рацион за " + date);

        SupabaseRepository repo = new SupabaseRepository(this);
        repo.fetchLogsForDate(date, new SupabaseRepository.LogListCallback() {
            @Override
            public void onSuccess(List<FoodLog> logs) {
                binding.llLogs.removeAllViews();
                if (logs.isEmpty()) {
                    TextView empty = new TextView(DayDetailActivity.this);
                    empty.setText("Записей за этот день нет");
                    empty.setGravity(Gravity.CENTER);
                    empty.setTextSize(14);
                    empty.setTextColor(0xFF757575);
                    empty.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    binding.llLogs.addView(empty);
                    return;
                }
                for (FoodLog log : logs) {
                    TextView tv = new TextView(DayDetailActivity.this);
                    tv.setText(String.format("• %s • %d г (%d ккал)", (log.foodName != null ? log.foodName : "Продукт"), log.grams, log.totalCalories));
                    tv.setTextSize(14);
                    tv.setTextColor(0xFF333333);
                    tv.setPadding(16, 12, 16, 12);
                    binding.llLogs.addView(tv);
                }
            }
            @Override
            public void onError(String m) {
                TextView err = new TextView(DayDetailActivity.this);
                err.setText("Ошибка загрузки: " + m);
                binding.llLogs.addView(err);
            }
        });
    }
}