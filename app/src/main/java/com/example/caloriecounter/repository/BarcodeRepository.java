package com.example.caloriecounter.repository;

import android.app.Application;
import android.util.LruCache;
import com.example.caloriecounter.api.OffApi;
import com.example.caloriecounter.model.Food;
import com.example.caloriecounter.model.OffResponse;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import android.util.Log;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class BarcodeRepository {
    private final OffApi offApi;
    private final OkHttpClient httpClient;
    private final SupabaseRepository supabaseRepo;
    private final LruCache<String, Food> memoryCache;
    private List<Food> localFoodsCache = new ArrayList<>();
    private boolean isLocalFoodsLoaded = false;

    public BarcodeRepository(Application app) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://world.openfoodfacts.org/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        offApi = retrofit.create(OffApi.class);

        httpClient = new OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .writeTimeout(3, TimeUnit.SECONDS)
                .build();

        supabaseRepo = new SupabaseRepository(app);
        memoryCache = new LruCache<>(50);

        loadLocalFoodsOnce();
    }

    private void loadLocalFoodsOnce() {
        supabaseRepo.fetchFoods(new SupabaseRepository.FoodCallback() {
            @Override public void onSuccess(List<Food> foods) {
                if (foods != null) {
                    localFoodsCache = foods;
                    isLocalFoodsLoaded = true;
                }
            }
            @Override public void onError(String m) { }
        });
    }

    public void fetchByBarcode(String barcode, BarcodeCallback callback) {
        Food cached = memoryCache.get(barcode);
        if (cached != null) {
            callback.onSuccess(cached);
            return;
        }

        offApi.getProduct(barcode).enqueue(new retrofit2.Callback<OffResponse>() {
            @Override public void onResponse(retrofit2.Call<OffResponse> call, retrofit2.Response<OffResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().status == 1) {
                    Food food = mapOffToFood(r.body(), barcode);
                    cacheAndReturn(barcode, food, callback);
                } else {
                    tryBarcodeLookup(barcode, callback);
                }
            }
            @Override public void onFailure(retrofit2.Call<OffResponse> call, Throwable t) {
                tryBarcodeLookup(barcode, callback);
            }
        });
    }

    private void tryBarcodeLookup(String barcode, BarcodeCallback callback) {
        String apiKey = "ifDzhmKslKav42OD93NEOH";

        String url = "https://api.barcodelookup.com/v3/products?barcode=" + barcode + "&formatted=y&key=" + apiKey;

        Request req = new Request.Builder().url(url).build();

        httpClient.newCall(req).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response r) throws IOException {
                try {
                    if (r.isSuccessful() && r.body() != null) {
                        String json = r.body().string();
                        JsonObject root = new Gson().fromJson(json, JsonObject.class);

                        if (root.has("products") && root.getAsJsonArray("products").size() > 0) {
                            JsonObject p = root.getAsJsonArray("products").get(0).getAsJsonObject();

                            Food food = new Food();
                            food.id = barcode;
                            food.foodId = barcode;
                            food.name = p.has("title") ? p.get("title").getAsString() : "Неизвестный продукт";
                            food.calories = 0;
                            food.protein = 0;
                            food.fat = 0;
                            food.carbs = 0;
                            food.sugar = 0;
                            food.fiber = 0;

                            cacheAndReturn(barcode, food, callback);
                            return;
                        }
                    }
                } catch (Exception e) {
                    Log.e("BARCODE_LOOKUP", "Parse error: " + e.getMessage());
                } finally {
                    if (r.body() != null) r.body().close();
                }
                searchLocalFallback(barcode, callback);
            }

            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("BARCODE_LOOKUP", "Network error: " + e.getMessage());
                searchLocalFallback(barcode, callback);
            }
        });
    }

    private void searchLocalFallback(String barcode, BarcodeCallback callback) {
        if (!isLocalFoodsLoaded || localFoodsCache.isEmpty()) {
            callback.onError("Продукт не найден. Введите данные вручную.");
            return;
        }

        callback.onError("not_found_in_db");
    }

    private void cacheAndReturn(String barcode, Food food, BarcodeCallback callback) {
        memoryCache.put(barcode, food);
        callback.onSuccess(food);
    }

    private Food mapOffToFood(OffResponse resp, String barcode) {
        Food f = new Food();
        f.id = barcode; f.foodId = barcode; f.category = "Сканирование";
        if (resp.product != null) {
            f.name = resp.product.productName != null ? resp.product.productName : "Неизвестный продукт";
            if (resp.product.nutriments != null) {
                OffResponse.OffNutriments n = resp.product.nutriments;
                f.calories = safeInt(n.energyKcal);
                f.protein = safeInt(n.proteins);
                f.fat = safeInt(n.fat);
                f.carbs = safeInt(n.carbs);
                f.sugar = safeInt(n.sugars);
                f.fiber = safeInt(n.fiber);
            }
        }
        return f;
    }

    private int safeInt(Double v) { return v != null ? v.intValue() : 0; }

    public interface BarcodeCallback {
        void onSuccess(Food food);
        void onError(String message);
    }
}