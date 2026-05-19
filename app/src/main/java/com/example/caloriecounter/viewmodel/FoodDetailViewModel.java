package com.example.caloriecounter.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.caloriecounter.model.FoodLog;
import com.example.caloriecounter.repository.SupabaseRepository;
import java.util.UUID;

public class FoodDetailViewModel extends ViewModel {
    private final SupabaseRepository repo;
    private final MutableLiveData<CalculationResult> calculationResult = new MutableLiveData<>();
    private final MutableLiveData<Boolean> saveSuccess = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    private String userId, foodId, foodName, mealType;
    private int cal100, pro100, fat100, carb100, sugar100, fiber100;

    public FoodDetailViewModel(SupabaseRepository repo) { this.repo = repo; }

    public void init(String uid, String meal, String fId, String fName,
                     int c, int p, int f, int cb, int s, int fib) {
        this.userId = uid; this.mealType = meal; this.foodId = fId; this.foodName = fName;
        this.cal100 = c; this.pro100 = p; this.fat100 = f; this.carb100 = cb; this.sugar100 = s; this.fiber100 = fib;
    }

    public void calculate(int grams) {
        if (grams <= 0) { calculationResult.postValue(null); return; }
        double k = grams / 100.0;
        CalculationResult res = new CalculationResult(
                grams, (int)(cal100*k), (int)(pro100*k), (int)(fat100*k),
                (int)(carb100*k), (int)(sugar100*k), (int)(fiber100*k));
        calculationResult.postValue(res);
    }


    public void save(int grams, String date) {
        if (grams <= 0 || foodId == null || foodId.isEmpty() || userId == null || userId.isEmpty()) {
            errorMessage.postValue("Ошибка: недостающие данные");
            return;
        }
        calculate(grams);
        CalculationResult res = calculationResult.getValue();
        if (res == null) return;

        FoodLog log = new FoodLog();
        log.id = java.util.UUID.randomUUID().toString();
        log.userId = userId;
        log.foodId = foodId;
        log.mealType = mealType;
        log.grams = grams;
        log.logDate = date;
        log.totalCalories = res.cal;
        log.totalProtein = res.pro;
        log.totalFat = res.fat;
        log.totalCarbs = res.carb;
        log.totalSugar = res.sugar;
        log.totalFiber = res.fiber;

        repo.addLog(log, date, new SupabaseRepository.VoidCallback() {
            @Override public void onSuccess() { saveSuccess.postValue(true); }
            @Override public void onError(String m) { errorMessage.postValue(m); }
        });
    }

    public static class CalculationResult {
        public int grams, cal, pro, fat, carb, sugar, fiber;
        public CalculationResult(int g, int c, int p, int f, int cb, int s, int fib) {
            grams=g; cal=c; pro=p; fat=f; carb=cb; sugar=s; fiber=fib;
        }
    }

    public LiveData<CalculationResult> getCalculationResult() { return calculationResult; }
    public LiveData<Boolean> getSaveSuccess() { return saveSuccess; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
}