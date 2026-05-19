package com.example.caloriecounter.view;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import android.util.Log;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import com.example.caloriecounter.R;
import com.example.caloriecounter.databinding.ActivityBarcodeScannerBinding;
import com.example.caloriecounter.model.Food;
import com.example.caloriecounter.viewmodel.BarcodeScannerViewModel;
import com.journeyapps.barcodescanner.CaptureManager;

public class BarcodeScannerActivity extends AppCompatActivity {
    private static final int CAMERA_PERMISSION_CODE = 101;
    private ActivityBarcodeScannerBinding binding;
    private BarcodeScannerViewModel vm;
    private CaptureManager capture;
    private String lastScannedBarcode;
    private String userId;
    private String meal;
    private String selectedDate;
    private boolean isScannerInitialized = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBarcodeScannerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        userId = getIntent().getStringExtra("userId");
        meal = getIntent().getStringExtra("meal");
        selectedDate = getIntent().getStringExtra("selected_date");

        if (userId == null || userId.isEmpty()) {
            Toast.makeText(this, "Ошибка: не передан userId", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        vm = new ViewModelProvider(this).get(BarcodeScannerViewModel.class);
        observeViewModel();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        } else {
            initScanner(savedInstanceState);
        }
    }

    private void initScanner(Bundle state) {
        if (isScannerInitialized) return;

        capture = new CaptureManager(this, binding.barcodeScanner);
        capture.initializeFromIntent(getIntent(), state);

        binding.barcodeScanner.decodeContinuous(result -> {
            lastScannedBarcode = result.getText();
            binding.barcodeScanner.pause();
            vm.searchBarcode(lastScannedBarcode);
        });

        binding.barcodeScanner.resume();
        isScannerInitialized = true;
    }

    private void observeViewModel() {
        vm.getIsLoading().observe(this, loading -> {
            binding.progressBar.setVisibility(loading ? android.view.View.VISIBLE : android.view.View.GONE);
        });

        vm.getScannedFood().observe(this, food -> {
            if (food != null) {
                openFoodDetail(food);
            }
        });

        vm.getError().observe(this, msg -> {
            if ("not_found_in_db".equals(msg)) {
                showManualEntryDialog();
            } else {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                if (binding.barcodeScanner != null) binding.barcodeScanner.resume();
            }
        });
    }

    private void showManualEntryDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Продукт не найден в базах")
                .setMessage("Ввести данные вручную?")
                .setPositiveButton("Да", (d, w) -> {
                    Intent i = new Intent(this, FoodDetailActivity.class);
                    i.putExtra("foodId", lastScannedBarcode);
                    i.putExtra("foodName", "");
                    i.putExtra("calories", 0); i.putExtra("protein", 0);
                    i.putExtra("fat", 0); i.putExtra("carbs", 0);
                    i.putExtra("sugar", 0); i.putExtra("fiber", 0);
                    i.putExtra("userId", userId);
                    i.putExtra("meal", meal);
                    i.putExtra("selected_date", selectedDate);
                    i.putExtra("isManualEntry", true);
                    startActivity(i);
                    finish();
                })
                .setNegativeButton("Отмена", (d, w) -> {
                    if (binding.barcodeScanner != null) binding.barcodeScanner.resume();
                })
                .show();
    }

    private void openFoodDetail(Food food) {
        Log.d("BARCODE_DEBUG", "Открытие FoodDetail: userId=" + userId);

        if (userId == null || userId.isEmpty()) {
            Toast.makeText(this, "Ошибка: нет userId", Toast.LENGTH_LONG).show();
            if (binding.barcodeScanner != null) binding.barcodeScanner.resume();
            return;
        }

        Intent i = new Intent(this, FoodDetailActivity.class);
        i.putExtra("userId", userId);
        i.putExtra("foodId", food.id);
        i.putExtra("foodName", food.name);
        i.putExtra("calories", food.calories);
        i.putExtra("protein", food.protein);
        i.putExtra("fat", food.fat);
        i.putExtra("carbs", food.carbs);
        i.putExtra("sugar", food.sugar);
        i.putExtra("fiber", food.fiber);
        i.putExtra("meal", meal);
        i.putExtra("selected_date", selectedDate);

        try {
            startActivity(i);
            finish();
        } catch (Exception e) {
            Log.e("BARCODE_DEBUG", "Crash: " + e.getMessage());
            Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_LONG).show();
            if (binding.barcodeScanner != null) binding.barcodeScanner.resume();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isScannerInitialized && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            initScanner(null);
        }
        else if (capture != null) {
            capture.onResume();
        }
        else if (binding.barcodeScanner != null) {
            binding.barcodeScanner.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (capture != null) capture.onPause();
        else if (binding.barcodeScanner != null) binding.barcodeScanner.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (capture != null) capture.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (capture != null) capture.onSaveInstanceState(outState);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initScanner(null);
        } else {
            Toast.makeText(this, "Разрешение камеры обязательно", Toast.LENGTH_LONG).show();
            finish();
        }
    }
}