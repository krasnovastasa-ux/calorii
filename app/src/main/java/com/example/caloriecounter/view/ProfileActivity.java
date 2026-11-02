package com.example.caloriecounter.view;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.caloriecounter.R;
import com.example.caloriecounter.databinding.ActivityProfileBinding;
import com.example.caloriecounter.repository.SupabaseRepository;
import com.example.caloriecounter.viewmodel.ProfileViewModel;

public class ProfileActivity extends BaseActivity {

    private static final String TAG = "PROFILE_ACTIVITY";

    private static final String ACTION_PROFILE_UPDATED =
            "com.example.caloriecounter.PROFILE_UPDATED";

    private static final String[] ACCENT_COLORS = {
            "#7041E8",
            "#E58BC0",
            "#F2B99C",
            "#80CB9E",
            "#8CB7E8",
            "#659CC3",
            "#B77CEB"
    };

    private static final String[] AVATARS = {
            "kot",
            "ttt",
            "logo",
            "sss",
            "kkk",
            "ggg",
            "qqq"
    };

    private static final String[] THEME_CODES = {"light", "dark", "system"};
    private static final String[] THEME_LABELS = {"Светлая", "Тёмная", "Как на устройстве"};

    private static final String ACCENT_DEFAULT = "#7041E8";
    private static final String AVATAR_DEFAULT = "kot";
    private static final String GOAL_MAINTAIN = "maintain";
    private static final String GOAL_LOSE = "lose";
    private static final String GOAL_GAIN = "gain";
    private static final String LIFESTYLE_SEDENTARY = "sedentary";
    private static final String LIFESTYLE_LIGHT = "light";
    private static final String LIFESTYLE_MODERATE = "moderate";
    private static final String LIFESTYLE_ACTIVE = "active";
    private static final String LIFESTYLE_VERY_ACTIVE = "very_active";
    private static final String THEME_SYSTEM = "system";
    private static final String THEME_LIGHT = "light";
    private static final String THEME_DARK = "dark";

    private ActivityProfileBinding binding;
    private ProfileViewModel viewModel;
    private SharedPreferences prefs;

    private String userId;
    private String email;

    private String[] gendersArr;
    private String[] goalsArr;
    private String[] lifestylesArr;

    private String selectedAccent = ACCENT_DEFAULT;
    private String selectedAvatar = AVATAR_DEFAULT;
    private String selectedGalleryUri;

    private ActivityResultLauncher<String[]> galleryLauncher;
    private boolean receiverRegistered;

    private final BroadcastReceiver profileUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Profile update received");

            if (userId != null && !userId.isEmpty()) {
                viewModel.loadProfile(userId);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        setupGalleryLauncher();

        gendersArr = getResources().getStringArray(R.array.genders);
        goalsArr = getResources().getStringArray(R.array.goals);
        lifestylesArr = getResources().getStringArray(R.array.lifestyles);

        userId = prefs.getString("user_id", "");
        email = prefs.getString("email", "");

        if (userId.isEmpty()) {
            new SupabaseRepository(this).clearSession();
            startActivity(new Intent(this, LoginActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
            return;
        }

        binding.tvEmail.setText(email);

        setupSpinners();
        setupAvatarClick();
        setupThemeUI();

        loadFromPrefs();
        loadThemePrefs();

        observeProfileData();
        setupObservers();
        setupClicks();

        applyThemeAndAccent();

        viewModel.loadProfile(userId);

        registerProfileUpdateReceiver();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (userId != null && !userId.isEmpty()) {
            viewModel.loadProfile(userId);
        }
    }

    @Override
    protected void onDestroy() {
        if (receiverRegistered) {
            try {
                unregisterReceiver(profileUpdateReceiver);
            } catch (IllegalArgumentException ignored) {
            }
            receiverRegistered = false;
        }

        super.onDestroy();
    }

    private void setupGalleryLauncher() {
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri == null) {
                        return;
                    }

                    try {
                        getContentResolver().takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (SecurityException ex) {
                        Log.w(TAG, "Не удалось сохранить разрешение на изображение", ex);
                    }

                    selectedGalleryUri = uri.toString();

                    prefs.edit()
                            .putString("profile_gallery_uri", selectedGalleryUri)
                            .putString("profile_avatar", "")
                            .apply();

                    updateMainAvatar();
                });
    }

    private void registerProfileUpdateReceiver() {
        IntentFilter filter = new IntentFilter(ACTION_PROFILE_UPDATED);

        ContextCompat.registerReceiver(
                this,
                profileUpdateReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED);

        receiverRegistered = true;
    }

