package com.example.safeinbox.detection;

import android.content.Context;
import com.example.safeinbox.database.SpamDao;
import com.example.safeinbox.models.ClassificationResult;
import com.example.safeinbox.utils.Constants;
import com.example.safeinbox.utils.ContactUtils;

public class SpamScoreEngine {

    private final KeywordFilter keywordFilter;
    private final MLClassifier mlClassifier;
    private final SpamDao spamDao;
    private final Context context;

    public SpamScoreEngine(Context context, MLClassifier mlClassifier) {
        this.context = context;
        this.keywordFilter = new KeywordFilter();
        this.mlClassifier = mlClassifier;
        this.spamDao = new SpamDao(context);
    }

    public ClassificationResult classify(String sender, String body, String[] words) {
        ClassificationResult result = new ClassificationResult();

        // 0. Blocklist Check Layer (Absolute Spam Verdict)
        if (spamDao.isBlockedNumber(sender)) {
            result.isSpam = true;
            result.reason = "Sender is explicitly blocked (Blacklist)";
            result.mlScore = 100; // Force a maxed-out spam score metric
            result.totalSpamScore = 100;
            return result; // Early exit
        }

        // 1. Keyword Filter Layer (0-40)
        result.keywordScore = keywordFilter.calculateSpamScore(words, body);

        // 2. ML Probability Layer (0-30)
        double probability = mlClassifier.getSpamProbability(words);
        result.mlScore = (int) (probability * Constants.WEIGHT_ML);

        // 3. History Check Layer (0-20)
        int spamHits = spamDao.getSenderSpamHits(sender);
        if (spamHits > 0) {
            // Cap history score at 20
            result.historyScore = Math.min(spamHits * 5, Constants.WEIGHT_HISTORY);
        }

        // 4. User Feedback Layer (0-15)
        int userReports = spamDao.getUserSpamReports(sender);
        if (userReports > 0) {
            result.feedbackScore = Math.min(userReports * 5, Constants.WEIGHT_FEEDBACK);
        }

        // 5. Contact Verification Layer (Trust +25)
        String contactName = ContactUtils.getContactName(context, sender);
        if (contactName != null) {
            // Early exit option for Contacts (Safe) can be added here
            result.contactScore = Constants.WEIGHT_CONTACT;
        }

        // 6. Safe History Layer (Trust +20)
        int hamHits = spamDao.getSenderHamHits(sender);
        if (hamHits > 0) {
            result.safeHistoryScore = Math.min(hamHits * 5, Constants.WEIGHT_SAFE_HISTORY);
        }

        // Final decision logic
        result.calculateTotals();

        // Bonus: Human readable reason for the verdict
        if (result.isSpam) {
            if (result.keywordScore > 20) result.reason = "Contains common spam patterns";
            else if (result.mlScore > 20) result.reason = "Classified as spam by ML model";
            else if (result.historyScore > 10) result.reason = "Sender has a history of spamming";
            else result.reason = "Hybrid signals indicate high spam risk";
        } else {
            if (result.contactScore > 0) result.reason = "Sender is in your contacts";
            else result.reason = "Classification score is low, message appears safe";
        }

        return result;
    }
}
