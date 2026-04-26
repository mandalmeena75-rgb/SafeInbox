package com.example.safeinbox.detection;

import android.content.Context;

import com.example.safeinbox.models.ClassificationResult;
import com.example.safeinbox.preprocessing.TextProcessor;
import com.example.safeinbox.database.SpamDao;

public class SpamDetector {

    private static SpamDetector instance;
    private final SpamScoreEngine scoringEngine;
    private final MLClassifier mlClassifier;
    private final TextProcessor textProcessor;

    public static synchronized SpamDetector getInstance(Context context) {
        if (instance == null) {
            instance = new SpamDetector(context.getApplicationContext());
        }
        return instance;
    }

    private SpamDetector(Context context) {
        this.mlClassifier = new MLClassifier(context);
        this.textProcessor = new TextProcessor();
        this.scoringEngine = new SpamScoreEngine(context, this.mlClassifier);
    }

    public boolean isSpam(String sender, String body) {
        ClassificationResult result = classifyWithDetails(sender, body);
        return result.status == ClassificationResult.Status.SPAM;
    }

    public ClassificationResult.Status getStatus(String sender, String body) {
        return classifyWithDetails(sender, body).status;
    }

    public ClassificationResult classifyWithDetails(String sender, String body) {
        String[] words = textProcessor.process(body);
        return scoringEngine.classify(sender, body, words);
    }

    /**
     * Advanced Training: Updates ML model AND similarity dataset.
     */
    public void trainFromFeedback(String sender, String body, boolean isSpam) {
        String[] words = textProcessor.process(body);
        // 1. Train ML Model
        mlClassifier.train(words, isSpam);
        // 2. Add to Similarity Dataset
        new SpamDao(mlClassifier.getContext()).insertSimilaritySample(body, isSpam ? "SPAM" : "HAM");
    }

    public void prepareSimilarityCache() {
        scoringEngine.prepareSimilarityCache();
    }

    public void clearSimilarityCache() {
        scoringEngine.clearSimilarityCache();
    }

    public void setContactCache(java.util.Set<String> cache) {
        scoringEngine.setContactCache(cache);
    }

    public MLClassifier getClassifier() {
        return mlClassifier;
    }

    public TextProcessor getTextProcessor() {
        return textProcessor;
    }
}
