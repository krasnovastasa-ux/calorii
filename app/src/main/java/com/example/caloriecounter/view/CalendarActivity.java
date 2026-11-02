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

        updateToggleText();
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
        binding.btnToggleView.setText(isYearView ? "Месяц  ⌄" : "Год  ⌄");
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
        int cellHeight = (int) (68 * density);

        for (int r = 0; r < rows; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cellHeight));
            row.setPadding(2, 2, 2, 2);

            for (int c = 0; c < 7; c++) {
                LinearLayout cell = new LinearLayout(this);
                cell.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
                cellParams.setMargins(ActivityUi.dp(this,2),ActivityUi.dp(this,3),ActivityUi.dp(this,2),ActivityUi.dp(this,3));
                cell.setLayoutParams(cellParams);
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
        bg.setCornerRadius(16 * getResources().getDisplayMetrics().density);
        bg.setStroke(1, androidx.core.graphics.ColorUtils.setAlphaComponent(ActivityPalette.muted(this),55));

        if (dateStr.equals(todayStr)) {
            String accentHex = com.example.caloriecounter.utils.ThemeUtils.getAccent(this);
            int accentColor = android.graphics.Color.parseColor(accentHex);

            bg.setColor(accentColor);
            bg.setStroke(0, Color.TRANSPARENT);
            dayTv.setTextColor(Color.WHITE); dayTv.setTypeface(null, android.graphics.Typeface.BOLD);
            calTv.setTextColor(Color.WHITE);

            cell.setTag("accent_circle");
        } else {
            bg.setColor(ActivityPalette.surface(this));
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
                            bg.setColor(ActivityPalette.surface(this));
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

    private int dp(int n){return ActivityUi.dp(this,n);}
    private TextView dayText(String value,int size,boolean bold){
        TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(ActivityPalette.text(this));
        if(bold)v.setTypeface(null,android.graphics.Typeface.BOLD);return v;
    }
    private void buildDayDialog(String date,int day,List<FoodLog> logs){
        if(isFinishing()||isDestroyed())return;
        int accent=ActivityPalette.accent(this);
        int cal=0,protein=0,fat=0,carbs=0,sugar=0;
        Map<String,List<FoodLog>> meals=new LinkedHashMap<>();
        for(String key:new String[]{"Завтрак","Обед","Ужин","Перекус"})meals.put(key,new ArrayList<>());
        if(logs!=null)for(FoodLog log:logs){
            if(log==null)continue;
            cal+=log.totalCalories;protein+=log.totalProtein;fat+=log.totalFat;carbs+=log.totalCarbs;sugar+=log.totalSugar;
            String key=meals.containsKey(log.mealType)?log.mealType:"Перекус";meals.get(key).add(log);
        }
        android.content.SharedPreferences p=repo.getPrefs();
        int weight=p.getInt("weight",0),height=p.getInt("height",0),age=p.getInt("age",0);
        int goal=weight>0&&height>0&&age>0?com.example.caloriecounter.utils.NutritionCalculator.calculateTargetCalories(weight,height,age,p.getString("gender","Женский"),p.getString("lifestyle","sedentary"),p.getString("goal","maintain")):0;
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(16),dp(16),dp(16),dp(16));root.setBackground(ActivityUi.shape(ActivityPalette.surface(this),dp(26)));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=dayText(java.time.LocalDate.parse(date).format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy",new Locale("ru"))),20,true);title.setGravity(Gravity.CENTER);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView cross=dayText("×",30,false);cross.setTextColor(accent);cross.setGravity(Gravity.CENTER);header.addView(cross,new LinearLayout.LayoutParams(dp(36),dp(44)));root.addView(header);
        TextView subtitle=dayText(goal>0?"Калории: "+cal+" из "+goal+" ккал":"Калории: "+cal+" ккал",14,false);subtitle.setTextColor(ActivityPalette.muted(this));subtitle.setGravity(Gravity.CENTER);root.addView(subtitle);
        if(goal>0){
            LinearLayout progressRow=new LinearLayout(this);progressRow.setGravity(Gravity.CENTER_VERTICAL);progressRow.setPadding(0,dp(14),0,dp(14));
            android.widget.ProgressBar bar=new android.widget.ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(1000);bar.setProgress((int)Math.min(1000,cal*1000L/goal));bar.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(androidx.core.graphics.ColorUtils.setAlphaComponent(ActivityPalette.muted(this),40)));progressRow.addView(bar,new LinearLayout.LayoutParams(0,dp(16),1));
            TextView percent=dayText(Math.round(cal*100.0/goal)+"%",14,true);percent.setPadding(dp(12),0,0,0);progressRow.addView(percent);root.addView(progressRow);
        }
        LinearLayout macros=new LinearLayout(this);String[] labels={"Белки","Жиры","Углеводы","Сахар"};int[] values={protein,fat,carbs,sugar};
        for(int i=0;i<4;i++){TextView item=dayText(labels[i]+"\n"+values[i]+" г",12,false);item.setGravity(Gravity.CENTER);item.setPadding(0,dp(12),0,dp(12));item.setBackground(ActivityUi.shape(ActivityPalette.background(this),dp(16)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(3),dp(6),dp(3),dp(10));macros.addView(item,lp);}root.addView(macros);
        ScrollView scroll=new ScrollView(this);LinearLayout cards=new LinearLayout(this);cards.setOrientation(1);scroll.addView(cards);
        for(Map.Entry<String,List<FoodLog>> meal:meals.entrySet()){
            if(meal.getValue().isEmpty())continue;
            LinearLayout card=new LinearLayout(this);card.setGravity(Gravity.CENTER_VERTICAL);card.setBackground(ActivityUi.shape(ActivityPalette.background(this),dp(18)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(8);cards.addView(card,lp);
            View strip=new View(this);strip.setBackgroundColor(accent);card.addView(strip,new LinearLayout.LayoutParams(dp(4),dp(76)));
            LinearLayout body=new LinearLayout(this);body.setOrientation(1);body.setPadding(dp(14),dp(12),dp(14),dp(12));card.addView(body,new LinearLayout.LayoutParams(0,-2,1));
            LinearLayout top=new LinearLayout(this);top.addView(dayText(meal.getKey(),16,true),new LinearLayout.LayoutParams(0,-2,1));int sum=0;StringBuilder foods=new StringBuilder();for(FoodLog log:meal.getValue()){sum+=log.totalCalories;if(foods.length()>0)foods.append("\n");foods.append(log.foodName==null?"Продукт":log.foodName);}top.addView(dayText(sum+" ккал",14,false));body.addView(top);TextView names=dayText(foods.toString(),13,false);names.setTextColor(ActivityPalette.muted(this));names.setPadding(0,dp(6),0,0);body.addView(names);
        }
        if(logs==null||logs.isEmpty()){TextView empty=dayText("Записей за этот день нет",15,false);empty.setPadding(0,dp(20),0,dp(20));cards.addView(empty);}
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        android.widget.Button close=new android.widget.Button(this);close.setText("Закрыть");close.setAllCaps(false);close.setTextColor(Color.WHITE);close.setBackground(ActivityUi.shape(accent,dp(20)));LinearLayout.LayoutParams closeLp=new LinearLayout.LayoutParams(-1,dp(48));closeLp.topMargin=dp(16);root.addView(close,closeLp);
        AlertDialog dialog=new AlertDialog.Builder(this).setView(root).create();cross.setOnClickListener(v->dialog.dismiss());close.setOnClickListener(v->dialog.dismiss());dialog.show();
        if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);dialog.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels-dp(24),(int)(getResources().getDisplayMetrics().heightPixels*0.78));}
    }
    @Override protected void onResume(){super.onResume();if(binding!=null)binding.getRoot().post(this::styleCalendarButtons);}
    private void styleCalendarButtons(){
        int accent=ActivityPalette.accent(this);
        for(com.google.android.material.button.MaterialButton b:new com.google.android.material.button.MaterialButton[]{binding.btnPrev,binding.btnNext,binding.btnToday,binding.btnToggleView}){
            b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ActivityPalette.surface(this)));b.setTextColor(accent);b.setCornerRadius(dp(16));b.setStrokeWidth(dp(1));b.setStrokeColor(android.content.res.ColorStateList.valueOf(androidx.core.graphics.ColorUtils.setAlphaComponent(accent,120)));b.setAllCaps(false);
        }
    }
}
