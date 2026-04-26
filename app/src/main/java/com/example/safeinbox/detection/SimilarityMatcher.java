package com.example.safeinbox.detection;

import android.content.Context;
import com.example.safeinbox.database.SpamDao;
import com.example.safeinbox.models.SmsMessage;
import java.util.List;

/**
 * Layer 3 detection: Fuzzy similarity matching against historical spam/ham.
 */
public class SimilarityMatcher {
    private final SpamDao spamDao;
    private List<SampleCache> cachedSamples = null;

    private static class SampleCache {
        String status;
        java.util.Set<String> wordSet;
    }

    public SimilarityMatcher(Context context) {
        this.spamDao = new SpamDao(context);
    }

    /**
     * ULTRA: Pre-loads and pre-processes all similarity samples into memory.
     * This is critical for high-speed project-wide forensic rescans.
     */
    public void prepareCache() {
        List<SmsMessage> samples = spamDao.getSimilaritySamples();
        List<SampleCache> newCache = new java.util.ArrayList<>();
        
        for (SmsMessage sample : samples) {
            SampleCache sc = new SampleCache();
            sc.status = sample.getClassificationStatus();
            String normalized = normalize(sample.getBody());
            sc.wordSet = new java.util.HashSet<>(java.util.Arrays.asList(normalized.split("\\s+")));
            newCache.add(sc);
        }
        this.cachedSamples = newCache;
    }

    public void clearCache() {
        this.cachedSamples = null;
    }

    public int getSimilarityWeight(String body) {
        if (body == null || body.length() < 10) return 0;
        
        // Use pre-warmed cache if available (rescan mode), otherwise load on-demand (real-time mode)
        List<SampleCache> localCache = cachedSamples;
        if (localCache == null) {
            prepareCache();
            localCache = cachedSamples;
        }
        
        if (localCache == null) return 0; // Should not happen after prepareCache
        
        String normalizedTarget = normalize(body);
        String[] targetWords = normalizedTarget.split("\\s+");
        java.util.Set<String> targetSet = new java.util.HashSet<>(java.util.Arrays.asList(targetWords));
        
        for (SampleCache sample : localCache) {
            double score = jaccardSimilarityFromSets(targetSet, sample.wordSet);
            if (score > 0.8) {
                return "SPAM".equals(sample.status) ? 40 : -40;
            }
        }
        return 0;
    }

    private String normalize(String text) {
        if (text == null) return "";
        return text.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private double jaccardSimilarityFromSets(java.util.Set<String> h1, java.util.Set<String> h2) {
        if (h1.isEmpty() || h2.isEmpty()) return 0.0;
        
        int intersection = 0;
        for (String s : h1) if (h2.contains(s)) intersection++;
        
        int union = h1.size() + h2.size() - intersection;
        if (union == 0) return 0.0;
        
        return (double) intersection / union;
    }
}
