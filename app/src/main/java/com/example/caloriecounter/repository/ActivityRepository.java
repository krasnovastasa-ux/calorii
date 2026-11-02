package com.example.caloriecounter.repository;

import android.content.Context;

import com.example.caloriecounter.SupabaseApi;
import com.example.caloriecounter.SupabaseClient;
import com.example.caloriecounter.model.ActivityRecord;
import com.example.caloriecounter.model.ActivityType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ActivityRepository {

    private final SupabaseRepository session;
    private final SupabaseApi api = SupabaseClient.getApi();

    public ActivityRepository(Context context) {
        session = new SupabaseRepository(context);
    }

    public String uid() {
        return session.getPrefs().getString("user_id", "");
    }

    public int weight() {
        return session.getPrefs().getInt("weight", 0);
    }

    private String auth() {
        return "Bearer " + session.getPrefs().getString("token", "");
    }

    public interface Result<T> {
        void ok(T value);

        void error(String message);
    }

    private <T> void run(Call<T> call, Result<T> callback) {
        call.enqueue(new Callback<T>() {
            @Override
            public void onResponse(Call<T> call, Response<T> response) {
                if (response.isSuccessful()) {
                    callback.ok(response.body());
                } else {
                    callback.error(response.code() == 401
                            ? "Войдите в аккаунт заново"
                            : "Ошибка сервера: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<T> call, Throwable throwable) {
                callback.error("Нет связи с сервером. Повторите после подключения.");
            }
        });
    }

    public void catalog(Result<List<ActivityType>> callback) {
        catalogPage(new ArrayList<>(), callback);
    }

    private void catalogPage(List<ActivityType> accumulated, Result<List<ActivityType>> callback) {
        run(api.activityCatalog(auth(), accumulated.size()), new Result<List<ActivityType>>() {
            @Override
            public void ok(List<ActivityType> page) {
                if (page == null || page.isEmpty()) {
                    callback.ok(accumulated);
                    return;
                }
                accumulated.addAll(page);
                catalogPage(accumulated, callback);
            }

            @Override
            public void error(String message) {
                callback.error(message);
            }
        });
    }

    public void records(String since, Result<List<ActivityRecord>> callback) {
        recordsPage(since, new ArrayList<>(), callback);
    }

    private void recordsPage(String since,
                             List<ActivityRecord> accumulated,
                             Result<List<ActivityRecord>> callback) {
        run(api.activityRecords(auth(), "eq." + uid(), "gte." + since, accumulated.size()),
                new Result<List<ActivityRecord>>() {
                    @Override
                    public void ok(List<ActivityRecord> page) {
                        if (page == null || page.isEmpty()) {
                            callback.ok(accumulated);
                            return;
                        }
                        accumulated.addAll(page);
                        recordsPage(since, accumulated, callback);
                    }

                    @Override
                    public void error(String message) {
                        callback.error(message);
                    }
                });
    }

    public void record(String id,
                       ActivityType activity,
                       String date,
                       double quantity,
                       double weight,
                       int sets,
                       Result<Void> callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("p_id", id);
        body.put("p_activity", activity.id);
        body.put("p_date", date);
        body.put("p_quantity", quantity);
        body.put("p_weight", weight);
        body.put("p_sets", sets);
        run(api.recordActivity(auth(), body), callback);
    }

    public void personal(String id,
                         ActivityType template,
                         String name,
                         Result<Void> callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("p_id", id);
        body.put("p_template", template.id);
        body.put("p_name", name);
        run(api.createPersonalActivity(auth(), body), callback);
    }

    public void delete(String id, Result<Void> callback) {
        run(api.deleteActivityRecord(auth(), "eq." + id), callback);
    }
}