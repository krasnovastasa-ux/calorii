package com.example.caloriecounter.model;

import com.google.gson.annotations.SerializedName;

public class Food {
    public String id;

    @SerializedName("food_id") public String foodId;

    public String name;
    public int calories, protein, fat, carbs, sugar, fiber;
    public String category;
}