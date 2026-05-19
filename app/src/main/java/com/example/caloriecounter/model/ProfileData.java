package com.example.caloriecounter.model;
import com.google.gson.annotations.SerializedName;

public class ProfileData {
    public String id;

    @SerializedName("user_id")
    public String userId;

    public String email;
    public String name;

    @SerializedName("height")
    public Integer height;

    @SerializedName("weight")
    public Integer weight;

    @SerializedName("age")
    public Integer age;

    @SerializedName("theme_mode")
    public String themeMode;
    @SerializedName("accent_color")
    public String accentColor;

    public String gender;
    public String goal;
    public String lifestyle;

    @SerializedName("created_at")
    public String createdAt;

    @SerializedName("updated_at")
    public String updatedAt;

    public ProfileData() {}
}