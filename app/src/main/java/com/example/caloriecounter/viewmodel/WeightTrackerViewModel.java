package com.example.caloriecounter.viewmodel;

import android.app.Application;
import android.util.Log;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.caloriecounter.model.WeightLog;
import com.example.caloriecounter.repository.SupabaseRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.ArrayList;
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
            @Override
            public void onSuccess(List<WeightLog> logs) {
                List<WeightLog> filtered = filterByPeriod(logs);
                Collections.sort(filtered, Comparator.comparing(a -> a.logDate));

                List<WeightLog> freshList = new ArrayList<>();
                for (WeightLog log : filtered) {
                    WeightLog fresh = new WeightLog();
                    fresh.id = log.id;
                    fresh.userId = log.userId;
                    fresh.weight = log.weight;
                    fresh.logDate = log.logDate;
                    fresh.createdAt = log.createdAt;
                    freshList.add(fresh);
                }

                logsLiveData.setValue(freshList);
                loading.setValue(false);
            }

            @Override
            public void onError(String msg) {
                Log.e("WEIGHT_LOAD", "Error loading logs: " + msg);
                error.setValue(msg);
                loading.setValue(false);
            }
        });
    }

    public void saveWeight(double weight) {
        if (selectedDate.isAfter(LocalDate.now())) {
            error.setValue("Cannot record weight for future date");
            return;
        }
        loading.setValue(true);

        Log.d("WEIGHT_SAVE", "Saving weight: " + weight + " for date: " + selectedDate);

        repo.saveOrUpdateWeightLog(userId, weight, selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                new SupabaseRepository.VoidCallback() {
                    @Override
                    public void onSuccess() {
                        Log.d("WEIGHT_SAVE", "Weight log saved");
                        updateProfileAndPrefs(weight);
                    }
                    @Override
                    public void onError(String msg) {
                        if (msg != null && (msg.contains("23505") || msg.contains("duplicate") || msg.contains("409"))) {
                            Log.d("WEIGHT_SAVE", "Entry already exists, updating via PATCH...");

                            final double finalWeight = weight;

                            repo.fetchWeightLogs(userId, new SupabaseRepository.WeightLogListCallback() {
                                @Override
                                public void onSuccess(List<WeightLog> logs) {
                                    String targetDate = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
                                    Log.d("WEIGHT_SAVE", "Looking for entry for date: " + targetDate + " in " + logs.size() + " entries");

                                    for (WeightLog log : logs) {
                                        Log.d("WEIGHT_SAVE", "   Checking: logDate=" + log.logDate + ", id=" + log.id);
                                        if (targetDate.equals(log.logDate) && log.id != null) {
                                            Log.d("WEIGHT_SAVE", "   Found! Updating via PATCH: id=" + log.id + ", weight=" + finalWeight);
                                            repo.updateWeightLogById(log.id, finalWeight, new SupabaseRepository.VoidCallback() {
                                                @Override public void onSuccess() {
                                                    Log.d("WEIGHT_SAVE", "PATCH success, weight updated in DB");
                                                    updateProfileAndPrefs(finalWeight);
                                                }
                                                @Override public void onError(String m) {
                                                    Log.e("WEIGHT_SAVE", "PATCH error: " + m);
                                                    error.setValue(m);
                                                    loading.setValue(false);
                                                }
                                            });
                                            return;
                                        }
                                    }
                                    loadLogs();
                                }
                                @Override
                                public void onError(String fetchMsg) {
                                    loadLogs();
                                }
                            });
                        } else {
                            Log.e("WEIGHT_SAVE", "Log error: " + msg);
                            error.setValue(msg);
                            loading.setValue(false);
                        }
                    }
                });
    }

    private void saveToPrefs(double weight) {
        repo.getPrefs().edit()
                .putString("profile_weight_date", selectedDate.toString())
                .putInt("weight", (int) Math.round(weight))
                .apply();
        saveSuccess.setValue(true);
        loading.setValue(false);
    }

    private void updateProfileAndPrefs(double weight) {
        if (selectedDate.equals(LocalDate.now())) {
            repo.updateProfileWeight(userId, weight, new SupabaseRepository.VoidCallback() {
                @Override public void onSuccess() {
                    Log.d("WEIGHT_SAVE", "Profile updated");
                    saveToPrefsAndReload(weight);
                }
                @Override public void onError(String msg) {
                    Log.e("WEIGHT_SAVE", "Profile error: " + msg);
                    saveToPrefsAndReload(weight);
                }
            });
        } else {
            saveToPrefsAndReload(weight);
        }
    }

    private void saveToPrefsAndReload(double weight) {
        repo.getPrefs().edit()
                .putString("profile_weight_date", selectedDate.toString())
                .putInt("weight", (int) Math.round(weight))
                .apply();
        saveSuccess.setValue(true);
        loadLogs();
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