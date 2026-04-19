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
import com.example.safeinbox.utils.TurboExecutor;
import com.example.safeinbox.detection.SpamScoreEngine;
import com.example.safeinbox.models.ClassificationResult;
import android.widget.ProgressBar;

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
    private volatile SpamScoreEngine spamScoreEngine;

    private SmsObserver smsObserver;
    private RcsMessageReceiver rcsReceiver;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private View headerStandard, headerSelection;
    private TextView textSelectionCount;

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
            // Use loadMessages() which has full dedup + blacklist pipeline
            bgExecutor.execute(() -> loadMessages());
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

        // Advanced Search Indicator Logic
        adapter.setOnFilterResultsListener(count -> {
            String query = adapter.getCurrentQuery();
            if (count == 0 && !query.isEmpty()) {
                emptyText.setText("🔍 No results found for \"" + query + "\"");
                emptyText.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else if (count == 0) {
                emptyText.setText("📭 Your inbox is empty");
                emptyText.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                emptyText.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            }
        });

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

        findViewById(R.id.btn_home).setOnClickListener(v -> {
            startActivity(new Intent(InboxActivity.this, DashboardActivity.class));
            finish();
        });
        setupSelectionToolbar();

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

    private void setupSelectionToolbar() {
        headerStandard = findViewById(R.id.header_standard);
        headerSelection = findViewById(R.id.header_selection);
        textSelectionCount = findViewById(R.id.text_selection_count);

        findViewById(R.id.btn_cancel_selection).setOnClickListener(v -> {
            adapter.clearSelection();
        });

        findViewById(R.id.btn_batch_archive).setOnClickListener(v -> {
            Set<Long> ids = adapter.getSelectedIds();
            List<SmsMessage> selectedMessages = adapter.getSelectedMessages();
            
            new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                    .setTitle("📦 Archive " + ids.size() + " messages")
                    .setMessage("These messages will be hidden from your inbox.")
                    .setPositiveButton("Archive", (d, w) -> {
                        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                        Set<String> archived = new HashSet<>(prefs.getStringSet(Constants.KEY_ARCHIVED_DEDUP_IDS, new HashSet<>()));
                        for (SmsMessage msg : selectedMessages) {
                            if (msg.getDedupId() != null) archived.add(msg.getDedupId());
                        }
                        prefs.edit().putStringSet(Constants.KEY_ARCHIVED_DEDUP_IDS, archived).apply();
                        
                        adapter.clearSelection();
                        loadMessages();
                        Toast.makeText(this, "📦 Batch archived", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        findViewById(R.id.btn_batch_delete).setOnClickListener(v -> {
            Set<Long> ids = adapter.getSelectedIds();
            new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                    .setTitle("🗑️ Delete " + ids.size() + " messages")
                    .setMessage("This action is permanent and cannot be undone.")
                    .setPositiveButton("Delete Permanently", (d, w) -> {
                        bgExecutor.execute(() -> {
                            spamDao.deleteMessagesBatch(ids);
                            loadMessages();
                        });
                        adapter.clearSelection();
                        Toast.makeText(this, "🗑️ Batch deleted", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    @Override
    public void onSelectionChanged(int count) {
        if (count > 0) {
            headerStandard.setVisibility(View.GONE);
            headerSelection.setVisibility(View.VISIBLE);
            textSelectionCount.setText(count + " selected");
        } else {
            headerStandard.setVisibility(View.VISIBLE);
            headerSelection.setVisibility(View.GONE);
        }
    }

    @Override
    public void onBackPressed() {
        if (adapter.isSelectionMode()) {
            adapter.clearSelection();
        } else {
            super.onBackPressed();
        }
    }

    // ───────────────────── Data loading helpers ───────────────────────────────

    private void loadMessages() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            Set<String> archived = prefs.getStringSet(Constants.KEY_ARCHIVED_DEDUP_IDS, new HashSet<>());
            
            final List<SmsMessage> allMessages = spamDao.getMessages(false);
            
            // TURBO: Load ALL deleted fingerprints in 1 query (not N queries!)
            Set<String> deletedFingerprints = spamDao.getAllDeletedFingerprints();
            
            // --- NUCLEAR DEDUPLICATION HAMMER ---
            // Use BODY FINGERPRINT (body only, no sender/timestamp) as the dedup key.
            // This catches the same message arriving via SMS (sender="650025") and 
            // RCS (sender="Airtel") which would have different content fingerprints.
            java.util.Map<String, SmsMessage> dedupMap = new java.util.LinkedHashMap<>();
            for (SmsMessage msg : allMessages) {
                // Skip archived messages
                if (msg.getDedupId() != null && archived.contains(msg.getDedupId())) continue;
                
                // Content fingerprint for deletion blacklist check
                String contentFp = spamDao.generateContentFingerprint(msg);
                if (deletedFingerprints.contains(contentFp)) continue;
                
                // Body fingerprint for UI dedup — catches cross-path duplicates
                String bodyFp = spamDao.generateBodyFingerprint(msg);
                if (bodyFp.isEmpty()) continue; // Skip empty body messages
                
                // Also check body-only blacklist (catches cross-sender deletions)
                if (deletedFingerprints.contains("body|" + bodyFp)) continue;
                
                if (!dedupMap.containsKey(bodyFp)) {
                    dedupMap.put(bodyFp, msg);
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
                    
                    // DELETION BLACKLIST: Never re-add a message the user explicitly deleted
                    // Uses content fingerprint (sender+body only, no timestamp) for reliable matching
                    String contentFp = spamDao.generateContentFingerprint(msg);
                    if (spamDao.isMessageDeleted(contentFp)) {
                        continue;
                    }
                    
                    // CHECK BLOCKED NUMBERS: If sender was blocked, always classify as spam
                    boolean isBlocked = spamDao.isBlockedNumber(msg.getSender());
                    boolean isSpam = isBlocked || spamDetector.isSpam(msg.getSender(), msg.getBody());
                    msg.setSpam(isSpam);
                    msg.setBlocked(isBlocked);
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
        showSecurityAuditDialog(message, position);
    }

    private void showSecurityAuditDialog(SmsMessage message, int position) {
        if (isFinishing() || isDestroyed()) return;

        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.dialog_security_audit, null);

        TextView senderTv = view.findViewById(R.id.audit_sender);
        TextView dateTv = view.findViewById(R.id.audit_date);
        TextView bodyTv = view.findViewById(R.id.audit_body_preview);
        
        TextView mlScoreTv = view.findViewById(R.id.audit_ml_score);
        ProgressBar mlProgress = view.findViewById(R.id.audit_ml_progress);
        
        TextView keywordScoreTv = view.findViewById(R.id.audit_keyword_score);
        ProgressBar keywordProgress = view.findViewById(R.id.audit_keyword_progress);
        
        TextView historyScoreTv = view.findViewById(R.id.audit_history_score);
        ProgressBar historyProgress = view.findViewById(R.id.audit_history_progress);
        
        TextView verdictTv = view.findViewById(R.id.audit_final_verdict);

        senderTv.setText(message.getSenderName() != null ? message.getSenderName() + " (" + message.getSender() + ")" : message.getSender());
        dateTv.setText(message.getFormattedDate());
        bodyTv.setText(message.getBody());

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setView(view)
                .create();

        // Custom High-Contrast Buttons natively inside the XML layout
        view.findViewById(R.id.btn_audit_positive).setOnClickListener(v -> {
            onMarkSpam(message, position);
            dialog.dismiss();
        });
        
        view.findViewById(R.id.btn_audit_negative).setOnClickListener(v -> {
            // Because they are in Inbox, "Not Spam" is technically "Archive" or "Ignore"
            onArchive(message, position);
            dialog.dismiss();
        });
        
        view.findViewById(R.id.btn_audit_block).setOnClickListener(v -> {
            onBlockReport(message, position);
            dialog.dismiss();
        });
        
        view.findViewById(R.id.btn_audit_cancel).setOnClickListener(v -> dialog.dismiss());

        dialog.show();

        // Perform Audit Scan in Background
        bgExecutor.execute(() -> {
            if (spamScoreEngine == null) {
                if (spamDetector == null) spamDetector = SpamDetector.getInstance(this);
                spamScoreEngine = new SpamScoreEngine(this, (com.example.safeinbox.detection.MLClassifier) spamDetector.getClassifier());
            }
            
            String[] words = message.getBody().toLowerCase().split("\\s+");
            ClassificationResult result = spamScoreEngine.classify(message.getSender(), message.getBody(), words);
            
            safePostToUi(() -> {
                mlScoreTv.setText(result.mlScore + "% confidence");
                mlProgress.setProgress(result.mlScore);
                
                keywordScoreTv.setText(result.keywordScore > 20 ? "High Risk" : "Normal");
                keywordProgress.setProgress(result.keywordScore * 2); // Scale to 100
                
                historyScoreTv.setText(result.historyScore > 10 ? "Known Spammer" : "Clean History");
                historyProgress.setProgress(result.historyScore * 5); // Scale to 100
                
                verdictTv.setText("VERDICT: " + result.reason);
                if (result.isSpam) {
                    verdictTv.setTextColor(getResources().getColor(R.color.error_red));
                } else {
                    verdictTv.setTextColor(getResources().getColor(R.color.success_green));
                }
            });
        });
    }

    // ── Block & Report ──────────────────────────────────────────────────────
    @Override
    public void onMarkSpam(SmsMessage message, int position) {
        // --- TURBO: Optimistic UI update ---
        adapter.removeMessageById(message.getId());
        checkEmptyState();
        Toast.makeText(this, "⚠️ Marked as spam", Toast.LENGTH_SHORT).show();
        
        bgExecutor.execute(() -> {
            // Use updateSpamStatus (which only sets is_spam=1, not is_blocked=1)
            // It still uses the cross-sender body matching I implemented earlier.
            spamDao.updateSpamStatus(message.getId(), true);
            
            if (spamDetector == null) spamDetector = SpamDetector.getInstance(this);
            spamDetector.trainFromFeedback(message.getBody(), true);
        });
    }

    @Override
    public void onBlockReport(SmsMessage message, int position) {
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("🚫 Block & Report")
                .setMessage("Block \"" + message.getSender() + "\" and report as spam?\n\nAll future messages from this number will be automatically blocked.")
                .setPositiveButton("Block & Report", (d, w) -> {
                    // --- TURBO FEEDBACK: Optimistic UI Update ---
                    adapter.removeMessageById(message.getId());
                    checkEmptyState();
                    Toast.makeText(this, "🚫 \"" + message.getSender() + "\" blocked & reported", Toast.LENGTH_LONG).show();

                    bgExecutor.execute(() -> {
                        try {
                            // BOOST: Use atomic batch transaction for near-instant DB update
                            spamDao.blockAndReportBatch(message.getId(), message.getSender());
                            
                            // BOOST: Offload ML and System blocking to parallel Turbo Pool
                            TurboExecutor.getInstance().execute(() -> {
                                try {
                                    if (spamDetector == null) spamDetector = SpamDetector.getInstance(this);
                                    spamDetector.trainFromFeedback(message.getBody(), true);

                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                        android.content.ContentValues cv = new android.content.ContentValues();
                                        cv.put(android.provider.BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, message.getSender());
                                        getContentResolver().insert(android.provider.BlockedNumberContract.BlockedNumbers.CONTENT_URI, cv);
                                    }
                                } catch (Exception e) {
                                    Log.w(TAG, "Error in parallel turbo tasks", e);
                                }
                            });
                        } catch (Exception e) {
                            Log.e(TAG, "Block error", e);
                            safePostToUi(() -> Toast.makeText(this, "❌ Failed to sync block", Toast.LENGTH_SHORT).show());
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

    @Override
    public void onUnblock(SmsMessage message, int position) {
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("🔓 Unblock Sender")
                .setMessage("Unblock \"" + message.getSender() + "\"?\n\nFuture messages from this number will arrive in your Inbox.")
                .setPositiveButton("Unblock", (d, w) -> {
                    Toast.makeText(this, "✅ Sender unblocked", Toast.LENGTH_SHORT).show();

                    bgExecutor.execute(() -> {
                        try {
                            // 1. Unblock in local database
                            spamDao.removeSpamNumber(message.getSender());
                            
                            // 2. Refresh UI to remove blocked indicators ([BLOCKED] tags)
                            loadMessages();
                            
                            // 3. Try to undo system-level block (Android 7+)
                            TurboExecutor.getInstance().execute(() -> {
                                try {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                        try {
                                            getContentResolver().delete(android.provider.BlockedNumberContract.BlockedNumbers.CONTENT_URI, 
                                                android.provider.BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER + "=?", 
                                                new String[]{message.getSender()});
                                        } catch (SecurityException se) {
                                            Log.w(TAG, "Cannot remove system block", se);
                                        }
                                    }
                                } catch (Exception e) {
                                    Log.w(TAG, "Error in parallel turbo tasks", e);
                                }
                            });
                        } catch (Exception e) {
                            Log.e(TAG, "Unblock error", e);
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Delete ──────────────────────────────────────────────────────────────
    @Override
    public void onDelete(SmsMessage message, int position) {
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("🗑️ Delete Message")
                .setMessage("Are you sure you want to permanently delete this message from \"" + message.getSender() + "\"?")
                .setPositiveButton("Delete", (d, w) -> {
                    // --- TURBO FEEDBACK: Optimistic UI Update ---
                    adapter.removeMessageById(message.getId());
                    checkEmptyState();
                    Toast.makeText(this, "🗑️ Message deleted", Toast.LENGTH_SHORT).show();

                    bgExecutor.execute(() -> {
                        try {
                            spamDao.deleteMessageById(message.getId());
                        } catch (Exception e) {
                            Log.e(TAG, "Delete error", e);
                            safePostToUi(() -> Toast.makeText(this, "❌ Background deletion failed", Toast.LENGTH_SHORT).show());
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
                    // Store archived message dedupId in SharedPreferences
                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                    Set<String> archived = new HashSet<>(prefs.getStringSet(Constants.KEY_ARCHIVED_DEDUP_IDS, new HashSet<>()));
                    if (message.getDedupId() != null) {
                        archived.add(message.getDedupId());
                        prefs.edit().putStringSet(Constants.KEY_ARCHIVED_DEDUP_IDS, archived).apply();
                    }

                    adapter.removeMessageById(message.getId());
                    checkEmptyState();
                    Toast.makeText(this, "📦 Message archived (fingerprint-aware)", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onUnarchive(SmsMessage message, int position) {
        // Not used in InboxActivity, option is only shown in Archive screen
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
