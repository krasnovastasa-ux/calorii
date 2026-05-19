package com.example.caloriecounter.viewmodel;
import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import com.example.caloriecounter.model.Food;
import com.example.caloriecounter.repository.BarcodeRepository;

public class BarcodeScannerViewModel extends AndroidViewModel {
    private final BarcodeRepository repo;
    private final MutableLiveData<Food> scannedFood = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    public BarcodeScannerViewModel(Application application) {
        super(application);
        repo = new BarcodeRepository(application);
    }

    public MutableLiveData<Food> getScannedFood() { return scannedFood; }
    public MutableLiveData<String> getError() { return error; }
    public MutableLiveData<Boolean> getIsLoading() { return isLoading; }

    public void searchBarcode(String barcode) {
        if (barcode == null || barcode.isEmpty()) return;
        isLoading.postValue(true);
        repo.fetchByBarcode(barcode, new BarcodeRepository.BarcodeCallback() {
            @Override public void onSuccess(Food food) { isLoading.postValue(false); scannedFood.postValue(food); }
            @Override public void onError(String msg) { isLoading.postValue(false); error.postValue(msg); }
        });
    }
}