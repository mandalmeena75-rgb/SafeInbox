package com.example.safeinbox.models;

/**
 * Model class that holds the full breakdown of a classification decision.
 */
public class ClassificationResult {
    public enum Status {
        HAM,         // Score <= 60
        SUSPICIOUS,  // Score 61-100
        SPAM         // Score >= 101
    }

    // --- 10-LAYER HYBRID SCORE BREAKDOWN ---
    public int structureScore;       // Layer 1
    public int semanticScore;        // Layer 2
    public int ruleScore;            // Layer 3
    public int urlScore;             // Layer 4
    public int senderScore;          // Layer 5
    public int behaviorScore;        // Layer 6
    public int similarityScore;      // Layer 7
    public int mlScore;              // Layer 8
    public int comboBoost;           // Layer 9
    public int personalizationAdjust;// Layer 10

    public int totalScore;
    public Status status;
    public String reason;
    public String urgencyDetails;
    public String linkDetails;
    public String historyDetails;
    public boolean isRiskyLink;
    public int confidence; // 0-100%

    public ClassificationResult() {
        this.structureScore = 0;
        this.semanticScore = 0;
        this.ruleScore = 0;
        this.urlScore = 0;
        this.senderScore = 0;
        this.behaviorScore = 0;
        this.similarityScore = 0;
        this.mlScore = 0;
        this.comboBoost = 0;
        this.personalizationAdjust = 0;
        
        this.totalScore = 0;
        this.status = Status.HAM;
        this.reason = "";
        this.isRiskyLink = false;
        this.confidence = 0;
    }

    public void calculateStatus() {
        // Advanced Multi-Tier Thresholds (Hardened v3)
        // SPAM: High probability threats
        // SUSPICIOUS: Early warning patterns
        // HAM: Safe/Verified communications
        
        if (totalScore >= 40) {
            this.status = Status.SPAM;
            this.confidence = (int) Math.min(100, 85 + (totalScore - 40) / 2.0);
        } else if (totalScore >= 15) {
            this.status = Status.SUSPICIOUS;
            this.confidence = (int) Math.min(84, 50 + (totalScore - 15) * 1.5);
        } else {
            this.status = Status.HAM;
            // For HAM, confidence is 100% if score is very low (trusted)
            int hamConfidence = (int) Math.min(100, 100 - totalScore * 2);
            this.confidence = Math.max(70, hamConfidence); 
        }
    }
}

