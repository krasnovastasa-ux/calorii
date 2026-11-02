package com.example.caloriecounter.view;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.caloriecounter.R;
import com.example.caloriecounter.model.ActivityType;
import com.example.caloriecounter.model.ProfileData;
import com.example.caloriecounter.repository.ActivityRepository;
import com.example.caloriecounter.repository.SupabaseRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ActivityEntryActivity extends BaseActivity {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private ActivityRepository repo;
    private ActivityType activity;

    private LocalDate date;
    private String requestId;
    private String restoredAmount = "";
    private String restoredSets = "1";
    private double profileWeight;
    private int restoredUnit;

    private EditText amount;
    private EditText sets;

    private Spinner units;
    private TextView calories;
    private TextView dateView;
    private Button save;

    private final List<String> unitCodes = new ArrayList<>();
    private boolean saving;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        repo = new ActivityRepository(this);

        String dateExtra = getIntent().getStringExtra("date");
        date = dateExtra == null ? LocalDate.now() : LocalDate.parse(dateExtra);
        if (date.isAfter(LocalDate.now())) {
            date = LocalDate.now();
        }

        requestId = UUID.randomUUID().toString();

        if (state != null) {
            requestId = state.getString("id", requestId);
            date = LocalDate.parse(state.getString("date", date.toString()));
            restoredAmount = state.getString("amount", "");
            restoredSets = state.getString("sets", "1");
            restoredUnit = state.getInt("unit");
        }

        TextView loading = ActivityUi.text(this, "Загрузка активности…", 18, ActivityPalette.text(this));
        loading.setBackgroundColor(ActivityPalette.background(this));
        setContentView(loading);

        load(loading);
    }

    private void load(TextView loading) {
        repo.catalog(new ActivityRepository.Result<List<ActivityType>>() {
            @Override
            public void ok(List<ActivityType> data) {
                if (!alive()) {
                    return;
                }
                if (data != null) {
                    String activityId = getIntent().getStringExtra("activity_id");
                    for (ActivityType item : data) {
                        if (item.id.equals(activityId)) {
                            activity = item;
                            loadProfile(loading);
                            return;
                        }
                    }
                }
                loading.setText("Активность недоступна.");
            }

            @Override
            public void error(String message) {
                if (!alive()) {
                    return;
                }
                loading.setText(message + " Нажмите для повтора.");
                loading.setOnClickListener(v -> load(loading));
            }
        });
    }

    private void loadProfile(TextView loading) {
        loading.setText("Загрузка профиля…");

        new SupabaseRepository(this).fetchProfile(new SupabaseRepository.ProfileLoadedCallback() {
            @Override
            public void onProfileLoaded(ProfileData profile) {
                if (!alive()) {
                    return;
                }
                if (profile == null
                        || profile.weight == null
                        || profile.weight < 20
                        || profile.weight > 400) {
                    loading.setText("Укажите корректный вес в профиле, затем откройте активность снова.");
                    loading.setOnClickListener(v -> {
                        startActivity(new Intent(ActivityEntryActivity.this, ProfileActivity.class));
                        finish();
                    });
                    return;
                }
                profileWeight = profile.weight;
                screen();
            }

            @Override
            public void onError(String message) {
                if (!alive()) {
                    return;
                }
                loading.setText("Не удалось загрузить вес из профиля. Нажмите для повтора.");
                loading.setOnClickListener(v -> loadProfile(loading));
            }
        });
    }

    private boolean alive() {
        return !isFinishing() && !isDestroyed();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("id", requestId);
        outState.putString("date", date.toString());
        outState.putString("amount", amount == null ? restoredAmount : amount.getText().toString());
        outState.putString("sets", sets == null ? restoredSets : sets.getText().toString());
        outState.putInt("unit", units == null ? restoredUnit : units.getSelectedItemPosition());
    }

    private void screen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(ActivityPalette.background(this));
        LinearLayout root = ActivityUi.column(this);
        scroll.addView(root);
        setContentView(scroll);

        LinearLayout hero = ActivityUi.column(this);
        hero.setBackgroundColor(ActivityPalette.background(this));
        root.addView(hero, new LinearLayout.LayoutParams(-1, ActivityUi.dp(this, 152)));

        TextView back = ActivityUi.text(this, "‹", 38, ActivityPalette.accent(this));
        back.setPadding(ActivityUi.dp(this, 20), 0, 0, 0);
        hero.addView(back);
        back.setOnClickListener(v -> {
            if (!saving) {
                finish();
            }
        });

        ImageView runner = new ImageView(this);
        runner.setImageResource(R.drawable.ic_activity_runner);
        runner.setImageTintList(ColorStateList.valueOf(ActivityPalette.accent(this)));
        runner.setTag("accent_icon");

        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                ActivityUi.dp(this, 76),
                ActivityUi.dp(this, 76));
        imageParams.gravity = Gravity.CENTER;
        imageParams.topMargin = ActivityUi.dp(this, 12);
        hero.addView(runner, imageParams);

        LinearLayout body = ActivityUi.column(this);
        int padding = ActivityUi.dp(this, 16);
        body.setPadding(padding, padding, padding, padding);
        root.addView(body);

        TextView title = ActivityUi.text(this, activity.name, 27, ActivityPalette.text(this));
        title.setTypeface(null, 1);
        title.setGravity(Gravity.CENTER);
        body.addView(title);

        LinearLayout dateCard = ActivityUi.card(this, body);
        dateView = ActivityUi.text(this, "", 16, ActivityPalette.accent(this));
        dateView.setTag("accent");
        dateCard.addView(dateView);
        updateDate();
        dateCard.setOnClickListener(v -> pickDate());

        LinearLayout measurement = ActivityUi.card(this, body);
        measurement.addView(ActivityUi.text(this,
                activity.timed() ? "◷  Время" : "Повторения и подходы",
                16, ActivityPalette.text(this)));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        measurement.addView(row);

        amount = ActivityUi.number(this, activity.timed() ? "Длительность" : "Раз в подходе");
        if (!activity.timed()) {
            amount.setInputType(InputType.TYPE_CLASS_NUMBER);
        }
        amount.setText(restoredAmount);
        row.addView(amount, new LinearLayout.LayoutParams(0, -2, 1));

        if (activity.timed()) {
            unitCodes.clear();
            if (activity.time_units != null) {
                for (String code : activity.time_units) {
                    if ("minutes".equals(code) || "hours".equals(code)) {
                        unitCodes.add(code);
                    }
                }
            }
            if (unitCodes.isEmpty()) {
                measurement.addView(ActivityUi.text(this,
                        "Обновите настройки каталога в Supabase: выполните SQL из исправления.",
                        14, ActivityPalette.muted(this)));
            }

            units = new Spinner(this, Spinner.MODE_DIALOG);
            units.setPrompt("Единица времени");

            List<String> labels = new ArrayList<>();
            for (String code : unitCodes) {
                labels.add("hours".equals(code) ? "Часы" : "Минуты");
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_dropdown_item, labels) {
                @Override
                public View getView(int position, View recycled, ViewGroup parent) {
                    TextView view = (TextView) super.getView(position, recycled, parent);
                    view.setTextColor(ActivityPalette.text(ActivityEntryActivity.this));
                    return view;
                }
            };
            units.setAdapter(adapter);
            units.setSelection(Math.min(restoredUnit, Math.max(0, labels.size() - 1)));
            row.addView(units, new LinearLayout.LayoutParams(0, ActivityUi.dp(this, 56), 1));
        } else {
            row.addView(ActivityUi.text(this, "повт.", 18, ActivityPalette.text(this)));
            if (activity.sets_enabled) {
                sets = ActivityUi.number(this, "Подходов");
                sets.setInputType(InputType.TYPE_CLASS_NUMBER);
                sets.setText(restoredSets);
                measurement.addView(ActivityUi.text(this, "Подходов", 14, ActivityPalette.muted(this)));
                measurement.addView(sets);
            }
        }

        calories = ActivityUi.text(this, "Введите количество", 22, ActivityPalette.accent(this));
        calories.setGravity(Gravity.CENTER);
        calories.setTag("accent");
        calories.setPadding(0, padding, 0, padding);
        body.addView(calories);

        save = ActivityUi.button(this, "ВНЕСТИ");
        body.addView(save, new LinearLayout.LayoutParams(-1, ActivityUi.dp(this, 54)));
        save.setOnClickListener(v -> save());

        LinearLayout energy = ActivityUi.card(this, body);
        energy.addView(ActivityUi.text(this, "♨  Энергия", 17, ActivityPalette.text(this)));

        TextView energyDetails = ActivityUi.text(this,
                "≈ " + ActivityUi.num((activity.met - 1) / 60)
                        + " ккал/кг/мин сверх покоя"
                        + (activity.timed()
                        ? ""
                        : "\nРасчётный темп: " + activity.seconds_per_rep + " сек/повтор"),
                16, ActivityPalette.muted(this));
        energyDetails.setPadding(0, padding, 0, 0);
        energy.addView(energyDetails);
        energy.setOnClickListener(v -> energyDetails.setVisibility(
                energyDetails.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));

        LinearLayout note = ActivityUi.card(this, body);
        note.addView(ActivityUi.text(this, "ⓘ  Примечание", 17, ActivityPalette.text(this)));

        TextView description = ActivityUi.text(this,
                activity.description + "\n\n"
                        + "Калории — оценка по весу и интенсивности. "
                        + "Для повторений время рассчитано по темпу из каталога. "
                        + "Они не прибавляются автоматически к норме питания.",
                15, ActivityPalette.muted(this));
        description.setPadding(0, padding, 0, 0);
        note.addView(description);
        note.setOnClickListener(v -> description.setVisibility(
                description.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                preview();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
        amount.addTextChangedListener(watcher);

        if (sets != null) {
            sets.addTextChangedListener(watcher);
        }

        if (units != null) {
            units.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    preview();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        }

        preview();
    }

    private void updateDate() {
        dateView.setText("▣  Дата                         " + date.format(DATE_FORMAT));
    }

    private void pickDate() {
        if (saving) {
            return;
        }
        DatePickerDialog dialog = new DatePickerDialog(this,
                (picker, year, month, day) -> {
                    date = LocalDate.of(year, month + 1, day);
                    updateDate();
                },
                date.getYear(),
                date.getMonthValue() - 1,
                date.getDayOfMonth());
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    private double number(EditText edit) {
        return Double.parseDouble(edit.getText().toString().trim().replace(',', '.'));
    }

    private double quantity() {
        double value = number(amount);
        if (activity.timed()) {
            if (units == null || unitCodes.isEmpty()) {
                throw new IllegalArgumentException();
            }
            if ("hours".equals(unitCodes.get(units.getSelectedItemPosition()))) {
                value *= 60;
            }
        }
        return value;
    }

    private int countSets() {
        return sets == null ? 1 : Integer.parseInt(sets.getText().toString().trim());
    }

    private double minutes() {
        return activity.timed()
                ? quantity()
                : quantity() * countSets() * activity.seconds_per_rep / 60;
    }

    private void validate() {
        amount.setError(null);
        if (sets != null) {
            sets.setError(null);
        }

        if (activity.timed() && unitCodes.isEmpty()) {
            throw new IllegalArgumentException(
                    "В каталоге не настроены единицы времени. Выполните SQL-обновление и откройте карточку заново.");
        }

        double quantity;
        try {
            quantity = quantity();
        } catch (Exception ex) {
            amount.setError("Введите длительность или число повторений");
            throw new IllegalArgumentException("Введите количество");
        }
        if (!Double.isFinite(quantity)
                || quantity <= 0
                || quantity > 100000
                || (!activity.timed() && quantity != Math.floor(quantity))) {
            amount.setError(activity.timed()
                    ? "Введите положительную длительность"
                    : "Повторения должны быть целым числом больше нуля");
            throw new IllegalArgumentException("Проверьте количество");
        }

        int setsCount;
        try {
            setsCount = countSets();
        } catch (Exception ex) {
            if (sets != null) {
                sets.setError("Введите число подходов");
            }
            throw new IllegalArgumentException("Введите количество подходов");
        }
        if (setsCount < 1 || setsCount > 1000) {
            if (sets != null) {
                sets.setError("Подходов должно быть от 1 до 1000");
            }
            throw new IllegalArgumentException("Проверьте число подходов");
        }

        if (!Double.isFinite(profileWeight) || profileWeight < 20 || profileWeight > 400) {
            throw new IllegalArgumentException("Проверьте вес в профиле");
        }

        if (!activity.timed()
                && (activity.seconds_per_rep == null || activity.seconds_per_rep <= 0)) {
            throw new IllegalArgumentException("Для упражнения не настроен темп повторений в базе");
        }

        double minutes;
        try {
            minutes = minutes();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Проверьте количество");
        }
        if (!Double.isFinite(minutes) || minutes <= 0 || minutes > 1440) {
            amount.setError("Длительность не должна превышать 24 часа");
            throw new IllegalArgumentException("Проверьте длительность");
        }
    }

    private void preview() {
        if (calories == null) {
            return;
        }
        try {
            double quantity = quantity();
            int setsCount = countSets();
            double minutes = minutes();

            if (!Double.isFinite(quantity)
                    || quantity <= 0
                    || setsCount < 1
                    || !Double.isFinite(minutes)
                    || minutes <= 0
                    || minutes > 1440
                    || (!activity.timed() && quantity != Math.floor(quantity))) {
                calories.setText("Введите корректное количество");
                return;
            }

            double kcal = (activity.met - 1) * profileWeight * minutes / 60;
            calories.setText("≈ " + ActivityUi.num(kcal) + " ккал");
        } catch (Exception ex) {
            calories.setText(activity.timed() && unitCodes.isEmpty()
                    ? "Единицы времени ещё не настроены в каталоге"
                    : "Введите " + (activity.timed() ? "длительность" : "повторения и подходы"));
        }
    }

    private void save() {
        if (saving) {
            return;
        }
        try {
            validate();
        } catch (Exception ex) {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        saving = true;
        save.setEnabled(false);
        save.setText("СОХРАНЕНИЕ…");
        amount.setEnabled(false);
        if (sets != null) {
            sets.setEnabled(false);
        }
        if (units != null) {
            units.setEnabled(false);
        }

        repo.record(requestId, activity, date.toString(),
                quantity(), profileWeight, countSets(),
                new ActivityRepository.Result<Void>() {
                    @Override
                    public void ok(Void result) {
                        if (!alive()) {
                            return;
                        }
                        saving = false;
                        startActivity(new Intent(ActivityEntryActivity.this, TrainingActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                .putExtra("saved_date", date.toString()));
                        finish();
                    }

                    @Override
                    public void error(String message) {
                        if (!alive()) {
                            return;
                        }
                        saving = false;
                        save.setEnabled(true);
                        save.setText("ПОВТОРИТЬ ВНЕСЕНИЕ");
                        amount.setEnabled(true);
                        if (sets != null) {
                            sets.setEnabled(true);
                        }
                        if (units != null) {
                            units.setEnabled(true);
                        }
                        Toast.makeText(ActivityEntryActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    @Override
    public void onBackPressed() {
        if (!saving) {
            super.onBackPressed();
        }
    }
}