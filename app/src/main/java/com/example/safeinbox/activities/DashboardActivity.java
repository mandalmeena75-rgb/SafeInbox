package com.example.safeinbox.activities;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.safeinbox.R;
import com.example.safeinbox.database.SpamDao;
import com.example.safeinbox.utils.Constants;
import com.example.safeinbox.utils.SessionManager;
import com.example.safeinbox.utils.TurboExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Master Dashboard — the central command center for SafeInbox.
 * Provides quick navigation to Inbox, Spam Vault, Archive Vault, and Security Insights.
 * Shows live stats on protection status.
 */
public class DashboardActivity extends AppCompatActivity {

    private static final String TAG = "DashboardActivity";

    private SpamDao spamDao;
    private SessionManager sessionManager;
    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService autoNeutralizer = Executors.newSingleThreadScheduledExecutor();

    private TextView statBlocked, statSafe, statFilters, shieldStatus, textSecurityScore;
    private View radarCircle1, radarCircle2, viewAiOrb;

    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        spamDao = new SpamDao(this);
        sessionManager = new SessionManager(this);

        // Bind stat views
        statBlocked = findViewById(R.id.stat_blocked);
        statSafe = findViewById(R.id.stat_safe);
        statFilters = findViewById(R.id.stat_filters);
        shieldStatus = findViewById(R.id.text_shield_status);
        textSecurityScore = findViewById(R.id.text_security_score);
        radarCircle1 = findViewById(R.id.radar_circle_1);
        radarCircle2 = findViewById(R.id.radar_circle_2);
        viewAiOrb = findViewById(R.id.view_ai_orb);

        startRadarAnimation();
        startOrbAnimation();

        // Launch AI Advisor
        viewAiOrb.setOnClickListener(v -> 
                startActivity(new Intent(this, SecurityAdvisorActivity.class)));

        // ═══ Navigation Cards ═══
        findViewById(R.id.card_inbox).setOnClickListener(v ->
                startActivity(new Intent(this, InboxActivity.class)));

        findViewById(R.id.card_spam).setOnClickListener(v ->
                startActivity(new Intent(this, SpamActivity.class)));

        findViewById(R.id.card_archive).setOnClickListener(v ->
                startActivity(new Intent(this, ArchiveActivity.class)));

        findViewById(R.id.card_insights).setOnClickListener(v ->
                startActivity(new Intent(this, AnalyticsActivity.class)));

