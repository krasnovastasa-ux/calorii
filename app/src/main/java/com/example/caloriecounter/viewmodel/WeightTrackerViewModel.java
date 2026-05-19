package com.example.caloriecounter.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.caloriecounter.model.WeightLog;
import com.example.caloriecounter.repository.SupabaseRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import android.content.Intent;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class WeightTrackerViewModel extends AndroidViewModel {
    private final SupabaseRepository repo;
    private final String userId;
    private final MutableLiveData<List<WeightLog>> logsLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> saveSuccess = new MutableLiveData<>();

    private LocalDate selectedDate = LocalDate.now();
    private String currentPeriod = "week";

    public WeightTrackerViewModel(Application app, String userId) {
        super(app);
        this.repo = new SupabaseRepository(app);
        this.userId = userId;
    }

    public LiveData<List<WeightLog>> getLogs() { return logsLiveData; }
    public LiveData<Boolean> isLoading() { return loading; }
    public LiveData<String> getError() { return error; }
    public LiveData<Boolean> getSaveSuccess() { return saveSuccess; }
    public LocalDate getSelectedDate() { return selectedDate; }
    public String getCurrentPeriod() { return currentPeriod; }

    public void setSelectedDate(LocalDate date) { this.selectedDate = date; }
    public void setPeriod(String period) { this.currentPeriod = period; loadLogs(); }

    public void loadLogs() {
        loading.setValue(true);
        repo.fetchWeightLogs(userId, new SupabaseRepository.WeightLogListCallback() {
            @Override public void onSuccess(List<WeightLog> logs) {
                List<WeightLog> filtered = filterByPeriod(logs);
                Collections.sort(filtered, Comparator.comparing(a -> a.logDate));
                logsLiveData.setValue(filtered);
                loading.setValue(false);
            }
            @Override public void onError(String msg) {
                error.setValue(msg);
                loading.setValue(false);
            }
        });
    }



    public void saveWeight(double weight) {
        if (selectedDate.isAfter(LocalDate.now())) {
            error.setValue("Запись веса в будущем невозможна");
            return;
        }
        loading.setValue(true);
        repo.saveOrUpdateWeightLog(userId, weight, selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE), new SupabaseRepository.VoidCallback() {
            @Override public void onSuccess() {
                if (selectedDate.equals(LocalDate.now())) {
                    repo.updateProfileWeight(userId, weight, new SupabaseRepository.VoidCallback() {
                        @Override public void onSuccess() { saveSuccess.setValue(true); loadLogs(); }
                        @Override public void onError(String msg) { saveSuccess.setValue(true); loadLogs(); }
                    });
                } else { saveSuccess.setValue(true); loadLogs(); }
            }
            @Override public void onError(String msg) {
                error.setValue(msg);
                loading.setValue(false);
            }
        });
    }




    private List<WeightLog> filterByPeriod(List<WeightLog> all) {
        LocalDate today = LocalDate.now();
        LocalDate start;
        switch (currentPeriod) {
            case "month": start = today.minusMonths(1); break;
            case "year": start = today.minusYears(1); break;
            default: start = today.minusDays(7); break;
        }
        String startDate = start.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String endDate = today.format(DateTimeFormatter.ISO_LOCAL_DATE);

        return all.stream()
                .filter(l -> l.logDate != null
                        && l.logDate.compareTo(startDate) >= 0
                        && l.logDate.compareTo(endDate) <= 0)
                .collect(Collectors.toList());
    }
}