package com.example.caloriecounter.model;
import com.google.gson.annotations.SerializedName;

public class OffResponse {
    public int status;
    @SerializedName("product") public OffProduct product;

    public static class OffProduct {
        @SerializedName("product_name") public String productName;
        @SerializedName("nutriments") public OffNutriments nutriments;
    }

    public static class OffNutriments {
        @SerializedName("energy-kcal_100g") public Double energyKcal;
        @SerializedName("proteins_100g") public Double proteins;
        @SerializedName("fat_100g") public Double fat;
        @SerializedName("carbohydrates_100g") public Double carbs;
        @SerializedName("sugars_100g") public Double sugars;
        @SerializedName("fiber_100g") public Double fiber;
    }
}