        // ═══ Logout ═══
        findViewById(R.id.btn_logout).setOnClickListener(v -> {
            sessionManager.logout();
            Toast.makeText(this, "🔒 Logged out", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        startAutoNeutralization();
        
        // --- INDUSTRIAL PERMISSION & INGESTION FLOW ---
        requestAppPermissions();
        checkNotificationAccess();
    }

    private void checkNotificationAccess() {
        if (!com.example.safeinbox.services.SmsNotificationListener.isEnabled(this)) {
            new androidx.appcompat.app.AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("📡 RCS Visibility Required")
                .setMessage("To protect your RCS (Google Messages) and keep your Inbox complete, SafeInbox needs 'Notification Access'.\n\nPlease enable it in the next screen.")
                .setPositiveButton("Enable Now", (d, w) -> {
                    startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                })
                .setNegativeButton("Later", null)
                .show();
        }
    }

    private void requestAppPermissions() {
        String[] permissions = {
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_CONTACTS
        };

        List<String> missingPermissions = new ArrayList<>();
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(p);
            }
        }

        if (!missingPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toArray(new String[0]), Constants.PERMISSION_REQUEST_CODE);
        } else {
            // Already have permissions, check for forensic rescan or first ingestion
            checkAndSyncSystemMessages();
        }
    }

    private void checkAndSyncSystemMessages() {
        android.content.SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        boolean isFirstRun = !prefs.getBoolean("is_first_ingestion_complete", false);
        int lastRescannedVersion = prefs.getInt("last_engine_rescan_version", 0);

        if (isFirstRun || lastRescannedVersion < Constants.CURRENT_ENGINE_VERSION) {
            Toast.makeText(this, "🛡️ Starting Industrial Forensic Audit...", Toast.LENGTH_SHORT).show();
            
            TurboExecutor.getInstance().execute(() -> {
                Log.i(TAG, "Starting Project-Wide Audit & Ingestion...");
                com.example.safeinbox.detection.SpamDetector detector = com.example.safeinbox.detection.SpamDetector.getInstance(this);
                
                // 1. Ingest existing system messages if first run
                if (isFirstRun) {
                    spamDao.ingestSystemMessages(detector);
                    prefs.edit().putBoolean("is_first_ingestion_complete", true).apply();
                }

                // 2. Reclassify existing project messages
                spamDao.reclassifyProjectWide(detector);
                
                prefs.edit().putInt("last_engine_rescan_version", Constants.CURRENT_ENGINE_VERSION).apply();
                Log.i(TAG, "Audit Completed.");
                
                // Refresh UI stats
                mainHandler.post(() -> {
                    Toast.makeText(this, "✅ Forensic Audit Complete. Protection Active.", Toast.LENGTH_SHORT).show();
                    loadLiveStats();
                });
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == Constants.PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int res : grantResults) {
                if (res != PackageManager.PERMISSION_GRANTED) allGranted = false;
            }
            
            if (allGranted) {
                Toast.makeText(this, "✅ Permissions Granted", Toast.LENGTH_SHORT).show();
                checkAndSyncSystemMessages();
            } else {
                Toast.makeText(this, "⚠️ App needs SMS and Contacts permissions to function properly.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadLiveStats();
    }

    /**
     * Load live stats from the database in the background.
     * Called every time the user returns to the dashboard.
     */
    private void loadLiveStats() {
        bgExecutor.execute(() -> {
            try {
                int spamCount = spamDao.getSpamMessageCount();
                int safeCount = spamDao.getSafeMessageCount();
                int filterCount = spamDao.getGlobalBlacklistCount();

                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    statBlocked.setText(String.valueOf(spamCount));
                    statSafe.setText(String.valueOf(safeCount));
                    statFilters.setText(String.valueOf(filterCount));

                    // ═══ Calculate Advanced Security Score ═══
                    // Logic: Start at 85%. Add 1% per filter (max 10%), 
                    // Subtract 2% per 100 spam if no filters active.
                    int baseScore = 85; 
                    int bonus = Math.min(15, filterCount * 2);
                    int finalScore = Math.min(100, baseScore + bonus);
                    textSecurityScore.setText(finalScore + "%");

                    // Update shield status text based on protection level
                    if (finalScore >= 95) {
                        shieldStatus.setText("🛡️ SYSTEM FULLY PROTECTED");
                        shieldStatus.setTextColor(getResources().getColor(R.color.success_green));
                    } else if (finalScore >= 80) {
                        shieldStatus.setText("🛡️ ENHANCED PROTECTION ACTIVE");
                        shieldStatus.setTextColor(getResources().getColor(R.color.success_green));
                    } else {
                        shieldStatus.setText("⚠️ SYSTEM REQUIRES ATTENTION");
                        shieldStatus.setTextColor(getResources().getColor(R.color.error_red));
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "loadLiveStats error", e);
            }
        });
    }

    private void startRadarAnimation() {
        animateCircle(radarCircle1, 0);
        animateCircle(radarCircle2, 1000);
    }

    private void animateCircle(View view, long delay) {
        ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(
                view,
                PropertyValuesHolder.ofFloat("scaleX", 1f, 3f),
                PropertyValuesHolder.ofFloat("scaleY", 1f, 3f),
                PropertyValuesHolder.ofFloat("alpha", 0.6f, 0f)
        );
        animator.setDuration(3000);
        animator.setStartDelay(delay);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.start();
    }

    private void startOrbAnimation() {
        ObjectAnimator orbAnimator = ObjectAnimator.ofPropertyValuesHolder(
                viewAiOrb,
                PropertyValuesHolder.ofFloat("scaleX", 1f, 1.2f),
                PropertyValuesHolder.ofFloat("scaleY", 1f, 1.2f),
                PropertyValuesHolder.ofFloat("alpha", 0.4f, 1.0f)
        );
        orbAnimator.setDuration(1200);
        orbAnimator.setRepeatMode(ValueAnimator.REVERSE);
        orbAnimator.setRepeatCount(ValueAnimator.INFINITE);
        orbAnimator.start();
    }

    /**
     * ULTRA 3.0: High-Performance Auto-Neutralization.
     * Silent background protection every 4 hours.
     */
    private void startAutoNeutralization() {
        autoNeutralizer.scheduleAtFixedRate(() -> {
            try {
                Log.d(TAG, "Executing scheduled Auto-Neutralization...");
                spamDao.executeAutoShredder();
            } catch (Exception e) {
                Log.e(TAG, "Auto-Neutralization failed", e);
            }
        }, 30, 240, TimeUnit.MINUTES); // Start after 30m, then every 4h
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        autoNeutralizer.shutdownNow();
    }
}
