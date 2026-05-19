package com.example.caloriecounter.model;

public class FoodItem {
    public String name;
    public int calories, protein, fat, carbs, sugar, fiber; // на 100г

    public FoodItem(String name, int cal, int p, int f, int c, int s, int fib) {
        this.name = name; this.calories = cal;
        this.protein = p; this.fat = f; this.carbs = c; this.sugar = s; this.fiber = fib;
    }
}