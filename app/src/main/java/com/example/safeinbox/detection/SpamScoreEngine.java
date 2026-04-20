package com.example.safeinbox.detection;

import android.content.Context;
import android.util.Log;
import com.example.safeinbox.database.SpamDao;
import com.example.safeinbox.models.ClassificationResult;
import com.example.safeinbox.utils.ContactUtils;
import com.example.safeinbox.utils.PrincipalEntityResolver;

/**
 * Robust Multi-Layer Classification Engine for SafeInbox.
 * Orchestrates Preprocessing, OTP Detection, Service Identification, and Dynamic Scoring.
 */
public class SpamScoreEngine {
    private static final String TAG = "SpamScoreEngine";

    private final KeywordFilter keywordFilter;
    private final LinkScanner linkScanner;
    private final SpamDao spamDao;
    private final Context context;

    public SpamScoreEngine(Context context, MLClassifier ignored) {
        this.context = context;
        this.keywordFilter = new KeywordFilter();
        this.linkScanner = new LinkScanner();
        this.spamDao = new SpamDao(context);
    }

    public ClassificationResult classify(String sender, String body, String[] ignoredWords) {
        ClassificationResult result = new ClassificationResult();
        
        // --- STEP 1: PREPROCESSING ---
        if (body == null || body.trim().isEmpty()) {
            result.status = ClassificationResult.Status.SAFE;
            return result;
        }
        String normalizedBody = body.toLowerCase().replaceAll("\\s+", " ").trim();

        // --- STEP 2: OTP DETECTION (HIGH PRIORITY) ---
        if (keywordFilter.isOTP(normalizedBody)) {
            result.status = ClassificationResult.Status.OTP;
            result.totalScore = 0;
            result.reason = "🔒 Transactional: One-Time Password (OTP) detected";
            return result;
        }

        // --- STEP 3: INITIAL LAYER (SERVICE / CONTACT) ---
        boolean isAlphanumeric = PrincipalEntityResolver.isAlphanumericSender(sender);
        boolean isServiceKeywords = keywordFilter.isServiceKeywordPresent(normalizedBody);
        boolean isInContacts = ContactUtils.getContactName(context, sender) != null;

        // --- STEP 4: SPAM SCORING SYSTEM ---
        
        // 1. Keyword Scoring
        result.keywordScore = keywordFilter.getSpamKeywordScore(normalizedBody); // +40
        result.urgencyScore = keywordFilter.getUrgencyScore(normalizedBody);   // +20
        int threatScore = keywordFilter.getThreatScore(normalizedBody);         // +30
        
        // 2. Link Safety Analysis
        LinkScanner.LinkRiskResult linkResult = linkScanner.scan(normalizedBody);
        result.linkScore = linkResult.score; // Dynamic (+20, +30, +40, or -20)
        result.isRiskyLink = linkResult.score >= 30;

        // 3. Trust Weights
        result.contactWeight = isInContacts ? -30 : 0;
        
        // 4. History Signal (Internal Reputation)
        int spamHits = spamDao.getSenderSpamHits(sender);
        result.historyScore = Math.min(spamHits * 10, 30); // Dynamic reputation risk

        // --- STEP 5: CALCULATION & OVERRIDES ---
        
        // Raw score calculation based on strict user requirements
        int rawScore = result.keywordScore + result.urgencyScore + threatScore + result.linkScore + result.contactWeight + result.historyScore;
        result.totalScore = Math.max(0, Math.min(100, rawScore));

        // Start with SAFE classification
        result.status = ClassificationResult.Status.SAFE;
        
        // Apply Base Thresholds
        if (result.totalScore > 70) {
            result.status = ClassificationResult.Status.SPAM;
        } else if (result.totalScore >= 40) {
            result.status = ClassificationResult.Status.SUSPICIOUS;
        }

        // --- LAYER 6: OVERRIDE RULES (VERY IMPORTANT) ---

        // RULE: Service Identification
        if (isAlphanumeric || isServiceKeywords) {
            // Service/Government messages are SAFE unless score is extremely high
            if (result.totalScore < 80) {
                result.status = ClassificationResult.Status.SERVICE;
                result.reason = "📡 Service Provider or Verified Government entity";
            } else {
                result.status = ClassificationResult.Status.SPAM;
                result.reason = "🚨 Service spoofing or Malicious provider detected";
            }
        }

        // RULE: Contact Protection
        if (isInContacts && result.status == ClassificationResult.Status.SPAM) {
            // NEVER directly mark a contact as SPAM
            result.status = ClassificationResult.Status.SUSPICIOUS;
            result.reason = "⚠️ Suspicious content from a known contact";
        }

        // Final Reason Assignment if not set by overrides
        if (result.reason == null || result.reason.isEmpty()) {
            if (result.status == ClassificationResult.Status.SPAM) {
                result.reason = "🚫 High-risk patterns: " + (result.isRiskyLink ? "Risky Link, " : "") + (result.keywordScore > 0 ? "Spam Keywords" : "Threat/Volatility");
            } else if (result.status == ClassificationResult.Status.SUSPICIOUS) {
                result.reason = "⚠️ " + linkResult.reason;
            } else {
                result.reason = isInContacts ? "✅ Verified sender in contacts" : "✅ Clear of threat patterns";
            }
        }

        Log.d(TAG, "FINAL CLASSIFICATION: " + result.status + " | Score: " + result.totalScore + " | Sender: " + sender);
        return result;
    }
}
