package com.example.safeinbox.detection;

import android.content.Context;
import android.util.Log;
import com.example.safeinbox.database.SpamDao;
import com.example.safeinbox.models.ClassificationResult;
import com.example.safeinbox.utils.ContactUtils;
import com.example.safeinbox.utils.PrincipalEntityResolver;

/**
 * Production-Grade 6-Level Classification Engine for SafeInbox.
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
        
        // --- STEP 0: PREPARATION ---
        if (body == null || body.trim().isEmpty()) {
            result.status = ClassificationResult.Status.SAFE;
            return result;
        }
        String normalizedBody = body.toLowerCase().replaceAll("\\s+", " ").trim();

        // Gather sub-signals
        boolean isAlphanumeric = PrincipalEntityResolver.isAlphanumericSender(sender);
        boolean isServiceKeywords = keywordFilter.isServiceMessage(normalizedBody);
        boolean isInContacts = ContactUtils.getContactName(context, sender) != null;
        LinkScanner.LinkRiskResult linkResult = linkScanner.scan(normalizedBody);
        KeywordFilter.KeywordAnalysisResult keywordResult = keywordFilter.analyzeText(normalizedBody);

        // Populate forensic metadata
        result.keywordScore = keywordResult.spamScore;
        result.urgencyScore = keywordResult.urgencyScore;
        result.threatScore = keywordResult.threatScore;

        // --- STEP 1: HARD PHISHING OVERRIDES (Highest Priority) ---
        // 🔴 1) Phishing SMS are downgraded due to “contact trust” (FIXED)
        
        // Brand Mismatch
        if (linkResult.isBrandMismatch) {
            result.status = ClassificationResult.Status.SPAM;
            result.reason = "🛡️ Phishing: Brand Spoofing detected (Mismatch URL)";
            result.totalScore = 100;
            return result;
        }

        // Bank Phishing Pattern: (bank/acc/kyc) + (verify/update/login) + (link)
        boolean isBankPhishingCombo = keywordResult.hasBankWords && keywordResult.hasActionWords && linkResult.hasLink;
        
        // Threat Pattern: (blocked/suspended) + (sim/acc/kyc) + (link/action)
        boolean isThreatPhishingCombo = keywordResult.hasThreatWords && (linkResult.hasLink || keywordResult.hasActionWords);

        // Prize/Amazon Phishing: (win/prize) + (link/action)
        boolean isPrizePhishingCombo = keywordResult.hasPrizeWords && (linkResult.hasLink || keywordResult.hasActionWords);

        if (isBankPhishingCombo || isThreatPhishingCombo || isPrizePhishingCombo || keywordResult.hasSuspiciousKeywords) {
            result.status = ClassificationResult.Status.SPAM;
            result.reason = "🛡️ Phishing Pattern: High-risk combination detected";
            result.totalScore = 100;
            return result;
        }

        // --- STEP 2: SAFE OVERRIDES ---
        // 🟢 2) OTP Pattern
        if (keywordFilter.isOTP(normalizedBody)) {
            result.status = ClassificationResult.Status.SAFE;
            result.totalScore = 0;
            result.reason = "✅ OTP/Transactional: Verified security code";
            return result;
        }

        // Service Pattern (Service sender/keys AND no spam keywords)
        if ((isAlphanumeric || isServiceKeywords) && !keywordResult.hasPrizeWords && !isBankPhishingCombo && !isThreatPhishingCombo) {
            result.status = ClassificationResult.Status.SAFE;
            result.totalScore = 0;
            result.reason = "📡 Service Message: Verified broadcaster";
            return result;
        }

        // --- STEP 3: SCORING (ONLY IF NO OVERRIDE) ---
        // 🟡 3) Weights as specified
        int totalScore = 0;

        // Keywords
        totalScore += keywordResult.spamScore;    // Prize/Lottery -> +40
        totalScore += keywordResult.urgencyScore; // Urgency -> +20
        totalScore += keywordResult.threatScore;  // Threat -> +30
        
        // Bonus for Financial context (Bank/Account words) to ensure they cross threshold
        if (keywordResult.hasBankWords) {
            totalScore += 20;
        }

        // Link Analysis
        if (linkResult.hasLink) {
            totalScore += 20; // Link present -> +20
            if (linkResult.isShortened) totalScore += 40; // Short link -> +40
            if (linkResult.isSuspiciousTLD) totalScore += 30; // Suspicious TLD -> +30
            if (linkResult.isTrustedDomain) totalScore -= 20; // Trusted domain -> -20
        }

        // Reputation Balance
        // 🧠 9) reputationScore = spam_count - safe_count (±20 max)
        int spamHits = spamDao.getSenderSpamHits(sender);
        int safeHits = spamDao.getSenderHamHits(sender);
        int reputationScore = spamHits - safeHits;
        reputationScore = Math.max(-20, Math.min(20, reputationScore)); 
        totalScore += reputationScore;

        // Contact Rule
        // Contact deduction -> -10 (max)
        if (isInContacts) {
            totalScore -= 10;
        }

        result.totalScore = Math.max(0, totalScore);

        // --- STEP 4: FINAL CLASSIFICATION ---
        // ⚫ 7) Thresholds
        if (result.totalScore < 40) {
            result.status = ClassificationResult.Status.SAFE;
            result.reason = "✅ Safe-Range Content (Score: " + result.totalScore + ")";
        } else if (result.totalScore >= 40 && result.totalScore <= 70) {
            result.status = ClassificationResult.Status.SUSPICIOUS;
            result.reason = "⚠️ Suspicious indicators present (Score: " + result.totalScore + ")";
        } else {
            // score > 70
            // 🟠 4) Contact Rule (FIX)
            if (isInContacts) {
                // contact cannot downgrade hard risk (Phishing was handled in Step 1)
                // for other high scores from contacts, downgrade to SUSPICIOUS
                result.status = ClassificationResult.Status.SUSPICIOUS;
                result.reason = "⚠️ High score from known contact (Shielded from Spam Vault)";
            } else {
                result.status = ClassificationResult.Status.SPAM;
                result.reason = "🚫 High-risk spam patterns (Score: " + result.totalScore + ")";
            }
        }

        Log.d(TAG, "PRODUCTION VERDICT: " + result.status + " | Total Score: " + result.totalScore + " | Sender: " + sender);
        return result;
    }
}