    private void setupSpinners() {
        setupSpinner(binding.spinnerGender, gendersArr);
        setupSpinner(binding.spinnerGoal, goalsArr);
        setupSpinner(binding.spinnerLifestyle, lifestylesArr);
    }

    private void setupSpinner(android.widget.Spinner spinner, String[] items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void setupAvatarClick() {
        selectedAvatar = prefs.getString("profile_avatar", AVATAR_DEFAULT);
        selectedGalleryUri = prefs.getString("profile_gallery_uri", null);

        if (selectedAvatar == null || selectedAvatar.isEmpty()) {
            selectedAvatar = AVATAR_DEFAULT;
        }

        updateMainAvatar();
        binding.ivAvatar.setOnClickListener(v -> showAvatarDialog());
    }

    private void showAvatarDialog() {
        String[] options = {
                "Выбрать из предложенных фотографий",
                "Выбрать из галереи"
        };

        new AlertDialog.Builder(this)
                .setTitle("Фотография профиля")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showPresetAvatars();
                    } else if (which == 1) {
                        galleryLauncher.launch(new String[]{"image/*"});
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showPresetAvatars() {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setPadding(dp(12), dp(12), dp(12), dp(12));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Выбери аватар")
                .setView(grid)
                .setNegativeButton("Назад", (d, which) -> showAvatarDialog())
                .create();

        for (String avatar : AVATARS) {
            int imageId = getResources().getIdentifier(avatar, "drawable", getPackageName());

            if (imageId == 0) {
                Log.e(TAG, "Аватар не найден: " + avatar);
                continue;
            }

            ImageView image = new ImageView(this);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = dp(82);
            params.height = dp(82);
            params.setMargins(dp(8), dp(8), dp(8), dp(8));

            image.setLayoutParams(params);
            image.setImageResource(imageId);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackground(createAvatarBackground(
                    avatar.equals(selectedAvatar) && selectedGalleryUri == null));
            image.setPadding(dp(3), dp(3), dp(3), dp(3));
            image.setClipToOutline(true);
            image.setClickable(true);

            image.setOnClickListener(v -> {
                selectedAvatar = avatar;
                selectedGalleryUri = null;

                prefs.edit()
                        .putString("profile_avatar", selectedAvatar)
                        .remove("profile_gallery_uri")
                        .apply();

                updateMainAvatar();
                dialog.dismiss();
            });

            grid.addView(image);
        }

        dialog.show();
    }

    private void updateMainAvatar() {
        if (selectedGalleryUri != null && !selectedGalleryUri.isEmpty()) {
            try {
                binding.ivAvatar.setImageURI(Uri.parse(selectedGalleryUri));
                return;
            } catch (Exception ex) {
                Log.e(TAG, "Gallery image error", ex);
                selectedGalleryUri = null;
            }
        }

        int imageId = getResources().getIdentifier(selectedAvatar, "drawable", getPackageName());

        if (imageId != 0) {
            binding.ivAvatar.setImageResource(imageId);
        } else {
            selectedAvatar = AVATAR_DEFAULT;
            binding.ivAvatar.setImageResource(R.drawable.kot);
        }
    }

    private GradientDrawable createAvatarBackground(boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(Color.WHITE);
        drawable.setStroke(
                selected ? dp(3) : dp(1),
                selected
                        ? Color.parseColor("#514B5D")
                        : Color.parseColor("#E5DDF5"));
        return drawable;
    }

    private void loadFromPrefs() {
        binding.etName.setText(prefs.getString("name", ""));

        int height = prefs.getInt("height", 0);
        int weight = prefs.getInt("weight", 0);
        int age = prefs.getInt("age", 0);

        if (height > 0) {
            binding.etHeight.setText(String.valueOf(height));
        }
        if (weight > 0) {
            binding.etWeight.setText(String.valueOf(weight));
        }
        if (age > 0) {
            binding.etAge.setText(String.valueOf(age));
        }

        binding.spinnerGender.setSelection(
                findIndex(gendersArr, prefs.getString("gender", "Женский")));

        String savedGoal = prefs.getString("goal", GOAL_MAINTAIN);
        binding.spinnerGoal.setSelection(findIndex(goalsArr, goalToDisplay(savedGoal)));

        String savedLifestyle = prefs.getString("lifestyle", LIFESTYLE_MODERATE);
        binding.spinnerLifestyle.setSelection(
                findIndex(lifestylesArr, lifestyleToDisplay(savedLifestyle)));
    }

    private String goalToDisplay(String key) {
        if (GOAL_LOSE.equals(key)) {
            return "Похудеть";
        }
        if (GOAL_GAIN.equals(key)) {
            return "Набрать";
        }
        return "Поддерживать";
    }

    private String goalToKey(String display) {
        if ("Похудеть".equals(display)) {
            return GOAL_LOSE;
        }
        if ("Набрать".equals(display)) {
            return GOAL_GAIN;
        }
        return GOAL_MAINTAIN;
    }

    private String lifestyleToDisplay(String key) {
        if (LIFESTYLE_SEDENTARY.equals(key)) {
            return "Сидячий";
        }
        if (LIFESTYLE_LIGHT.equals(key)) {
            return "Лёгкая активность";
        }
        if (LIFESTYLE_MODERATE.equals(key)) {
            return "Средняя активность";
        }
        if (LIFESTYLE_ACTIVE.equals(key)) {
            return "Высокая активность";
        }
        return "Очень высокая активность";
    }

    private String lifestyleToKey(String display) {
        if ("Сидячий".equals(display)) {
            return LIFESTYLE_SEDENTARY;
        }
        if ("Лёгкая активность".equals(display)) {
            return LIFESTYLE_LIGHT;
        }
        if ("Средняя активность".equals(display)) {
            return LIFESTYLE_MODERATE;
        }
        if ("Высокая активность".equals(display)) {
            return LIFESTYLE_ACTIVE;
        }
        return LIFESTYLE_VERY_ACTIVE;
    }

    private int findIndex(String[] arr, String value) {
        if (value == null) {
            return 0;
        }
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] != null && arr[i].trim().equalsIgnoreCase(value.trim())) {
                return i;
            }
        }
        return 0;
    }

    private void observeProfileData() {
        viewModel.getProfileData().observe(this, profile -> {
            if (profile == null) {
                Log.w(TAG, "Profile is null");
                return;
            }

            if (profile.name != null) {
                binding.etName.setText(profile.name);
            }
            if (profile.height != null && profile.height > 0) {
                binding.etHeight.setText(String.valueOf(profile.height));
            }
            if (profile.weight != null && profile.weight > 0) {
                binding.etWeight.setText(String.valueOf(profile.weight));
            }
            if (profile.age != null && profile.age > 0) {
                binding.etAge.setText(String.valueOf(profile.age));
            }
            if (profile.gender != null) {
                binding.spinnerGender.setSelection(findIndex(gendersArr, profile.gender));
            }
            if (profile.goal != null) {
                binding.spinnerGoal.setSelection(
                        findIndex(goalsArr, goalToDisplay(profile.goal)));
            }
            if (profile.lifestyle != null) {
                binding.spinnerLifestyle.setSelection(
                        findIndex(lifestylesArr, lifestyleToDisplay(profile.lifestyle)));
            }

            prefs.edit()
                    .putString("name", profile.name)
                    .putInt("height", profile.height != null ? profile.height : 0)
                    .putInt("weight", profile.weight != null ? profile.weight : 0)
                    .putInt("age", profile.age != null ? profile.age : 0)
                    .putString("gender", profile.gender)
                    .putString("goal", profile.goal)
                    .putString("lifestyle", profile.lifestyle)
                    .apply();
        });
    }

    private void setupObservers() {
        viewModel.getErrorMessage().observe(this, message -> {
            if (message == null || message.isEmpty()) {
                return;
            }

            Log.e(TAG, "Profile error: " + message);

            binding.progressBar.setVisibility(View.GONE);
            binding.btnSave.setEnabled(true);

            new AlertDialog.Builder(this)
                    .setTitle("Ошибка")
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show();
        });

        viewModel.getProfileSaved().observe(this, saved -> {
            if (!Boolean.TRUE.equals(saved)) {
                return;
            }

            prefs.edit()
                    .putString("accent_color", selectedAccent)
                    .putString("profile_avatar",
                            selectedGalleryUri == null ? selectedAvatar : "")
                    .putString("profile_gallery_uri", selectedGalleryUri)
                    .apply();

            binding.progressBar.setVisibility(View.GONE);

            sendBroadcast(new Intent(ACTION_PROFILE_UPDATED));

            finish();
        });
    }

    private void setupClicks() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnSave.setOnClickListener(v -> saveProfile());
        binding.btnLogout.setOnClickListener(v -> confirmLogout());
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Выход")
                .setMessage("Точно выйти из аккаунта?")
                .setPositiveButton("Выйти", (dialog, which) -> performLogout())
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void performLogout() {
        new SupabaseRepository(this).clearSession();

        startActivity(new Intent(this, LoginActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }

    private void saveProfile() {
        try {
            String name = binding.etName.getText().toString().trim();
            String heightText = binding.etHeight.getText().toString().trim();
            String weightText = binding.etWeight.getText().toString().trim();
            String ageText = binding.etAge.getText().toString().trim();

            if (name.isEmpty()
                    || heightText.isEmpty()
                    || weightText.isEmpty()
                    || ageText.isEmpty()) {
                showError("Заполните все поля");
                return;
            }

            int height = Integer.parseInt(heightText);
            int weight = Integer.parseInt(weightText);
            int age = Integer.parseInt(ageText);

            if (height < 100 || height > 250
                    || weight < 20 || weight > 300
                    || age < 10 || age > 100) {
                showError("Проверьте введённые данные");
                return;
            }

            String gender = gendersArr[binding.spinnerGender.getSelectedItemPosition()];
            String goalDisplay = goalsArr[binding.spinnerGoal.getSelectedItemPosition()];
            String lifestyleDisplay = lifestylesArr[binding.spinnerLifestyle.getSelectedItemPosition()];

            String goalKey = goalToKey(goalDisplay);
            String lifestyleKey = lifestyleToKey(lifestyleDisplay);

            prefs.edit()
                    .putString("name", name)
                    .putInt("height", height)
                    .putInt("weight", weight)
                    .putInt("age", age)
                    .putString("gender", gender)
                    .putString("goal", goalKey)
                    .putString("lifestyle", lifestyleKey)
                    .putString("accent_color", selectedAccent)
                    .putString("profile_avatar",
                            selectedGalleryUri == null ? selectedAvatar : "")
                    .putString("profile_gallery_uri", selectedGalleryUri)
                    .putLong("profile_updated_at", System.currentTimeMillis())
                    .apply();

            binding.progressBar.setVisibility(View.VISIBLE);
            binding.btnSave.setEnabled(false);

            viewModel.saveProfile(
                    userId,
                    email,
                    name,
                    height,
                    weight,
                    age,
                    gender,
                    goalKey,
                    lifestyleKey,
                    "system",
                    selectedAccent);

        } catch (NumberFormatException ex) {
            Log.e(TAG, "Number parsing error", ex);

            binding.progressBar.setVisibility(View.GONE);
            binding.btnSave.setEnabled(true);

            showError("Проверьте правильность числовых данных");
        }
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Внимание")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void setupThemeUI() {
        binding.llAccentColors.removeAllViews();

        addThemeSelector();

        int size = dp(40);
        int margin = dp(6);

        for (String hex : ACCENT_COLORS) {
            View circle = new View(this);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(margin, dp(3), margin, dp(3));

            circle.setLayoutParams(params);
            circle.setBackground(getCircleDrawable(hex));
            circle.setClickable(true);
            circle.setTag(hex);

            circle.setOnClickListener(v -> {
                selectedAccent = (String) v.getTag();
                updateAccentSelection();
                applyThemeAndAccent();
            });

            binding.llAccentColors.addView(circle);
        }
    }

    private android.widget.Button lightThemeButton;
    private android.widget.Button darkThemeButton;

    private void addThemeSelector() {
        View anchor = (View) binding.llAccentColors.getParent();
        ViewGroup parent = (ViewGroup) anchor.getParent();
        int index = parent.indexOfChild(anchor);
        // The accent heading is directly before its horizontal scroll container.
        if (index > 0 && parent.getChildAt(index - 1) instanceof TextView) index--;
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(20), 0, 0);
        TextView title = new TextView(this);
        title.setText("Тема приложения");
        title.setTextSize(14);
        title.setTextColor(getResources().getColor(R.color.text_primary, getTheme()));
        block.addView(title);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        lightThemeButton = new android.widget.Button(this);
        darkThemeButton = new android.widget.Button(this);
        lightThemeButton.setText("Светлая");
        darkThemeButton.setText("Тёмная");
        lightThemeButton.setAllCaps(false);
        darkThemeButton.setAllCaps(false);
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(52), 1);
        left.setMarginEnd(dp(8));
        row.addView(lightThemeButton, left);
        row.addView(darkThemeButton, new LinearLayout.LayoutParams(0, dp(52), 1));
        block.addView(row);
        parent.addView(block, index);
        lightThemeButton.setOnClickListener(v -> chooseTheme(false));
        darkThemeButton.setOnClickListener(v -> chooseTheme(true));
        updateThemeButtons();
    }

    private void chooseTheme(boolean dark) {
        prefs.edit().putString("theme_mode", dark ? THEME_DARK : THEME_LIGHT).apply();
        AppCompatDelegate.setDefaultNightMode(dark
                ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        updateThemeButtons();
    }

    private void updateThemeButtons() {
        if (lightThemeButton == null || darkThemeButton == null) return;
        boolean dark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int accent;
        try { accent = Color.parseColor(selectedAccent == null
                ? prefs.getString("accent_color", ACCENT_DEFAULT) : selectedAccent); }
        catch (Exception e) { accent = Color.parseColor(ACCENT_DEFAULT); }
        android.widget.Button[] buttons = {lightThemeButton, darkThemeButton};
        for (int i = 0; i < buttons.length; i++) {
            boolean selected = (i == 1) == dark;
            GradientDrawable background = new GradientDrawable();
            background.setCornerRadius(dp(18));
            background.setColor(selected ? accent : getResources().getColor(R.color.app_surface, getTheme()));
            background.setStroke(dp(1), accent);
            buttons[i].setBackground(background);
            buttons[i].setBackgroundTintList(null);
            buttons[i].setTextColor(selected ? Color.WHITE : getResources().getColor(R.color.text_primary, getTheme()));
        }
    }

    private String currentThemeLabel(String theme) {
        if (THEME_DARK.equals(theme)) {
            return "Тёмная";
        }
        if (THEME_LIGHT.equals(theme)) {
            return "Светлая";
        }
        return "Как на устройстве";
    }

    private void showThemeDialog() {
        String saved = prefs.getString("theme_mode", THEME_SYSTEM);
        int checked = THEME_LIGHT.equals(saved) ? 0 : THEME_DARK.equals(saved) ? 1 : 2;

        new AlertDialog.Builder(this)
                .setTitle("Тема приложения")
                .setSingleChoiceItems(THEME_LABELS, checked, (dialog, which) -> {
                    prefs.edit().putString("theme_mode", THEME_CODES[which]).apply();
                    dialog.dismiss();

                    AppCompatDelegate.setDefaultNightMode(
                            which == 0
                                    ? AppCompatDelegate.MODE_NIGHT_NO
                                    : which == 1
                                    ? AppCompatDelegate.MODE_NIGHT_YES
                                    : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private GradientDrawable getCircleDrawable(String hex) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);

        try {
            drawable.setColor(Color.parseColor(hex));
        } catch (Exception ex) {
            drawable.setColor(Color.parseColor(ACCENT_DEFAULT));
        }

        drawable.setStroke(dp(2), Color.WHITE);
        return drawable;
    }

    private void updateAccentSelection() {
        for (int i = 0; i < binding.llAccentColors.getChildCount(); i++) {
            View circle = binding.llAccentColors.getChildAt(i);
            String color = (String) circle.getTag();

            boolean selected = color.equals(selectedAccent);

            circle.setAlpha(selected ? 1.0f : 0.65f);

            GradientDrawable drawable = getCircleDrawable(color);
            drawable.setStroke(
                    selected ? dp(3) : dp(1),
                    selected
                            ? Color.parseColor("#514B5D")
                            : Color.WHITE);
            circle.setBackground(drawable);
        }
    }

    private void loadThemePrefs() {
        selectedAccent = prefs.getString("accent_color", ACCENT_DEFAULT);
        updateAccentSelection();
    }

    private void applyThemeAndAccent() {
        updateThemeButtons();
        try {
            int color = Color.parseColor(selectedAccent);

            binding.btnSave.setBackgroundTintList(ColorStateList.valueOf(color));

            boolean dark = (getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            int backgroundColor = dark
                    ? getResources().getColor(R.color.bg_primary, getTheme())
                    : blendWithWhite(color, 0.92f);

            binding.getRoot().setBackgroundColor(backgroundColor);
            binding.btnLogout.setBackgroundTintList(
                    ColorStateList.valueOf(Color.parseColor("#FFF0F3")));

        } catch (Exception ex) {
            Log.e(TAG, "Theme error", ex);
        }
    }

    private int blendWithWhite(int color, float whiteAmount) {
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);

        red = (int) (red * (1 - whiteAmount) + 255 * whiteAmount);
        green = (int) (green * (1 - whiteAmount) + 255 * whiteAmount);
        blue = (int) (blue * (1 - whiteAmount) + 255 * whiteAmount);

        return Color.rgb(red, green, blue);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}