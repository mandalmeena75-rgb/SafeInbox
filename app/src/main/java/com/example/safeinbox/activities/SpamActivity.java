package com.example.safeinbox.activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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
public class SpamActivity extends AppCompatActivity implements SmsAdapter.OnMessageActionListener {

    private static final String TAG = "SpamActivity";
    private static final String PREFS_NAME = "SafeInboxPrefs";
    private static final String KEY_ARCHIVED_IDS = "archived_message_ids"; // Left for backward compatibility if needed

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private SmsAdapter adapter;
    private TextView emptyText;
    private SpamDao spamDao;
    private volatile SpamDetector spamDetector;
    private volatile SpamScoreEngine spamScoreEngine;
    private SmsObserver smsObserver;
    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();

    private View headerStandard, headerSelection;
    private TextView textSelectionCount;

    private class SmsObserver extends android.database.ContentObserver {
        public SmsObserver(android.os.Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            bgExecutor.execute(() -> loadSpamMessages());
        }
    }

    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    
    private void safePostToUi(Runnable r) {
        if (!isFinishing() && !isDestroyed()) {
            mainHandler.post(r);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spam);

        spamDao = new SpamDao(this);

        recyclerView = findViewById(R.id.recycler_spam);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_spam);
        emptyText = findViewById(R.id.text_empty_spam);
        EditText searchEdit = findViewById(R.id.edit_search_spam);

        swipeRefreshLayout.setOnRefreshListener(() -> bgExecutor.execute(this::loadSpamMessages));

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SmsAdapter(new ArrayList<SmsMessage>(), this);
        recyclerView.setAdapter(adapter);

