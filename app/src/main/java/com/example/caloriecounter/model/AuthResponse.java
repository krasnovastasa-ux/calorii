package com.example.caloriecounter.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class AuthResponse {
    @SerializedName("access_token") public String accessToken;
    @SerializedName("token_type") public String tokenType;
    @SerializedName("expires_in") public int expiresIn;
    @SerializedName("expires_at") public long expiresAt;
    @SerializedName("refresh_token") public String refreshToken;
    public User user;

    public static class User {
        public String id;
        public String email;
        public String aud;
        public String role;
        @SerializedName("email_confirmed_at") public String emailConfirmedAt;
        @SerializedName("confirmed_at") public String confirmedAt;
        @SerializedName("last_sign_in_at") public String lastSignInAt;
        @SerializedName("app_metadata") public AppMetadata appMetadata;
        @SerializedName("user_metadata") public UserMetadata userMetadata;
        public List<Identity> identities;
        @SerializedName("created_at") public String createdAt;
        @SerializedName("updated_at") public String updatedAt;
        @SerializedName("is_anonymous") public boolean isAnonymous;
    }
    public static class AppMetadata { public String provider; public List<String> providers; }
    public static class UserMetadata { public String email; @SerializedName("email_verified") public boolean emailVerified; @SerializedName("phone_verified") public boolean phoneVerified; public String sub; }
    public static class Identity {
        @SerializedName("identity_id") public String identityId;
        public String id;
        @SerializedName("user_id") public String userId;
        @SerializedName("identity_data") public IdentityData identityData;
        public String provider;
        @SerializedName("last_sign_in_at") public String lastSignInAt;
        @SerializedName("created_at") public String createdAt;
        @SerializedName("updated_at") public String updatedAt;
        public String email;
    }
    public static class IdentityData { public String email; @SerializedName("email_verified") public boolean emailVerified; @SerializedName("phone_verified") public boolean phoneVerified; public String sub; }
}