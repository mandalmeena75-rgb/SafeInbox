package com.example.safeinbox.models;

/**
 * Model class that holds the full breakdown of a classification decision.
 */
public class ClassificationResult {
    public enum Status {
        SAFE,
        SUSPICIOUS,
        SPAM,
        OTP,
        SERVICE
    }

    public int keywordScore;      // Spam keywords: +40
    public int urgencyScore;      // Urgency patterns: +20
    public int threatScore;       // Threat patterns: +30
    public int financialScore;    // Financial patterns: +40
    public int fakeServiceScore;  // Fake Service patterns: +30
    public int actionTriggerScore;// Action triggers: +20
    
    public int linkScore;         // Suspicious link: +30
    public int contactWeight;     // Sender in contacts: -30
    public int contentWeight;     // No link + casual text: -10
    
    public int mlScore;           // Naive Bayes contribution
    public int historyScore;      // Reputation contribution
    public int reputationScore;   // New Reputation score (0-100 mapped)
    public int feedbackScore;     // User learning contribution

    public int totalScore;        // Final weighted score
    public Status status;         // Classification: SAFE, SUSPICIOUS, SPAM
    public String reason;         // Human-readable explanation
    public boolean isRiskyLink;   // Flag for safe link warning popup

    public ClassificationResult() {
        this.keywordScore = 0;
        this.urgencyScore = 0;
        this.threatScore = 0;
        this.financialScore = 0;
        this.fakeServiceScore = 0;
        this.actionTriggerScore = 0;
        this.linkScore = 0;
        this.contactWeight = 0;
        this.contentWeight = 0;
        this.mlScore = 0;
        this.historyScore = 0;
        this.reputationScore = 0;
        this.feedbackScore = 0;
        this.totalScore = 0;
        this.status = Status.SAFE;
        this.reason = "";
        this.isRiskyLink = false;
    }

    public void calculateStatus() {
        this.totalScore = keywordScore + urgencyScore + threatScore + financialScore + fakeServiceScore + actionTriggerScore + linkScore + contactWeight + contentWeight + mlScore + reputationScore + feedbackScore;
        
        if (totalScore < 40) {
            this.status = Status.SAFE;
        } else if (totalScore <= 70) {
            this.status = Status.SUSPICIOUS;
        } else {
            this.status = Status.SPAM;
        }
    }
}

