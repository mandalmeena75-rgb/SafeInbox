package com.example.safeinbox.activities;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.safeinbox.R;
import com.example.safeinbox.adapters.SmsAdapter;
import com.example.safeinbox.database.SpamDao;
import com.example.safeinbox.detection.SpamDetector;
import com.example.safeinbox.models.SmsMessage;
import com.example.safeinbox.services.SmsNotificationListener;
import com.example.safeinbox.sms.SmsReader;
import com.example.safeinbox.utils.Constants;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class InboxActivity extends AppCompatActivity implements SmsAdapter.OnMessageActionListener {

    private static final String TAG = "InboxActivity";
    private static final String PREFS_NAME = "SafeInboxPrefs";
    private static final String KEY_ARCHIVED_IDS = "archived_message_ids";

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private SmsAdapter adapter;
    private TextView emptyText;
    private SpamDao spamDao;

    // Lazy-initialized on background thread only (never blocks UI)
    private volatile SpamDetector spamDetector;

    private SmsObserver smsObserver;
    private RcsMessageReceiver rcsReceiver;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Single background thread for all DB work – prevents contention
    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();

    // ── ContentObserver for standard SMS/MMS changes ─────────────────────────
    private class SmsObserver extends ContentObserver {
        SmsObserver(Handler handler) { super(handler); }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            Log.d(TAG, "SMS content changed – syncing");
            bgExecutor.execute(() -> syncNewMessages());
        }
    }

    // ── BroadcastReceiver for RCS messages saved by the NotificationListener ─
    private class RcsMessageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "RCS broadcast received – refreshing");
            bgExecutor.execute(() -> {
                final List<SmsMessage> messages = spamDao.getMessages(false);
                safePostToUi(() -> updateList(messages));
            });
        }
    }

    // ─────────────────────────── Lifecycle ───────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inbox);

        spamDao = new SpamDao(this);

        recyclerView       = findViewById(R.id.recycler_inbox);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh);
        emptyText          = findViewById(R.id.text_empty);
        EditText searchEdit = findViewById(R.id.edit_search);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SmsAdapter(new ArrayList<>(), this);
        recyclerView.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(
                () -> bgExecutor.execute(this::syncNewMessages));

        searchEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.getFilter().filter(s);
            }
        });

        findViewById(R.id.btn_view_spam).setOnClickListener(v ->
                startActivity(new Intent(InboxActivity.this, SpamActivity.class)));

        // Fast initial load from DB (no sync yet)
        bgExecutor.execute(this::loadMessages);

        // Background sync of any new standard SMS since last run
        bgExecutor.execute(this::syncNewMessages);

        // Watch SMS content provider for standard SMS/MMS changes
        smsObserver = new SmsObserver(mainHandler);
        getContentResolver().registerContentObserver(
                Uri.parse("content://sms"), true, smsObserver);

        // Watch for RCS messages from SmsNotificationListener
        rcsReceiver = new RcsMessageReceiver();
        IntentFilter filter = new IntentFilter(SmsNotificationListener.ACTION_RCS_MESSAGE_RECEIVED);
        androidx.core.content.ContextCompat.registerReceiver(this, rcsReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_EXPORTED);
    }

    @Override
    protected void onResume() {
        super.onResume();
        bgExecutor.execute(this::loadMessages);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (smsObserver != null) {
            getContentResolver().unregisterContentObserver(smsObserver);
        }
        if (rcsReceiver != null) {
            try { unregisterReceiver(rcsReceiver); } catch (Exception ignored) {}
        }
    }

    // ───────────────────── Data loading helpers ───────────────────────────────

    /** Load all non-spam messages from DB and push to the RecyclerView. */
    private void loadMessages() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            Set<String> archived = prefs.getStringSet(KEY_ARCHIVED_IDS, new HashSet<>());
            
            final List<SmsMessage> allMessages = spamDao.getMessages(false);
            
            // --- JAVA-SIDE DEDUPLICATION HAMMER ---
            // Manually collapse identical messages (content-wise) to ensure 0 duplicates in UI.
            java.util.Map<String, SmsMessage> dedupMap = new java.util.LinkedHashMap<>();
            for (SmsMessage msg : allMessages) {
                if (archived.contains(String.valueOf(msg.getId()))) continue;
                
                String body = msg.getBody() != null ? msg.getBody().trim().toLowerCase() : "";
                String key = com.example.safeinbox.utils.ContactUtils.normalizeSender(msg.getSender()) + "|" + body;
                
                if (!dedupMap.containsKey(key)) {
                    dedupMap.put(key, msg);
                }
            }
            final List<SmsMessage> filteredMessages = new ArrayList<>(dedupMap.values());
            // --------------------------------------
            safePostToUi(() -> updateList(filteredMessages));
        } catch (Exception e) {
            Log.e(TAG, "loadMessages error", e);
        }
    }

    private void updateList(List<SmsMessage> messages) {
        adapter.setMessages(messages);
        if (messages.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
        if (swipeRefreshLayout.isRefreshing()) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    /**
     * Syncs new SMS/MMS from the system content provider.
     * RCS messages arrive separately via SmsNotificationListener.
     */
    private void syncNewMessages() {
        try {
            // Lazy-init SpamDetector on background thread (heavy constructor)
            if (spamDetector == null) {
                spamDetector = SpamDetector.getInstance(this);
            }

            long lastTimestamp = spamDao.getLastSyncTimestamp();
            SmsReader reader = new SmsReader(this);
            List<SmsMessage> newMessages = reader.readMessagesSince(lastTimestamp);

            if (!newMessages.isEmpty()) {
                long maxTimestamp = lastTimestamp;
                for (SmsMessage msg : newMessages) {
                    // Advanced Dedup: Check if this message was already processed in the last 60s
                    // This catches cross-provider race conditions (RCS/SMS)
                    if (spamDao.checkIfMessageExistsRecently(msg.getSender(), msg.getBody())) {
                        continue;
                    }
                    
                    boolean isSpam = spamDetector.isSpam(msg.getSender(), msg.getBody());
                    msg.setSpam(isSpam);
                    spamDao.insertMessage(msg);
                    if (msg.getDate() > maxTimestamp) {
                        maxTimestamp = msg.getDate();
                    }
                }
                // Save the new point in time
                spamDao.saveLastSyncTimestamp(maxTimestamp);
            }
        } catch (Exception e) {
            Log.e(TAG, "syncNewMessages error", e);
        }
        // Always refresh the list
        loadMessages();
    }

    /** Posts a Runnable to the main thread only if the Activity is still alive. */
    private void safePostToUi(Runnable r) {
        if (!isFinishing() && !isDestroyed()) {
            mainHandler.post(r);
        }
    }

    private void checkEmptyState() {
        if (adapter.getItemCount() == 0) {
            emptyText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        }
    }

    // ─────────────────── Menu Action Handlers ─────────────────────────────────

    @Override
    public void onMessageClick(SmsMessage message, int position) {
        showDetailDialog(message, position);
    }

    private void showDetailDialog(SmsMessage message, int position) {
        if (isFinishing() || isDestroyed()) return;

        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.dialog_message_detail, null);

        ((TextView) view.findViewById(R.id.detail_sender)).setText(message.getSender());
        ((TextView) view.findViewById(R.id.detail_body)).setText(message.getBody());
        ((TextView) view.findViewById(R.id.detail_date)).setText(message.getFormattedDate());

        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setView(view)
                .setPositiveButton("Mark as Spam", (d, w) -> {
                    bgExecutor.execute(() -> {
                        try {
                            spamDao.insertFeedback(message.getId(), Constants.LABEL_SPAM);
                            
                            if (spamDetector == null) spamDetector = SpamDetector.getInstance(this);
                            spamDetector.trainFromFeedback(message.getBody(), true);
                        } catch (Exception e) {
                            Log.e(TAG, "Mark spam error", e);
                        }
                    });
                    adapter.removeMessageById(message.getId());
                    checkEmptyState();
                    Toast.makeText(this, "✅ Moved to Spam", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Ham", (d, w) -> {
                    bgExecutor.execute(() -> {
                        try {
                            spamDao.insertFeedback(message.getId(), Constants.LABEL_HAM);
                            
                            if (spamDetector == null) spamDetector = SpamDetector.getInstance(this);
                            spamDetector.trainFromFeedback(message.getBody(), false);
                        } catch (Exception e) {
                            Log.e(TAG, "Ham error", e);
                        }
                    });
                    Toast.makeText(this, "✅ Confirmed as safe (Ham)", Toast.LENGTH_SHORT).show();
                    d.dismiss();
                })
                .setNeutralButton("Close", (d, w) -> d.dismiss())
                .show();
    }

    // ── Block & Report ──────────────────────────────────────────────────────
    @Override
    public void onBlockReport(SmsMessage message, int position) {
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("🚫 Block & Report")
                .setMessage("Block \"" + message.getSender() + "\" and report as spam?\n\nAll future messages from this number will be automatically blocked.")
                .setPositiveButton("Block & Report", (d, w) -> {
                    bgExecutor.execute(() -> {
                        try {
                            // 1. Add number to our internal spam blocklist
                            spamDao.addSpamNumber(message.getSender());
                            
                            // 2. Mark this message as spam in the DB
                            spamDao.insertFeedback(message.getId(), Constants.LABEL_SPAM);
                            
                            // 3. Train the ML model
                            if (spamDetector == null) spamDetector = SpamDetector.getInstance(this);
                            spamDetector.trainFromFeedback(message.getBody(), true);

                            // 4. Try to add to system block list (Android 7+)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                try {
                                    android.content.ContentValues values = new android.content.ContentValues();
                                    values.put(android.provider.BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, message.getSender());
                                    getContentResolver().insert(android.provider.BlockedNumberContract.BlockedNumbers.CONTENT_URI, values);
                                } catch (SecurityException se) {
                                    Log.w(TAG, "Cannot add to system block list – not default dialer", se);
                                }
                            }

                            safePostToUi(() -> {
                                adapter.removeMessageById(message.getId());
                                checkEmptyState();
                                Toast.makeText(this, "🚫 \"" + message.getSender() + "\" blocked & reported", Toast.LENGTH_LONG).show();
                            });
                        } catch (Exception e) {
                            Log.e(TAG, "Block error", e);
                            safePostToUi(() -> Toast.makeText(this, "❌ Failed to block", Toast.LENGTH_SHORT).show());
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Mark Ham (Direct Action) ──────────────────────────────────────────
    @Override
    public void onMarkNotSpam(SmsMessage message, int position) {
        bgExecutor.execute(() -> {
            try {
                spamDao.insertFeedback(message.getId(), Constants.LABEL_HAM);
                if (spamDetector == null) spamDetector = SpamDetector.getInstance(this);
                spamDetector.trainFromFeedback(message.getBody(), false);
                
                safePostToUi(() -> Toast.makeText(this, "✅ Confirmed as safe (Ham)", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                Log.e(TAG, "Mark ham error", e);
            }
        });
    }

    // ── Delete ──────────────────────────────────────────────────────────────
    @Override
    public void onDelete(SmsMessage message, int position) {
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("🗑️ Delete Message")
                .setMessage("Are you sure you want to permanently delete this message from \"" + message.getSender() + "\"?")
                .setPositiveButton("Delete", (d, w) -> {
                    bgExecutor.execute(() -> {
                        try {
                            spamDao.deleteMessageById(message.getId());
                            safePostToUi(() -> {
                                adapter.removeMessageById(message.getId());
                                checkEmptyState();
                                Toast.makeText(this, "🗑️ Message deleted", Toast.LENGTH_SHORT).show();
                            });
                        } catch (Exception e) {
                            Log.e(TAG, "Delete error", e);
                            safePostToUi(() -> Toast.makeText(this, "❌ Failed to delete", Toast.LENGTH_SHORT).show());
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Archive ─────────────────────────────────────────────────────────────
    @Override
    public void onArchive(SmsMessage message, int position) {
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("📦 Archive Message")
                .setMessage("Archive this message? It will be hidden from your inbox but not deleted.")
                .setPositiveButton("Archive", (d, w) -> {
                    // Store archived message ID in SharedPreferences
                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                    Set<String> archived = new HashSet<>(prefs.getStringSet(KEY_ARCHIVED_IDS, new HashSet<>()));
                    archived.add(String.valueOf(message.getId()));
                    prefs.edit().putStringSet(KEY_ARCHIVED_IDS, archived).apply();

                    adapter.removeMessageById(message.getId());
                    checkEmptyState();
                    Toast.makeText(this, "📦 Message archived", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Help & Feedback ─────────────────────────────────────────────────────
    @Override
    public void onHelpFeedback(SmsMessage message, int position) {
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("ℹ️ Help & Feedback")
                .setMessage("SafeInbox uses a 6-layer hybrid spam detection engine:\n\n"
                        + "🔹 Keyword Filtering\n"
                        + "🔹 Naive Bayes ML Classifier\n"
                        + "🔹 Sender Reputation Analysis\n"
                        + "🔹 Number Blocklist\n"
                        + "🔹 Pattern Detection\n"
                        + "🔹 Weighted Scoring Engine\n\n"
                        + "Use the 'Block & Report' or 'Not Spam' options to train the AI and improve accuracy.\n\n"
                        + "Version: 1.0")
                .setPositiveButton("Got it", null)
                .setNeutralButton("Report Issue", (d, w) -> {
                    // Open email intent for feedback
                    Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
                    emailIntent.setData(Uri.parse("mailto:"));
                    emailIntent.putExtra(Intent.EXTRA_SUBJECT, "SafeInbox Feedback");
                    emailIntent.putExtra(Intent.EXTRA_TEXT, "Message ID: " + message.getId() 
                            + "\nSender: " + message.getSender() 
                            + "\n\nDescribe your issue:\n");
                    try {
                        startActivity(Intent.createChooser(emailIntent, "Send Feedback"));
                    } catch (Exception e) {
                        Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }
}
