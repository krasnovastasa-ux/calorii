package com.example.caloriecounter.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import com.example.caloriecounter.SupabaseApi;
import com.example.caloriecounter.SupabaseClient;
import com.example.caloriecounter.model.*;
import java.io.IOException;
import java.util.*;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SupabaseRepository {
    private static final String TAG = "SUPABASE_REPO";
    private final SupabaseApi api;
    private final SharedPreferences prefs;
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USER_ID = "user_id";

    public SupabaseRepository(Context context) {
        api = SupabaseClient.getApi();
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public SharedPreferences getPrefs() { return prefs; }

    public interface AuthCallback { void onSuccess(AuthResponse response); void onError(String message); }
    public interface SignUpCallback { void onSuccess(AuthResponse response); void onError(String message); }
    public interface SignInCallback { void onSuccess(AuthResponse response); void onError(String message); }
    public interface VoidCallback { void onSuccess(); void onError(String message); }
    public interface ProfileLoadedCallback { void onProfileLoaded(ProfileData profile); void onError(String message); }
    public interface LogListCallback { void onSuccess(List<FoodLog> logs); void onError(String message); }
    public interface FoodCallback { void onSuccess(List<Food> foods); void onError(String message); }
    public interface RecipeFoodListCallback { void onSuccess(List<Recipe.RecipeFood> foods); void onError(String message); }
    public interface UserRecipeCallback { void onSuccess(UserRecipe recipe); void onError(String message); }
    public interface FoodListCallback { void onSuccess(List<Food> foods); void onError(String message); }
    public interface RecipeListCallback { void onSuccess(List<Recipe> recipes); void onError(String message); }
    public interface UserRecipeListCallback { void onSuccess(List<UserRecipe> recipes); void onError(String message); }
    public interface RecipeCallback { void onSuccess(Recipe recipe); void onError(String message); }

    public void signIn(String email, String password, SignInCallback cb) {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("email", email);
        credentials.put("password", password);

        api.signIn(credentials).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    cb.onSuccess(response.body());
                } else {
                    String error = parseAuthError(response);
                    Log.e("SUPABASE", "signIn error: " + error);
                    cb.onError(error);
                }
            }
            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                Log.e("SUPABASE", "signIn failure: " + t.getMessage());
                cb.onError("Сеть: " + t.getMessage());
            }
        });
    }
    public void signUp(String email, String password, SignUpCallback cb) {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("email", email);
        credentials.put("password", password);

        api.signUp(credentials).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    cb.onSuccess(response.body());
                } else {
                    String error = parseAuthError(response);
                    Log.e("SUPABASE", "signUp error: " + error);
                    cb.onError(error);
                }
            }
            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                Log.e("SUPABASE", "signUp failure: " + t.getMessage());
                cb.onError("Сеть: " + t.getMessage());
            }
        });
    }

    private String parseAuthError(Response<AuthResponse> response) {
        try {
            if (response.errorBody() != null) {
                String err = response.errorBody().string();
                if (err.contains("User already registered")) return "User already registered";
                if (err.contains("email_exists")) return "Email already registered";
                if (err.contains("weak_password")) return "Weak password";
                if (err.contains("invalid_credentials")) return "Invalid credentials";
                return "Ошибка " + response.code();
            }
        } catch (Exception e) {
            Log.e("SUPABASE", "Parse error: " + e.getMessage());
        }
        return "Неизвестная ошибка";
    }

    public void updateProfile(ProfileData profile, VoidCallback cb) {
        String token = prefs.getString(KEY_TOKEN, null);
        if (token == null || profile.userId == null) {
            cb.onError("Нет токена или ID");
            return;
        }

        Log.d(TAG, "updateProfile UPSERT: id=" + profile.id + ", user_id=" + profile.userId + ", name=" + profile.name);

        api.createProfile("Bearer " + token, "resolution=merge-duplicates", profile)
                .enqueue(new Callback<Void>() {
                    @Override public void onResponse(Call<Void> call, Response<Void> r) {
                        Log.d(TAG, "UPSERT response: code=" + r.code() + ", successful=" + r.isSuccessful());
                        if (handleTokenError(r, cb)) return;

                        if (r.isSuccessful() || r.code() == 201 || r.code() == 204 || r.code() == 409) {
                            cb.onSuccess();
                        } else {
                            String err = parseError(r);
                            Log.e(TAG, "UPSERT error: " + err);
                            cb.onError(err);
                        }
                    }
                    @Override public void onFailure(Call<Void> call, Throwable t) {
                        Log.e(TAG, "UPSERT failure: " + t.getMessage());
                        cb.onError("Сеть: " + t.getMessage());
                    }
                });
    }

    public void fetchProfile(ProfileLoadedCallback cb) {
        String token = prefs.getString(KEY_TOKEN, null);
        String userId = prefs.getString(KEY_USER_ID, null);
        if (token == null || userId == null) {
            cb.onError("Нет сессии");
            return;
        }
        api.getProfiles("Bearer " + token, "eq." + userId).enqueue(new Callback<List<ProfileData>>() {
            @Override public void onResponse(Call<List<ProfileData>> call, Response<List<ProfileData>> r) {
                if (handleTokenError(r, cb)) return;
                if (r.isSuccessful() && r.body() != null && !r.body().isEmpty())
                    cb.onProfileLoaded(r.body().get(0));
                else cb.onError("Профиль не найден");
            }
            @Override public void onFailure(Call<List<ProfileData>> call, Throwable t) {
                cb.onError("Сеть: " + t.getMessage());
            }
        });
    }

    public void fetchLogsForDate(String date, LogListCallback cb) {
        String token = prefs.getString(KEY_TOKEN, null);
        String userId = prefs.getString(KEY_USER_ID, null);
        if (token == null || userId == null) {
            cb.onError("Нет сессии");
            return;
        }
        api.getLogs("Bearer " + token, "eq." + userId).enqueue(new Callback<List<FoodLog>>() {
            @Override public void onResponse(Call<List<FoodLog>> call, Response<List<FoodLog>> r) {
                if (handleTokenError(r, cb)) return;
                if (r.isSuccessful() && r.body() != null) {
                    List<FoodLog> f = new ArrayList<>();
                    for (FoodLog l : r.body())
                        if (date.equals(l.logDate)) f.add(l);
                    cb.onSuccess(f);
                } else cb.onError(parseError(r));
            }
            @Override public void onFailure(Call<List<FoodLog>> call, Throwable t) {
                cb.onError("Сеть: " + t.getMessage());
            }
        });
    }

    public void addLog(FoodLog log, String date, VoidCallback cb) {
        String token = prefs.getString(KEY_TOKEN, null);
        String userId = prefs.getString(KEY_USER_ID, null);
        if (token == null || userId == null) {
            cb.onError("Нет сессии");
            return;
        }
        if (log != null) log.userId = userId;
        if (log != null && log.logDate == null) log.logDate = date;
        api.addLog("Bearer " + token, "return=minimal", log).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> r) {
                if (handleTokenError(r, cb)) return;
                if (r.isSuccessful() || r.code() == 201 || r.code() == 204) cb.onSuccess();
                else cb.onError(parseError(r));
            }
            @Override public void onFailure(Call<Void> call, Throwable t) {
                cb.onError("Сеть: " + t.getMessage());
            }
        });
    }

    public void deleteLog(String logId, VoidCallback cb) {
        String token = prefs.getString(KEY_TOKEN, null);
        if (token == null) {
            cb.onError("Нет токена");
            return;
        }
        api.deleteLog("Bearer " + token, "eq." + logId).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> r) {
                if (handleTokenError(r, cb)) return;
                if (r.isSuccessful() || r.code() == 204 || r.code() == 200) cb.onSuccess();
                else cb.onError(parseError(r));
            }
            @Override public void onFailure(Call<Void> call, Throwable t) {
                cb.onError("Сеть: " + t.getMessage());
            }
        });
    }

    public void fetchFoods(FoodCallback cb) {
        api.getFoods().enqueue(new Callback<List<Food>>() {
            @Override public void onResponse(Call<List<Food>> call, Response<List<Food>> r) {
                if (r.isSuccessful() && r.body() != null) cb.onSuccess(r.body());
                else cb.onError(parseError(r));
            }
            @Override public void onFailure(Call<List<Food>> call, Throwable t) {
                cb.onError("Сеть: " + t.getMessage());
            }
        });
    }

    public void fetchFavorites(FoodListCallback cb) {
        String token = prefs.getString(KEY_TOKEN, null);
        String userId = prefs.getString(KEY_USER_ID, null);
        if (token == null || userId == null) {
            cb.onError("Нет сессии");
            return;
        }
        api.getFavorites("Bearer " + token, "eq." + userId).enqueue(new Callback<List<Food>>() {
            @Override public void onResponse(Call<List<Food>> call, Response<List<Food>> r) {
                if (r.code() == 401) {
                    clearSession();
                    cb.onError("Сессия истекла");
                    return;
                }
                if (r.isSuccessful() && r.body() != null) cb.onSuccess(r.body());
                else cb.onError(parseError(r));
            }
            @Override public void onFailure(Call<List<Food>> call, Throwable t) {
                cb.onError("Сеть: " + t.getMessage());
            }
        });
    }

    public void addToFavorites(String userId, Food food, VoidCallback cb) {
        String token = prefs.getString(KEY_TOKEN, null);

        if (token == null || userId == null || userId.isEmpty()) {
            userId = prefs.getString("user_id", null);
            if (userId == null || userId.isEmpty()) {
                cb.onError("Нет сессии");
                return;
            }
        }

        Map<String, Object> body = new HashMap<>();
        body.put("user_id", userId);
        body.put("food_id", food.id);
        body.put("food_name", food.name);

        api.addFavorite("Bearer " + token, "return=minimal", body).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> r) {
                if (handleTokenError(r, cb)) return;
                if (r.isSuccessful() || r.code() == 201 || r.code() == 204) cb.onSuccess();
                else cb.onError(parseError(r));
            }
            @Override public void onFailure(Call<Void> call, Throwable t) {
                cb.onError("Сеть: " + t.getMessage());
            }
        });
    }

    public void removeFromFavorites(String userId, String foodId, VoidCallback cb) {
        String token = prefs.getString(KEY_TOKEN, null);
        if (token == null) {
            cb.onError("Нет токена");
            return;
        }
        api.removeFavorite("Bearer " + token, "eq." + userId, "eq." + foodId).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> r) {
                if (handleTokenError(r, cb)) return;
                if (r.isSuccessful() || r.code() == 204) cb.onSuccess();
                else cb.onError(parseError(r));
            }
            @Override public void onFailure(Call<Void> call, Throwable t) {
                cb.onError("Сеть: " + t.getMessage());
            }
        });
    }

    public void fetchRecipeFoods(String recipeIdFilter, RecipeFoodListCallback cb) {
        api.getRecipeFoods(recipeIdFilter).enqueue(new Callback<List<Recipe.RecipeFood>>() {
            @Override public void onResponse(Call<List<Recipe.RecipeFood>> call, Response<List<Recipe.RecipeFood>> r) {
                if (r.isSuccessful() && r.body() != null) cb.onSuccess(r.body());
                else cb.onError(parseError(r));
            }
            @Override public void onFailure(Call<List<Recipe.RecipeFood>> call, Throwable t) {
                cb.onError("Сеть: " + t.getMessage());
            }
        });
    }

    public void fetchRecipes(RecipeListCallback cb) {
        api.getRecipes("*").enqueue(new Callback<List<Recipe>>() {
            @Override public void onResponse(Call<List<Recipe>> call, Response<List<Recipe>> r) {
                if (r.isSuccessful() && r.body() != null) {
                    cb.onSuccess(r.body());
                    for (Recipe recipe : r.body()) {
                        fetchRecipeFoods("eq." + recipe.id, new RecipeFoodListCallback() {
                            @Override public void onSuccess(List<Recipe.RecipeFood> ingredients) {
                                int cal = 0, p = 0, f = 0, c = 0;
                                for (Recipe.RecipeFood ing : ingredients) {
                                    cal += ing.getTotalCalories();
                                    p += ing.getTotalProtein();
                                    f += ing.getTotalFat();
                                    c += ing.getTotalCarbs();
                                }
                                if (recipe.totalCalories == 0) recipe.totalCalories = cal;
                                if (recipe.totalProtein == 0) recipe.totalProtein = p;
                                if (recipe.totalFat == 0) recipe.totalFat = f;
                                if (recipe.totalCarbs == 0) recipe.totalCarbs = c;
                                Log.d("RECIPE_CALC", recipe.name + ": " + recipe.totalCalories + " ккал");
                            }
                            @Override public void onError(String msg) {
                                Log.w("RECIPE_CALC", "Не загрузили ингредиенты для " + recipe.name);
                            }
                        });
                    }
                } else cb.onError(parseError(r));
            }
            @Override public void onFailure(Call<List<Recipe>> call, Throwable t) {
                cb.onError("Сеть: " + t.getMessage());
            }
        });
    }

    public void fetchRecipeById(String id, RecipeCallback cb) {
        api.getRecipeById("eq." + id).enqueue(new Callback<List<Recipe>>() {
            @Override public void onResponse(Call<List<Recipe>> call, Response<List<Recipe>> r) {
                if (r.isSuccessful() && r.body() != null && !r.body().isEmpty())
                    cb.onSuccess(r.body().get(0));
                else cb.onError(parseError(r));
            }
            @Override public void onFailure(Call<List<Recipe>> call, Throwable t) {
                cb.onError("Сеть: " + t.getMessage());
            }
        });
    }

    public void fetchUserRecipeById(String id, UserRecipeCallback cb) {
        String token = prefs.getString(KEY_TOKEN, null);
        if (token == null) { cb.onError("Нет токена"); return; }

        api.getUserRecipeById("Bearer " + token, "eq." + id)
                .enqueue(new Callback<List<UserRecipe>>() {
                    @Override public void onResponse(Call<List<UserRecipe>> call, Response<List<UserRecipe>> r) {
                        if (handleTokenError(r, cb)) return;
                        if (r.isSuccessful() && r.body() != null && !r.body().isEmpty()) {
                            cb.onSuccess(r.body().get(0));
                        } else {
                            cb.onError(parseError(r));
                        }
                    }
                    @Override public void onFailure(Call<List<UserRecipe>> call, Throwable t) {
                        cb.onError("Сеть: " + t.getMessage());
                    }
                });
    }

    public void fetchUserRecipes(UserRecipeListCallback cb) {
        String token = prefs.getString(KEY_TOKEN, null);
        String userId = prefs.getString(KEY_USER_ID, null);
        if (token == null || userId == null) {
            cb.onError("Нет сессии");
            return;
        }
        api.getUserRecipes("Bearer " + token, "eq." + userId).enqueue(new Callback<List<UserRecipe>>() {
            @Override public void onResponse(Call<List<UserRecipe>> call, Response<List<UserRecipe>> r) {
                if (r.code() == 401) {
                    clearSession();
                    cb.onError("Сессия истекла");
                    return;
                }
                if (r.isSuccessful() && r.body() != null) cb.onSuccess(r.body());
                else cb.onError(parseError(r));
            }
            @Override public void onFailure(Call<List<UserRecipe>> call, Throwable t) {
                cb.onError("Сеть: " + t.getMessage());
            }
        });
    }

    public void createUserRecipe(UserRecipe recipe, UserRecipeCallback cb) {
        String token = prefs.getString(KEY_TOKEN, null);
        if (token == null) {
            cb.onError("Нет токена");
            return;
        }
        api.createUserRecipe("Bearer " + token, "return=representation", recipe)
                .enqueue(new Callback<List<UserRecipe>>() {
                    @Override
                    public void onResponse(Call<List<UserRecipe>> call, Response<List<UserRecipe>> r) {
                        if (handleTokenError(r, cb)) return;
                        if (r.isSuccessful() && r.body() != null && !r.body().isEmpty()) {
                            cb.onSuccess(r.body().get(0));
                        } else {
                            cb.onError(parseError(r));
                        }
                    }
                    @Override
                    public void onFailure(Call<List<UserRecipe>> call, Throwable t) {
                        cb.onError("Сеть: " + t.getMessage());
                    }
                });
    }

    public void addUserRecipeIngredient(UserRecipeIngredient ing, VoidCallback cb) {
        String token = prefs.getString(KEY_TOKEN, null);
        if (token == null) {
            Log.e(TAG, "addUserRecipeIngredient: нет токена");
            cb.onError("Нет токена");
            return;
        }

        Log.d(TAG, "addUserRecipeIngredient: recipe_id=" + ing.recipeId + ", food_id=" + ing.foodId + ", name=" + ing.name);

        api.createUserRecipeIngredient("Bearer " + token, "return=minimal", ing).enqueue(new Callback<UserRecipeIngredient>() {
            @Override public void onResponse(Call<UserRecipeIngredient> call, Response<UserRecipeIngredient> r) {
                Log.d(TAG, "addUserRecipeIngredient response: code=" + r.code() + ", successful=" + r.isSuccessful());
                if (handleTokenError(r, cb)) return;
                if (r.isSuccessful() || r.code() == 201) cb.onSuccess();
                else cb.onError(parseError(r));
            }
            @Override public void onFailure(Call<UserRecipeIngredient> call, Throwable t) {
                Log.e(TAG, "addUserRecipeIngredient failure: " + t.getMessage());
                cb.onError("Сеть: " + t.getMessage());
            }
        });
    }

    public void fetchUserRecipeIngredients(String recipeIdFilter, RecipeFoodListCallback cb) {
        String token = prefs.getString(KEY_TOKEN, null);
        if (token != null) {
            api.getUserRecipeIngredients("Bearer " + token, recipeIdFilter)
                    .enqueue(new Callback<List<Recipe.RecipeFood>>() {
                        @Override public void onResponse(Call<List<Recipe.RecipeFood>> call, Response<List<Recipe.RecipeFood>> r) {
                            if (r.isSuccessful() && r.body() != null) cb.onSuccess(r.body());
                            else cb.onError(parseError(r));
                        }
                        @Override public void onFailure(Call<List<Recipe.RecipeFood>> call, Throwable t) {
                            cb.onError("Сеть: " + t.getMessage());
                        }
                    });
        } else {
            api.getUserRecipeIngredients(null, recipeIdFilter)
                    .enqueue(new Callback<List<Recipe.RecipeFood>>() {
                        @Override public void onResponse(Call<List<Recipe.RecipeFood>> call, Response<List<Recipe.RecipeFood>> r) {
                            if (r.isSuccessful() && r.body() != null) cb.onSuccess(r.body());
                            else cb.onError(parseError(r));
                        }
                        @Override public void onFailure(Call<List<Recipe.RecipeFood>> call, Throwable t) {
                            cb.onError("Сеть: " + t.getMessage());
                        }
                    });
        }
    }

    public void deleteUserRecipe(String recipeId, VoidCallback cb) {
        String token = prefs.getString(KEY_TOKEN, null);
        if (token == null) {
            cb.onError("Нет токена");
            return;
        }
        api.deleteUserRecipe("Bearer " + token, "eq." + recipeId).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> r) {
                if (handleTokenError(r, cb)) return;
                if (r.isSuccessful() || r.code() == 204) cb.onSuccess();
                else cb.onError(parseError(r));
            }
            @Override public void onFailure(Call<Void> call, Throwable t) {
                cb.onError("Сеть: " + t.getMessage());
            }
        });
    }

    public void clearSession() {
        prefs.edit().clear().apply();
    }

    public boolean isLoggedIn() {
        return prefs.getString("user_id", null) != null;
    }

    private String parseError(Response<?> r) {
        try {
            if (r.errorBody() != null) {
                String e = r.errorBody().string();
                com.google.gson.JsonObject j = new com.google.gson.Gson().fromJson(e, com.google.gson.JsonObject.class);
                if (j.has("message")) return j.get("message").getAsString();
            }
        } catch (IOException ex) {
            Log.e(TAG, "Parse error", ex);
        }
        return "Ошибка " + r.code();
    }

    private boolean handleTokenError(Response<?> r, Object cb) {
        if (r.code() == 401) {
            clearSession();
            if (cb instanceof AuthCallback) ((AuthCallback)cb).onError("Сессия истекла");
            else if (cb instanceof SignUpCallback) ((SignUpCallback)cb).onError("Сессия истекла");
            else if (cb instanceof SignInCallback) ((SignInCallback)cb).onError("Сессия истекла");
            else if (cb instanceof VoidCallback) ((VoidCallback)cb).onError("Сессия истекла");
            else if (cb instanceof ProfileLoadedCallback) ((ProfileLoadedCallback)cb).onError("Сессия истекла");
            else if (cb instanceof LogListCallback) ((LogListCallback)cb).onError("Сессия истекла");
            else if (cb instanceof FoodListCallback) ((FoodListCallback)cb).onError("Сессия истекла");
            else if (cb instanceof RecipeListCallback) ((RecipeListCallback)cb).onError("Сессия истекла");
            else if (cb instanceof UserRecipeListCallback) ((UserRecipeListCallback)cb).onError("Сессия истекла");
            else if (cb instanceof RecipeCallback) ((RecipeCallback)cb).onError("Сессия истекла");
            else if (cb instanceof UserRecipeCallback) ((UserRecipeCallback)cb).onError("Сессия истекла");
            return true;
        }
        return false;
    }

    public void addWaterLog(String userId, int ml, String date, VoidCallback cb) {
        String token = prefs.getString(KEY_TOKEN, null);
        if (token == null || userId == null) {
            cb.onError("Нет сессии");
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("user_id", userId);
        body.put("ml", ml);
        body.put("log_date", date);

        api.addWaterLog("Bearer " + token, body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> r) {
                if (handleTokenError(r, cb)) return;
                if (r.isSuccessful() || r.code() == 201 || r.code() == 204) {
                    cb.onSuccess();
                } else {
                    String err = parseError(r);
                    Log.e("WATER_LOG", "Ошибка: " + err + " | Code: " + r.code());
                    cb.onError("Ошибка: " + err);
                }
            }
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e("WATER_LOG", "Network: " + t.getMessage());
                cb.onError("Сеть: " + t.getMessage());
            }
        });
    }

    public void fetchWaterLogs(String userId, String date, LogListCallback cb) {
        String token = prefs.getString(KEY_TOKEN, null);
        if (token == null || userId == null) {
            cb.onError("Нет сессии");
            return;
        }

        api.fetchWaterLogs("Bearer " + token, "eq." + userId, null).enqueue(new Callback<List<FoodLog>>() {
            @Override
            public void onResponse(Call<List<FoodLog>> call, Response<List<FoodLog>> r) {
                if (handleTokenError(r, cb)) return;
                if (r.isSuccessful() && r.body() != null) {
                    cb.onSuccess(r.body());
                } else {
                    cb.onError(parseError(r));
                }
            }
            @Override
            public void onFailure(Call<List<FoodLog>> call, Throwable t) {
                cb.onError("Сеть: " + t.getMessage());
            }
        });
    }

    public void deleteWaterLog(String logId, VoidCallback cb) {
        String token = prefs.getString(KEY_TOKEN, null);
        if (token == null) { cb.onError("Нет сессии"); return; }

        api.deleteWaterLog("Bearer " + token, "eq." + logId)
                .enqueue(new Callback<Void>() {
                    @Override public void onResponse(Call<Void> call, Response<Void> r) {
                        if (r.isSuccessful() || r.code() == 204) cb.onSuccess();
                        else cb.onError(parseError(r));
                    }
                    @Override public void onFailure(Call<Void> call, Throwable t) {
                        cb.onError("Сеть: " + t.getMessage());
                    }
                });
    }

    public interface DietListCallback { void onSuccess(List<Diet> diets); void onError(String message); }
    public interface DietCallback { void onSuccess(Diet diet); void onError(String message); }

    public void fetchDiets(DietListCallback cb) {
        api.getDiets().enqueue(new Callback<List<Diet>>() {
            @Override public void onResponse(Call<List<Diet>> call, Response<List<Diet>> r) {
                if (r.isSuccessful() && r.body() != null) cb.onSuccess(r.body());
                else cb.onError(parseError(r));
            }
            @Override public void onFailure(Call<List<Diet>> call, Throwable t) {
                cb.onError("Сеть: " + t.getMessage());
            }
        });
    }

    public void fetchDietById(String id, DietCallback cb) {
        api.getDietById("eq." + id).enqueue(new Callback<List<Diet>>() {
            @Override public void onResponse(Call<List<Diet>> call, Response<List<Diet>> r) {
                if (r.isSuccessful() && r.body() != null && !r.body().isEmpty())
                    cb.onSuccess(r.body().get(0));
                else cb.onError(parseError(r));
            }
            @Override public void onFailure(Call<List<Diet>> call, Throwable t) {
                cb.onError("Сеть: " + t.getMessage());
            }
        });
    }

    public void fetchDietsByGoal(String goal, DietListCallback cb) {
        api.getDietsByGoal("eq." + goal).enqueue(new Callback<List<Diet>>() {
            @Override
            public void onResponse(Call<List<Diet>> call, Response<List<Diet>> r) {
                if (r.isSuccessful() && r.body() != null) cb.onSuccess(r.body());
                else cb.onError(parseError(r));
            }
            @Override
            public void onFailure(Call<List<Diet>> call, Throwable t) {
                cb.onError("Сеть: " + t.getMessage());
            }
        });
    }


    public interface WeightLogListCallback { void onSuccess(List<WeightLog> logs); void onError(String message); }

    public void fetchWeightLogs(String userId, WeightLogListCallback cb) {
        String token = prefs.getString("token", null);
        if (token == null || userId == null) { cb.onError("Нет сессии"); return; }

        api.getWeightLogs("Bearer " + token, "eq." + userId, null)
                .enqueue(new Callback<List<WeightLog>>() {
                    @Override public void onResponse(Call<List<WeightLog>> call, Response<List<WeightLog>> r) {
                        if (handleTokenError(r, cb)) return;
                        if (r.isSuccessful() && r.body() != null) cb.onSuccess(r.body());
                        else cb.onError(parseError(r));
                    }
                    @Override public void onFailure(Call<List<WeightLog>> call, Throwable t) {
                        cb.onError("Сеть: " + t.getMessage());
                    }
                });
    }

    public void saveOrUpdateWeightLog(String userId, double weight, String date, VoidCallback cb) {
        String token = prefs.getString("token", null);
        if (token == null) { cb.onError("Нет сессии"); return; }
        WeightLog log = new WeightLog();
        log.userId = userId; log.weight = weight; log.logDate = date;

        api.addWeightLog("Bearer " + token, "resolution=merge-duplicates", log).enqueue(new retrofit2.Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> r) {
                if (r.isSuccessful() || r.code() == 201 || r.code() == 204 || r.code() == 409) cb.onSuccess();
                else cb.onError(parseError(r));
            }
            @Override public void onFailure(Call<Void> call, Throwable t) { cb.onError("Сеть: " + t.getMessage()); }
        });
    }

    public void updateProfileWeight(String userId, double weight, VoidCallback cb) {
        String token = prefs.getString("token", null);
        if (token == null) { cb.onError("Нет сессии"); return; }
        ProfileData update = new ProfileData();
        update.id = userId; update.userId = userId; update.weight = (int)Math.round(weight);
        api.createProfile("Bearer " + token, "resolution=merge-duplicates", update).enqueue(new retrofit2.Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> r) {
                if (r.isSuccessful() || r.code() == 200 || r.code() == 204) {
                    prefs.edit().putInt("weight", (int)Math.round(weight)).apply();
                    cb.onSuccess();
                } else cb.onError(parseError(r));
            }
            @Override public void onFailure(Call<Void> call, Throwable t) { cb.onError("Сеть: " + t.getMessage()); }
        });
    }
}