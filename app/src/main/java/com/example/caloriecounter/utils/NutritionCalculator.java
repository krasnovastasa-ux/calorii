package com.example.caloriecounter.utils;

import java.util.HashMap;
import java.util.Map;

public class NutritionCalculator {
    public static int calculateTargetCalories(int weight, int height, int age, String gender, String lifestyle, String goal) {
        int bmr = 10 * weight + (int)(6.25 * height) - 5 * age;
        bmr += gender.equals("Мужской") ? 5 : -161;

        double multiplier;
        switch (lifestyle) {
            case "sedentary": multiplier = 1.2; break;
            case "light": multiplier = 1.375; break;
            case "moderate": multiplier = 1.55; break;
            case "active": multiplier = 1.725; break;
            case "very_active": multiplier = 1.9; break;
            default: multiplier = 1.2;
        }

        int adjustment;
        switch (goal) {
            case "lose": adjustment = -500; break;
            case "gain": adjustment = 300; break;
            default: adjustment = 0;
        }

        return (int) Math.round(bmr * multiplier + adjustment);
    }

    public static Map<String, Integer> calculateMacros(int calories) {
        Map<String, Integer> m = new HashMap<>();
        m.put("protein", (int)Math.round(calories * 0.25 / 4));
        m.put("fat", (int)Math.round(calories * 0.25 / 9));
        m.put("carbs", (int)Math.round(calories * 0.50 / 4));
        m.put("sugar", 50);
        m.put("fiber", 25);
        return m;
    }
}