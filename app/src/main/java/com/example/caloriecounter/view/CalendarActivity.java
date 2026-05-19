package com.example.caloriecounter.view;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.example.caloriecounter.R;
import com.example.caloriecounter.databinding.ActivityCalendarBinding;
import com.example.caloriecounter.model.FoodLog;
import com.example.caloriecounter.repository.SupabaseRepository;
import java.text.SimpleDateFormat;
import java.util.*;

public class CalendarActivity extends BaseActivity {
    private ActivityCalendarBinding binding;
    private SupabaseRepository repo;
    private Calendar currentCal = Calendar.getInstance();
    private Calendar today = Calendar.getInstance();
    private boolean isYearView = false;
    private int pendingFetches = 0;

    private final SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
    private final SimpleDateFormat monthFormat = new SimpleDateFormat("LLLL yyyy", new Locale("ru"));
    private final SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy", Locale.ROOT);
    private final String[] monthNames = {"Январь","Февраль","Март","Апрель","Май","Июнь","Июль","Август","Сентябрь","Октябрь","Ноябрь","Декабрь"};
    private String todayStr;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCalendarBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        repo = new SupabaseRepository(this);
        todayStr = dayFormat.format(today.getTime());

        updateTitle();
        render();

        binding.btnPrev.setOnClickListener(v -> navigate(-1));
        binding.btnNext.setOnClickListener(v -> navigate(1));

        binding.btnToday.setOnClickListener(v -> {
            currentCal = Calendar.getInstance();
            if (isYearView) { isYearView = false; updateToggleText(); }
            updateTitle();
            render();
        });

