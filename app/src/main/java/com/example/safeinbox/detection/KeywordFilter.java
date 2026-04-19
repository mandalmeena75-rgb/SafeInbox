package com.example.safeinbox.detection;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class KeywordFilter {

    private static final Pattern LINK_PATTERN = Pattern.compile(
            "http|bit\\.ly|tinyurl|\\.xyz|goog\\.le|t\\.ly|tiny\\.one|ow\\.ly|is\\.gd|cutt\\.ly|bit\\.do|tiny\\.cc", 
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern URGENCY_PATTERN = Pattern.compile(
            "act now|last chance|your account|verify immediately|urgent|action required|final notice|immediately",
            Pattern.CASE_INSENSITIVE
    );

    // Stealth Pattern: Character Masking (W-I-N, F.R.E.E, P.A.Y)
    private static final Pattern MASKING_PATTERN = Pattern.compile(
            "(?:[a-z][\\-. _]){2,}[a-z]",
            Pattern.CASE_INSENSITIVE
    );

    // Stealth Pattern: Financial Fraud Signatures
    private static final Pattern FRAUD_PATTERN = Pattern.compile(
            "kyc|suspended|a/c|verification required|mandatory update|account locked|income tax|irs|unauthorized access",
            Pattern.CASE_INSENSITIVE
    );

    private static final Set<String> SPAM_KEYWORDS = new HashSet<>(Arrays.asList(
            "win", "free", "prize", "click", "urgent", "congratulations", "winner", "claim",
            "cash", "offer", "deal", "discount", "limited", "subscribe", "unsubscribe", "buy",
            "cheap", "credit", "loan", "lottery", "selected", "reward", "gift", "voucher",
            "bonus", "jackpot", "lucky", "promotion"
    ));

    public int calculateSpamScore(String[] words, String rawBody) {
        if (words == null || rawBody == null) {
            return 0;
        }

        int score = 0;
        String lowerBody = rawBody.toLowerCase();

        // 1. Keyword check (Weight: max 20)
        int keywordMatches = 0;
        for (String word : words) {
            if (SPAM_KEYWORDS.contains(word)) {
                keywordMatches++;
                if (word.equals("lottery") || word.equals("jackpot") || word.equals("prize")) {
                    score += 5;
                } else if (word.equals("congratulations")) {
                    score += 1;
                } else {
                    score += 2;
                }
            }
        }
        score = Math.min(score, 20);

        // 2. Suspicious Link check (Weight: 15)
        boolean hasLink = hasSuspiciousLink(lowerBody);
        if (hasLink) {
            score += 15;
        }

        // 3. Urgency / Fraud Pattern check (Weight: 10)
        boolean hasUrgency = URGENCY_PATTERN.matcher(lowerBody).find();
        boolean hasFraud = FRAUD_PATTERN.matcher(lowerBody).find();
        
        if (hasUrgency) score += 5;
        if (hasFraud) score += 10; // Fraud signatures are very high signal

        // 4. Character Masking Intelligence (Weight: 10)
        if (MASKING_PATTERN.matcher(lowerBody).find()) {
            score += 10;
        }

        // INTER-LAYER BONUSES: Shortlink + Urgency/Fraud is almost certainly spam
        if (hasLink && (hasUrgency || hasFraud)) {
            score += 10;
        }

        return Math.min(score, 50); // Increased cap to 50 for more granular hybrid scoring
    }

    public boolean hasSuspiciousLink(String rawBody) {
        if (rawBody == null) return false;
        return LINK_PATTERN.matcher(rawBody.toLowerCase()).find();
    }

    public boolean containsSpamKeyword(String[] words) {
        if (words == null) {
            return false;
        }
        for (String word : words) {
            if (SPAM_KEYWORDS.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
