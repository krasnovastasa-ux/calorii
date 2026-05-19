package com.example.caloriecounter.viewmodel;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.caloriecounter.model.Food;
import com.example.caloriecounter.model.FoodLog;
import android.preference.PreferenceManager;
import com.example.caloriecounter.model.Recipe;
import com.example.caloriecounter.model.UserRecipe;
import com.example.caloriecounter.repository.SupabaseRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FoodSearchViewModel extends AndroidViewModel {
    private static final String TAG = "FAV_DEBUG";
    private final SupabaseRepository repo;
    private final SharedPreferences prefs;

    private final MutableLiveData<List<Food>> products = new MutableLiveData<>();
    private final MutableLiveData<List<Food>> favorites = new MutableLiveData<>();
    private final MutableLiveData<List<Recipe>> recipes = new MutableLiveData<>();
    private final MutableLiveData<List<UserRecipe>> userRecipes = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();

    private final Set<String> favoriteFoodIds = new HashSet<>();

    public FoodSearchViewModel(Application app) {
        super(app);
        repo = new SupabaseRepository(app);
        prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(getApplication());
    }

    public LiveData<List<Food>> getProducts() { return products; }
    public LiveData<List<Food>> getFavorites() { return favorites; }
    public LiveData<List<Recipe>> getRecipes() { return recipes; }
    public LiveData<List<UserRecipe>> getUserRecipes() { return userRecipes; }
    public LiveData<String> getError() { return error; }
    public Set<String> getFavoriteIds() { return new HashSet<>(favoriteFoodIds); }

    public void loadProducts() {
        repo.fetchFoods(new SupabaseRepository.FoodCallback() {
            @Override public void onSuccess(List<Food> list) { products.postValue(list); }
            @Override public void onError(String m) { error.postValue(m); }
        });
    }

    public void loadFavorites() {
        String userId = prefs.getString("user_id", "");
        if (userId == null) return;
        repo.fetchFavorites(new SupabaseRepository.FoodListCallback() {
            @Override public void onSuccess(List<Food> list) {
                favoriteFoodIds.clear();
                if (list != null) {
                    for (Food f : list) {
                        String targetId = (f.foodId != null) ? f.foodId : f.id;
                        if (targetId != null) favoriteFoodIds.add(targetId);
                    }
                }
                Log.d(TAG, " Fav cache updated: " + favoriteFoodIds.size());
                favorites.postValue(list);
            }
            @Override public void onError(String m) { error.postValue(m); }
        });
    }

    public void loadRecipes() {
        repo.fetchRecipes(new SupabaseRepository.RecipeListCallback() {
            @Override public void onSuccess(List<Recipe> list) { recipes.postValue(list); }
            @Override public void onError(String m) { error.postValue(m); }
        });
    }

    public void loadUserRecipes() {
        repo.fetchUserRecipes(new SupabaseRepository.UserRecipeListCallback() {
            @Override public void onSuccess(List<UserRecipe> list) { userRecipes.postValue(list); }
            @Override public void onError(String m) { error.postValue(m); }
        });
    }

    public boolean isFavoriteCached(String foodId) { return favoriteFoodIds.contains(foodId); }

    public void addToFavorites(Food food, SupabaseRepository.VoidCallback cb) {
        String userId = prefs.getString("user_id", null);
        if (userId == null || userId.isEmpty()) {
            cb.onError("Нет сессии");
            return;
        }

        if (favoriteFoodIds.contains(food.id)) {
            cb.onSuccess();
            return;
        }

        repo.addToFavorites(userId, food, new SupabaseRepository.VoidCallback() {
            @Override public void onSuccess() {
                favoriteFoodIds.add(food.id);
                cb.onSuccess();
            }
            @Override public void onError(String m) {
                if (m.contains("409") || m.contains("duplicate") || m.contains("unique")) {
                    favoriteFoodIds.add(food.id);
                    cb.onSuccess();
                } else cb.onError(m);
            }
        });
    }

    public void removeFromFavorites(String foodId, SupabaseRepository.VoidCallback cb) {
        String userId = prefs.getString("user_id", "");
        repo.removeFromFavorites(userId, foodId, new SupabaseRepository.VoidCallback() {
            @Override public void onSuccess() {
                favoriteFoodIds.remove(foodId);
                cb.onSuccess();
            }
            @Override public void onError(String m) { cb.onError(m); }
        });
    }

    public void saveLog(FoodLog log, String date, SupabaseRepository.VoidCallback cb) { repo.addLog(log, date, cb); }



}