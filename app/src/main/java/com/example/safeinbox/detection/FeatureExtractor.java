package com.example.safeinbox.detection;

import java.util.HashMap;
import java.util.Map;

public class FeatureExtractor {

    public static class TextFeatures {
        public double capsRatio;
        public double specialCharRatio;
        public int repeatedWordCount;
        public boolean hasSuspiciousFormatting;
        public double entropy;
        public int distortionScore; // Detected leet-speak
        public int length;
    }

    public TextFeatures extract(String body) {
        TextFeatures features = new TextFeatures();
        if (body == null || body.isEmpty()) return features;

        features.length = body.length();
        
        int caps = 0;
        int special = 0;
        String[] words = body.split("\\s+");
        Map<String, Integer> wordCounts = new HashMap<>();

        for (char c : body.toCharArray()) {
            if (Character.isUpperCase(c)) caps++;
            if (!Character.isLetterOrDigit(c) && !Character.isWhitespace(c)) special++;
        }

        features.capsRatio = (double) caps / body.length();
        features.specialCharRatio = (double) special / body.length();
        features.entropy = calculateEntropy(body);
        features.distortionScore = calculateDistortionScore(body);

        int repeated = 0;
        for (String w : words) {
            String lower = w.toLowerCase().replaceAll("[^a-z0-9]", "");
            if (lower.length() < 3) continue;
            int count = wordCounts.getOrDefault(lower, 0) + 1;
            wordCounts.put(lower, count);
            if (count > 2) repeated++;
        }
        features.repeatedWordCount = repeated;

        // Check for obfuscation patterns
        features.hasSuspiciousFormatting = body.matches(".*([A-Z]\\s){2,}.*") || body.matches(".*([A-Z]\\.){2,}.*");

        return features;
    }

    private double calculateEntropy(String s) {
        Map<Character, Integer> freq = new HashMap<>();
        for (char c : s.toCharArray()) {
            freq.put(c, freq.getOrDefault(c, 0) + 1);
        }
        double entropy = 0;
        for (int count : freq.values()) {
            double p = (double) count / s.length();
            entropy -= p * (Math.log(p) / Math.log(2));
        }
        return entropy;
    }

    private int calculateDistortionScore(String s) {
        int score = 0;
        // Detect common leet-speak patterns used by spammers
        String[] patterns = {"[fF][rR]33", "[0oO][fF]{2}3[rR]", "[lL]0[aA][nN]", "[pP][aA][yY][tT][mM]"};
        for (String p : patterns) {
            if (s.matches("(?i).*"+p+".*")) score += 10;
        }
        // Generic leet-speak: numbers inside words
        if (s.matches(".*[a-zA-Z][0-9][a-zA-Z].*")) score += 5;
        return score;
    }
}
