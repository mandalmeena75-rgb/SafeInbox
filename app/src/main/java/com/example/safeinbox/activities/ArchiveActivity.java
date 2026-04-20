package com.example.safeinbox.activities;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ProgressBar;
import android.view.LayoutInflater;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.activity.OnBackPressedCallback;

import com.example.safeinbox.R;
import com.example.safeinbox.adapters.SmsAdapter;
import com.example.safeinbox.database.SpamDao;
import com.example.safeinbox.models.SmsMessage;
import com.example.safeinbox.utils.Constants;
import com.example.safeinbox.detection.SpamScoreEngine;
import com.example.safeinbox.detection.SpamDetector;
import com.example.safeinbox.models.ClassificationResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ArchiveActivity extends AppCompatActivity implements SmsAdapter.OnMessageActionListener {

    private static final String TAG = "ArchiveActivity";
    private static final String PREFS_NAME = "SafeInboxPrefs";

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private SmsAdapter adapter;
    private TextView emptyText;
    private SpamDao spamDao;
    private volatile SpamScoreEngine spamScoreEngine;
    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();

    private View headerStandard, headerSelection;
    private TextView textSelectionCount;

    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    private void safePostToUi(Runnable r) {
        if (!isFinishing() && !isDestroyed()) {
            mainHandler.post(r);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_archive);

        spamDao = new SpamDao(this);

        recyclerView = findViewById(R.id.recycler_archive);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_archive);
        emptyText = findViewById(R.id.text_empty_archive);
        EditText searchEdit = findViewById(R.id.edit_search_archive);

        swipeRefreshLayout.setOnRefreshListener(() -> bgExecutor.execute(this::loadArchivedMessages));

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SmsAdapter(new ArrayList<>(), this);
        recyclerView.setAdapter(adapter);

        adapter.setOnFilterResultsListener(count -> {
            String query = adapter.getCurrentQuery();
            if (count == 0 && !query.isEmpty()) {
                emptyText.setText("🔍 No archives found for \"" + query + "\"");
                emptyText.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else if (count == 0) {
                emptyText.setText("📦 Archive Vault is empty");
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

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        setupSelectionToolbar();

        bgExecutor.execute(this::loadArchivedMessages);
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
        bgExecutor.execute(this::loadArchivedMessages);
    }

    private void setupSelectionToolbar() {
        headerStandard = findViewById(R.id.header_standard_archive);
        headerSelection = findViewById(R.id.header_selection_archive);
        textSelectionCount = findViewById(R.id.text_selection_count_archive);

        findViewById(R.id.btn_cancel_selection_archive).setOnClickListener(v -> {
            adapter.clearSelection();
        });

        findViewById(R.id.btn_batch_unarchive).setOnClickListener(v -> {
            Set<Long> ids = adapter.getSelectedIds();
            List<SmsMessage> selectedMessages = adapter.getSelectedMessages();

            new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                    .setTitle("📦 Unarchive " + ids.size() + " messages")
                    .setMessage("These messages will be returned to their original locations (Inbox/Spam).")
                    .setPositiveButton("Unarchive", (d, w) -> {
                        bgExecutor.execute(() -> {
                            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                            Set<String> archived = new HashSet<>(prefs.getStringSet(Constants.KEY_ARCHIVED_DEDUP_IDS, new HashSet<>()));
                            for (SmsMessage msg : selectedMessages) {
                                if (msg.getDedupId() != null) archived.remove(msg.getDedupId());
                            }
                            prefs.edit().putStringSet(Constants.KEY_ARCHIVED_DEDUP_IDS, archived).apply();
                            loadArchivedMessages();
                        });
                        adapter.clearSelection();
                        Toast.makeText(this, "📦 Batch unarchived", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        findViewById(R.id.btn_batch_delete_archive).setOnClickListener(v -> {
            Set<Long> ids = adapter.getSelectedIds();
            new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                    .setTitle("🗑️ Permanent Delete " + ids.size() + " archive items")
                    .setMessage("This action is permanent and cannot be undone.")
                    .setPositiveButton("Shred Permanently", (d, w) -> {
                        bgExecutor.execute(() -> {
                            spamDao.deleteMessagesBatch(ids);
                            loadArchivedMessages();
                        });
                        adapter.clearSelection();
                        Toast.makeText(this, "🔥 Archives deleted", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void loadArchivedMessages() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            Set<String> archivedIds = prefs.getStringSet(Constants.KEY_ARCHIVED_DEDUP_IDS, new HashSet<>());

            if (archivedIds.isEmpty()) {
                safePostToUi(() -> {
                    adapter.setMessages(new ArrayList<>());
                    emptyText.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                    if (swipeRefreshLayout.isRefreshing()) swipeRefreshLayout.setRefreshing(false);
                });
                return;
            }

            // Load all messages from DB (both spam and ham)
            List<SmsMessage> hamMessages = spamDao.getMessages(false);
            List<SmsMessage> spamMessages = spamDao.getMessages(true);

            List<SmsMessage> archivedList = new ArrayList<>();
            for (SmsMessage msg : hamMessages) {
                if (msg.getDedupId() != null && archivedIds.contains(msg.getDedupId())) {
                    archivedList.add(msg);
                }
            }
            for (SmsMessage msg : spamMessages) {
                if (msg.getDedupId() != null && archivedIds.contains(msg.getDedupId())) {
                    archivedList.add(msg);
                }
            }

            safePostToUi(() -> {
                adapter.setMessages(archivedList);
                if (archivedList.isEmpty()) {
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
            Log.e(TAG, "loadArchivedMessages error", e);
        }
    }

    @Override
    public void onMessageClick(SmsMessage message, int position) {
        showSecurityAuditDialog(message, position);
    }

    private void showSecurityAuditDialog(SmsMessage message, int position) {
        if (isFinishing() || isDestroyed()) return;

        android.view.LayoutInflater inflater = android.view.LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.dialog_security_audit, null);

        TextView senderTv = view.findViewById(R.id.audit_sender);
        TextView dateTv = view.findViewById(R.id.audit_date);
        TextView bodyTv = view.findViewById(R.id.audit_body_preview);
        
        TextView mlScoreTv = view.findViewById(R.id.audit_ml_score);
        ProgressBar mlProgress = view.findViewById(R.id.audit_ml_progress);
        
        TextView urgencyTv = view.findViewById(R.id.audit_urgency_score);
        ProgressBar urgencyProgress = view.findViewById(R.id.audit_urgency_progress);
        
        TextView historyScoreTv = view.findViewById(R.id.audit_history_score);
        ProgressBar historyProgress = view.findViewById(R.id.audit_history_progress);
        
        TextView verdictTv = view.findViewById(R.id.audit_final_verdict);
        TextView linkInfoTv = view.findViewById(R.id.audit_link_score);
        ProgressBar linkProgress = view.findViewById(R.id.audit_link_progress);

        senderTv.setText(message.getSenderName() != null ? message.getSenderName() + " (" + message.getSender() + ")" : message.getSender());
        dateTv.setText(message.getFormattedDate());
        
        com.example.safeinbox.utils.LinkDetector.applyLinkHighlighting(bodyTv, message.getBody(), this, v -> {
            String url = (String) v.getTag(R.id.tag_link_url);
            com.example.safeinbox.utils.LinkDetector.showLinkSafetyPopup(this, url);
        });

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setView(view)
                .create();

        // Read-only Forensic mode for Archive
        view.findViewById(R.id.btn_audit_positive).setEnabled(false);
        view.findViewById(R.id.btn_audit_negative).setEnabled(false);
        view.findViewById(R.id.btn_audit_block).setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(this, "Unarchive first to block", Toast.LENGTH_SHORT).show();
        });
        
        view.findViewById(R.id.btn_audit_cancel).setOnClickListener(v -> dialog.dismiss());
        dialog.show();

        bgExecutor.execute(() -> {
            if (spamScoreEngine == null) {
                com.example.safeinbox.detection.SpamDetector sd = com.example.safeinbox.detection.SpamDetector.getInstance(this);
                spamScoreEngine = new com.example.safeinbox.detection.SpamScoreEngine(this, (com.example.safeinbox.detection.MLClassifier) sd.getClassifier());
            }
            
            String[] words = message.getBody().toLowerCase().split("\\s+");
            com.example.safeinbox.models.ClassificationResult result = spamScoreEngine.classify(message.getSender(), message.getBody(), words);
            
            safePostToUi(() -> {
                mlScoreTv.setText(result.mlScore + "% confidence");
                mlProgress.setProgress(result.mlScore);
                urgencyProgress.setProgress(result.urgencyScore);
                historyProgress.setProgress(result.historyScore * 5);
                
                verdictTv.setText("AUDIT VERDICT: " + result.status);
                if (result.status == com.example.safeinbox.models.ClassificationResult.Status.SPAM) {
                    verdictTv.setTextColor(getResources().getColor(R.color.error_red));
                } else {
                    verdictTv.setTextColor(getResources().getColor(R.color.success_green));
                }

                // Advanced Link Safety Mapping
                linkProgress.setProgress(100 - result.linkScore);
                
                // --- ADVANCED VISUALIZATION UPGRADE ---
                androidx.cardview.widget.CardView verdictCard = (androidx.cardview.widget.CardView) verdictTv.getParent();
                
                String verdictText = "DECISION: " + result.status;
                int verdictColor = getResources().getColor(R.color.success_green);
                int cardBg = 0xFF1B382A; // Dark Emerald

                switch (result.status) {
                    case SPAM:
                        verdictColor = getResources().getColor(R.color.error_red);
                        cardBg = 0xFF2D1212; // Dark Red
                        break;
                    case SUSPICIOUS:
                        verdictColor = getResources().getColor(R.color.warning_orange);
                        cardBg = 0xFF2D251B; // Dark Brown
                        break;
                    case OTP:
                        verdictColor = getResources().getColor(R.color.primary_light_purple);
                        cardBg = 0xFF1D1B38; // Dark Purple
                        verdictText = "🔒 SECURE OTP DETECTED";
                        break;
                    case SERVICE:
                        verdictColor = getResources().getColor(R.color.accent_cyan);
                        cardBg = 0xFF102A2B; // Dark Cyan
                        verdictText = "📡 VERIFIED SERVICE";
                        break;
                }

                verdictTv.setText(verdictText);
                verdictTv.setTextColor(verdictColor);
                verdictCard.setCardBackgroundColor(cardBg);

                if (result.isRiskyLink) {
                    ((TextView)view.findViewById(R.id.audit_link_score)).setText(result.reason);
                    ((TextView)view.findViewById(R.id.audit_link_score)).setTextColor(getResources().getColor(R.color.error_red));
                } else {
                    ((TextView)view.findViewById(R.id.audit_link_score)).setText(result.reason != null && !result.reason.isEmpty() ? result.reason : "✅ Link identity verified.");
                    ((TextView)view.findViewById(R.id.audit_link_score)).setTextColor(verdictColor);
                }
            });
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
    public void onMarkSpam(SmsMessage message, int position) {}

    @Override
    public void onBlockReport(SmsMessage message, int position) {}

    @Override
    public void onMarkNotSpam(SmsMessage message, int position) {}

    @Override
    public void onUnblock(SmsMessage message, int position) {}

    @Override
    public void onDelete(SmsMessage message, int position) {
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("🗑️ Delete Archive")
                .setMessage("Permanently delete this archived message?")
                .setPositiveButton("Delete", (d, w) -> {
                    adapter.removeMessageById(message.getId());
                    if (adapter.getItemCount() == 0) emptyText.setVisibility(View.VISIBLE);
                    bgExecutor.execute(() -> {
                        // 1. Remove from archive tracking set
                        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                        Set<String> archived = new HashSet<>(prefs.getStringSet(Constants.KEY_ARCHIVED_DEDUP_IDS, new HashSet<>()));
                        if (message.getDedupId() != null) {
                            archived.remove(message.getDedupId());
                            prefs.edit().putStringSet(Constants.KEY_ARCHIVED_DEDUP_IDS, archived).apply();
                        }
                        // 2. Delete the actual message from DB
                        spamDao.deleteMessageById(message.getId());
                        // 3. Reload
                        loadArchivedMessages();
                    });
                    Toast.makeText(this, "🗑️ Deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onArchive(SmsMessage message, int position) {}

    @Override
    public void onUnarchive(SmsMessage message, int position) {
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("📦 Unarchive")
                .setMessage("Bring this message back to your main folders?")
                .setPositiveButton("Unarchive", (d, w) -> {
                    adapter.removeMessageById(message.getId());
                    if (adapter.getItemCount() == 0) emptyText.setVisibility(View.VISIBLE);
                    
                    bgExecutor.execute(() -> {
                        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                        Set<String> archived = new HashSet<>(prefs.getStringSet(Constants.KEY_ARCHIVED_DEDUP_IDS, new HashSet<>()));
                        if (message.getDedupId() != null) {
                            archived.remove(message.getDedupId());
                            prefs.edit().putStringSet(Constants.KEY_ARCHIVED_DEDUP_IDS, archived).apply();
                        }
                        loadArchivedMessages();
                    });
                    Toast.makeText(this, "📦 Message unarchived", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onHelpFeedback(SmsMessage message, int position) {
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("ℹ️ Help & Feedback")
                .setMessage("SafeInbox Forensic Audit provides deep insight into message trust.")
                .setPositiveButton("Got it", null)
                .show();
    }
}
