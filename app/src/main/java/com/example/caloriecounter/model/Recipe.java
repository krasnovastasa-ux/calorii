package com.example.caloriecounter.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class Recipe {
    public String id;
    public String name;
    @SerializedName("instructions")
    public String description;
    public String image_url;

    @SerializedName("prep_time") public int prepTime;
    @SerializedName("cook_time") public int cookTime;
    public int servings;

    @SerializedName("total_calories") public int totalCalories;
    @SerializedName("total_protein") public int totalProtein;
    @SerializedName("total_fat") public int totalFat;
    @SerializedName("total_carbs") public int totalCarbs;

    @SerializedName("created_at") public String createdAt;
    @SerializedName("updated_at") public String updatedAt;
    public int getTotalCalories() { return totalCalories; }
    public int getTotalProtein() { return totalProtein; }
    public int getTotalFat() { return totalFat; }
    public int getTotalCarbs() { return totalCarbs; }

    public void calculateFromIngredients(List<RecipeFood> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) return;
        int cal = 0, p = 0, f = 0, c = 0;
        for (RecipeFood ing : ingredients) {
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

    public static class RecipeFood {
        public String id;
        @SerializedName("recipe_id") public String recipeId;
        @SerializedName("food_id") public String foodId;

        @SerializedName("name")
        public String foodName;

        public int grams;

        @SerializedName("calories") public int caloriesPer100g;
        @SerializedName("protein") public int proteinPer100g;
        @SerializedName("fat") public int fatPer100g;
        @SerializedName("carbs") public int carbsPer100g;

        public int getTotalCalories() {
            return (int) Math.round(caloriesPer100g * grams / 100.0);
        }
        public int getTotalProtein() {
            return (int) Math.round(proteinPer100g * grams / 100.0);
        }
        public int getTotalFat() {
            return (int) Math.round(fatPer100g * grams / 100.0);
        }
        public int getTotalCarbs() {
            return (int) Math.round(carbsPer100g * grams / 100.0);
        }
    }
}