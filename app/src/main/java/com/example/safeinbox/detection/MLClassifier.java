package com.example.safeinbox.detection;

import com.example.safeinbox.database.MLDao;
import com.example.safeinbox.preprocessing.TextProcessor;
import com.example.safeinbox.utils.Constants;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MLClassifier {

    // Shared static data to keep weights in memory once loaded
    private static HashMap<String, Integer> spamWordCounts = new HashMap<>();
    private static HashMap<String, Integer> hamWordCounts = new HashMap<>();
    private static int totalSpamMessages = 0;
    private static int totalHamMessages = 0;
    private static int spamTotalWords = 0;
    private static int hamTotalWords = 0;
    private static int vocabularySize = 0;
    private static boolean isLoaded = false;
    
    private final MLDao mlDao;

    public MLClassifier(android.content.Context context) {
        this.mlDao = new MLDao(context);
        
        // Only load if not already in memory
        if (!isLoaded) {
            loadFromPersistence();
            isLoaded = true;
        }
    }

    private void loadFromPersistence() {
        // First load from DB
        mlDao.loadWordCounts(spamWordCounts, hamWordCounts);
        Map<String, Integer> stats = mlDao.loadGlobalStats();
        
        if (!stats.isEmpty()) {
            totalSpamMessages = stats.getOrDefault(Constants.KEY_TOTAL_SPAM_MSGS, 0);
            totalHamMessages = stats.getOrDefault(Constants.KEY_TOTAL_HAM_MSGS, 0);
            spamTotalWords = stats.getOrDefault(Constants.KEY_SPAM_TOTAL_WORDS, 0);
            hamTotalWords = stats.getOrDefault(Constants.KEY_HAM_TOTAL_WORDS, 0);
            updateVocabularySize();
        } else {
            // If DB is empty, load defaults and save them
            loadDefaultTrainingData();
            saveToPersistence();
        }
    }

    public void saveToPersistence() {
        mlDao.saveWordCounts(spamWordCounts, hamWordCounts);
        mlDao.saveGlobalStats(totalSpamMessages, totalHamMessages, spamTotalWords, hamTotalWords);
    }

    private void updateVocabularySize() {
        Set<String> vocab = new HashSet<>();
        vocab.addAll(spamWordCounts.keySet());
        vocab.addAll(hamWordCounts.keySet());
        vocabularySize = vocab.size();
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

        saveToPersistence();
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

        updateVocabularySize();
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

            // Skip completely unknown words. With imbalanced datasets, unknown words 
            // mathematically favor the class with fewer total words.
            if (spamCount == 0 && hamCount == 0) {
                continue;
            }

            // Laplace smoothing: P(word|class) = (count + 1) / (totalWords + vocabSize)
            logProbSpam += Math.log((double) (spamCount + 1) / (spamTotalWords + vocabularySize));
            logProbHam += Math.log((double) (hamCount + 1) / (hamTotalWords + vocabularySize));
        }

        // Default to Ham if the confidence isn't strictly higher for Spam
        return logProbSpam > logProbHam && (logProbSpam - logProbHam > 0.5);
    }
}
