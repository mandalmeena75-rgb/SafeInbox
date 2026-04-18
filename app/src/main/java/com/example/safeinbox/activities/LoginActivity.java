package com.example.safeinbox.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.safeinbox.databinding.ActivityLoginBinding;
import com.example.safeinbox.utils.FirebaseAuthHelper;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;

/**
 * LoginActivity handles phone number entry and initiates Firebase Phone Authentication.
 */
public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private FirebaseAuthHelper authHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authHelper = new FirebaseAuthHelper();

        binding.btnSendOtp.setOnClickListener(v -> {
            String phone = binding.etPhone.getText().toString().trim();
            if (validatePhone(phone)) {
                sendOtp("+" + phone);
            }
        });
    }

    private boolean validatePhone(String phone) {
        if (phone.isEmpty()) {
            binding.tilPhone.setError("Phone number required");
            return false;
        }
        if (phone.length() < 10) {
            binding.tilPhone.setError("Invalid phone number");
            return false;
        }
        binding.tilPhone.setError(null);
        return true;
    }

    private void sendOtp(String fullPhone) {
        binding.btnSendOtp.setEnabled(false);
        binding.btnSendOtp.setText("Sending...");

        authHelper.sendVerificationCode(this, fullPhone, new FirebaseAuthHelper.PhoneAuthCallback() {
            @Override
            public void onCodeSent(@NonNull String verificationId, @NonNull PhoneAuthProvider.ForceResendingToken token) {
                Intent intent = new Intent(LoginActivity.this, OTPActivity.class);
                intent.putExtra("verificationId", verificationId);
                intent.putExtra("phone", fullPhone);
                startActivity(intent);
                binding.btnSendOtp.setEnabled(true);
                binding.btnSendOtp.setText("Send OTP");
            }

            @Override
            public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                // Auto-verification handled in OTPActivity or if device supports it
            }

            @Override
            public void onVerificationFailed(@NonNull FirebaseException e) {
                Toast.makeText(LoginActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                binding.btnSendOtp.setEnabled(true);
                binding.btnSendOtp.setText("Send OTP");
            }
        });
    }
}
