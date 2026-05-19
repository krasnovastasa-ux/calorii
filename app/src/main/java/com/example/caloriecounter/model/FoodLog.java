package com.example.caloriecounter.model;

import com.google.gson.annotations.SerializedName;

public class FoodLog {
    public String id;

    @SerializedName("user_id")
    public String userId;

    @SerializedName("food_id")
    public String foodId;

    @SerializedName("food_name")
    public String foodName;

    @SerializedName("meal_type")
    public String mealType;

    public int grams;

    @SerializedName("total_calories")
    public int totalCalories;

    @SerializedName("total_protein")
    public int totalProtein;

    @SerializedName("total_fat")
    public int totalFat;

    @SerializedName("total_carbs")
    public int totalCarbs;

    @SerializedName("total_sugar")
    public int totalSugar;

    @SerializedName("total_fiber")
    public int totalFiber;

    @SerializedName("log_date")
    public String logDate;

    @SerializedName("created_at")
    public String createdAt;

    public Integer ml;
}