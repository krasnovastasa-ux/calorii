package com.example.caloriecounter.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import com.example.caloriecounter.model.AuthResponse;
import com.example.caloriecounter.repository.SupabaseRepository;

public class AuthViewModel extends AndroidViewModel {
    private final SupabaseRepository repository;
    public final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    public final MutableLiveData<Boolean> authSuccess = new MutableLiveData<>();

    public AuthViewModel(Application application) {
        super(application);

        repository = new SupabaseRepository(application);
    }

    public void signUp(String email, String password) {
        repository.signUp(email, password, new SupabaseRepository.SignUpCallback() {
            @Override public void onSuccess(AuthResponse response) {
                authSuccess.postValue(true);
            }
            @Override public void onError(String message) {
                errorMessage.postValue(message);
            }
        });
    }

    public void signIn(String email, String password) {
        repository.signIn(email, password, new SupabaseRepository.SignInCallback() {
            @Override public void onSuccess(AuthResponse response) {
                authSuccess.postValue(true);
            }
            @Override public void onError(String message) {
                errorMessage.postValue(message);
            }
        });
    }
}