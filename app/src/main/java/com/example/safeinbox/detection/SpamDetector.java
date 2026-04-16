package com.example.safeinbox.detection;

import android.content.Context;

import com.example.safeinbox.database.SpamDao;
import com.example.safeinbox.preprocessing.TextProcessor;

public class SpamDetector {

    private final KeywordFilter keywordFilter;
    private final MLClassifier mlClassifier;
    private final TextProcessor textProcessor;
    private final SpamDao spamDao;

    public SpamDetector(Context context) {
        this.keywordFilter = new KeywordFilter();
        this.mlClassifier = new MLClassifier();
        this.textProcessor = new TextProcessor();
        this.spamDao = new SpamDao(context);
    }

    public boolean isSpam(String sender, String body) {
        // Check 1: Is the sender a blocked number?
        if (spamDao.isBlockedNumber(sender)) {
            return true;
        }

        // Process the message body into cleaned words
        String[] words = textProcessor.process(body);

        // Check 2: Keyword filter
        boolean keywordResult = keywordFilter.containsSpamKeyword(words);

        // Check 3: Naive Bayes ML classifier
        boolean mlResult = mlClassifier.classify(words);

        // If either detection method says spam, final result is spam
        return keywordResult || mlResult;
    }

    public void trainFromFeedback(String body, boolean isSpam) {
        String[] words = textProcessor.process(body);
        mlClassifier.train(words, isSpam);
    }
}
