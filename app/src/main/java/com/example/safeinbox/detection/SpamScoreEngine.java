package com.example.safeinbox.detection;

import android.content.Context;
import android.util.Log;
import com.example.safeinbox.database.SpamDao;
import com.example.safeinbox.models.ClassificationResult;
import com.example.safeinbox.utils.ContactUtils;
import com.example.safeinbox.utils.PrincipalEntityResolver;

/**
 * Production-Grade 6-Level Classification Engine for SafeInbox.
 */
public class SpamScoreEngine {
    private static final String TAG = "SpamScoreEngine";

    private final KeywordFilter keywordFilter;
    private final LinkScanner linkScanner;
    private final SpamDao spamDao;
    private final MLClassifier mlClassifier;
    private final SimilarityMatcher similarityMatcher;
    private final Context context;

    private final FeatureExtractor featureExtractor;

    public SpamScoreEngine(Context context, MLClassifier mlClassifier) {
        this.context = context;
        this.mlClassifier = mlClassifier;
        this.keywordFilter = new KeywordFilter();
        this.linkScanner = new LinkScanner();
        this.spamDao = new SpamDao(context);
        this.similarityMatcher = new SimilarityMatcher(context);
        this.featureExtractor = new FeatureExtractor();
    }

    private java.util.Set<String> contactCache = null;

    public void setContactCache(java.util.Set<String> cache) {
        this.contactCache = cache;
    }

    public ClassificationResult classify(String sender, String body, String[] words) {
        ClassificationResult result = new ClassificationResult();
        
        if (body == null || body.trim().isEmpty()) {
            result.status = ClassificationResult.Status.HAM;
            result.confidence = 100;
            return result;
        }

        String normalizedBody = body.toLowerCase().replaceAll("\\s+", " ").trim();
        
        // TURBO: Use memory cache if available, otherwise hit system provider (O(1) vs O(N) penalty)
        boolean isInContacts = false;
        if (contactCache != null) {
            String suffix = ContactUtils.normalizeSender(sender);
            if (suffix.length() >= 10) suffix = suffix.substring(suffix.length() - 10);
            isInContacts = contactCache.contains(suffix);
        } else {
            // CRITICAL FIX: Only numeric senders can be "System Contacts". 
            // Otherwise, resolving "Airtel" makes it a "Trusted Contact" and bypasses all spam checks.
            isInContacts = !PrincipalEntityResolver.isAlphanumericSender(sender) && 
                           ContactUtils.getContactName(context, sender) != null;
        }
        
        // --- LAYER 1: Structural Forensics (Enhanced) ---
        FeatureExtractor.TextFeatures features = featureExtractor.extract(body);
        result.structureScore = 0;
        if (features.capsRatio > 0.4) result.structureScore += 5;
        if (features.entropy > 4.5) result.structureScore += 5; // Randomized text
        if (features.distortionScore > 0) result.structureScore += features.distortionScore;
        result.structureScore = Math.min(15, result.structureScore);

        // --- LAYER 2: Semantic Intelligence (NEW) ---
        result.semanticScore = detectSemanticIntent(normalizedBody);

        // --- LAYER 3: Advanced Rule Engine (Upgraded) ---
        KeywordFilter.KeywordAnalysisResult keywordRes = keywordFilter.analyzeText(normalizedBody);
        result.ruleScore = keywordRes.overallScore / 2; // Rescale to fit 20pt cap
        if (normalizedBody.contains("account") && normalizedBody.contains("block")) result.ruleScore += 10;
        if (normalizedBody.contains("update") && normalizedBody.contains("kyc")) result.ruleScore += 15;
        result.ruleScore = Math.min(20, result.ruleScore);

        // --- LAYER 4: URL & Domain Intelligence (Upgraded) ---
        LinkScanner.LinkRiskResult linkRes = linkScanner.scan(normalizedBody);
        result.urlScore = Math.min(30, linkRes.score);
        result.isRiskyLink = linkRes.hasLink && (linkRes.score > 25 || linkRes.isHomograph);

        // --- LAYER 5: Sender Reputation Engine (Enhanced) ---
        int spamHits = spamDao.getSenderSpamHits(sender);
        int hamHits = spamDao.getSenderHamHits(sender);
        boolean isInternational = ContactUtils.isInternational(context, sender);
        
        result.senderScore = (spamHits * 5) - (hamHits * 3);
        if (isInContacts) result.senderScore -= 15; // Safe boost
        if (isInternational && !isInContacts) result.senderScore += 10; // Risk
        result.senderScore = Math.max(-20, Math.min(20, result.senderScore));

        // --- LAYER 6: Behavioral Pattern Engine (NEW) ---
        int recentCount = spamDao.getRecentMessageCount(sender, 10 * 60 * 1000);
        int similarCount = spamDao.getRecentSimilarMessageCount(sender, body, 2 * 60 * 60 * 1000);
        result.behaviorScore = (recentCount >= 3 ? 10 : 0) + (similarCount >= 1 ? 5 : 0);
        result.behaviorScore = Math.min(15, result.behaviorScore);

        // --- LAYER 7: Fuzzy Similarity (Upgraded) ---
        result.similarityScore = Math.min(25, similarityMatcher.getSimilarityWeight(normalizedBody));

        // --- LAYER 8: ML Classifier (Ensemble Part) ---
        double mlProb = mlClassifier.getSpamProbability(words);
        result.mlScore = (int) (mlProb * 30); // Scaled to 30pt cap
        if (body.length() < 10) result.mlScore = Math.min(5, result.mlScore); // Cap ML for short text

        // --- LAYER 9: Scam Pattern Combiner (Super Boost) ---
        result.comboBoost = 0;
        if (keywordRes.hasBankWords && linkRes.hasLink && keywordRes.hasUrgencyWords) result.comboBoost += 40;
        if (keywordRes.hasPrizeWords && !isInContacts) result.comboBoost += 30;
        if (keywordFilter.isOTP(normalizedBody) && linkRes.hasLink) result.comboBoost += 50; // VERY DANGEROUS

        // --- LAYER 10: Personalization Engine (NEW) ---
        // Learned behavior adjustment
        result.personalizationAdjust = 0;
        if (hamHits > 5 && !isInContacts) result.personalizationAdjust -= 10; // User trusts this unknown sender

        // --- FINAL DECISION ENGINE (10-LAYER SUM) ---
        result.totalScore = result.structureScore + result.semanticScore + result.ruleScore + 
                            result.urlScore + result.senderScore + result.behaviorScore + 
                            result.similarityScore + result.mlScore + result.comboBoost + 
                            result.personalizationAdjust;

        // --- CRITICAL OVERRIDES (ACCURACY GUARANTEE) ---
        
        // 1. Short Message Immunity (e.g. "hi", "ok", "yes")
        if (body.trim().length() < 5 && !linkRes.hasLink) {
            result.totalScore = 0;
            result.status = ClassificationResult.Status.HAM;
            result.reason = "✅ Short Communication";
            result.calculateStatus();
            return result;
        }

        // 2. Absolute Contact Whitelisting
        if (isInContacts) {
            if (!result.isRiskyLink) {
                result.totalScore = 0;
                result.status = ClassificationResult.Status.HAM;
                result.reason = "👤 TRUSTED CONTACT";
                result.calculateStatus();
                result.confidence = 100; // 100% Trusted
                populateForensicInsights(result, linkRes, keywordRes);
                return result;
            } else {
                result.status = ClassificationResult.Status.SUSPICIOUS;
                result.reason = "⚠️ Trust compromised: Link detected.";
                result.calculateStatus();
                populateForensicInsights(result, linkRes, keywordRes);
                return result;
            }
        }

        result.calculateStatus();
        
        // --- INDUSTRIAL REASON REPORTING (FOR UI) ---
        StringBuilder tags = new StringBuilder();
        if (result.status == ClassificationResult.Status.SPAM) {
            tags.append("🚫 Threat Detected. ");
        } else if (result.status == ClassificationResult.Status.SUSPICIOUS) {
            tags.append("⚠️ Suspicious. ");
        }

        if (result.isRiskyLink) tags.append("🔗 Unsafe Link. ");
        if (result.comboBoost > 30) tags.append("🚨 Scam Pattern. ");
        if (result.mlScore >= 20) tags.append("🧠 AI Insight. ");
        if (result.ruleScore >= 20) tags.append("📋 Rule Match. ");
        
        if (tags.length() == 0) {
            if (result.status == ClassificationResult.Status.HAM) {
                result.reason = "✅ Safe Patterns.";
            } else {
                result.reason = "🛡️ Forensic Intelligence Warning.";
            }
        } else {
            result.reason = tags.toString().trim();
        }

        populateForensicInsights(result, linkRes, keywordRes);

        // --- FINAL SAFETY OVERRIDES ---
        if (keywordFilter.isOTP(normalizedBody) && !linkRes.hasLink) {
            result.status = ClassificationResult.Status.HAM;
            result.totalScore = 0;
            result.confidence = 99;
            result.reason = "🔒 VERIFIED OTP";
            result.urgencyDetails = "✅ Secure verification code pattern detected.";
            return result;
        }

        return result;
    }

