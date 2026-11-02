package com.example.caloriecounter.view;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
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

    private ActivityProfileBinding binding;
    private ProfileViewModel viewModel;
    private SharedPreferences prefs;

    private String userId;
    private String email;

    private String[] gendersArr;
    private String[] goalsArr;
    private String[] lifestylesArr;

    private String selectedAccent = "#7041E8";
    private String selectedAvatar = "kot";
    private String selectedGalleryUri;

    private ActivityResultLauncher<String[]> galleryLauncher;

    private boolean receiverRegistered = false;

    private final BroadcastReceiver profileUpdateReceiver =
            new BroadcastReceiver() {
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

        viewModel = new ViewModelProvider(this)
                .get(ProfileViewModel.class);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        try {
                            getContentResolver().takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            );
                        } catch (SecurityException e) {
                            Log.w(TAG, "Не удалось сохранить разрешение на изображение", e);
                        }

                        selectedGalleryUri = uri.toString();

                        prefs.edit()
                                .putString("profile_gallery_uri", selectedGalleryUri)
                                .putString("profile_avatar", "")
                                .apply();

                        updateMainAvatar();
                    }
                }
        );

        gendersArr = getResources().getStringArray(R.array.genders);
        goalsArr = getResources().getStringArray(R.array.goals);
        lifestylesArr = getResources().getStringArray(R.array.lifestyles);

        userId = prefs.getString("user_id", "");
        email = prefs.getString("email", "");

        if (userId.isEmpty()) {
            new SupabaseRepository(this).clearSession();

            startActivity(new Intent(this, LoginActivity.class)
                    .setFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK |
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                    ));

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

        Log.d(TAG, "ProfileActivity initialized");
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

    private void registerProfileUpdateReceiver() {
        IntentFilter filter = new IntentFilter(ACTION_PROFILE_UPDATED);

        ContextCompat.registerReceiver(
                this,
                profileUpdateReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
        );

        receiverRegistered = true;
        Log.d(TAG, "Receiver registered");
    }

    private void setupSpinners() {
        ArrayAdapter<String> genderAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        gendersArr
                );

        genderAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        binding.spinnerGender.setAdapter(genderAdapter);

        ArrayAdapter<String> goalAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        goalsArr
                );

        goalAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        binding.spinnerGoal.setAdapter(goalAdapter);

        ArrayAdapter<String> lifestyleAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        lifestylesArr
                );

        lifestyleAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        binding.spinnerLifestyle.setAdapter(lifestyleAdapter);
    }

    private void setupAvatarClick() {
        selectedAvatar = prefs.getString("profile_avatar", "kot");
        selectedGalleryUri = prefs.getString("profile_gallery_uri", null);

        if (selectedAvatar == null || selectedAvatar.isEmpty()) {
            selectedAvatar = "kot";
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
        android.widget.GridLayout grid = new android.widget.GridLayout(this);
        grid.setColumnCount(3);
        grid.setPadding(dp(12), dp(12), dp(12), dp(12));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Выбери аватар")
                .setView(grid)
                .setNegativeButton("Назад", (d, which) -> showAvatarDialog())
                .create();

        for (String avatar : AVATARS) {
            int imageId = getResources().getIdentifier(
                    avatar,
                    "drawable",
                    getPackageName()
            );

            if (imageId == 0) {
                Log.e(TAG, "Аватар не найден: " + avatar);
                continue;
            }

            android.widget.ImageView image = new android.widget.ImageView(this);

            android.widget.GridLayout.LayoutParams params =
                    new android.widget.GridLayout.LayoutParams();

            params.width = dp(82);
            params.height = dp(82);
            params.setMargins(dp(8), dp(8), dp(8), dp(8));

            image.setLayoutParams(params);
            image.setImageResource(imageId);
            image.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            image.setBackground(createAvatarBackground(
                    avatar.equals(selectedAvatar) && selectedGalleryUri == null
            ));
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
            } catch (Exception e) {
                Log.e(TAG, "Gallery image error", e);
                selectedGalleryUri = null;
            }
        }


        int imageId = getResources().getIdentifier(
                selectedAvatar,
                "drawable",
                getPackageName()
        );

        if (imageId != 0) {
            binding.ivAvatar.setImageResource(imageId);
        } else {
            selectedAvatar = "kot";
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
                        : Color.parseColor("#E5DDF5")
        );

        return drawable;
    }


    private void loadFromPrefs() {
        binding.etName.setText(
                prefs.getString("name", "")
        );

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
                findIndex(
                        gendersArr,
                        prefs.getString("gender", "Женский")
                )
        );

        String savedGoal = prefs.getString("goal", "maintain");

        String goalDisplay =
                savedGoal.equals("lose") ? "Похудеть" :
                        savedGoal.equals("gain") ? "Набрать" :
                                "Поддерживать";

        binding.spinnerGoal.setSelection(
                findIndex(goalsArr, goalDisplay)
        );

        String savedLifestyle = prefs.getString("lifestyle", "moderate");

        String lifeDisplay =
                savedLifestyle.equals("sedentary") ? "Сидячий" :
                        savedLifestyle.equals("light") ? "Лёгкая активность" :
                                savedLifestyle.equals("moderate") ? "Средняя активность" :
                                        savedLifestyle.equals("active") ? "Высокая активность" :
                                                "Очень высокая активность";

        binding.spinnerLifestyle.setSelection(
                findIndex(lifestylesArr, lifeDisplay)
        );
    }

    private int findIndex(String[] arr, String value) {
        if (value == null) return 0;

        for (int i = 0; i < arr.length; i++) {
            if (arr[i] != null &&
                    arr[i].trim().equalsIgnoreCase(value.trim())) {
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

            Log.d(TAG, "Profile loaded");

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
                binding.spinnerGender.setSelection(
                        findIndex(gendersArr, profile.gender)
                );
            }

            if (profile.goal != null) {
                String goalDisplay =
                        profile.goal.equals("lose") ? "Похудеть" :
                                profile.goal.equals("gain") ? "Набрать" :
                                        "Поддерживать";

                binding.spinnerGoal.setSelection(
                        findIndex(goalsArr, goalDisplay)
                );
            }

            if (profile.lifestyle != null) {
                String lifeDisplay =
                        profile.lifestyle.equals("sedentary") ? "Сидячий" :
                                profile.lifestyle.equals("light") ? "Лёгкая активность" :
                                        profile.lifestyle.equals("moderate") ? "Средняя активность" :
                                                profile.lifestyle.equals("active") ? "Высокая активность" :
                                                        "Очень высокая активность";

                binding.spinnerLifestyle.setSelection(
                        findIndex(lifestylesArr, lifeDisplay)
                );
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
            if (message != null && !message.isEmpty()) {
                Log.e(TAG, "Profile error: " + message);

                binding.progressBar.setVisibility(View.GONE);
                binding.btnSave.setEnabled(true);

                new AlertDialog.Builder(this)
                        .setTitle("Ошибка")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show();
            }
        });

        viewModel.getProfileSaved().observe(this, saved -> {
            if (Boolean.TRUE.equals(saved)) {
                Log.d(TAG, "Profile saved successfully");

                prefs.edit()
                        .putString("accent_color", selectedAccent)
                        .putString(
                                "profile_avatar",
                                selectedGalleryUri == null ? selectedAvatar : ""
                        )
                        .putString("profile_gallery_uri", selectedGalleryUri)
                        .apply();

                binding.progressBar.setVisibility(View.GONE);

                Intent intent = new Intent(ACTION_PROFILE_UPDATED);
                sendBroadcast(intent);

                Log.d(TAG, "Profile update broadcast sent");

                finish();
            }
        });
    }

    private void setupClicks() {
        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnSave.setOnClickListener(v -> saveProfile());

        binding.btnLogout.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Выход")
                    .setMessage("Точно выйти из аккаунта?")
                    .setPositiveButton("Выйти", (dialog, which) -> {
                        new SupabaseRepository(this).clearSession();

                        Intent intent = new Intent(
                                this,
                                LoginActivity.class
                        );

                        intent.setFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK |
                                        Intent.FLAG_ACTIVITY_CLEAR_TASK
                        );

                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
        });
    }

    private void saveProfile() {
        try {
            String name = binding.etName.getText().toString().trim();
            String h = binding.etHeight.getText().toString().trim();
            String w = binding.etWeight.getText().toString().trim();
            String a = binding.etAge.getText().toString().trim();

            if (name.isEmpty() ||
                    h.isEmpty() ||
                    w.isEmpty() ||
                    a.isEmpty()) {
                showError("Заполните все поля");
                return;
            }

            int height = Integer.parseInt(h);
            int weight = Integer.parseInt(w);
            int age = Integer.parseInt(a);

            if (height < 100 || height > 250 ||
                    weight < 20 || weight > 300 ||
                    age < 10 || age > 100) {
                showError("Проверьте введённые данные");
                return;
            }

            int genderPosition =
                    binding.spinnerGender.getSelectedItemPosition();

            int goalPosition =
                    binding.spinnerGoal.getSelectedItemPosition();

            int lifestylePosition =
                    binding.spinnerLifestyle.getSelectedItemPosition();

            String gender = gendersArr[genderPosition];
            String goalDisplay = goalsArr[goalPosition];
            String lifeDisplay = lifestylesArr[lifestylePosition];

            String goalKey =
                    goalDisplay.equals("Похудеть") ? "lose" :
                            goalDisplay.equals("Набрать") ? "gain" :
                                    "maintain";

            String lifeKey =
                    lifeDisplay.equals("Сидячий") ? "sedentary" :
                            lifeDisplay.equals("Лёгкая активность") ? "light" :
                                    lifeDisplay.equals("Средняя активность") ? "moderate" :
                                            lifeDisplay.equals("Высокая активность") ? "active" :
                                                    "very_active";

            Log.d(TAG,
                    "Saving profile: " + name +
                            ", accent: " + selectedAccent +
                            ", avatar: " + selectedAvatar
            );

            prefs.edit()
                    .putString("name", name)
                    .putInt("height", height)
                    .putInt("weight", weight)
                    .putInt("age", age)
                    .putString("gender", gender)
                    .putString("goal", goalKey)
                    .putString("lifestyle", lifeKey)
                    .putString("accent_color", selectedAccent)
                    .putString(
                            "profile_avatar",
                            selectedGalleryUri == null ? selectedAvatar : ""
                    )
                    .putString("profile_gallery_uri", selectedGalleryUri)
                    .putLong(
                            "profile_updated_at",
                            System.currentTimeMillis()
                    )
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
                    lifeKey,
                    "system",
                    selectedAccent
            );

        } catch (NumberFormatException e) {
            Log.e(TAG, "Number parsing error", e);

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

        int size = dp(40);
        int margin = dp(6);

        for (String hex : ACCENT_COLORS) {
            View circle = new View(this);

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(size, size);

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

    private GradientDrawable getCircleDrawable(String hex) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);

        try {
            drawable.setColor(Color.parseColor(hex));
        } catch (Exception e) {
            drawable.setColor(Color.parseColor("#7041E8"));
        }

        drawable.setStroke(dp(2), Color.WHITE);

        return drawable;
    }

    private void updateAccentSelection() {
        for (int i = 0;
             i < binding.llAccentColors.getChildCount();
             i++) {

            View circle = binding.llAccentColors.getChildAt(i);
            String color = (String) circle.getTag();

            boolean selected = color.equals(selectedAccent);

            circle.setAlpha(selected ? 1.0f : 0.65f);

            GradientDrawable drawable = getCircleDrawable(color);

            drawable.setStroke(
                    selected ? dp(3) : dp(1),
                    selected
                            ? Color.parseColor("#514B5D")
                            : Color.WHITE
            );

            circle.setBackground(drawable);
        }
    }

    private void loadThemePrefs() {
        selectedAccent = prefs.getString(
                "accent_color",
                "#7041E8"
        );

        updateAccentSelection();
    }

    private void applyThemeAndAccent() {
        try {
            int color = Color.parseColor(selectedAccent);

            binding.btnSave.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(color)
            );

            int backgroundColor = blendWithWhite(color, 0.92f);

            binding.getRoot().setBackgroundColor(backgroundColor);

            binding.btnLogout.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            Color.parseColor("#FFF0F3")
                    )
            );

        } catch (Exception e) {
            Log.e(TAG, "Theme error", e);
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
        return (int) (
                value * getResources()
                        .getDisplayMetrics().density + 0.5f
        );
    }
}