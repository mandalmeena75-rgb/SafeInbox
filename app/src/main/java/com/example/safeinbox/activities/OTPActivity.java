package com.example.safeinbox.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.safeinbox.databinding.ActivityOtpBinding;
import com.example.safeinbox.utils.BiometricHelper;
import com.example.safeinbox.utils.FirebaseAuthHelper;
import com.example.safeinbox.utils.SessionManager;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;

import java.util.Locale;

/**
 * OTPActivity handles the verification of the 6-digit code.
 * Includes a resend timer and automatic session initialization.
 */
public class OTPActivity extends AppCompatActivity {

    private ActivityOtpBinding binding;
    private FirebaseAuthHelper authHelper;
    private SessionManager sessionManager;
    private String verificationId;
    private String phoneNumber;
    private CountDownTimer countDownTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOtpBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authHelper = new FirebaseAuthHelper();
        sessionManager = new SessionManager(this);

        verificationId = getIntent().getStringExtra("verificationId");
        phoneNumber = getIntent().getStringExtra("phone");

        startResendTimer();

        binding.btnVerifyOtp.setOnClickListener(v -> {
            String code = binding.etOtp.getText().toString().trim();
            if (code.length() == 6) {
                verifyCode(code);
            } else {
                Toast.makeText(this, "Enter 6-digit code", Toast.LENGTH_SHORT).show();
            }
        });

        binding.btnResend.setOnClickListener(v -> {
            // In a real app, call authHelper.sendVerificationCode again
            Toast.makeText(this, "Resending code to " + phoneNumber, Toast.LENGTH_SHORT).show();
            startResendTimer();
        });
    }

    private void startResendTimer() {
        binding.btnResend.setEnabled(false);
        countDownTimer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                binding.txtTimer.setText(String.format(Locale.getDefault(), "Resend in %ds", millisUntilFinished / 1000));
            }

            @Override
            public void onFinish() {
                binding.txtTimer.setText("You can resend now");
                binding.btnResend.setEnabled(true);
            }
        }.start();
    }

    private void verifyCode(String code) {
        binding.btnVerifyOtp.setEnabled(false);
        binding.btnVerifyOtp.setText("Verifying...");

        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
        authHelper.signInWithCredential(credential, new FirebaseAuthHelper.OnSignInCallback() {
            @Override
            public void onSuccess() {
                completeAuth();
            }

            @Override
            public void onFailure(String errorMessage) {
                Toast.makeText(OTPActivity.this, "Verification failed: " + errorMessage, Toast.LENGTH_LONG).show();
                binding.btnVerifyOtp.setEnabled(true);
                binding.btnVerifyOtp.setText("Verify Now");
            }
        });
    }

    private void completeAuth() {
        sessionManager.setLogin(true, phoneNumber);
        
        // Check if biometric is available to offer it as a setting later
        if (BiometricHelper.isBiometricAvailable(this)) {
            sessionManager.setBiometricEnabled(true);
            Toast.makeText(this, "Biometric security enabled", Toast.LENGTH_SHORT).show();
        }

        startActivity(new Intent(this, MainActivity.class));
        finishAffinity(); // Clear auth stack
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) countDownTimer.cancel();
    }
}
