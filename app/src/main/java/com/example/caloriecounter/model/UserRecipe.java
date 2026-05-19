package com.example.caloriecounter.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class UserRecipe {
    public String id;

    @SerializedName("user_id")
    public String userId;

    public String name;

    @SerializedName("prep_time")
    public int prepTime;

    @SerializedName("cook_time")
    public int cookTime;

    public int servings;

    @SerializedName("instructions")
    public String instructions;

    @SerializedName("image_url")
    public String imageUrl;

    @SerializedName("created_at")
    public String createdAt;

    @SerializedName("updated_at")
    public String updatedAt;

    @SerializedName("total_calories") public int totalCalories;
    @SerializedName("total_protein") public int totalProtein;
    @SerializedName("total_fat") public int totalFat;
    @SerializedName("total_carbs") public int totalCarbs;

    public int getTotalTime() { return prepTime + cookTime; }
    public int getTotalCalories() { return totalCalories; }

    public void calculateFromIngredients(List<UserRecipeIngredient> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) return;
        int cal = 0, p = 0, f = 0, c = 0;
        for (UserRecipeIngredient ing : ingredients) {
            cal += ing.getTotalCalories();
            p += ing.getTotalProtein();
            f += ing.getTotalFat();
            c += ing.getTotalCarbs();
        }
        if (this.totalCalories == 0) this.totalCalories = cal;
        if (this.totalProtein == 0) this.totalProtein = p;
        if (this.totalFat == 0) this.totalFat = f;
        if (this.totalCarbs == 0) this.totalCarbs = c;
    }
}