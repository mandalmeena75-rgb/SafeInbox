package com.example.safeinbox.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.safeinbox.databinding.ActivitySplashBinding;
import com.example.safeinbox.utils.BiometricHelper;
import com.example.safeinbox.utils.SessionManager;

/**
 * SplashActivity handles initial routing based on session status and security settings.
 */
public class SplashActivity extends AppCompatActivity {

    private ActivitySplashBinding binding;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = new SessionManager(this);

        // Simulated delay for premium feel
        new Handler(Looper.getMainLooper()).postDelayed(this::checkSessionAndNavigate, 2000);
    }

    private void checkSessionAndNavigate() {
        if (sessionManager.isLoggedIn()) {
            if (sessionManager.isBiometricEnabled() && BiometricHelper.isBiometricAvailable(this)) {
                requestBiometricAuth();
            } else {
                navigateToMain();
            }
        } else {
            navigateToLogin();
        }
    }

    private void requestBiometricAuth() {
        BiometricHelper.showBiometricPrompt(this, new BiometricHelper.BiometricCallback() {
            @Override
            public void onAuthenticationSuccess() {
                navigateToMain();
            }

            @Override
            public void onAuthenticationError(int errorCode, String errString) {
                Toast.makeText(SplashActivity.this, "Authentication error: " + errString, Toast.LENGTH_SHORT).show();
                // Optionally allow retry or fallback to Login
                navigateToLogin(); 
            }

            @Override
            public void onAuthenticationFailed() {
                Toast.makeText(SplashActivity.this, "Authentication failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void navigateToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void navigateToLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
