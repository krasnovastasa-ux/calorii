package com.example.caloriecounter.view;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.ColorUtils;
import androidx.lifecycle.ViewModelProvider;

import com.example.caloriecounter.R;
import com.example.caloriecounter.databinding.ActivityFoodSearchBinding;
import com.example.caloriecounter.model.Food;
import com.example.caloriecounter.model.Recipe;
import com.example.caloriecounter.model.UserRecipe;
import com.example.caloriecounter.repository.SupabaseRepository;
import com.example.caloriecounter.utils.ThemeUtils;
import com.example.caloriecounter.viewmodel.FoodSearchViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class FoodSearchActivity extends BaseActivity {

    private static final String TAG = "FAV_UI";

    private static final int REQ_SCAN_BARCODE = 202;
    private static final int REQUEST_CODE_MY_RECIPE = 100;
    private static final int REQUEST_CODE_ADD_TO_DIARY = 101;

    private static final String CATEGORY_ALL = "Все";

    private static final String[] CATEGORIES = {
            "Все", "Хлеб", "Гарниры", "Мясо", "Рыба", "Молочные",
            "Овощи", "Фрукты", "Супы", "Десерты", "Напитки", "Соусы", "Снеки", "Орехи"
    };

    private enum SortType {
        NONE, A_Z, Z_A, CAL_ASC, CAL_DESC
    }

    private ActivityFoodSearchBinding binding;
    private FoodSearchViewModel vm;

    private List<Food> allFoods = new ArrayList<>();
    private Set<String> favIds = new HashSet<>();
    private List<Recipe> allRecipes = new ArrayList<>();
    private List<UserRecipe> myRecipes = new ArrayList<>();

    private String meal;
    private String userId;
    private String selectedDate;

    private boolean productsLoaded;

    private SortType currentSort = SortType.NONE;
    private String selectedCategory = CATEGORY_ALL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFoodSearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        findViewById(R.id.foodSearchBack).setOnClickListener(v -> finish());
        findViewById(R.id.foodSearchClear).setOnClickListener(v -> binding.etSearch.setText(""));

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String token = prefs.getString("token", null);
        String uid = prefs.getString("user_id", null);

        if (token == null || uid == null) {
            Log.e("AUTH", "Сессия не найдена. Требуется вход.");
            new AlertDialog.Builder(this)
                    .setTitle("Сессия истекла")
                    .setMessage("Пожалуйста, войдите в аккаунт заново")
                    .setPositiveButton("Войти", (d, w) -> {
                        Intent intent = new Intent(this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .setCancelable(false)
                    .show();
            return;
        }

        vm = new ViewModelProvider(this).get(FoodSearchViewModel.class);
        meal = getIntent().getStringExtra("meal");
        userId = getIntent().getStringExtra("userId");
        selectedDate = getIntent().getStringExtra("selected_date");

        binding.btnScanBarcode.setOnClickListener(v -> {
            Intent intent = new Intent(this, BarcodeScannerActivity.class);
            intent.putExtra("userId", userId);
            intent.putExtra("meal", meal);
            intent.putExtra("selected_date", selectedDate);
            startActivityForResult(intent, REQ_SCAN_BARCODE);
        });

        setupTabs();
        loadData();
        setupSearch();
        showContainer(binding.llProducts);
        setupFilterButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (vm == null || isFinishing() || isDestroyed()) {
            return;
        }

        if (binding != null) {
            binding.getRoot().post(this::styleSearchControls);
        }
        vm.loadFavorites();
        vm.loadUserRecipes();
    }

    private void setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Все"));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Избранное"));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Рецепты"));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Мои рецепты"));

        hideAllLists();

        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                hideAllLists();
                binding.etSearch.setText("");

                int position = tab.getPosition();

                binding.btnCategoryMenu.setVisibility(
                        position == 2 || position == 3 ? View.GONE : View.VISIBLE);

                switch (position) {
                    case 0:
                        binding.llProducts.setVisibility(LinearLayout.VISIBLE);
                        applyFiltersAndSort();
                        break;
                    case 1:
                        binding.llFavorites.setVisibility(LinearLayout.VISIBLE);
                        syncFavIdsFromViewModel();
                        renderFavorites("");
                        break;
                    case 2:
                        binding.llRecipes.setVisibility(LinearLayout.VISIBLE);
                        renderRecipes("");
                        break;
                    case 3:
                        binding.llMyRecipes.setVisibility(LinearLayout.VISIBLE);
                        renderMyRecipes("");
                        break;
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        if (binding.tabLayout.getTabAt(0) != null) {
            binding.tabLayout.getTabAt(0).select();
        }
    }

    private void hideAllLists() {
        binding.llProducts.setVisibility(LinearLayout.GONE);
        binding.llFavorites.setVisibility(LinearLayout.GONE);
        binding.llRecipes.setVisibility(LinearLayout.GONE);
        binding.llMyRecipes.setVisibility(LinearLayout.GONE);
    }

    private void showContainer(LinearLayout target) {
        hideAllLists();
        target.setVisibility(LinearLayout.VISIBLE);
    }

    private void syncFavIdsFromViewModel() {
        Set<String> vmIds = vm.getFavoriteIds();
        if (!favIds.equals(vmIds)) {
            favIds = new HashSet<>(vmIds);
        }
    }

    private void loadData() {
        vm.getProducts().observe(this, foods -> {
            if (foods != null) {
                allFoods = foods;
                productsLoaded = true;
                if (binding.tabLayout.getSelectedTabPosition() == 0) {
                    applyFiltersAndSort();
                }
            }
        });

        vm.getFavorites().observe(this, favs -> {
            if (favs != null) {
                favIds.clear();
                for (Food f : favs) {
                    String targetId = f.foodId != null ? f.foodId : f.id;
                    if (targetId != null) {
                        favIds.add(targetId);
                    }
                }
                if (binding.tabLayout.getSelectedTabPosition() == 1) {
                    renderFavorites("");
                }
            }
        });

        vm.getRecipes().observe(this, recipes -> {
            if (recipes != null) {
                allRecipes = recipes;
                if (binding.tabLayout.getSelectedTabPosition() == 2) {
                    renderRecipes("");
                }
            }
        });

        vm.getUserRecipes().observe(this, recipes -> {
            if (recipes != null) {
                myRecipes = recipes;
                calculateUserRecipeTotalsAsync(myRecipes, () -> {
                    if (binding.tabLayout.getSelectedTabPosition() == 3) {
                        renderMyRecipes("");
                    }
                });
            }
        });

        vm.loadProducts();
        vm.loadFavorites();
        vm.loadRecipes();
        vm.loadUserRecipes();
    }

    private void setupSearch() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFiltersAndSort();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void applyFiltersAndSort() {
        String query = binding.etSearch.getText().toString()
                .toLowerCase(Locale.getDefault())
                .trim();

        updateCategoryButtonText();

        switch (binding.tabLayout.getSelectedTabPosition()) {
            case 0:
                renderProducts(query);
                break;
            case 1:
                renderFavorites(query);
                break;
            case 2:
                renderRecipes(query);
                break;
            case 3:
                renderMyRecipes(query);
                break;
        }
    }

    private boolean matchesCategory(Food food) {
        return CATEGORY_ALL.equals(selectedCategory)
                || (food.category != null && food.category.equals(selectedCategory));
    }

    private boolean matchesQuery(String name, String query) {
        return name != null && name.toLowerCase(Locale.getDefault()).contains(query);
    }

    private boolean isFavorite(Food food) {
        return favIds.contains(food.id) || favIds.contains(food.foodId);
    }

    private void renderProducts(String query) {
        binding.llProducts.removeAllViews();

        if (allFoods == null || allFoods.isEmpty()) {
            addEmpty(binding.llProducts, "Загрузка...");
            return;
        }

        List<Food> filtered = new ArrayList<>();
        for (Food food : allFoods) {
            if (matchesCategory(food) && matchesQuery(food.name, query)) {
                filtered.add(food);
            }
        }
        applySortingToFoodList(filtered);

        for (Food food : filtered) {
            addProductRow(binding.llProducts, food, isFavorite(food));
        }

        if (filtered.isEmpty()) {
            addEmpty(binding.llProducts, query.isEmpty() ? "Список пуст" : "Ничего не найдено");
        }
    }

    private void renderFavorites(String query) {
        binding.llFavorites.removeAllViews();

        if (allFoods == null || allFoods.isEmpty()) {
            addEmpty(binding.llFavorites, "Загрузка...");
            return;
        }

        List<Food> filtered = new ArrayList<>();
        for (Food food : allFoods) {
            if (!isFavorite(food)) {
                continue;
            }
            if (matchesCategory(food) && matchesQuery(food.name, query)) {
                filtered.add(food);
            }
        }
        applySortingToFoodList(filtered);

        for (Food food : filtered) {
            addProductRow(binding.llFavorites, food, true);
        }

        if (filtered.isEmpty()) {
            addEmpty(binding.llFavorites, query.isEmpty() ? "Нет избранных" : "Ничего не найдено");
        }
    }

    private void renderRecipes(String query) {
        binding.llRecipes.removeAllViews();

        if (allRecipes == null || allRecipes.isEmpty()) {
            addEmpty(binding.llRecipes, "Загрузка...");
            return;
        }

        List<Recipe> filtered = new ArrayList<>();
        for (Recipe recipe : allRecipes) {
            if (matchesQuery(recipe.name, query)) {
                filtered.add(recipe);
            }
        }
        applySortingToRecipeList(filtered);

        for (Recipe recipe : filtered) {
            LinearLayout row = catalogRow(
                    recipe.name + " — " + recipe.getTotalCalories() + " ккал", null);
            row.setOnClickListener(v -> openRecipeDetail(recipe));
            binding.llRecipes.addView(row);
        }

        if (filtered.isEmpty()) {
            addEmpty(binding.llRecipes, query.isEmpty() ? "Рецепты не найдены" : "Ничего не найдено");
        }
    }

    private void renderMyRecipes(String query) {
        if (binding.llMyRecipes == null) {
            return;
        }
        binding.llMyRecipes.removeAllViews();

        if (myRecipes == null) {
            addEmpty(binding.llMyRecipes, "Загрузка...");
            return;
        }

        List<UserRecipe> filtered = new ArrayList<>();
        for (UserRecipe recipe : myRecipes) {
            if (matchesQuery(recipe.name, query)) {
                filtered.add(recipe);
            }
        }
        applySortingToUserRecipeList(filtered);

        for (UserRecipe recipe : filtered) {
            addMyRecipeRow(recipe);
        }

        if (query.isEmpty()) {
            binding.llMyRecipes.addView(createAddRecipeButton());
        }

        if (filtered.isEmpty() && !query.isEmpty()) {
            addEmpty(binding.llMyRecipes, "Ничего не найдено");
        }
    }

    private void addMyRecipeRow(UserRecipe recipe) {
        String title = (recipe.name != null ? recipe.name : "Без названия")
                + " — " + recipe.totalCalories + " ккал";
        String subtitle = (recipe.prepTime + recipe.cookTime) + " мин · Порций: " + recipe.servings;

        LinearLayout row = catalogRow(title, subtitle);
        row.setOnClickListener(v -> {
            Intent intent = new Intent(this, RecipeDetailActivity.class);
            intent.putExtra("recipe_id", recipe.id);
            intent.putExtra("recipe_name", recipe.name);
            intent.putExtra("meal", meal);
            intent.putExtra("userId", userId);
            intent.putExtra("selected_date", selectedDate);
            startActivity(intent);
        });
        binding.llMyRecipes.addView(row);
    }

    private MaterialButton createAddRecipeButton() {
        MaterialButton button = new MaterialButton(this);
        button.setText("Создать новый рецепт");
        button.setTextColor(Color.WHITE);
        String accent = ThemeUtils.getAccent(this);
        button.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(accent)));
        button.setCornerRadius(ActivityUi.dp(this, 18));
        button.setAllCaps(false);
        button.setPadding(ActivityUi.dp(this, 12), 0, ActivityUi.dp(this, 12), 0);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, ActivityUi.dp(this, 8), 0, ActivityUi.dp(this, 12));
        button.setLayoutParams(params);

        button.setOnClickListener(v -> {
            Intent intent = new Intent(this, MyRecipeEditorActivity.class);
            intent.putExtra("userId", userId);
            startActivityForResult(intent, REQUEST_CODE_MY_RECIPE);
        });

        return button;
    }

    private void applySortingToFoodList(List<Food> list) {
        switch (currentSort) {
            case A_Z:
                Collections.sort(list, (a, b) -> a.name.compareToIgnoreCase(b.name));
                break;
            case Z_A:
                Collections.sort(list, (a, b) -> b.name.compareToIgnoreCase(a.name));
                break;
            case CAL_ASC:
                Collections.sort(list, (a, b) -> Integer.compare(a.calories, b.calories));
                break;
            case CAL_DESC:
                Collections.sort(list, (a, b) -> Integer.compare(b.calories, a.calories));
                break;
        }
    }

    private void applySortingToRecipeList(List<Recipe> list) {
        switch (currentSort) {
            case A_Z:
                Collections.sort(list, (a, b) -> a.name.compareToIgnoreCase(b.name));
                break;
            case Z_A:
                Collections.sort(list, (a, b) -> b.name.compareToIgnoreCase(a.name));
                break;
            case CAL_ASC:
                Collections.sort(list, (a, b) -> Integer.compare(a.getTotalCalories(), b.getTotalCalories()));
                break;
            case CAL_DESC:
                Collections.sort(list, (a, b) -> Integer.compare(b.getTotalCalories(), a.getTotalCalories()));
                break;
        }
    }

    private void applySortingToUserRecipeList(List<UserRecipe> list) {
        switch (currentSort) {
            case A_Z:
                Collections.sort(list, (a, b) -> a.name.compareToIgnoreCase(b.name));
                break;
            case Z_A:
                Collections.sort(list, (a, b) -> b.name.compareToIgnoreCase(a.name));
                break;
            case CAL_ASC:
                Collections.sort(list, (a, b) -> Integer.compare(a.totalCalories, b.totalCalories));
                break;
            case CAL_DESC:
                Collections.sort(list, (a, b) -> Integer.compare(b.totalCalories, a.totalCalories));
                break;
        }
    }

    private void addProductRow(LinearLayout container, Food food, boolean isFav) {
        LinearLayout row = catalogRowShell();

        TextView name = new TextView(this);
        name.setText(String.format("%s — %d ккал", food.name, food.calories));
        name.setTextSize(15);
        name.setTextColor(ActivityPalette.text(this));
        name.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView star = new TextView(this);
        star.setText(isFav ? "★" : "☆");
        star.setTextSize(20);
        star.setTextColor(ActivityPalette.accent(this));
        star.setPadding(8, 0, 8, 0);
        star.setClickable(true);
        star.setFocusable(false);
        star.setFocusableInTouchMode(false);

        String targetId = food.foodId != null ? food.foodId : food.id;
        star.setOnClickListener(v -> toggleFavorite(food, targetId, star));

        row.addView(name);
        row.addView(star);
        row.setClickable(false);
        row.setOnClickListener(v -> openFoodDetail(food));
        container.addView(row);
    }

    private void toggleFavorite(Food food, String targetId, TextView star) {
        boolean currentlyFav = favIds.contains(targetId);

        if (!currentlyFav) {
            favIds.add(targetId);
            star.setText("★");
            if (binding.tabLayout.getSelectedTabPosition() == 1) {
                renderFavorites("");
            }
            vm.addToFavorites(food, new SupabaseRepository.VoidCallback() {
                @Override
                public void onSuccess() {
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        favIds.remove(targetId);
                        star.setText("☆");
                    });
                }
            });
        } else {
            favIds.remove(targetId);
            star.setText("☆");
            if (binding.tabLayout.getSelectedTabPosition() == 1) {
                renderFavorites("");
            }
            vm.removeFromFavorites(targetId, new SupabaseRepository.VoidCallback() {
                @Override
                public void onSuccess() {
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        favIds.add(targetId);
                        star.setText("★");
                    });
                }
            });
        }
    }

    private void addEmpty(LinearLayout container, String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, 40, 0, 40);
        view.setTextColor(ActivityPalette.accent(this));
        container.addView(view);
    }

    private void openFoodDetail(Food food) {
        Intent intent = new Intent(this, FoodDetailActivity.class);
        intent.putExtra("meal", meal);
        intent.putExtra("foodId", food.id);
        intent.putExtra("foodName", food.name);
        intent.putExtra("calories", food.calories);
        intent.putExtra("protein", food.protein);
        intent.putExtra("fat", food.fat);
        intent.putExtra("carbs", food.carbs);
        intent.putExtra("sugar", food.sugar);
        intent.putExtra("fiber", food.fiber);
        intent.putExtra("userId", userId);
        intent.putExtra("selected_date", selectedDate);
        startActivity(intent);
    }

    private void openRecipeDetail(Recipe recipe) {
        Intent intent = new Intent(this, RecipeDetailActivity.class);
        intent.putExtra("recipe_id", recipe.id);
        intent.putExtra("recipe_name", recipe.name);
        intent.putExtra("meal", meal);
        intent.putExtra("userId", userId);
        intent.putExtra("selected_date", selectedDate);
        startActivity(intent);
    }

    private void openMyRecipeEditor(UserRecipe recipe) {
        Intent intent = new Intent(this, MyRecipeEditorActivity.class);
        intent.putExtra("meal", meal);
        intent.putExtra("userId", userId);
        intent.putExtra("selected_date", selectedDate);
        if (recipe != null) {
            intent.putExtra("recipe_id", recipe.id);
            intent.putExtra("recipe_name", recipe.name);
            intent.putExtra("instructions", recipe.instructions);
            intent.putExtra("prep_time", recipe.prepTime);
            intent.putExtra("cook_time", recipe.cookTime);
            intent.putExtra("servings", recipe.servings);
        }
        startActivity(intent);
    }

    private void setupFilterButtons() {
        setupSortMenu();
        setupCategoryMenu();
        updateSortButtonText();
        updateCategoryButtonText();
    }

    private void setupSortMenu() {
        binding.btnSortMenu.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, binding.btnSortMenu);
            popup.getMenu().add(0, 1, 0, "Без сортировки");
            popup.getMenu().add(0, 2, 1, "А до Я");
            popup.getMenu().add(0, 3, 2, "Я до А");
            popup.getMenu().add(0, 4, 3, "Ккал: по возрастанию");
            popup.getMenu().add(0, 5, 4, "Ккал: по убыванию");

            int currentId = getCurrentMenuItemId();
            popup.getMenu().findItem(currentId).setChecked(true);
            popup.getMenu().setGroupCheckable(0, true, true);
            popup.getMenu().findItem(currentId).setIcon(new ColorDrawable(ActivityPalette.accent(this)));

            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == 2) {
                    currentSort = SortType.A_Z;
                } else if (id == 3) {
                    currentSort = SortType.Z_A;
                } else if (id == 4) {
                    currentSort = SortType.CAL_ASC;
                } else if (id == 5) {
                    currentSort = SortType.CAL_DESC;
                } else {
                    currentSort = SortType.NONE;
                }

                updateSortButtonText();
                applyFiltersAndSort();
                return true;
            });
            popup.show();
        });
    }

    private void setupCategoryMenu() {
        binding.btnCategoryMenu.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, binding.btnCategoryMenu);
            for (int i = 0; i < CATEGORIES.length; i++) {
                popup.getMenu().add(0, i, i, CATEGORIES[i]);
            }

            int currentPos = 0;
            for (int i = 0; i < CATEGORIES.length; i++) {
                if (CATEGORIES[i].equals(selectedCategory)) {
                    currentPos = i;
                    break;
                }
            }
            popup.getMenu().findItem(currentPos).setChecked(true);
            popup.getMenu().setGroupCheckable(0, true, true);
            popup.getMenu().findItem(currentPos).setIcon(new ColorDrawable(ActivityPalette.accent(this)));

            popup.setOnMenuItemClickListener(item -> {
                selectedCategory = CATEGORIES[item.getItemId()];
                updateCategoryButtonText();
                applyFiltersAndSort();
                return true;
            });
            popup.show();
        });
    }

    private int getCurrentMenuItemId() {
        switch (currentSort) {
            case A_Z:
                return 2;
            case Z_A:
                return 3;
            case CAL_ASC:
                return 4;
            case CAL_DESC:
                return 5;
            default:
                return 1;
        }
    }

    private void updateSortButtonText() {
        switch (currentSort) {
            case A_Z:
                binding.btnSortMenu.setText("А до Я");
                break;
            case Z_A:
                binding.btnSortMenu.setText("Я до А");
                break;
            case CAL_ASC:
                binding.btnSortMenu.setText("Ккал возр.");
                break;
            case CAL_DESC:
                binding.btnSortMenu.setText("Ккал убыв.");
                break;
            default:
                binding.btnSortMenu.setText("Сортировка");
        }
    }

    private void updateCategoryButtonText() {
        binding.btnCategoryMenu.setText(
                CATEGORY_ALL.equals(selectedCategory) ? "Категория" : selectedCategory);
    }

    private void calculateUserRecipeTotalsAsync(List<UserRecipe> recipes, Runnable onComplete) {
        if (recipes == null || recipes.isEmpty()) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        AtomicInteger pending = new AtomicInteger(recipes.size());

        for (UserRecipe recipe : recipes) {
            if (recipe.totalCalories > 0) {
                pending.decrementAndGet();
                continue;
            }

            vm.getRepo().fetchUserRecipeIngredients("eq." + recipe.id,
                    new SupabaseRepository.RecipeFoodListCallback() {
                        @Override
                        public void onSuccess(List<Recipe.RecipeFood> foods) {
                            int total = 0;
                            if (foods != null) {
                                for (Recipe.RecipeFood food : foods) {
                                    total += food.getTotalCalories();
                                }
                            }
                            recipe.totalCalories = total;
                            if (pending.decrementAndGet() == 0 && onComplete != null) {
                                onComplete.run();
                            }
                        }

                        @Override
                        public void onError(String message) {
                            if (pending.decrementAndGet() == 0 && onComplete != null) {
                                onComplete.run();
                            }
                        }
                    });
        }
    }

    private void styleSearchControls() {
        int accent = ActivityPalette.accent(this);
        int pale = ColorUtils.blendARGB(ActivityPalette.surface(this), accent, 0.13f);

        MaterialButton[] filters = {
                binding.btnSortMenu,
                binding.btnCategoryMenu,
                binding.btnScanBarcode
        };

        for (MaterialButton button : filters) {
            button.setBackgroundTintList(ColorStateList.valueOf(pale));
            button.setTextColor(accent);
            button.setIconTint(ColorStateList.valueOf(accent));
            button.setCornerRadius(ActivityUi.dp(this, 22));
        }

        binding.tabLayout.setTabTextColors(ActivityPalette.muted(this), accent);
        binding.tabLayout.setSelectedTabIndicatorColor(accent);

        ((TextView) findViewById(R.id.foodSearchBack)).setTextColor(accent);
        ((TextView) findViewById(R.id.foodSearchClear)).setTextColor(accent);
    }

    private LinearLayout catalogRowShell() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        int padding = ActivityUi.dp(this, 12);
        row.setPadding(padding, ActivityUi.dp(this, 8), padding, ActivityUi.dp(this, 8));
        row.setMinimumHeight(ActivityUi.dp(this, 52));
        row.setBackground(ActivityUi.shape(
                ActivityPalette.surface(this), ActivityUi.dp(this, 18)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = ActivityUi.dp(this, 6);
        row.setLayoutParams(params);

        return row;
    }

    private LinearLayout catalogRow(String title, String subtitle) {
        LinearLayout row = catalogRowShell();

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);

        TextView name = new TextView(this);
        name.setText(title);
        name.setTextSize(15);
        name.setTextColor(ActivityPalette.text(this));
        labels.addView(name);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView info = new TextView(this);
            info.setText(subtitle);
            info.setTextSize(12);
            info.setTextColor(ActivityPalette.muted(this));
            info.setPadding(0, ActivityUi.dp(this, 3), 0, 0);
            labels.addView(info);
        }

        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(26);
        arrow.setTextColor(ActivityPalette.accent(this));
        arrow.setTag("accent");
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(
                ActivityUi.dp(this, 32), ActivityUi.dp(this, 36)));

        return row;
    }
}