        // Advanced Search Indicator Logic
        adapter.setOnFilterResultsListener(count -> {
            String query = adapter.getCurrentQuery();
            if (count == 0 && !query.isEmpty()) {
                emptyText.setText("🔍 No spam found for \"" + query + "\"");
                emptyText.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else if (count == 0) {
                emptyText.setText("🛡️ You are safe! No spam here.");
                emptyText.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                emptyText.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            }
        });

        searchEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.getFilter().filter(s);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Back button
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // Selection Mode Initialization
        setupSelectionToolbar();

        // Support Smart Tag / Intent queries
        String initialQuery = getIntent().getStringExtra("search_query");
        if (initialQuery != null && !initialQuery.isEmpty()) {
            searchEdit.setText(initialQuery);
        }

        findViewById(R.id.btn_sync).setOnClickListener(v -> {
            Toast.makeText(this, "🛰️ Syncing Global Blacklist...", Toast.LENGTH_SHORT).show();
            bgExecutor.execute(() -> {
                final int newEntries = spamDao.downloadGlobalBlacklist();
                safePostToUi(() -> {
                    if (newEntries > 0) {
                        Toast.makeText(this, "🛡️ Success: " + newEntries + " global blocks added", Toast.LENGTH_LONG).show();
                        loadSpamMessages(); // Refresh list in case new entries match
                    } else {
                        Toast.makeText(this, "✅ Your list is already up to date", Toast.LENGTH_SHORT).show();
                    }
                });
            });
        });

        bgExecutor.execute(this::loadSpamMessages);

        // Register the live listener
        smsObserver = new SmsObserver(new android.os.Handler(android.os.Looper.getMainLooper()));
        getContentResolver().registerContentObserver(
                android.net.Uri.parse("content://sms"), true, smsObserver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (smsObserver != null) {
            getContentResolver().unregisterContentObserver(smsObserver);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        bgExecutor.execute(this::loadSpamMessages);
    }

    private void setupSelectionToolbar() {
        headerStandard = findViewById(R.id.header_standard_spam);
        headerSelection = findViewById(R.id.header_selection_spam);
        textSelectionCount = findViewById(R.id.text_selection_count_spam);

        findViewById(R.id.btn_cancel_selection_spam).setOnClickListener(v -> {
            adapter.clearSelection();
        });

        findViewById(R.id.btn_batch_restore).setOnClickListener(v -> {
            Set<Long> ids = adapter.getSelectedIds();
            List<SmsMessage> selectedMessages = adapter.getSelectedMessages();
            
            new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                    .setTitle("🔄 Restore " + ids.size() + " messages")
                    .setMessage("These messages will be moved back to your inbox.")
                    .setPositiveButton("Restore", (d, w) -> {
                        bgExecutor.execute(() -> {
                            for (SmsMessage msg : selectedMessages) {
                                spamDao.insertFeedback(msg.getId(), Constants.LABEL_HAM);
                                spamDetector.trainFromFeedback(msg.getBody(), false);
                                // Automatically unblock the sender if they were previously blocked
                                spamDao.removeSpamNumber(msg.getSender());
                            }
                            loadSpamMessages();
                        });
                        adapter.clearSelection();
                        Toast.makeText(this, "✅ Batch restored to Inbox", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        findViewById(R.id.btn_batch_delete_spam).setOnClickListener(v -> {
            Set<Long> ids = adapter.getSelectedIds();
            new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                    .setTitle("🗑️ Permanent Delete " + ids.size() + " items")
                    .setMessage("Burn them all? This cannot be undone.")
                    .setPositiveButton("Shred Permanently", (d, w) -> {
                        bgExecutor.execute(() -> {
                            spamDao.deleteMessagesBatch(ids);
                            loadSpamMessages();
                        });
                        adapter.clearSelection();
                        Toast.makeText(this, "🔥 Messages incinerated", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        findViewById(R.id.btn_batch_archive_spam).setOnClickListener(v -> {
            Set<Long> ids = adapter.getSelectedIds();
            List<SmsMessage> selectedMessages = adapter.getSelectedMessages();

            new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                    .setTitle("📦 Archive " + ids.size() + " messages")
                    .setMessage("Move selected spam messages to the archive vault? They will be hidden but not deleted.")
                    .setPositiveButton("Archive", (d, w) -> {
                        bgExecutor.execute(() -> {
                            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                            Set<String> archived = new HashSet<>(prefs.getStringSet(Constants.KEY_ARCHIVED_DEDUP_IDS, new HashSet<>()));
                            for (SmsMessage msg : selectedMessages) {
                                if (msg.getDedupId() != null) archived.add(msg.getDedupId());
                            }
                            prefs.edit().putStringSet(Constants.KEY_ARCHIVED_DEDUP_IDS, archived).apply();
                            loadSpamMessages();
                        });
                        adapter.clearSelection();
                        Toast.makeText(this, "📦 Batch archived", Toast.LENGTH_SHORT).show();
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

    private void loadSpamMessages() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            Set<String> archived = prefs.getStringSet(Constants.KEY_ARCHIVED_DEDUP_IDS, new HashSet<>());
            
            final List<SmsMessage> allMessages = spamDao.getMessages(true);
            
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
            
            safePostToUi(() -> {
                adapter.setMessages(filteredMessages);
                
                // Re-apply filter if intent/user populated the search bar during load
                String currentSearch = ((EditText)findViewById(R.id.edit_search_spam)).getText().toString();
                if (!currentSearch.isEmpty()) {
                    adapter.getFilter().filter(currentSearch);
                } else if (filteredMessages.isEmpty()) {
                    emptyText.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    emptyText.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                }
                
                if (swipeRefreshLayout.isRefreshing()) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "loadSpamMessages error", e);
        }
    }

    private void checkEmptyState() {
        if (adapter.getItemCount() == 0) {
            emptyText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        }
    }

    private SpamDetector getSpamDetector() {
        if (spamDetector == null) {
            spamDetector = SpamDetector.getInstance(this);
        }
        return spamDetector;
    }

    // ==================== Menu Action Handlers ====================

    @Override
    public void onMessageClick(SmsMessage message, int position) {
        showSecurityAuditDialog(message, position);
    }

    private void showSecurityAuditDialog(final SmsMessage message, final int position) {
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
        android.widget.Button btnPositive = view.findViewById(R.id.btn_audit_positive);
        android.widget.Button btnNegative = view.findViewById(R.id.btn_audit_negative);
        android.widget.Button btnBlock = view.findViewById(R.id.btn_audit_block);
        android.widget.Button btnCancel = view.findViewById(R.id.btn_audit_cancel);
        
        btnPositive.setText("Restore (Ham)");
        btnPositive.setOnClickListener(v -> {
            onMarkNotSpam(message, position);
            dialog.dismiss();
        });
        
        btnNegative.setText("Archive");
        btnNegative.setOnClickListener(v -> {
            onArchive(message, position);
            dialog.dismiss();
        });
        
        btnBlock.setOnClickListener(v -> {
            onBlockReport(message, position);
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

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
        // Redundant in SpamActivity as message is already in spam
        Log.d(TAG, "onMarkSpam called in SpamActivity - already spam");
    }

    @Override
    public void onBlockReport(SmsMessage message, int position) {
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("🚫 Block & Report")
                .setMessage("Block \"" + message.getSender() + "\" permanently?\n\nAll future messages from this number will be automatically blocked.")
                .setPositiveButton("Block & Report", (d, w) -> {
                    // --- TURBO FEEDBACK: Optimistic UI Update ---
                    adapter.removeMessageById(message.getId());
                    checkEmptyState();
                    Toast.makeText(this, "🚫 \"" + message.getSender() + "\" blocked permanently", Toast.LENGTH_LONG).show();

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

    // ── Mark Ham (restore to inbox) ────────────────────────────────────
    @Override
    public void onMarkNotSpam(SmsMessage message, int position) {
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("✅ Restore to Inbox (Ham)")
                .setMessage("Move this message back to your inbox? This will train the AI that messages like this are safe (Ham) and unblock the sender.")
                .setPositiveButton("Restore", (d, w) -> {
                    // --- TURBO FEEDBACK: Optimistic UI Update ---
                    adapter.removeMessageById(message.getId());
                    checkEmptyState();
                    Toast.makeText(this, "✅ Restored to Inbox", Toast.LENGTH_SHORT).show();

                    bgExecutor.execute(() -> {
                        try {
                            // 1. Unblock the sender first (full reversal)
                            spamDao.removeSpamNumber(message.getSender());
                            
                            // 2. Mark as Ham
                            spamDao.insertFeedback(message.getId(), Constants.LABEL_HAM);
                            
                            // BOOST: Offload ML training
                            TurboExecutor.getInstance().execute(() -> {
                                try {
                                    if (spamDetector == null) spamDetector = SpamDetector.getInstance(this);
                                    spamDetector.trainFromFeedback(message.getBody(), false);
                                } catch (Exception e) {
                                    Log.w(TAG, "Error in parallel training", e);
                                }
                            });
                        } catch (Exception e) {
                            Log.e(TAG, "Restore error", e);
                            safePostToUi(() -> Toast.makeText(this, "❌ Failed to restore", Toast.LENGTH_SHORT).show());
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Unblock Sender ────────────────────────────────────────────────────────
    @Override
    public void onUnblock(SmsMessage message, int position) {
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("🔓 Unblock Sender")
                .setMessage("Are you sure you want to unblock this sender?\n\nFuture messages will go to your Inbox, but current messages will stay in Spam.")
                .setPositiveButton("Unblock", (d, w) -> {
                    Toast.makeText(this, "✅ Sender unblocked", Toast.LENGTH_SHORT).show();

                    bgExecutor.execute(() -> {
                        try {
                            // Just unblock the sender - do NOT mark as HAM or move to inbox
                            spamDao.removeSpamNumber(message.getSender());
                            
                            // Refresh UI to remove blocked indicators
                            loadSpamMessages();
                            
                            TurboExecutor.getInstance().execute(() -> {
                                try {
                                    // Try to undo system-level block (Android 7+)
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
                .setMessage("Permanently delete this spam message from \"" + message.getSender() + "\"?")
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
                .setMessage("Archive this spam message? It will be hidden but not deleted.")
                .setPositiveButton("Archive", (d, w) -> {
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
        // Not used in SpamActivity, option is only shown in Archive screen
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
                        + "Use 'Not Spam' to restore wrongly flagged messages.\n\n"
                        + "Version: 1.0")
                .setPositiveButton("Got it", null)
                .setNeutralButton("Report Issue", (d, w) -> {
                    Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
                    emailIntent.setData(Uri.parse("mailto:"));
                    emailIntent.putExtra(Intent.EXTRA_SUBJECT, "SafeInbox Feedback - Spam Issue");
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
