package com.example.safeinbox.detection;

import android.content.Context;

import com.example.safeinbox.models.ClassificationResult;
import com.example.safeinbox.preprocessing.TextProcessor;

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
        return result.isSpam;
    }

    public ClassificationResult classifyWithDetails(String sender, String body) {
        String[] words = textProcessor.process(body);
        return scoringEngine.classify(sender, body, words);
    }

    public void trainFromFeedback(String body, boolean isSpam) {
        String[] words = textProcessor.process(body);
        mlClassifier.train(words, isSpam);
    }

    public MLClassifier getClassifier() {
        return mlClassifier;
    }
}
