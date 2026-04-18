package com.example.safeinbox.models;

/**
 * Model class that holds the full breakdown of a classification decision.
 */
public class ClassificationResult {
    public int keywordScore;      // 0–40 (spam keywords + link + urgency patterns)
    public int mlScore;           // 0–30 (Naive Bayes probability mapped to score)
    public int historyScore;      // 0–20 (past spam classifications for this sender)
    public int feedbackScore;     // 0–15 (user-reported spam count for this sender)

    public int contactScore;      // 0–25 (sender is in device contacts)
    public int safeHistoryScore;  // 0–20 (past legitimate classifications)

    public int totalSpamScore;    // Sum of spam signals
    public int totalTrustScore;   // Sum of trust signals
    public boolean isSpam;        // Final verdict
    public String reason;         // Human-readable explanation

    public ClassificationResult() {
        this.keywordScore = 0;
        this.mlScore = 0;
        this.historyScore = 0;
        this.feedbackScore = 0;
        this.contactScore = 0;
        this.safeHistoryScore = 0;
        this.totalSpamScore = 0;
        this.totalTrustScore = 0;
        this.isSpam = false;
        this.reason = "";
    }

    public void calculateTotals() {
        this.totalSpamScore = keywordScore + mlScore + historyScore + feedbackScore;
        this.totalTrustScore = contactScore + safeHistoryScore;
        this.isSpam = totalSpamScore > totalTrustScore;
    }
}
