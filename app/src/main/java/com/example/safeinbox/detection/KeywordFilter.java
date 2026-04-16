package com.example.safeinbox.detection;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class KeywordFilter {

    private static final Set<String> SPAM_KEYWORDS = new HashSet<>(Arrays.asList(
            "win",
            "free",
            "prize",
            "click",
            "urgent",
            "congratulations",
            "winner",
            "claim",
            "cash",
            "offer",
            "deal",
            "discount",
            "limited",
            "subscribe",
            "unsubscribe",
            "buy",
            "cheap",
            "credit",
            "loan",
            "lottery",
            "selected",
            "reward",
            "gift",
            "voucher",
            "bonus",
            "jackpot",
            "lucky",
            "promotion"
    ));

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
