package com.example.safeinbox.detection;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Enhanced KeywordFilter for multi-layer classification.
 * Tracks Spam, Urgency, Threat, and OTP signals.
 */
public class KeywordFilter {

    // Detection Patterns
    private static final Pattern OTP_PATTERN = Pattern.compile("(\\b\\d{4,8}\\b)", Pattern.CASE_INSENSITIVE);
    
    private static final Set<String> OTP_KEYWORDS = new HashSet<>(Arrays.asList(
            "otp", "verification", "code", "vcode", "one time password", "do not share", "secret"
    ));

    private static final Set<String> SPAM_KEYWORDS = new HashSet<>(Arrays.asList(
            "win", "free", "prize", "gift card", "reward", "winner", "jackpot", "lucky", "lottery"
    ));

    private static final Set<String> URGENCY_WORDS = new HashSet<>(Arrays.asList(
            "now", "claim", "urgent", "hurry", "expires", "immediately", "today", "instantly"
    ));

    private static final Set<String> THREAT_WORDS = new HashSet<>(Arrays.asList(
            "account blocked", "update now", "action required", "unauthorized", "suspended", "security alert"
    ));

    private static final Set<String> SERVICE_KEYWORDS = new HashSet<>(Arrays.asList(
            "airtel", "jio", "vi", "bank", "aadhaar", "irctc", "uidai", "recharge", "balance"
    ));

    public boolean isOTP(String body) {
        if (body == null) return false;
        String lower = body.toLowerCase();
        
        // Pattern: Contains 4-8 digit number AND OTP keywords
        boolean hasDigits = OTP_PATTERN.matcher(body).find();
        boolean hasKeywords = false;
        for (String kw : OTP_KEYWORDS) {
            if (lower.contains(kw)) {
                hasKeywords = true;
                break;
            }
        }
        return hasDigits && hasKeywords;
    }

    public boolean isServiceKeywordPresent(String body) {
        if (body == null) return false;
        String lower = body.toLowerCase();
        for (String kw : SERVICE_KEYWORDS) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    public int getSpamKeywordScore(String body) {
        if (body == null) return 0;
        String lower = body.toLowerCase();
        for (String kw : SPAM_KEYWORDS) {
            if (lower.contains(kw)) return 40; // Requirements: +40 for spam keywords
        }
        return 0;
    }

    public int getUrgencyScore(String body) {
        if (body == null) return 0;
        String lower = body.toLowerCase();
        for (String kw : URGENCY_WORDS) {
            if (lower.contains(kw)) return 20; // Requirements: +20 for urgency
        }
        return 0;
    }

    public int getThreatScore(String body) {
        if (body == null) return 0;
        String lower = body.toLowerCase();
        for (String kw : THREAT_WORDS) {
            if (lower.contains(kw)) return 30; // Requirements: +30 for threat
        }
        return 0;
    }

    public boolean isGenericCasualText(String body) {
        if (body == null) return false;
        String lower = body.toLowerCase().trim();
        return lower.length() < 30 && !lower.contains("http") && !lower.contains("www") 
                && (lower.contains("hi") || lower.contains("hello") || lower.contains("ok") || lower.contains("bye"));
    }
}
