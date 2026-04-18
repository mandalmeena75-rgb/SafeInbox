package com.example.safeinbox.detection;

import android.content.Context;
import com.example.safeinbox.database.SpamDao;
import com.example.safeinbox.detection.SpamDetector;
import com.example.safeinbox.utils.TurboExecutor;

/**
 * Central coordinator for the Turbo Engine.
 */
public class TurboEngine {

    private static TurboEngine instance;
    private final SpamDetector spamDetector;
    private final TurboExecutor turboExecutor;

    private TurboEngine(Context context) {
        this.spamDetector = SpamDetector.getInstance(context);
        this.turboExecutor = TurboExecutor.getInstance();
    }

    public static synchronized TurboEngine getInstance(Context context) {
        if (instance == null) {
            instance = new TurboEngine(context.getApplicationContext());
        }
        return instance;
    }

    public SpamDetector getSpamDetector() {
        return spamDetector;
    }

    public TurboExecutor getTurboExecutor() {
        return turboExecutor;
    }
}
