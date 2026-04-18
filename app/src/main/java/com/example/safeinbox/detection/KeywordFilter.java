package com.example.safeinbox.detection;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class KeywordFilter {

    private static final Pattern LINK_PATTERN = Pattern.compile(
            "http|bit\\.ly|tinyurl|\\.xyz|goog\\.le", 
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern URGENCY_PATTERN = Pattern.compile(
            "act now|last chance|your account|verify immediately|urgent|action required",
            Pattern.CASE_INSENSITIVE
    );

    private static final Set<String> URGENCY_PHRASES = new HashSet<>(Arrays.asList(
            "act now", "last chance", "your account", "verify immediately", "urgent", "action required"
    ));

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

        // 1. Keyword check (Weight: max 25)
        int keywordMatches = 0;
        for (String word : words) {
            if (SPAM_KEYWORDS.contains(word)) {
                keywordMatches++;
                // High signal words get more weight
                if (word.equals("lottery") || word.equals("jackpot") || word.equals("prize")) {
                    score += 5;
                } else {
                    score += 2;
                }
            }
        }
        score = Math.min(score, 25);

        // 2. Suspicious Link check (Weight: 10)
        if (LINK_PATTERN.matcher(lowerBody).find()) {
            score += 10;
        }

        // 3. Urgency / Scam Pattern check (Weight: 5)
        if (URGENCY_PATTERN.matcher(lowerBody).find()) {
            score += 5;
        }

        return Math.min(score, 40); // Cap at 40
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
