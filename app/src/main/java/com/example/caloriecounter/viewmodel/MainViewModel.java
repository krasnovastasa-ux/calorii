package com.example.caloriecounter.viewmodel;

import android.app.Application;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import com.example.caloriecounter.model.FoodLog;
import com.example.caloriecounter.model.ProfileData;
import com.example.caloriecounter.repository.SupabaseRepository;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainViewModel extends AndroidViewModel {
    private static final String TAG = "MAIN_VM_CALC";
    private final SupabaseRepository repo;
    private final SharedPreferences prefs;
    private final MutableLiveData<List<FoodLog>> allLogs = new MutableLiveData<>();
    private final MutableLiveData<TargetData> targetData = new MutableLiveData<>();
    private final MutableLiveData<ConsumedData> consumedData = new MutableLiveData<>(new ConsumedData(0,0,0,0,0,0));
    private String currentDate;

    private final Observer<Long> profileUpdateObserver = timestamp -> {
        Log.d(TAG, " Сигнал обновления профиля, пересчитываю...");
        loadProfileAndCalculateTargets();
    };

    public MainViewModel(Application app) {
        super(app);
        repo = new SupabaseRepository(app);
        prefs = PreferenceManager.getDefaultSharedPreferences(app);
        currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());

        ProfileViewModel.profileUpdatedSignal.observeForever(profileUpdateObserver);

        loadProfileAndCalculateTargets();
        loadLogsForDate(currentDate);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        ProfileViewModel.profileUpdatedSignal.removeObserver(profileUpdateObserver);
    }

    public LiveData<TargetData> getTargetData() { return targetData; }
    public LiveData<ConsumedData> getConsumedData() { return consumedData; }
    public LiveData<List<FoodLog>> getAllLogs() { return allLogs; }
    public String getCurrentDate() { return currentDate; }

    public void setToToday() { currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date()); loadLogsForDate(currentDate); }
    public void setDate(String date) { this.currentDate = date; loadLogsForDate(date); }
    public void refresh() { loadLogsForDate(currentDate); }
    public void refreshTargets() { loadProfileAndCalculateTargets(); }

    private void loadProfileAndCalculateTargets() {
        int weight = prefs.getInt("weight", 70);
        int height = prefs.getInt("height", 170);
        int age = prefs.getInt("age", 30);
        String gender = prefs.getString("gender", "Женский");
        String goal = prefs.getString("goal", "maintain");
        String lifestyle = prefs.getString("lifestyle", "moderate");

        Log.d(TAG, " Из prefs: w=" + weight + ", g=" + gender + ", gl=" + goal);

        calculateTargets(weight, height, age, gender, goal, lifestyle);

        repo.fetchProfile(new SupabaseRepository.ProfileLoadedCallback() {
            public void onProfileLoaded(ProfileData p) {
                try {
                    int w = (p.weight != null && p.weight > 20 && p.weight < 300) ? p.weight : weight;
                    int h = (p.height != null && p.height > 100 && p.height < 250) ? p.height : height;
                    int a = (p.age != null && p.age > 10 && p.age < 100) ? p.age : age;
                    String g = p.gender != null ? p.gender : gender;
                    String gl = p.goal != null ? p.goal : goal;
                    String l = p.lifestyle != null ? p.lifestyle : lifestyle;

                    prefs.edit()
                            .putInt("weight", w).putInt("height", h).putInt("age", a)
                            .putString("gender", g).putString("goal", gl).putString("lifestyle", l).apply();

                    calculateTargets(w, h, a, g, gl, l);
                } catch (Exception e) { fallbackDefault(); }
            }
            public void onError(String m) {
                Log.d(TAG, " Профиль из БД не загружен, использую prefs");

            }
        });
    }

    private void calculateTargets(int weight, int height, int age, String gender, String goal, String lifestyle) {
        try {
            boolean isMale = gender != null && (gender.toLowerCase().contains("муж") || gender.toLowerCase().contains("male"));
            double bmr = isMale
                    ? 10 * weight + 6.25 * height - 5 * age + 5
                    : 10 * weight + 6.25 * height - 5 * age - 161;

            String act = lifestyle != null ? lifestyle.toLowerCase() : "moderate";
            double activity = act.contains("sedentary") || act.contains("сидяч") ? 1.2
                    : act.contains("light") || act.contains("лёгк") ? 1.375
                    : act.contains("very") || act.contains("очень") ? 1.9
                    : act.contains("active") ? 1.725
                    : 1.55;

            double tdee = bmr * activity;
            String g = goal != null ? goal.toLowerCase() : "maintain";
            if (g.equals("lose") || g.contains("похуд")) tdee -= 500;
            else if (g.equals("gain") || g.contains("набор")) tdee += 300;

            int cal = (int) Math.round(Math.max(1200, Math.min(5000, tdee)));
            int pro = (int) Math.round((cal * 0.30) / 4);
            int fat = (int) Math.round((cal * 0.30) / 9);
            int carb = (int) Math.round((cal * 0.40) / 4);
            int sugar = Math.min(50, (int) Math.round(cal * 0.10 / 4));
            int fiber = Math.min(35, cal / 100);

            Log.d(TAG, " Цель: " + cal + " ккал");
            targetData.postValue(new TargetData(cal, pro, fat, carb, sugar, fiber));
        } catch (Exception e) { fallbackDefault(); }
    }

    private void fallbackDefault() {
        int weight = prefs.getInt("weight", 70);
        int height = prefs.getInt("height", 170);
        int age = prefs.getInt("age", 30);
        calculateTargets(weight, height, age, "Женский", "maintain", "moderate");
    }

    public void loadLogsForDate(String date) {
        if (date == null) date = currentDate; this.currentDate = date;
        repo.fetchLogsForDate(date, new SupabaseRepository.LogListCallback() {
            @Override public void onSuccess(List<FoodLog> list) { allLogs.postValue(list); recalculateConsumed(list); }
            @Override public void onError(String m) { Log.e(TAG, "Ошибка логов: " + m); }
        });
    }

    private void recalculateConsumed(List<FoodLog> logs) {
        int c=0, p=0, f=0, cb=0, s=0, fib=0;
        if (logs != null) for (FoodLog l : logs) { c += l.totalCalories; p += l.totalProtein; f += l.totalFat; cb += l.totalCarbs; s += l.totalSugar; fib += l.totalFiber; }
        consumedData.postValue(new ConsumedData(c, p, f, cb, s, fib));
    }

    public static class TargetData { public int cal, pro, fat, carb, sugar, fiber; public TargetData(int c, int p, int f, int cb, int s, int fib) { cal=c; pro=p; fat=f; carb=cb; sugar=s; fiber=fib; } }
    public static class ConsumedData { public int cal, pro, fat, carb, sugar, fiber; public ConsumedData(int c, int p, int f, int cb, int s, int fib) { cal=c; pro=p; fat=f; carb=cb; sugar=s; fiber=fib; } }
}