    private void populateForensicInsights(ClassificationResult res, LinkScanner.LinkRiskResult linkRes, KeywordFilter.KeywordAnalysisResult keywordRes) {
        // 1. Urgency & Semantics
        if (res.comboBoost > 0) {
            res.urgencyDetails = "🚨 Multi-vector threat: Combination of urgency and financial triggers.";
        } else if (res.ruleScore > 10 || res.semanticScore > 5) {
            res.urgencyDetails = "⚠️ Social engineering patterns detected in text structure.";
        } else {
            res.urgencyDetails = "✅ Low communication pressure. Normal intent detected.";
        }

        // 2. Link Safety DNA
        if (res.isRiskyLink) {
            res.linkDetails = "🚩 CRITICAL: " + (linkRes.isHomograph ? "Homograph attack (URL Spoofing)." : "High-risk redirection dna.");
        } else if (linkRes.hasLink) {
            res.linkDetails = "🔍 Link detected. Forensic safety: " + (30 - linkRes.score) + "/30.";
        } else {
            res.linkDetails = "✅ No external links detected in message body.";
        }

        // 3. Sender & History
        if (res.senderScore > 20) {
            res.historyDetails = "🚩 Sender blacklisted due to high spam frequency.";
        } else if (res.similarityScore > 10) {
            res.historyDetails = "⚠️ Forensic match found with known scam templates.";
        } else if (res.reason != null && res.reason.contains("CONTACT")) {
            res.historyDetails = "👤 Verified safe via system contact list.";
        } else {
            res.historyDetails = "✅ Clean history. No known behavioral threat matches.";
        }
    }

    private int detectSemanticIntent(String text) {
        int score = 0;
        // Intent: Financial Threat
        if (text.contains("verify") && (text.contains("bank") || text.contains("card"))) score += 15;
        // Intent: Urgency Pressure
        if (text.contains("immediately") || text.contains("within 24 hours")) score += 10;
        // Intent: Social Engineering
        if (text.contains("gift card") || text.contains("customer support")) score += 10;
        return Math.min(25, score);
    }

    public void prepareSimilarityCache() {
        similarityMatcher.prepareCache();
    }

    public void clearSimilarityCache() {
        similarityMatcher.clearCache();
    }
}
