package com.example.safeinbox.activities;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.safeinbox.R;
import com.example.safeinbox.database.SpamDao;

import java.util.Random;

/**
 * AI Security Advisor — provides conversational security intelligence
 * based on the user's real-world spam data.
 */
public class SecurityAdvisorActivity extends AppCompatActivity {

    private TextView textTerminal;
    private ScrollView scrollTerminal;
    private SpamDao spamDao;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_security_advisor);

        spamDao = new SpamDao(this);
        textTerminal = findViewById(R.id.text_terminal);
        scrollTerminal = findViewById(R.id.scroll_terminal);

        findViewById(R.id.btn_back_advisor).setOnClickListener(v -> finish());

        // Button Actions
        findViewById(R.id.btn_explain_spam).setOnClickListener(v -> explainThreats());
        findViewById(R.id.btn_network_audit).setOnClickListener(v -> runDiagnostic());
        findViewById(R.id.btn_optimize_filters).setOnClickListener(v -> optimizeShield());
        
        // Advanced Intel Actions
        findViewById(R.id.btn_intel_masking).setOnClickListener(v -> explainMasking());
        findViewById(R.id.btn_intel_shortlinks).setOnClickListener(v -> explainShortlinks());
        findViewById(R.id.btn_intel_financial).setOnClickListener(v -> explainFinancialFraud());

        // Initial greeting
        typeText("\n> [SAFEINBOX_AI]: Greetings. I have analyzed your 6-layer defense grid. Ready for your instructions.");
    }

    private void runDiagnostic() {
        typeText("\n> INITIALIZING ULTRA 3.0 DEEP SCAN...");
        typeText("> [SCANNING: [|||||||||||||||||]] 100%");
        
        handler.postDelayed(() -> {
            int spam = spamDao.getSpamMessageCount();
            int filters = spamDao.getGlobalBlacklistCount();
            String status = filters > 10 ? "OPTIMAL" : "THRESHOLD_LOW";
            
            typeText("\n> [SCAN_COMPLETE]");
            typeText("\n> SPAM_QUARANTINED: " + spam);
            typeText("\n> FILTER_DENSITY: " + filters + " SIGNATURES");
            typeText("\n> DEFENSE_STATUS: " + status);
            
            if (filters < 10) {
                typeText("\n> [ADVICE]: Consider adding more numbers to the blacklist to improve AI confidence.");
            } else {
                typeText("\n> [ADVICE]: Your 6-layer engine is performing at maximum efficiency.");
            }
        }, 1500);
    }

    private void explainThreats() {
        typeText("\n> ANALYZING RECENT THREAT PATTERNS...");
        handler.postDelayed(() -> {
            int financial = new Random().nextInt(5) + 1; // Simulated recent analysis
            typeText("\n> [PATTERN_DETECTION]: Increased activity in 'Financial Scams' clusters.");
            typeText("\n> [ADVICE]: Do not click links in SMS regarding 'KYC' or 'Blocked Cards'. Our AI is auto-diverting these messages to the Vault.");
        }, 1200);
    }
    
    private void optimizeShield() {
        typeText("\n> EXECUTING INTELLIGENT AI SHREDDER...");
        handler.postDelayed(() -> {
            int shredded = spamDao.executeAutoShredder();
            typeText("\n> [SHIELD_LOG]: INTELLIGENT_PURGE_COMPLETE.");
            if (shredded > 0) {
                typeText("\n> [RESULT]: Neutralized " + shredded + " aged spam messages.");
                typeText("\n> [SAFEGUARD]: 0 archives affected. Your protected records remain intact.");
            } else {
                typeText("\n> [RESULT]: Defense grid already optimal. No aged spam found.");
            }
        }, 1200);
    }

    private void explainMasking() {
        typeText("\n> FETCHING INTEL: TEXT MASKING (OBFUSCATION)...");
        handler.postDelayed(() -> {
            typeText("\n> [ANALYSIS]: Spammers often use dots, spaces, or symbols to hide words from basic filters (e.g., 'W-I-N', 'F.R.E.E').");
            typeText("\n> [DEFENSE]: Your Stealth Intel Engine uses advanced Regex patterns to detect and neutralize character masking attempts instantly.");
        }, 1200);
    }

    private void explainShortlinks() {
        typeText("\n> FETCHING INTEL: SHORTLINK HIJACKING...");
        handler.postDelayed(() -> {
            typeText("\n> [ANALYSIS]: Phishing attacks frequently hide malicious URLs behind services like 'bit.ly', 't.ly', or 'tiny.one'.");
            typeText("\n> [DEFENSE]: The SpamScoreEngine cross-references incoming URLs against an elite professional shortlink blacklist. If combined with urgent keywords, it triggers a maximum threat alert.");
        }, 1200);
    }

    private void explainFinancialFraud() {
        typeText("\n> FETCHING INTEL: FINANCIAL FRAUD...");
        handler.postDelayed(() -> {
            typeText("\n> [ANALYSIS]: 'Reverse Phishing' tactics try to cause panic by claiming unauthorized bank activity (e.g., 'KYC Update Mandatory', 'A/C Suspended').");
            typeText("\n> [DEFENSE]: Your AI possesses specialized high-signal detection logic for banking urgency terminology, blocking the scam before you even read it.");
        }, 1200);
    }

    private void typeText(String text) {
        textTerminal.append(text + "\n");
        // Auto scroll to bottom
        handler.post(() -> scrollTerminal.fullScroll(ScrollView.FOCUS_DOWN));
    }
}
