package com.example.caloriecounter.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.caloriecounter.model.Recipe;
import com.example.caloriecounter.repository.SupabaseRepository;
import java.util.List;

public class RecipeViewModel extends AndroidViewModel {
    private final SupabaseRepository repo;

    private final MutableLiveData<List<Recipe>> recipes = new MutableLiveData<>();
    private final MutableLiveData<List<Recipe.RecipeFood>> recipeFoods = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public RecipeViewModel(Application app) {
        super(app);
        repo = new SupabaseRepository(app);
    }

    public LiveData<List<Recipe>> getRecipes() { return recipes; }
    public LiveData<List<Recipe.RecipeFood>> getRecipeFoods() { return recipeFoods; }
    public LiveData<String> getError() { return error; }

    public void loadRecipes() {
        repo.fetchRecipes(new SupabaseRepository.RecipeListCallback() {
            @Override public void onSuccess(List<Recipe> list) { recipes.postValue(list); }
            @Override public void onError(String m) { error.postValue(m); }
        });
    }

    public void loadRecipeFoods(String recipeId) {
        repo.fetchRecipeFoods("eq." + recipeId, new SupabaseRepository.RecipeFoodListCallback() {
            @Override public void onSuccess(List<Recipe.RecipeFood> list) { recipeFoods.postValue(list); }
            @Override public void onError(String m) { error.postValue(m); }
        });
    }
}