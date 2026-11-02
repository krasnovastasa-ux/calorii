package com.example.caloriecounter.model;

import java.util.List;

public class ActivityType {

    public String id;
    public String owner_id;
    public String template_id;
    public String name;
    public String category;
    public String unit;
    public String description;

    public double met;

    public Double seconds_per_rep;

    public boolean sets_enabled;

    public List<String> time_units;

    public boolean timed() {
        return "minutes".equals(unit);
    }

    public String unitLabel() {
        return timed() ? "мин" : "повт.";
    }

    @Override
    public String toString() {
        return name + " • " + unitLabel();
    }
}