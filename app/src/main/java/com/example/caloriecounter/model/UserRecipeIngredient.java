package com.example.caloriecounter.model;

import com.google.gson.annotations.SerializedName;

public class UserRecipeIngredient {
    public String id;

    @SerializedName("recipe_id") public String recipeId;
    @SerializedName("food_id") public String foodId;

    @SerializedName("name") public String name;
    public int grams;
    public String unit;

    @SerializedName("calories") public int calories;
    @SerializedName("protein") public int protein;
    @SerializedName("fat") public int fat;
    @SerializedName("carbs") public int carbs;

    public int getTotalCalories() { return calories; }
    public int getTotalProtein() { return protein; }
    public int getTotalFat() { return fat; }
    public int getTotalCarbs() { return carbs; }
}