package com.example.safeinbox.detection;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class KeywordFilter {

    private static final Pattern OTP_PATTERN = Pattern.compile("(\\b\\d{4,8}\\b)", Pattern.CASE_INSENSITIVE);

    // B) OTP / Security (SAFE)
    public static final Set<String> OTP_KEYWORDS = new HashSet<>(Arrays.asList(
            "otp", "one time password", "do not share", "verification code", "valid for", "expires", "secure", "login code", "vcode"
    ));

    // A) Bank / Financial (+High)
    public static final Set<String> BANK_KEYWORDS = new HashSet<>(Arrays.asList(
            "account", "bank", "debit", "credit", "upi", "kyc", "verify", "update", "login", "password", 
            "pin", "otp misuse", "transaction", "refund", "charge", "limit", "freeze", "blocked", "reset", "credentials", "netbanking"
    ));

    // C) Telecom / Service (SAFE)
    public static final Set<String> SERVICE_KEYWORDS = new HashSet<>(Arrays.asList(
            "airtel", "jio", "vi", "recharge", "validity", "balance", "data pack", "sms pack", "plan", "expiry", "call cost", "uidai", "irctc"
    ));

    // D) Prize / Lottery (SPAM)
    public static final Set<String> PRIZE_KEYWORDS = new HashSet<>(Arrays.asList(
            "win", "won", "winner", "prize", "lottery", "jackpot", "reward", "giveaway", "gift", "voucher", "coupon", "redeem", "claim", "hurray", "hurry",
            "rupees", "cash", "amount", "credited", "bonus"
    ));

    // E) Urgency (RISK)
    public static final Set<String> URGENCY_KEYWORDS = new HashSet<>(Arrays.asList(
            "now", "urgent", "immediately", "hurry", "act now", "last chance", "today", "final notice", "asap"
    ));

    // F) Phishing Actions (RISK)
    public static final Set<String> ACTION_TRIGGERS = new HashSet<>(Arrays.asList(
            "click", "click here", "tap", "open link", "verify here", "login here", "submit", "update now", "access", "claim now", "claimed", "visit",
            "enroll", "register", "apply", "download"
    ));

    // G) Suspicious Keywords (RISK)
    public static final Set<String> SUSPICIOUS_KEYWORDS = new HashSet<>(Arrays.asList(
            "free-money", "secure-login", "verify-account", "update-now"
    ));

    // H) Trusted Keywords (SAFE)
    public static final Set<String> TRUSTED_KEYWORDS = new HashSet<>(Arrays.asList(
            "google.com", "youtube.com", "amazon.in", "uidai.gov.in", "irctc.co.in"
    ));

    public static class KeywordAnalysisResult {
        public int spamScore = 0;
        public int urgencyScore = 0;
        public int threatScore = 0;
        public int overallScore = 0;

        // Pattern Flags for Combination Detection
        public boolean hasBankWords = false;
        public boolean hasActionWords = false;
        public boolean hasPrizeWords = false;
        public boolean hasUrgencyWords = false;
        public boolean hasSuspiciousKeywords = false;
        public boolean hasThreatWords = false; // Combination of Bank/Blocked etc.
    }

    public KeywordAnalysisResult analyzeText(String body) {
        KeywordAnalysisResult result = new KeywordAnalysisResult();
        if (body == null) return result;
        String lower = body.toLowerCase();

        // 1. Scoring (Targeting exactly requested weights)
        // Strong spam/Prize words -> +40
        int prizeHits = countMatches(lower, PRIZE_KEYWORDS);
        result.spamScore = prizeHits * 40;
        result.hasPrizeWords = prizeHits > 0;

        // Urgency -> +20
        int urgencyHits = countMatches(lower, URGENCY_KEYWORDS);
        result.urgencyScore = urgencyHits * 20;
        result.hasUrgencyWords = urgencyHits > 0;

        // Phishing Actions -> +20 (used as Link/Trigger weight in engine)
        int actionHits = countMatches(lower, ACTION_TRIGGERS);
        result.hasActionWords = actionHits > 0;

        // 2. Combo Detection Flags
        int bankHits = countMatches(lower, BANK_KEYWORDS);
        result.hasBankWords = bankHits > 0;

        int suspHits = countMatches(lower, SUSPICIOUS_KEYWORDS);
        result.hasSuspiciousKeywords = suspHits > 0;

        // Threat Keywords (Bank + Blocked/Verify etc) -> +30
        // We define a broad "Threat" category for scoring
        result.hasThreatWords = (lower.contains("blocked") || lower.contains("suspended") || lower.contains("disabled") || lower.contains("frozen") || lower.contains("kyc")) 
                                && (result.hasBankWords || lower.contains("sim") || lower.contains("account"));
        
        if (result.hasThreatWords) {
            result.threatScore = 30;
        }

        // 3. Final Overall Keyword Score (Capped logic is now handled in Engine)
        result.overallScore = result.spamScore + result.urgencyScore + result.threatScore;

        return result;
    }

    private int countMatches(String body, Set<String> dictionary) {
        int count = 0;
        for (String word : dictionary) {
            if (body.contains(word)) {
                // Heuristic: check if it's a standalone word or surrounded by delimiters
                // This is a simple implementation; more robust tokenization could be used.
                count++;
            }
        }
        return count;
    }

    public boolean isOTP(String body) {
        if (body == null) return false;
        String lower = body.toLowerCase();
        
        // Context Check: "Your OTP is" (Safe) vs "Share OTP" (Risky)
        boolean isSafeContext = lower.contains("your") || lower.contains("is") || lower.contains("requested");
        boolean isRiskyContext = lower.contains("share") || lower.contains("call") || lower.contains("win") || lower.contains("give");
        
        // STRICTION: Legitimate OTPs almost NEVER contain external web links (http/https).
        // If it has a link, it's likely a redirect to a phishing page.
        boolean hasLink = lower.contains("http://") || lower.contains("https://") || lower.contains("bit.ly") || lower.contains("tinyurl");
        
        boolean hasDigits = OTP_PATTERN.matcher(body).find();
        boolean hasKeywords = countMatches(lower, OTP_KEYWORDS) > 0;
        
        return hasDigits && hasKeywords && isSafeContext && !isRiskyContext && !hasLink;
    }

    public boolean isServiceMessage(String body) {
        if (body == null) return false;
        String lower = body.toLowerCase();
        
        // Service messages often have transaction IDs or plan details
        boolean hasTransactionRef = lower.contains("ref:") || lower.contains("id:") || lower.contains("txn");
        return (countMatches(lower, SERVICE_KEYWORDS) > 0) || hasTransactionRef;
    }

    public boolean containsTrustedKeywords(String body) {
        if (body == null) return false;
        return countMatches(body.toLowerCase(), TRUSTED_KEYWORDS) > 0;
    }
}
