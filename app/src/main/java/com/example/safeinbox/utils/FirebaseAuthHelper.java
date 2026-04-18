package com.example.safeinbox.utils;

import android.app.Activity;
import androidx.annotation.NonNull;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import java.util.concurrent.TimeUnit;

/**
 * FirebaseAuthHelper encapsulates Firebase Phone Authentication logic.
 */
public class FirebaseAuthHelper {
    private FirebaseAuth mAuth;
    private PhoneAuthCallback callback;

    public interface PhoneAuthCallback {
        void onCodeSent(@NonNull String verificationId, @NonNull PhoneAuthProvider.ForceResendingToken token);
        void onVerificationCompleted(@NonNull PhoneAuthCredential credential);
        void onVerificationFailed(@NonNull FirebaseException e);
    }

    public FirebaseAuthHelper() {
        mAuth = FirebaseAuth.getInstance();
    }

    public void sendVerificationCode(Activity activity, String phoneNumber, PhoneAuthCallback callback) {
        this.callback = callback;

        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(mAuth)
                .setPhoneNumber(phoneNumber)       // Phone number to verify
                .setTimeout(60L, TimeUnit.SECONDS) // Timeout and unit
                .setActivity(activity)             // Activity (for callback binding)
                .setCallbacks(mCallbacks)          // OnVerificationStateChangedCallbacks
                .build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private final PhoneAuthProvider.OnVerificationStateChangedCallbacks mCallbacks = 
            new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

        @Override
        public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
            if (callback != null) callback.onVerificationCompleted(credential);
        }

        @Override
        public void onVerificationFailed(@NonNull FirebaseException e) {
            if (callback != null) callback.onVerificationFailed(e);
        }

        @Override
        public void onCodeSent(@NonNull String verificationId,
                @NonNull PhoneAuthProvider.ForceResendingToken token) {
            if (callback != null) callback.onCodeSent(verificationId, token);
        }
    };

    public void signInWithCredential(PhoneAuthCredential credential, OnSignInCallback signInCallback) {
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        signInCallback.onSuccess();
                    } else {
                        signInCallback.onFailure(task.getException() != null ? 
                                task.getException().getMessage() : "Authentication failed");
                    }
                });
    }

    public interface OnSignInCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }
}