        binding.btnToggleView.setOnClickListener(v -> {
            isYearView = !isYearView;
            updateToggleText();
            updateTitle();
            render();
        });
    }

    private void navigate(int delta) {
        if (isYearView) currentCal.add(Calendar.YEAR, delta);
        else currentCal.add(Calendar.MONTH, delta);
        updateTitle();
        render();
    }

    private void updateTitle() {
        String m = monthFormat.format(currentCal.getTime());
        binding.tvTitle.setText(isYearView ? yearFormat.format(currentCal.getTime()) :
                m.substring(0,1).toUpperCase() + m.substring(1));
    }

    private void updateToggleText() {
        binding.btnToggleView.setText(isYearView ? "Год" : "Месяц");
        binding.daysHeader.setVisibility(isYearView ? View.GONE : View.VISIBLE);
        binding.monthGrid.setVisibility(isYearView ? View.GONE : View.VISIBLE);
        binding.yearScroll.setVisibility(isYearView ? View.VISIBLE : View.GONE);
    }

    private void render() {
        binding.monthGrid.removeAllViews();
        binding.yearGrid.removeAllViews();
        pendingFetches = 0;
        if (isYearView) renderYear(); else renderMonth();
    }

    private void renderMonth() {
        Calendar cal = (Calendar) currentCal.clone();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        int firstDay = cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY;
        if (firstDay < 0) firstDay += 7;

        float density = getResources().getDisplayMetrics().density;
        int rows = (int) Math.ceil((firstDay + daysInMonth) / 7.0);
        int cellHeight = (int) (70 * density);

        for (int r = 0; r < rows; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cellHeight));
            row.setPadding(2, 2, 2, 2);

            for (int c = 0; c < 7; c++) {
                LinearLayout cell = new LinearLayout(this);
                cell.setGravity(Gravity.CENTER);
                cell.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setPadding(4, 4, 4, 4);

                int dayNum = r * 7 + c - firstDay + 1;
                if (dayNum >= 1 && dayNum <= daysInMonth) {
                    Calendar dayCal = (Calendar) currentCal.clone();
                    dayCal.set(Calendar.DAY_OF_MONTH, dayNum);
                    String dateStr = dayFormat.format(dayCal.getTime());

                    TextView dayTv = new TextView(this);
                    dayTv.setText(String.valueOf(dayNum));
                    dayTv.setTextSize(16); dayTv.setGravity(Gravity.CENTER_HORIZONTAL);

                    TextView calTv = new TextView(this);
                    calTv.setTag(dateStr); calTv.setText("");
                    calTv.setTextSize(10); calTv.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                    calTv.setGravity(Gravity.CENTER_HORIZONTAL); calTv.setPadding(0, 2, 0, 0);

                    cell.addView(dayTv); cell.addView(calTv); cell.setTag(dateStr);
                    applyCellStyle(cell, dateStr, dayTv, calTv);
                    cell.setClickable(true);

                    final String d = dateStr; final int n = dayNum;
                    cell.setOnClickListener(v -> showDayDetails(d, n));

                    pendingFetches++; loadDayData(dateStr, calTv, dayTv);
                } else { cell.setBackgroundColor(Color.TRANSPARENT); }
                row.addView(cell);
            }
            binding.monthGrid.addView(row);
        }
    }

    private void applyCellStyle(LinearLayout cell, String dateStr, TextView dayTv, TextView calTv) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(8 * getResources().getDisplayMetrics().density);
        bg.setStroke(1, ContextCompat.getColor(this, R.color.text_secondary));

        if (dateStr.equals(todayStr)) {
            String accentHex = com.example.caloriecounter.utils.ThemeUtils.getAccent(this);
            int accentColor = android.graphics.Color.parseColor(accentHex);

            bg.setColor(accentColor);
            bg.setStroke(0, Color.TRANSPARENT);
            dayTv.setTextColor(Color.WHITE); dayTv.setTypeface(null, android.graphics.Typeface.BOLD);
            calTv.setTextColor(Color.WHITE);

            cell.setTag("accent_circle");
        } else {
            bg.setColor(Color.WHITE);
            dayTv.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            calTv.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }
        cell.setBackground(bg);
    }

    private void renderYear() {
        float density = getResources().getDisplayMetrics().density;
        int year = currentCal.get(Calendar.YEAR);

        int availableWidth = getResources().getDisplayMetrics().widthPixels;
        int paddingPx = (int) (20 * density);
        int cellSize = (availableWidth - paddingPx) / 7;
        if (cellSize < 32 * density) cellSize = (int)(32 * density);
        if (cellSize > 48 * density) cellSize = (int)(48 * density);

        for (int month = 0; month < 12; month++) {
            TextView monthTitle = new TextView(this);
            monthTitle.setText(monthNames[month].substring(0,1).toUpperCase() + monthNames[month].substring(1) + " " + year);
            monthTitle.setTextSize(16); monthTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            monthTitle.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            monthTitle.setPadding(8, 16, 8, 8);
            binding.yearGrid.addView(monthTitle);

            LinearLayout daysRow = new LinearLayout(this);
            daysRow.setOrientation(LinearLayout.HORIZONTAL); daysRow.setPadding(4, 0, 4, 4);
            String[] days = {"Пн","Вт","Ср","Чт","Пт","Сб","Вс"};
            for (String d : days) {
                TextView dw = new TextView(this); dw.setText(d); dw.setTextSize(10);
                dw.setGravity(Gravity.CENTER);
                dw.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                dw.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                daysRow.addView(dw);
            }
            binding.yearGrid.addView(daysRow);

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.YEAR, year); cal.set(Calendar.MONTH, month); cal.set(Calendar.DAY_OF_MONTH, 1);
            int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
            int firstDay = cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY;
            if (firstDay < 0) firstDay += 7;
            int rows = (int) Math.ceil((firstDay + daysInMonth) / 7.0);

            for (int r = 0; r < rows; r++) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(2, 1, 2, 1);

                for (int c = 0; c < 7; c++) {
                    LinearLayout cell = new LinearLayout(this);
                    cell.setGravity(Gravity.CENTER);
                    cell.setLayoutParams(new LinearLayout.LayoutParams(0, cellSize, 1));
                    cell.setPadding(1, 1, 1, 1);

                    TextView circle = new TextView(this);
                    circle.setGravity(Gravity.CENTER);
                    circle.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    circle.setTextSize(11);

                    int dayNum = r * 7 + c - firstDay + 1;
                    if (dayNum >= 1 && dayNum <= daysInMonth) {
                        Calendar dayCal = (Calendar) currentCal.clone();
                        dayCal.set(Calendar.MONTH, month); dayCal.set(Calendar.DAY_OF_MONTH, dayNum);
                        String dateStr = dayFormat.format(dayCal.getTime());
                        circle.setText(String.valueOf(dayNum));

                        GradientDrawable bg = new GradientDrawable(); bg.setShape(GradientDrawable.OVAL);
                        if (dateStr.equals(todayStr)) {
                            String accentHex = com.example.caloriecounter.utils.ThemeUtils.getAccent(this);
                            int accentColor = android.graphics.Color.parseColor(accentHex);

                            bg.setColor(accentColor);
                            circle.setTextColor(Color.WHITE); circle.setTypeface(null, android.graphics.Typeface.BOLD);
                            circle.setTag("accent_circle");
                        } else {
                            bg.setColor(Color.WHITE);
                            circle.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                        }
                        bg.setStroke(1, ContextCompat.getColor(this, R.color.text_secondary));
                        circle.setBackground(bg);

                        final String d = dateStr; final int n = dayNum;
                        circle.setClickable(true);
                        circle.setOnClickListener(v -> showDayDetails(d, n));
                    } else { circle.setBackgroundColor(Color.TRANSPARENT); }

                    cell.addView(circle);
                    row.addView(cell);
                }
                binding.yearGrid.addView(row);
            }

            if (month < 11) {
                View divider = new View(this);
                divider.setBackgroundColor(ContextCompat.getColor(this, R.color.text_secondary));
                divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
                divider.setAlpha(0.2f); divider.setPadding(0, 12, 0, 12);
                binding.yearGrid.addView(divider);
            }
        }
    }

    private void loadDayData(String dateStr, TextView calTv, TextView dayTv) {
        if (repo.getPrefs().getString("user_id", null) == null) {
            runOnUiThread(() -> {
                startActivity(new Intent(this, LoginActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                finish();
            });
            return;
        }
        repo.fetchLogsForDate(dateStr, new SupabaseRepository.LogListCallback() {
            @Override public void onSuccess(List<FoodLog> logs) {
                runOnUiThread(() -> {
                    int total = 0;
                    for (FoodLog log : logs) total += log.totalCalories;
                    if (total > 0) calTv.setText(total + " ккал");
                    else calTv.setText("");
                    pendingFetches--;
                });
            }
            @Override public void onError(String m) {
                runOnUiThread(() -> {
                    if (m.contains("401") || m.contains("Unauthorized")) {
                        startActivity(new Intent(CalendarActivity.this, LoginActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                        finish();
                    }
                    pendingFetches--;
                });
            }
        });
    }

    private void showDayDetails(String date, int day) {
        if (!repo.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish(); return;
        }
        repo.fetchLogsForDate(date, new SupabaseRepository.LogListCallback() {
            @Override public void onSuccess(List<FoodLog> logs) {
                runOnUiThread(() -> buildDayDialog(date, day, logs));
            }
            @Override public void onError(String m) {
                runOnUiThread(() -> {
                    if (m.contains("401") || m.contains("Unauthorized")) {
                        startActivity(new Intent(CalendarActivity.this, LoginActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                        finish();
                    }
                });
            }
        });
    }

    private void buildDayDialog(String date, int day, List<FoodLog> logs) {
        String accentHex = com.example.caloriecounter.utils.ThemeUtils.getAccent(this);
        int accentColor = android.graphics.Color.parseColor(accentHex);

        if (logs.isEmpty()) {
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle(day + " " + monthFormat.format(currentCal.getTime()))
                    .setMessage("Записей за этот день нет")
                    .setPositiveButton("Закрыть", null)
                    .create();
            dialog.show();
            try { dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setTextColor(accentColor); } catch (Exception ignored) {}
            return;
        }

        Map<String, List<FoodLog>> meals = new LinkedHashMap<>();
        meals.put("Завтрак", new ArrayList<>()); meals.put("Обед", new ArrayList<>());
        meals.put("Ужин", new ArrayList<>()); meals.put("Перекус", new ArrayList<>());
        int totalCal=0, pro=0, fat=0, carb=0, sugar=0, fiber=0;
        for (FoodLog l : logs) {
            String t = l.mealType!=null?l.mealType:"Перекус"; meals.get(t).add(l);
            totalCal+=l.totalCalories; pro+=l.totalProtein; fat+=l.totalFat; carb+=l.totalCarbs; sugar+=l.totalSugar; fiber+=l.totalFiber;
        }

        ScrollView sv = new ScrollView(this);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(40, 32, 40, 32); content.setBackgroundColor(ContextCompat.getColor(this, R.color.bg_primary));

        TextView title = new TextView(this); title.setText(day + " " + monthFormat.format(currentCal.getTime()));
        title.setTextSize(20); title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(ContextCompat.getColor(this, R.color.text_primary)); title.setGravity(Gravity.CENTER); title.setPadding(0,0,0,20); content.addView(title);

        TextView sum = new TextView(this); sum.setText(String.format(Locale.getDefault(),
                "Калории: %d ккал\n\nБелки: %d г | Жиры: %d г | Углеводы: %d г\nСахар: %d г | Клетчатка: %d г",
                totalCal, pro, fat, carb, sugar, fiber));
        sum.setTextSize(14);
        sum.setTextColor(accentColor);
        sum.setPadding(0,0,0,24); sum.setGravity(Gravity.CENTER); sum.setTypeface(null, android.graphics.Typeface.BOLD); content.addView(sum);

        for (Map.Entry<String, List<FoodLog>> e : meals.entrySet()) {
            if (!e.getValue().isEmpty()) {
                TextView mt = new TextView(this); mt.setText(e.getKey()); mt.setTextSize(16);
                mt.setTypeface(null, android.graphics.Typeface.BOLD); mt.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                mt.setPadding(0,16,0,8); content.addView(mt);
                int mc=0;

                for (FoodLog l : e.getValue()) {
                    mc += l.totalCalories;
                    String name = (l.foodName != null ? l.foodName : "Продукт");
                    TextView it = new TextView(this);
                    it.setText(name + " — " + l.grams + " г (" + l.totalCalories + " ккал)");
                    it.setTextSize(14); it.setTextColor(ContextCompat.getColor(this, R.color.text_secondary)); it.setPadding(12,4,12,4);
                    content.addView(it);
                }

                TextView tot = new TextView(this); tot.setText("Итого: "+mc+" ккал"); tot.setTextSize(13);
                tot.setTypeface(null, android.graphics.Typeface.BOLD);
                tot.setTextColor(accentColor);
                tot.setPadding(12,6,12,12); tot.setGravity(Gravity.END); content.addView(tot);

                View div = new View(this); div.setBackgroundColor(ContextCompat.getColor(this, R.color.text_secondary));
                div.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)); div.setAlpha(0.3f); content.addView(div);
            }
        }
        sv.addView(content);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(sv).setPositiveButton("Закрыть", null).create();
        dialog.show();
        try { dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setTextColor(accentColor); } catch (Exception ignored) {}
    }
}