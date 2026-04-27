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
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.activity.OnBackPressedCallback;

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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class InboxActivity extends AppCompatActivity implements SmsAdapter.OnMessageActionListener {

    private static final String TAG = "InboxActivity";
    private static final String PREFS_NAME = "SafeInboxPrefs";

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private SmsAdapter adapter;
    private TextView emptyText;
    private SpamDao spamDao;

    private volatile SpamDetector spamDetector;
    private volatile SpamScoreEngine spamScoreEngine;
    private volatile boolean isSyncing = false;

    private SmsObserver smsObserver;
    private RcsMessageReceiver rcsReceiver;
    private BroadcastReceiver rescanReceiver;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private View headerStandard, headerSelection;
    private TextView textSelectionCount;

    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();

    private class SmsObserver extends ContentObserver {
        SmsObserver(Handler handler) { super(handler); }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            bgExecutor.execute(() -> syncNewMessages());
        }
    }

    private class RcsMessageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            bgExecutor.execute(() -> loadMessages());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inbox);

        spamDao = new SpamDao(this);
        recyclerView = findViewById(R.id.recycler_inbox);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh);
        emptyText = findViewById(R.id.text_empty);
        EditText searchEdit = findViewById(R.id.edit_search);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SmsAdapter(new ArrayList<>(), this);
        recyclerView.setAdapter(adapter);

        setupSelectionToolbar();

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

        swipeRefreshLayout.setOnRefreshListener(() -> bgExecutor.execute(this::syncNewMessages));

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

        bgExecutor.execute(this::loadMessages);
        bgExecutor.execute(this::syncNewMessages);

        smsObserver = new SmsObserver(mainHandler);
        getContentResolver().registerContentObserver(Uri.parse("content://sms"), true, smsObserver);

        rcsReceiver = new RcsMessageReceiver();
        IntentFilter filter = new IntentFilter(SmsNotificationListener.ACTION_RCS_MESSAGE_RECEIVED);
        androidx.core.content.ContextCompat.registerReceiver(this, rcsReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_EXPORTED);

        // --- DATABASE RESCAN LISTENER ---
        rescanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                bgExecutor.execute(() -> loadMessages());
            }
        };
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this).registerReceiver(rescanReceiver, 
                new IntentFilter(com.example.safeinbox.utils.Constants.ACTION_DATABASE_RESCANNED));

        setupBackNavigation();
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (adapter != null && adapter.isSelectionMode()) {
                    adapter.clearSelection();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        bgExecutor.execute(() -> {
            spamDao.repairCorruptedMessages();
            loadMessages();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (smsObserver != null) getContentResolver().unregisterContentObserver(smsObserver);
        if (rcsReceiver != null) {
            try { unregisterReceiver(rcsReceiver); } catch (Exception ignored) {}
        }
        if (rescanReceiver != null) {
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this).unregisterReceiver(rescanReceiver);
        }
    }

    private void setupSelectionToolbar() {
        headerStandard = findViewById(R.id.header_standard);
        headerSelection = findViewById(R.id.header_selection);
        textSelectionCount = findViewById(R.id.text_selection_count);

        findViewById(R.id.btn_cancel_selection).setOnClickListener(v -> adapter.clearSelection());

        findViewById(R.id.btn_batch_archive).setOnClickListener(v -> {
            List<SmsMessage> selected = adapter.getSelectedMessages();
            new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                    .setTitle("📦 Archive " + selected.size() + " messages")
                    .setMessage("Move to archive?")
                    .setPositiveButton("Archive", (d, w) -> {
                        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                        Set<String> archived = new HashSet<>(prefs.getStringSet(Constants.KEY_ARCHIVED_DEDUP_IDS, new HashSet<>()));
                        for (SmsMessage msg : selected) if (msg.getDedupId() != null) archived.add(msg.getDedupId());
                        prefs.edit().putStringSet(Constants.KEY_ARCHIVED_DEDUP_IDS, archived).apply();
                        adapter.clearSelection();
                        loadMessages();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        findViewById(R.id.btn_batch_delete).setOnClickListener(v -> {
            Set<Long> ids = adapter.getSelectedIds();
            new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                    .setTitle("🗑️ Delete " + ids.size() + " messages")
                    .setPositiveButton("Delete", (d, w) -> {
                        bgExecutor.execute(() -> {
                            spamDao.deleteMessagesBatch(ids);
                            loadMessages();
                        });
                        adapter.clearSelection();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void loadMessages() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            Set<String> archived = prefs.getStringSet(Constants.KEY_ARCHIVED_DEDUP_IDS, new HashSet<>());
            List<SmsMessage> allMessages = spamDao.getMessages(false);
            Set<String> deletedFingerprints = spamDao.getAllDeletedFingerprints();
            
            java.util.Map<String, SmsMessage> dedupMap = new java.util.LinkedHashMap<>();
            for (SmsMessage msg : allMessages) {
                if (msg.getDedupId() != null && archived.contains(msg.getDedupId())) continue;
                String contentFp = spamDao.generateContentFingerprint(msg);
                if (deletedFingerprints.contains(contentFp)) continue;
                String bodyFp = spamDao.generateBodyFingerprint(msg);
                if (bodyFp.isEmpty()) continue;
                if (!dedupMap.containsKey(bodyFp)) dedupMap.put(bodyFp, msg);
            }
            final List<SmsMessage> filtered = new ArrayList<>(dedupMap.values());
            safePostToUi(() -> {
                adapter.setMessages(filtered);
                checkEmptyState();
                // Spinner must ONLY stop after the data is truly ready in the adapter
                if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
            });
        } catch (Exception e) { 
            Log.e(TAG, "Load error", e); 
            safePostToUi(() -> {
                if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            });
        }
    }

    private void syncNewMessages() {
        if (isSyncing) return;
        isSyncing = true;
        try {
            if (spamDetector == null) spamDetector = SpamDetector.getInstance(this);
            long lastSync = spamDao.getLastSyncTimestamp();
            List<SmsMessage> newMsgs = new SmsReader(this).readMessagesSince(lastSync);
            
            if (!newMsgs.isEmpty()) {
                List<SmsMessage> processedBatch = new ArrayList<>();
                long maxTs = lastSync;
                
                // SUPER BOOST: Preload deleted and existing IDs into memory to avoid O(N) DB lookups
                java.util.Set<String> deletedFingerprints = spamDao.getAllDeletedFingerprints();
                java.util.Set<String> existingDedupIds = spamDao.getAllExistingDedupIds();
                java.util.Set<String> batchDedup = new java.util.HashSet<>();
                
                // Pre-warm the engine caches for ultra-fast batch processing
                spamDetector.prepareSimilarityCache();
                
                for (SmsMessage msg : newMsgs) {
                    // MUST update maxTs for all messages, even skipped ones
                    if (msg.getDate() > maxTs) maxTs = msg.getDate();
                    
                    // O(1) Memory Dedup before heavy classification
                    String dedupId = spamDao.generateDedupId(msg);
                    if (batchDedup.contains(dedupId)) continue;
                    batchDedup.add(dedupId);

                    // NUCLEAR DEDUP: Skip classification for messages already in DB
                    if (existingDedupIds.contains(dedupId)) continue;
                    
                    // O(1) Memory Deletion Check
                    if (deletedFingerprints.contains(spamDao.generateContentFingerprint(msg))) continue;

                    // Industrial Classification (runs entirely in memory now)
                    ClassificationResult res = spamDetector.classifyWithDetails(msg.getSender(), msg.getBody());
                    msg.setClassificationStatus(res.status.name());
                    msg.setHasRiskyLink(res.isRiskyLink);
                    msg.setScore(res.totalScore);
                    msg.setClassificationReason(res.reason);
                    
                    processedBatch.add(msg);
                }
                
                if (!processedBatch.isEmpty()) {
                    // BOOST: Atomic batch insertion in a single transaction
                    spamDao.insertMessagesBatch(processedBatch);
                }
                // ALWAYS update sync timestamp so we don't re-read skipped messages forever
                spamDao.saveLastSyncTimestamp(maxTs);
                
                // Clean up caches
                spamDetector.clearSimilarityCache();
            }
        } catch (Exception e) { 
            Log.e(TAG, "Sync error", e); 
        } finally {
            isSyncing = false;
        }
        loadMessages();
    }

    private void safePostToUi(Runnable r) {
        if (!isFinishing() && !isDestroyed()) mainHandler.post(r);
    }

    private void checkEmptyState() {
        if (adapter.getItemCount() == 0) {
            emptyText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onMessageClick(SmsMessage message, int position) {
        showSecurityAuditDialog(message, position);
    }

    private void showSecurityAuditDialog(SmsMessage message, int position) {
        if (isFinishing() || isDestroyed()) return;

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_security_audit, null);
        TextView bodyTv = view.findViewById(R.id.audit_body_preview);
        ProgressBar mlProgress = view.findViewById(R.id.audit_ml_progress);
        ProgressBar urgencyProgress = view.findViewById(R.id.audit_urgency_progress);
        ProgressBar historyProgress = view.findViewById(R.id.audit_history_progress);
        ProgressBar linkProgress = view.findViewById(R.id.audit_link_progress);
        TextView mlScoreTv = view.findViewById(R.id.audit_ml_score);
        TextView historyScoreTv = view.findViewById(R.id.audit_history_score);
        TextView verdictTv = view.findViewById(R.id.audit_final_verdict);

        ((TextView)view.findViewById(R.id.audit_sender)).setText(message.getSender());
        ((TextView)view.findViewById(R.id.audit_date)).setText(message.getFormattedDate());
        
        com.example.safeinbox.utils.LinkDetector.applyLinkHighlighting(bodyTv, message.getBody(), this, v -> {
            String url = (String) v.getTag(R.id.tag_link_url);
            com.example.safeinbox.utils.LinkDetector.showLinkSafetyPopup(this, url);
        });

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.DarkAlertDialog).setView(view).create();
        view.findViewById(R.id.btn_audit_positive).setOnClickListener(v -> { dialog.dismiss(); onMarkSpam(message, position); });
        view.findViewById(R.id.btn_audit_negative).setOnClickListener(v -> { dialog.dismiss(); onMarkNotSpam(message, position); });
        view.findViewById(R.id.btn_audit_block).setOnClickListener(v -> { dialog.dismiss(); onBlockReport(message, position); });
        view.findViewById(R.id.btn_audit_cancel).setOnClickListener(v -> dialog.dismiss());
        dialog.show();

        bgExecutor.execute(() -> {
            try {
                com.example.safeinbox.detection.SpamDetector sd = com.example.safeinbox.detection.SpamDetector.getInstance(this);
                if (spamScoreEngine == null) {
                    spamScoreEngine = new com.example.safeinbox.detection.SpamScoreEngine(this, (com.example.safeinbox.detection.MLClassifier) sd.getClassifier());
                }
                String[] processedWords = sd.getTextProcessor().process(message.getBody());
                ClassificationResult res = spamScoreEngine.classify(message.getSender(), message.getBody(), processedWords);
                safePostToUi(() -> {                // UNIFIED INTELLIGENCE SCALING
                // Ensure text and progress bars are perfectly synchronized (Fix for "unmatched confidence")
                mlScoreTv.setText(res.confidence + "% Total Confidence");
                mlProgress.setProgress(res.confidence);

                // Intelligence Layers: Scaled to 0-100% for visual clarity
                urgencyProgress.setProgress(Math.min(100, (res.ruleScore + res.semanticScore) * 100 / 30));
                historyProgress.setProgress(Math.min(100, res.similarityScore * 100 / 25));
                linkProgress.setProgress(Math.min(100, res.urlScore * 100 / 30));
                
                // --- ADVANCED VISUALIZATION UPGRADE ---
                androidx.cardview.widget.CardView verdictCard = (androidx.cardview.widget.CardView) verdictTv.getParent();
                
                String verdictText;
                int verdictColor;
                int cardBg;

                switch (res.status) {
                    case SPAM:
                        verdictText = "🚫 SPAM DETECTED";
                        verdictColor = getResources().getColor(R.color.error_red);
                        cardBg = 0xFF2D1212; // Dark Red
                        break;
                    case SUSPICIOUS:
                        verdictText = "⚠️ SUSPICIOUS PATTERN";
                        verdictColor = getResources().getColor(R.color.warning_orange);
                        cardBg = 0xFF2D251B; // Dark Orange/Brown
                        break;
                    case HAM:
                    default:
                        verdictColor = getResources().getColor(R.color.success_green);
                        cardBg = 0xFF1B382A; // Dark Emerald
                        if (res.reason != null && (res.reason.contains("VERIFIED") || res.reason.contains("OTP"))) {
                            verdictText = "🔒 VERIFIED SECURE";
                        } else {
                            verdictText = "✅ SAFE COMMUNICATION";
                        }
                        break;
                }

                verdictTv.setText(verdictText);
                verdictTv.setTextColor(verdictColor);
                verdictCard.setCardBackgroundColor(cardBg);

                // --- DEEP SYNC: SOLDER DATABASE TO FORENSIC DECISION ---
                // If the live engine disagrees with the current database folder, fix it now!
                boolean currentIsSpam = message.getClassificationStatus().equalsIgnoreCase("SPAM") || message.getClassificationStatus().equalsIgnoreCase("SUSPICIOUS");
                boolean newIsSpam = (res.status == ClassificationResult.Status.SPAM || res.status == ClassificationResult.Status.SUSPICIOUS);
                
                if (currentIsSpam != newIsSpam) {
                    bgExecutor.execute(() -> {
                        com.example.safeinbox.database.SpamDao dao = new com.example.safeinbox.database.SpamDao(this);
                        dao.updateMessageStatus(message.getId(), res.status.name(), newIsSpam, res.totalScore, res.reason);
                        safePostToUi(() -> {
                            message.setClassificationStatus(res.status.name());
                            message.setSpam(newIsSpam);
                            adapter.notifyItemChanged(position);
                            Toast.makeText(this, "🔄 Security Profile Updated: Message Re-classified.", Toast.LENGTH_SHORT).show();
                        });
                    });
                }

                // --- POPULATE FORENSIC DEPTH INSIGHTS ---
                // Combine details for better transparency
                String structuralInsight = res.structureScore > 5 ? "🚩 Structural anomalies found." : "✅ Normal structure.";
                String urgencyText = (res.urgencyDetails != null) ? res.urgencyDetails : "Analyzing pressure patterns...";
                ((TextView)view.findViewById(R.id.audit_urgency_depth)).setText(structuralInsight + " " + urgencyText);
                
                ((TextView)view.findViewById(R.id.audit_link_depth_insight)).setText(res.linkDetails != null ? res.linkDetails : "Verifying URL DNA...");
                ((TextView)view.findViewById(R.id.audit_history_depth)).setText(res.historyDetails != null ? res.historyDetails : "Checking trust history...");
                
                // Set high-level labels based on rules + semantics
                int combinedUrgency = res.ruleScore + res.semanticScore;
                ((TextView)view.findViewById(R.id.audit_urgency_score)).setText(combinedUrgency > 25 ? "HIGH" : "NORMAL");
                ((TextView)view.findViewById(R.id.audit_urgency_score)).setTextColor(getResources().getColor(combinedUrgency > 25 ? R.color.error_red : R.color.success_green));

                if (res.isRiskyLink) {
                    ((TextView)view.findViewById(R.id.audit_link_score)).setText(res.reason);
                    ((TextView)view.findViewById(R.id.audit_link_score)).setTextColor(getResources().getColor(R.color.error_red));
                } else {
                    ((TextView)view.findViewById(R.id.audit_link_score)).setText(res.reason != null && !res.reason.isEmpty() ? res.reason : "✅ Link identity verified.");
                    ((TextView)view.findViewById(R.id.audit_link_score)).setTextColor(verdictColor);
                }
            });
            } catch (Exception e) {
                Log.e(TAG, "Forensic audit failed", e);
                safePostToUi(() -> {
                    verdictTv.setText("⚠️ ANALYSIS FAILED");
                    verdictTv.setTextColor(getResources().getColor(R.color.error_red));
                    mlScoreTv.setText("0% Confidence");
                    ((TextView)view.findViewById(R.id.audit_urgency_score)).setText("ERROR");
                    ((TextView)view.findViewById(R.id.audit_link_score)).setText("Analysis Failed");
                    ((TextView)view.findViewById(R.id.audit_history_score)).setText("ERROR");
                });
            }
        });
    }

    @Override
    public void onLinkClick(SmsMessage message, String url) {
        com.example.safeinbox.utils.LinkDetector.showLinkSafetyPopup(this, url);
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
    public void onMarkSpam(SmsMessage message, int position) {
        adapter.removeMessageById(message.getId());
        checkEmptyState();
        bgExecutor.execute(() -> {
            // 1. Mark this message AND all messages from sender as spam
            spamDao.updateSpamStatus(message.getId(), true);
            // 2. Add sender to permanent blocklist so they stay blocked across syncs
            spamDao.addSpamNumber(message.getSender());
            // 3. Record user feedback for ML learning
            spamDao.insertFeedback(message.getId(), Constants.LABEL_SPAM);
            // 4. Reload to clear any remaining duplicates from this sender
            loadMessages();
        });
    }

    @Override
    public void onBlockReport(SmsMessage message, int position) {
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("🚫 Block & Report")
                .setMessage("All messages from this sender will be permanently moved to Spam Vault.")
                .setPositiveButton("Block Permanently", (d, w) -> {
                    adapter.removeMessageById(message.getId());
                    checkEmptyState();
                    bgExecutor.execute(() -> {
                        spamDao.blockAndReportBatch(message.getId(), message.getSender());
                        // Reload to flush ALL remaining messages from this sender out of Inbox
                        loadMessages();
                    });
                })
                .setNegativeButton("Cancel", null).show();
    }

    @Override
    public void onMarkNotSpam(SmsMessage message, int position) {
        Toast.makeText(this, "✅ Marked as safe", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onUnblock(SmsMessage message, int position) {}

    @Override
    public void onDelete(SmsMessage message, int position) {
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("🗑️ Delete")
                .setMessage("This message will be permanently deleted.")
                .setPositiveButton("Delete", (d, w) -> {
                    adapter.removeMessageById(message.getId());
                    checkEmptyState();
                    bgExecutor.execute(() -> {
                        spamDao.deleteMessageById(message.getId());
                        // Reload to ensure UI is in sync
                        loadMessages();
                    });
                })
                .setNegativeButton("Cancel", null).show();
    }

    @Override
    public void onArchive(SmsMessage message, int position) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Set<String> archived = new HashSet<>(prefs.getStringSet(Constants.KEY_ARCHIVED_DEDUP_IDS, new HashSet<>()));
        if (message.getDedupId() != null) archived.add(message.getDedupId());
        prefs.edit().putStringSet(Constants.KEY_ARCHIVED_DEDUP_IDS, archived).apply();
        adapter.removeMessageById(message.getId());
        checkEmptyState();
    }

    @Override
    public void onUnarchive(SmsMessage message, int position) {}

    @Override
    public void onHelpFeedback(SmsMessage message, int position) {
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("ℹ️ Help")
                .setMessage("SafeInbox Forensic Audit provides deep insight into message trust.")
                .setPositiveButton("Got it", null)
                .show();
    }
}


