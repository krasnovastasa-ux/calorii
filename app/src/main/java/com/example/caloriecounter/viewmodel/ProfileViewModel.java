package com.example.caloriecounter.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.caloriecounter.model.ProfileData;
import com.example.caloriecounter.repository.SupabaseRepository;

public class ProfileViewModel extends AndroidViewModel {
    private final SupabaseRepository repo;

    private final MutableLiveData<ProfileData> profileData = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> profileSaved = new MutableLiveData<>();
    public static MutableLiveData<Long> profileUpdatedSignal = new MutableLiveData<>();

    public ProfileViewModel(Application application) {
        super(application);
        repo = new SupabaseRepository(application);
    }

    public LiveData<ProfileData> getProfileData() { return profileData; }
    public LiveData<String> getErrorMessage() { return error; }
    public LiveData<Boolean> getProfileSaved() { return profileSaved; }

    public void loadProfile(String userId) {
        repo.fetchProfile(new SupabaseRepository.ProfileLoadedCallback() {
            @Override public void onProfileLoaded(ProfileData p) { profileData.postValue(p); }
            @Override public void onError(String m) { error.postValue(m); }
        });
    }

    public void saveProfile(String userId, String email, String name, int height, int weight, int age, String gender, String goal, String lifestyle) {
        ProfileData data = new ProfileData();
        data.id = userId;
        data.userId = userId;
        data.email = email;
        data.name = name;
        data.height = height;
        data.weight = weight;
        data.age = age;
        data.gender = gender;
        data.goal = goal;
        data.lifestyle = lifestyle;

        repo.updateProfile(data, new SupabaseRepository.VoidCallback() {
            @Override public void onSuccess() {
                profileSaved.postValue(true);
                profileUpdatedSignal.postValue(System.currentTimeMillis());
            }
            @Override public void onError(String m) {
                error.postValue(m);
            }
        });
    }
}