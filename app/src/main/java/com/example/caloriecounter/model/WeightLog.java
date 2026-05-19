package com.example.caloriecounter.model;
import com.google.gson.annotations.SerializedName;

public class WeightLog {
    public String id;
    @SerializedName("user_id") public String userId;
    public double weight;
    @SerializedName("log_date") public String logDate; // формат yyyy-MM-dd
    @SerializedName("created_at") public String createdAt;
}