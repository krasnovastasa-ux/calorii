package com.example.caloriecounter.model;
import com.google.gson.annotations.SerializedName;

public class Diet {
    public String id;
    public String name;
    public String goal;
    public String benefits;
    public String contraindications;
    @SerializedName("allowed_foods") public String allowedFoods;
    @SerializedName("forbidden_foods") public String forbiddenFoods;
    @SerializedName("meal_plan_2weeks") public String mealPlan2Weeks;
    @SerializedName("expected_results") public String expectedResults;
    public String frequency;
    @SerializedName("image_url") public String imageUrl;
}