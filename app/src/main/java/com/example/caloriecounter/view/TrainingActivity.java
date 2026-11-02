package com.example.caloriecounter.view;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.caloriecounter.R;
import com.example.caloriecounter.model.ActivityRecord;
import com.example.caloriecounter.model.ActivityType;
import com.example.caloriecounter.repository.ActivityRepository;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TrainingActivity extends BaseActivity {

    private static final Locale RU = new Locale("ru");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String[] WEEKDAYS = {"ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС"};

    private LocalDate selected = LocalDate.now();
    private ActivityRepository repo;
    private int version;

    private boolean recordsReady;
    private LocalDate loadedFrom;

    private final List<ActivityRecord> records = new ArrayList<>();
    private final List<ActivityType> catalog = new ArrayList<>();

    private boolean deleting;
    private EditText searchInput;

    private boolean catalogLoaded;
    private String catalogError;
    private int catalogVersion;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_training);
        repo = new ActivityRepository(this);

        if (repo.uid().isEmpty()) {
            finish();
            return;
        }

        if (state != null) {
            selected = LocalDate.parse(state.getString("date", LocalDate.now().toString()));
        }

        searchInput = findViewById(R.id.actSearch);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterSearch();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        searchInput.setOnFocusChangeListener((v, focused) -> filterSearch());
        searchInput.setOnEditorActionListener((v, action, event) -> {
            filterSearch();
            return false;
        });
        findViewById(R.id.actSearchStatus).setOnClickListener(v -> {
            if (catalogError != null) {
                loadCatalog();
            }
        });

        if (state != null) {
            searchInput.setText(state.getString("query", ""));
        }

        loadCatalog();

        findViewById(R.id.actToday).setOnClickListener(v -> {
            selected = LocalDate.now();
            week();
            load();
        });

        BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        nav.setSelectedItemId(R.id.nav_training);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_training) {
                return true;
            }
            Class<?> target = id == R.id.nav_food ? MainActivity.class
                    : id == R.id.nav_diets ? DietListActivity.class
                    : id == R.id.nav_water ? WaterTrackerActivity.class
                    : WeightTrackerActivity.class;
            startActivity(new Intent(this, target));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            return false;
        });

        week();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String date = intent.getStringExtra("saved_date");
        if (date != null) {
            selected = LocalDate.parse(date);
            week();
            load();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        recordsReady = false;
        if (searchInput != null) {
            filterSearch();
        }
        week();
        if (repo != null && !repo.uid().isEmpty()) {
            load();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("date", selected.toString());
        outState.putString("query", searchInput.getText().toString());
    }

    private void loadCatalog() {
        final int request = ++catalogVersion;
        catalogLoaded = false;
        catalogError = null;
        filterSearch();

        repo.catalog(new ActivityRepository.Result<List<ActivityType>>() {
            @Override
            public void ok(List<ActivityType> data) {
                if (!alive() || request != catalogVersion) {
                    return;
                }
                catalog.clear();
                if (data != null) {
                    catalog.addAll(data);
                }
                catalogLoaded = true;
                filterSearch();
            }

            @Override
            public void error(String message) {
                if (!alive() || request != catalogVersion) {
                    return;
                }
                catalogError = message;
                filterSearch();
            }
        });
    }

    private void filterSearch() {
        if (searchInput == null) {
            return;
        }

        LinearLayout panel = findViewById(R.id.actSearchPanel);
        LinearLayout results = findViewById(R.id.actSearchResults);
        TextView info = findViewById(R.id.actSearchStatus);

        String query = searchInput.getText().toString().trim().toLowerCase(RU);
        String normalizedQuery = query.replace('ё', 'е');

        panel.setVisibility(!query.isEmpty() ? View.VISIBLE : View.GONE);
        results.removeAllViews();

        if (query.isEmpty()) {
            info.setText("");
            return;
        }

        if (!catalogLoaded) {
            info.setText(catalogError == null
                    ? "Загрузка упражнений…"
                    : catalogError + " Нажмите для повтора.");
            return;
        }

        int count = 0;
        for (ActivityType activity : catalog) {
            String normalizedName = activity.name.toLowerCase(RU).replace('ё', 'е');
            if (!normalizedName.contains(normalizedQuery)) {
                continue;
            }
            count++;
            results.addView(searchResultRow(activity));
        }

        info.setText(count == 0
                ? "Совпадений нет"
                : "Найдено: " + count);
    }

    private LinearLayout searchResultRow(ActivityType activity) {
        int padding = ActivityUi.dp(this, 12);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(padding, padding, padding, padding);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_activity_runner);
        icon.setColorFilter(ActivityPalette.accent(this));
        icon.setTag("accent_icon");
        row.addView(icon, new LinearLayout.LayoutParams(
                ActivityUi.dp(this, 30),
                ActivityUi.dp(this, 30)));

        String subtitle = activity.timed()
                ? "Время"
                : "Повторения" + (activity.sets_enabled ? " и подходы" : "");
        TextView name = new TextView(this);
        name.setText(activity.name + "\n" + subtitle);
        name.setTextSize(16);
        name.setTextColor(getResources().getColor(R.color.text_primary, getTheme()));
        name.setPadding(padding, 0, padding, 0);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));

        TextView plus = new TextView(this);
        plus.setText("＋");
        plus.setTextSize(26);
        plus.setTag("accent");
        plus.setTextColor(ActivityPalette.accent(this));
        row.addView(plus);

        row.setOnClickListener(v -> openEntry(activity));

        return row;
    }

    private void openEntry(ActivityType activity) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        searchInput.clearFocus();
        searchInput.setText("");
        startActivity(new Intent(this, ActivityEntryActivity.class)
                .putExtra("activity_id", activity.id)
                .putExtra("date", selected.toString()));
    }

    private void week() {
        LinearLayout box = findViewById(R.id.actWeek);
        box.removeAllViews();

        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int size = (int) (47.5 * getResources().getDisplayMetrics().density);

        for (int i = 0; i < 7; i++) {
            LocalDate date = monday.plusDays(i);
            box.addView(weekDayItem(date, i, size));
        }
    }

    private LinearLayout weekDayItem(LocalDate date, int index, int size) {
        boolean checked = date.equals(selected);

        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER_HORIZONTAL);
        item.setPadding(4, 2, 4, 2);

        TextView circle = new TextView(this);
        circle.setText(String.valueOf(date.getDayOfMonth()));
        circle.setTextSize(15);
        circle.setTypeface(null, 1);
        circle.setGravity(Gravity.CENTER);
        circle.setTextColor(checked
                ? Color.WHITE
                : getResources().getColor(R.color.text_primary, getTheme()));
        circle.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        circle.setBackgroundResource(checked
                ? R.drawable.circle_selected
                : R.drawable.circle_bg);
        if (checked) {
            circle.setTag("accent_circle");
            circle.setBackgroundTintList(ColorStateList.valueOf(ActivityPalette.accent(this)));
        }

        TextView label = new TextView(this);
        label.setText(WEEKDAYS[index]);
        label.setTextSize(9);
        label.setGravity(Gravity.CENTER);
        label.setLayoutParams(new LinearLayout.LayoutParams(size, -2));
        label.setTextColor(getResources().getColor(R.color.text_secondary, getTheme()));
        label.setPadding(0, 1, 0, 0);

        item.addView(circle);
        item.addView(label);

        item.setOnClickListener(v -> {
            selected = date;
            week();
            load();
        });

        return item;
    }

    private boolean alive() {
        return !isFinishing() && !isDestroyed();
    }

    private void load() {
        LocalDate neededFrom = selected.minusDays(1);
        if (recordsReady && loadedFrom != null && !neededFrom.isBefore(loadedFrom)) {
            render();
            return;
        }

        LocalDate weekStart = LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .minusDays(1);
        final LocalDate requestFrom = neededFrom.isBefore(weekStart) ? neededFrom : weekStart;
        int request = ++version;

        TextView status = findViewById(R.id.actStatus);
        status.setText("Загрузка…");

        ((TextView) findViewById(R.id.actBurned)).setText("");
        ((TextView) findViewById(R.id.actComparison)).setText("");
        ((LinearLayout) findViewById(R.id.actRecords)).removeAllViews();

        repo.records(requestFrom.toString(),
                new ActivityRepository.Result<List<ActivityRecord>>() {
                    @Override
                    public void ok(List<ActivityRecord> data) {
                        if (!alive() || request != version) {
                            return;
                        }
                        recordsReady = true;
                        loadedFrom = requestFrom;
                        records.clear();
                        if (data != null) {
                            records.addAll(data);
                        }
                        render();
                    }

                    @Override
                    public void error(String message) {
                        if (alive() && request == version) {
                            status.setText(message);
                        }
                    }
                });
    }

    private void render() {
        double sum = 0;
        double previous = 0;
        int count = 0;
        int previousCount = 0;

        LinearLayout box = findViewById(R.id.actRecords);
        box.removeAllViews();

        String selectedDate = selected.toString();
        String previousDate = selected.minusDays(1).toString();

        for (ActivityRecord record : records) {
            if (record.log_date.equals(previousDate)) {
                previous += record.calories;
                previousCount++;
            }
            if (!record.log_date.equals(selectedDate)) {
                continue;
            }

            sum += record.calories;
            count++;
            box.addView(recordCard(record));
        }

        ((TextView) findViewById(R.id.actBurned)).setText(
                Math.round(sum) <= 0
                        ? "0 ккал"
                        : "≈ " + Math.round(sum) + " ккал");

        String comparison = buildComparison(sum, previous, count, previousCount);
        ((TextView) findViewById(R.id.actComparison)).setText(comparison
                + "\nПо внесённым активностям"
                + (selected.equals(LocalDate.now())
                ? "; сегодняшний день ещё не завершён."
                : "."));

        ((TextView) findViewById(R.id.actStatus)).setText(count == 0
                ? "Активностей за этот день пока нет. Добавьте их через поиск."
                : selected.format(DATE_FORMAT) + " • Записей: " + count);
    }

    private String buildComparison(double sum, double previous, int count, int previousCount) {
        if (previousCount == 0) {
            return "За предыдущий день нет записей для сравнения.";
        }
        if (count == 0) {
            return "За выбранный день пока нет записей.";
        }
        if (Math.abs(sum - previous) < 0.05) {
            return "Расход такой же, как в предыдущий день.";
        }
        if (previous == 0) {
            return "Расход выше на " + ActivityUi.num(sum) + " ккал.";
        }
        return "Расход " + (sum > previous ? "выше" : "ниже")
                + " на " + ActivityUi.num(Math.abs(sum - previous) / previous * 100)
                + "% к предыдущему дню.";
    }

    private MaterialCardView recordCard(ActivityRecord record) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(12 * getResources().getDisplayMetrics().density);
        card.setCardElevation(2);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = 12;
        card.setLayoutParams(params);

        TextView row = new TextView(this);
        row.setPadding(20, 22, 20, 22);
        row.setTextSize(16);
        row.setTextColor(getResources().getColor(R.color.text_primary, getTheme()));
        row.setText(record.activity_name + "\n"
                + formatQuantity(record) + "   •   ≈ "
                + ActivityUi.num(record.calories) + " ккал");

        card.addView(row);
        card.setOnClickListener(v -> showActivityDialog(record));

        return card;
    }

    private void showActivityDialog(ActivityRecord record) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(record.activity_name)
                .setMessage("Дата: " + record.log_date + "\nРасход: ≈ "
                        + ActivityUi.num(record.calories) + " ккал")
                .setNegativeButton("Закрыть", null)
                .setPositiveButton("Удалить", (d, which) -> remove(record))
                .create();

        dialog.setOnShowListener(d -> styleDialogButtons(dialog));
        dialog.show();
    }

    private void styleDialogButtons(AlertDialog dialog) {
        int color = ActivityPalette.accent(this);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(color);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(color);
    }

    private String formatQuantity(ActivityRecord record) {
        if ("minutes".equals(record.unit)) {
            return ActivityUi.num(record.quantity) + " мин";
        }
        return record.sets_count + " подход. × " + ActivityUi.num(record.quantity) + " повт.";
    }

    private void remove(ActivityRecord record) {
        if (deleting) {
            return;
        }
        deleting = true;

        repo.delete(record.id, new ActivityRepository.Result<Void>() {
            @Override
            public void ok(Void result) {
                if (!alive()) {
                    return;
                }
                deleting = false;
                recordsReady = false;
                load();
            }

            @Override
            public void error(String message) {
                if (!alive()) {
                    return;
                }
                deleting = false;
                Toast.makeText(TrainingActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }
}