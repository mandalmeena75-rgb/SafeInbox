package com.example.safeinbox.detection;

import android.content.Context;
import com.example.safeinbox.database.SpamDao;

public class UserFeedbackManager {

    private final SpamDao spamDao;

    public UserFeedbackManager(Context context) {
        this.spamDao = new SpamDao(context);
    }

    /**
     * Records a user's action on a message to improve future detection.
     * 
     * @param sender The sender's number
     * @param isSpam True if user marked as spam, False if marked as "Not Spam" (Ham)
     */
    public void recordFeedback(String sender, boolean isSpam) {
        if (sender == null || sender.isEmpty()) return;
        spamDao.recordUserFeedback(sender, isSpam);
    }

    /**
     * Calculates a weight based on past user feedback for this sender.
     * Marking "Not Spam" reduces the spam score.
     * Marking "Spam" increases it.
     */
    public int getFeedbackWeight(String sender) {
        if (sender == null || sender.isEmpty()) return 0;

        int spamReports = spamDao.getUserSpamReports(sender);
        int hamReports = spamDao.getUserHamReports(sender);

        // Logic: Each spam report adds +10 to the score (max +40)
        // Each ham report subtracts -15 from the score (max -45)
        int score = (spamReports * 10) - (hamReports * 15);
        
        // Cap the weights to prevent extreme outcomes from single signals
        if (score > 40) return 40;
        if (score < -45) return -45;
        
        return score;
    }
}
