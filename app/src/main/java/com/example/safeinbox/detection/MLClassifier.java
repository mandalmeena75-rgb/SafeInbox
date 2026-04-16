package com.example.safeinbox.detection;

import com.example.safeinbox.preprocessing.TextProcessor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class MLClassifier {

    private final HashMap<String, Integer> spamWordCounts;
    private final HashMap<String, Integer> hamWordCounts;
    private int totalSpamMessages;
    private int totalHamMessages;
    private int spamTotalWords;
    private int hamTotalWords;
    private int vocabularySize;

    public MLClassifier() {
        spamWordCounts = new HashMap<>();
        hamWordCounts = new HashMap<>();
        totalSpamMessages = 0;
        totalHamMessages = 0;
        spamTotalWords = 0;
        hamTotalWords = 0;
        vocabularySize = 0;
        loadDefaultTrainingData();
    }

    private void loadDefaultTrainingData() {
        TextProcessor processor = new TextProcessor();

        String[] spamSamples = {
                "congratulations you have won a free prize claim now",
                "urgent action required click the link to win cash",
                "you are selected for a free gift voucher reply now",
                "claim your lottery winnings today call this number",
                "free entry in our weekly contest text win to 80085",
                "limited time offer get a free iphone click here",
                "you have a chance to win big money act now",
                "cheap loans available apply now for easy credit",
                "earn extra cash from home guaranteed income click here",
                "special promotion just for you buy now save big",
                "winner winner you won the grand jackpot prize",
                "exclusive deal free subscription just for you sign up",
                "alert your account has been compromised click to verify",
                "prize notification you are our lucky winner today",
                "discount offer buy one get one free limited stock"
        };

        String[] hamSamples = {
                "hey are you coming to the meeting tomorrow morning",
                "can you pick up some groceries on your way home",
                "happy birthday hope you have a wonderful day ahead",
                "the project deadline has been moved to next friday",
                "thanks for dinner last night it was really great",
                "mom called she wants us to visit this weekend",
                "just finished the report will send it over to you now",
                "running late be there in about fifteen minutes sorry",
                "did you see the news about the game last night",
                "can we reschedule our lunch to wednesday instead of today",
                "the doctor appointment is confirmed for thursday at 3pm",
                "great job on the presentation today really well done",
                "please remind me to call the plumber tomorrow morning",
                "the kids have a school event next tuesday afternoon",
                "just wanted to check in and see how you are doing"
        };

        for (String spam : spamSamples) {
            train(processor.process(spam), true);
        }
        for (String ham : hamSamples) {
            train(processor.process(ham), false);
        }
    }

    public void train(String[] words, boolean isSpam) {
        if (words == null || words.length == 0) {
            return;
        }

        if (isSpam) {
            totalSpamMessages++;
            for (String word : words) {
                spamWordCounts.put(word, spamWordCounts.getOrDefault(word, 0) + 1);
                spamTotalWords++;
            }
        } else {
            totalHamMessages++;
            for (String word : words) {
                hamWordCounts.put(word, hamWordCounts.getOrDefault(word, 0) + 1);
                hamTotalWords++;
            }
        }

        // Recalculate vocabulary size
        Set<String> vocab = new HashSet<>();
        vocab.addAll(spamWordCounts.keySet());
        vocab.addAll(hamWordCounts.keySet());
        vocabularySize = vocab.size();
    }

    public boolean classify(String[] words) {
        if (words == null || words.length == 0) {
            return false;
        }

        int totalMessages = totalSpamMessages + totalHamMessages;
        if (totalMessages == 0) {
            return false;
        }

        // Prior probabilities (log space to prevent underflow)
        double logProbSpam = Math.log((double) totalSpamMessages / totalMessages);
        double logProbHam = Math.log((double) totalHamMessages / totalMessages);

        for (String word : words) {
            int spamCount = spamWordCounts.getOrDefault(word, 0);
            int hamCount = hamWordCounts.getOrDefault(word, 0);

            // Laplace smoothing: P(word|class) = (count + 1) / (totalWords + vocabSize)
            logProbSpam += Math.log((double) (spamCount + 1) / (spamTotalWords + vocabularySize));
            logProbHam += Math.log((double) (hamCount + 1) / (hamTotalWords + vocabularySize));
        }

        return logProbSpam > logProbHam;
    }
}
