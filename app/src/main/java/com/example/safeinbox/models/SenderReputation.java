package com.example.safeinbox.models;

/**
 * Model class for sender reputation stats, cached in memory.
 */
public class SenderReputation {
    public int spamHits = 0;
    public int hamHits = 0;
    public int userSpamReports = 0;
    public int userHamReports = 0;

    public boolean isEmpty() {
        return spamHits == 0 && hamHits == 0 && userSpamReports == 0 && userHamReports == 0;
    }
}
