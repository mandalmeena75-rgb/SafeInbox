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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SpamActivity extends AppCompatActivity implements SmsAdapter.OnMessageActionListener {

    private static final String TAG = "SpamActivity";
    private static final String PREFS_NAME = "SafeInboxPrefs";
    private static final String KEY_ARCHIVED_IDS = "archived_message_ids";

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private SmsAdapter adapter;
    private TextView emptyText;
    private SpamDao spamDao;
    private volatile SpamDetector spamDetector;
    private SmsObserver smsObserver;
    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();

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

        searchEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.getFilter().filter(s);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Back button
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

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

    private void loadSpamMessages() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            Set<String> archived = prefs.getStringSet(KEY_ARCHIVED_IDS, new HashSet<>());
            
            final List<SmsMessage> allMessages = spamDao.getMessages(true);
            
            // --- JAVA-SIDE DEDUPLICATION HAMMER ---
            // Even if the DB fails to group perfectly, we manually enforce it here.
            java.util.Map<String, SmsMessage> dedupMap = new java.util.LinkedHashMap<>();
            for (SmsMessage msg : allMessages) {
                if (archived.contains(String.valueOf(msg.getId()))) continue;
                
                // Construct a normalized key: stripped sender + lowercased trimmed body
                String body = msg.getBody() != null ? msg.getBody().trim().toLowerCase() : "";
                String key = com.example.safeinbox.utils.ContactUtils.normalizeSender(msg.getSender()) + "|" + body;
                
                if (!dedupMap.containsKey(key)) {
                    dedupMap.put(key, msg);
                }
            }
            final List<SmsMessage> filteredMessages = new ArrayList<>(dedupMap.values());
            // --------------------------------------
            
            safePostToUi(() -> {
                adapter.setMessages(filteredMessages);
                if (filteredMessages.isEmpty()) {
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
        showSpamDetailDialog(message, position);
    }

    private void showSpamDetailDialog(final SmsMessage message, final int position) {
        if (isFinishing() || isDestroyed()) return;

        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_message_detail, null);

        ((TextView) dialogView.findViewById(R.id.detail_sender)).setText(message.getSender());
        ((TextView) dialogView.findViewById(R.id.detail_body)).setText(message.getBody());
        ((TextView) dialogView.findViewById(R.id.detail_date)).setText(message.getFormattedDate());

        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setView(dialogView)
                .setPositiveButton("Restore to Inbox (Ham)", (dialog, which) -> {
                    bgExecutor.execute(() -> {
                        spamDao.insertFeedback(message.getId(), Constants.LABEL_HAM);
                        getSpamDetector().trainFromFeedback(message.getBody(), false);
                    });
                    adapter.removeMessageById(message.getId());
                    checkEmptyState();
                    Toast.makeText(this, "✅ Restored to Inbox as Ham", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Confirm Spam", (dialog, which) -> {
                    bgExecutor.execute(() -> {
                        spamDao.insertFeedback(message.getId(), Constants.LABEL_SPAM);
                        getSpamDetector().trainFromFeedback(message.getBody(), true);
                    });
                    Toast.makeText(this, "⚠️ Confirmed as spam", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setNeutralButton("Close", (dialog, which) -> dialog.dismiss())
                .show();
    }

    // ── Block & Report ──────────────────────────────────────────────────────
    @Override
    public void onBlockReport(SmsMessage message, int position) {
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("🚫 Block & Report")
                .setMessage("Block \"" + message.getSender() + "\" permanently?\n\nAll future messages from this number will be automatically blocked.")
                .setPositiveButton("Block & Report", (d, w) -> {
                    bgExecutor.execute(() -> {
                        try {
                            spamDao.addSpamNumber(message.getSender());
                            spamDao.insertFeedback(message.getId(), Constants.LABEL_SPAM);
                            getSpamDetector().trainFromFeedback(message.getBody(), true);

                            // Try system-level block (Android 7+)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                try {
                                    android.content.ContentValues values = new android.content.ContentValues();
                                    values.put(android.provider.BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, message.getSender());
                                    getContentResolver().insert(android.provider.BlockedNumberContract.BlockedNumbers.CONTENT_URI, values);
                                } catch (SecurityException se) {
                                    Log.w(TAG, "Cannot add to system block list", se);
                                }
                            }

                            safePostToUi(() -> {
                                adapter.removeMessageById(message.getId());
                                checkEmptyState();
                                Toast.makeText(this, "🚫 \"" + message.getSender() + "\" blocked permanently", Toast.LENGTH_LONG).show();
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

    // ── Mark Ham (restore to inbox) ────────────────────────────────────
    @Override
    public void onMarkNotSpam(SmsMessage message, int position) {
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("✅ Restore to Inbox (Ham)")
                .setMessage("Move this message back to your inbox? This will train the AI that messages like this are safe (Ham).")
                .setPositiveButton("Restore", (d, w) -> {
                    bgExecutor.execute(() -> {
                        spamDao.insertFeedback(message.getId(), Constants.LABEL_HAM);
                        getSpamDetector().trainFromFeedback(message.getBody(), false);
                    });
                    adapter.removeMessageById(message.getId());
                    checkEmptyState();
                    Toast.makeText(this, "✅ Restored to Inbox", Toast.LENGTH_SHORT).show();
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
                .setMessage("Archive this spam message? It will be hidden but not deleted.")
                .setPositiveButton("Archive", (d, w) -> {
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
