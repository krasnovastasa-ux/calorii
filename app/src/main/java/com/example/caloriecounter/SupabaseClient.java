package com.example.caloriecounter;

import android.util.Log;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import java.util.concurrent.TimeUnit;

public class SupabaseClient {
    private static final String SUPABASE_URL = "https://ktwwknqtjloudktzvlwl.supabase.co/";
    private static final String SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imt0d3drbnF0amxvdWRrdHp2bHdsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTMzODMyMTgsImV4cCI6MjEwODk1OTIxOH0.FdEN1IbKqXALkQqrk9IKC5ZKZnAWvSEWOjDvvoeNThk";

    private static SupabaseApi api;

    public static SupabaseApi getApi() {
        if (api == null) {

            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .addInterceptor(logging)
                    .addInterceptor(chain -> {
                        okhttp3.Request original = chain.request();
                        okhttp3.Request request = original.newBuilder()
                                .header("apikey", SUPABASE_ANON_KEY)
                                .header("Content-Type", "application/json")
                                .method(original.method(), original.body())
                                .build();
                        return chain.proceed(request);
                    })
                    .build();

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(SUPABASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            api = retrofit.create(SupabaseApi.class);
            Log.d("SUPABASE_CLIENT", " Retrofit инициализирован с Anon Key");
        }
        return api;
    }
}