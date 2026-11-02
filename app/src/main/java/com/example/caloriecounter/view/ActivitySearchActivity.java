package com.example.caloriecounter.view;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.caloriecounter.R;
import com.example.caloriecounter.model.ActivityType;
import com.example.caloriecounter.repository.ActivityRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ActivitySearchActivity extends BaseActivity {

    private static final Locale RU = new Locale("ru");

    private ActivityRepository repo;
    private final List<ActivityType> items = new ArrayList<>();

    private LinearLayout list;
    private EditText search;
    private TextView status;

    private String date;
    private boolean saving;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        repo = new ActivityRepository(this);
        date = getIntent().getStringExtra("date");

        buildUi();

        if (state != null) {
            search.setText(state.getString("q", ""));
        }

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                render();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        load();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("q", search.getText().toString());
    }

    private void buildUi() {
        int padding = ActivityUi.dp(this, 12);

        LinearLayout root = ActivityUi.column(this);
        root.setBackgroundColor(ActivityUi.BG);
        root.setPadding(padding, padding, padding, padding);
        setContentView(root);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(bar, new LinearLayout.LayoutParams(-1, -2));

        TextView back = ActivityUi.text(this, "‹", 38, ActivityUi.TEXT);
        back.setPadding(padding, 0, padding, 0);
        bar.addView(back);
        back.setOnClickListener(v -> finish());

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(ActivityUi.TEXT);
        search.setHintTextColor(ActivityUi.MUTED);
        search.setHint("Поиск активности");
        search.setBackground(ActivityUi.shape(ActivityUi.CARD, ActivityUi.dp(this, 24)));
        search.setPadding(padding, padding, padding, padding);
        bar.addView(search, new LinearLayout.LayoutParams(0, -2, 1));

        TextView add = ActivityUi.text(this, "＋", 32, ActivityUi.GREEN);
        add.setPadding(padding, 0, padding, 0);
        add.setContentDescription("Создать свою активность по шаблону");
        add.setOnClickListener(v -> personal());
        bar.addView(add);

        status = ActivityUi.text(this, "Загрузка каталога…", 16, ActivityUi.MUTED);
        status.setPadding(0, padding, 0, padding);
        root.addView(status);

        ScrollView scroll = new ScrollView(this);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        list = ActivityUi.column(this);
        scroll.addView(list);
    }

    private boolean alive() {
        return !isFinishing() && !isDestroyed();
    }

    private void load() {
        repo.catalog(new ActivityRepository.Result<List<ActivityType>>() {
            @Override
            public void ok(List<ActivityType> data) {
                if (!alive()) {
                    return;
                }
                items.clear();
                if (data != null) {
                    items.addAll(data);
                }
                render();
            }

            @Override
            public void error(String message) {
                if (!alive()) {
                    return;
                }
                status.setText(message + " Нажмите для повтора.");
                status.setOnClickListener(v -> load());
            }
        });
    }

    private void render() {
        list.removeAllViews();

        String query = search.getText().toString().trim().toLowerCase(RU);
        int count = 0;

        for (ActivityType activity : items) {
            if (!activity.name.toLowerCase(RU).contains(query)) {
                continue;
            }
            count++;
            activityRow(activity);
        }

        status.setText(count == 0
                ? "Ничего не найдено"
                : "Результаты поиска в разделе Деятельность");
    }

    private void activityRow(ActivityType activity) {
        LinearLayout card = ActivityUi.card(this, list);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_activity_runner);
        icon.setColorFilter(ActivityUi.MUTED);
        card.addView(icon, new LinearLayout.LayoutParams(
                ActivityUi.dp(this, 38),
                ActivityUi.dp(this, 38)));

        String subtitle = activity.timed()
                ? "Время"
                : "Повторения" + (activity.sets_enabled ? " и подходы" : "");
        TextView name = ActivityUi.text(this, activity.name + "\n" + subtitle, 17, ActivityUi.TEXT);
        name.setPadding(ActivityUi.dp(this, 12), 0, 0, 0);
        card.addView(name, new LinearLayout.LayoutParams(0, -2, 1));

        TextView plus = ActivityUi.text(this, "⊕", 28, ActivityUi.GREEN);
        card.addView(plus);

        card.setOnClickListener(v -> startActivity(new Intent(this, ActivityEntryActivity.class)
                .putExtra("activity_id", activity.id)
                .putExtra("date", date)));
    }

    private void personal() {
        if (saving) {
            return;
        }

        List<ActivityType> bases = new ArrayList<>();
        for (ActivityType activity : items) {
            if (activity.owner_id == null) {
                bases.add(activity);
            }
        }
        if (bases.isEmpty()) {
            Toast.makeText(this, "Сначала загрузите каталог", Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout box = ActivityUi.column(this);

        EditText name = new EditText(this);
        name.setHint("Название своей активности");
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100)});
        box.addView(name);

        Spinner spinner = new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, bases));
        box.addView(spinner);

        String id = UUID.randomUUID().toString();

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Своя активность по шаблону")
                .setView(box)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Создать", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    if (saving) {
                        return;
                    }

                    String customName = name.getText().toString().trim();
                    if (customName.isEmpty()) {
                        name.setError("Введите название");
                        return;
                    }

                    saving = true;
                    dialog.setCancelable(false);
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);

                    repo.personal(id, bases.get(spinner.getSelectedItemPosition()), customName,
                            new ActivityRepository.Result<Void>() {
                                @Override
                                public void ok(Void result) {
                                    if (!alive()) {
                                        return;
                                    }
                                    saving = false;
                                    dialog.dismiss();
                                    search.setText(customName);
                                    load();
                                }

                                @Override
                                public void error(String message) {
                                    if (!alive()) {
                                        return;
                                    }
                                    saving = false;
                                    dialog.setCancelable(true);
                                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                                    Toast.makeText(ActivitySearchActivity.this, message, Toast.LENGTH_LONG).show();
                                }
                            });
                }));

        dialog.show();
    }
}