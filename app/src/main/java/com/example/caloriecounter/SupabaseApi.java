package com.example.caloriecounter;

import com.example.caloriecounter.model.*;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.*;

public interface SupabaseApi {

    // авторизация
    @POST("auth/v1/token?grant_type=password")
    Call<AuthResponse> signIn(@Body Map<String, String> credentials);

    @POST("auth/v1/signup")
    Call<AuthResponse> signUp(@Body Map<String, String> credentials);

    // профтль
    @PATCH("rest/v1/profiles")
    Call<Void> updateProfile(@Header("Authorization") String token, @Query("user_id") String userId, @Body ProfileData profile);

    @GET("rest/v1/profiles")
    Call<List<ProfileData>> getProfiles(@Header("Authorization") String token, @Query("user_id") String userId);


    @POST("rest/v1/profiles")
    Call<Void> createProfile(
            @Header("Authorization") String token,
            @Header("Prefer") String prefer,
            @Body ProfileData profile
    );

    @GET("rest/v1/food_logs")
    Call<List<FoodLog>> getLogs(@Header("Authorization") String token, @Query("user_id") String userId);

    @POST("rest/v1/food_logs")
    Call<Void> addLog(@Header("Authorization") String token, @Header("Prefer") String prefer, @Body FoodLog log);

    @DELETE("rest/v1/food_logs")
    Call<Void> deleteLog(@Header("Authorization") String token, @Query("id") String logId);

    //  продукты и избранное
    @GET("rest/v1/foods")
    Call<List<Food>> getFoods();

    @GET("rest/v1/favorites")
    Call<List<Food>> getFavorites(@Header("Authorization") String token, @Query("user_id") String userId);

    @POST("rest/v1/favorites")
    Call<Void> addFavorite(@Header("Authorization") String token, @Header("Prefer") String prefer, @Body Map<String, Object> body);

    @DELETE("rest/v1/favorites")
    Call<Void> removeFavorite(@Header("Authorization") String token, @Query("user_id") String userId, @Query("food_id") String foodId);

    // рецепты
    @GET("rest/v1/recipes")
    Call<List<Recipe>> getRecipes(@Query("select") String select);

    @GET("rest/v1/recipes")
    Call<List<Recipe>> getRecipeById(@Query("id") String id);

    @GET("rest/v1/recipe_foods")
    Call<List<Recipe.RecipeFood>> getRecipeFoods(@Query("recipe_id") String recipeId);

    // мои рецепты
    @GET("rest/v1/user_recipes")
    Call<List<UserRecipe>> getUserRecipes(@Header("Authorization") String token, @Query("user_id") String userId);

    @POST("rest/v1/user_recipes")
    Call<List<UserRecipe>> createUserRecipe(@Header("Authorization") String token, @Header("Prefer") String prefer, @Body UserRecipe recipe);

    //
    @GET("rest/v1/user_recipes")
    Call<List<UserRecipe>> getUserRecipeById(@Header("Authorization") String token, @Query("id") String id);

    @DELETE("rest/v1/user_recipes")
    Call<Void> deleteUserRecipe(@Header("Authorization") String token, @Query("id") String recipeId);

    @POST("rest/v1/user_recipe_ingredients")
    Call<UserRecipeIngredient> createUserRecipeIngredient(@Header("Authorization") String token, @Header("Prefer") String prefer, @Body UserRecipeIngredient ing);

    @GET("rest/v1/user_recipe_ingredients")
    Call<List<Recipe.RecipeFood>> getUserRecipeIngredients(@Header("Authorization") String token, @Query("recipe_id") String recipeId);


    @POST("rest/v1/water_logs")
    Call<Void> addWaterLog(@Header("Authorization") String auth, @Body Object body);

    @DELETE("rest/v1/water_logs")
    Call<Void> deleteWaterLog(@Header("Authorization") String auth, @Query("id") String id);

    @GET("rest/v1/water_logs")
    Call<List<FoodLog>> fetchWaterLogs(@Header("Authorization") String auth, @Query("user_id") String userId);

    @GET("rest/v1/water_logs")
    Call<List<FoodLog>> fetchWaterLogs(
            @Header("Authorization") String auth,
            @Query("user_id") String userId,
            @Query("log_date") String date
    );


    // диеты
    @GET("rest/v1/diets")
    Call<List<Diet>> getDiets();
    @GET("rest/v1/diets")
    Call<List<Diet>> getDietById(@Query("id") String id);
    @GET("rest/v1/diets")
    Call<List<Diet>> getDietsByGoal(@Query("goal") String goalFilter);


    // вес
    @GET("rest/v1/weight_logs")
    Call<List<WeightLog>> getWeightLogs(
            @Header("Authorization") String token,
            @Query("user_id") String userIdFilter,
            @Query("log_date") String dateFilter
    );

    @POST("rest/v1/weight_logs?on_conflict=user_id,log_date")
    Call<Void> addWeightLog(
            @Header("Authorization") String token,
            @Header("Prefer") String prefer,
            @Body WeightLog log);

    @DELETE("rest/v1/weight_logs")
    Call<Void> deleteWeightLog(
            @Header("Authorization") String token,
            @Query("id") String logId);


    @PATCH("rest/v1/weight_logs")
    Call<Void> updateWeightLog(
            @Header("Authorization") String token,
            @Query("id.eq") String id,
            @Body Map<String, Object> updates
    );
}