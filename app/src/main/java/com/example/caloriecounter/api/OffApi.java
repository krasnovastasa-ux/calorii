package com.example.caloriecounter.api;
import com.example.caloriecounter.model.OffResponse;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface OffApi {
    @GET("api/v2/product/{barcode}.json")
    Call<OffResponse> getProduct(@Path("barcode") String barcode);
}