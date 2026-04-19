package com.example.safeinbox.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.safeinbox.R;
import com.example.safeinbox.database.SpamDao;
import com.example.safeinbox.models.SmsMessage;
import com.example.safeinbox.utils.TurboExecutor;

import java.util.ArrayList;
import java.util.List;

public class AnalyticsActivity extends AppCompatActivity {

    private SpamDao spamDao;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private TextView textSpamCount, textHamCount, textBlacklistCount;
    private TextView textSpamPercent, textHamPercent;
    private View barSpam, barHam;
    private LinearLayout layoutTopSenders, layoutTopKeywords;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analytics);

        spamDao = new SpamDao(this);

        textSpamCount = findViewById(R.id.text_spam_count);
        textHamCount = findViewById(R.id.text_ham_count);
        textBlacklistCount = findViewById(R.id.text_blacklist_count);
        textSpamPercent = findViewById(R.id.text_spam_percent);
        textHamPercent = findViewById(R.id.text_ham_percent);
        barSpam = findViewById(R.id.bar_spam);
        barHam = findViewById(R.id.bar_ham);
        layoutTopSenders = findViewById(R.id.layout_top_senders);
        layoutTopKeywords = findViewById(R.id.layout_top_keywords);

        findViewById(R.id.btn_back_analytics).setOnClickListener(v -> finish());

        // ============ INTERACTIVE HERO CARDS ============
        // Blocked Card -> Opens Spam Vault
        findViewById(R.id.card_blocked).setOnClickListener(v -> {
            startActivity(new Intent(this, SpamActivity.class));
        });

        // Safe Scans Card -> Opens Inbox
        findViewById(R.id.card_safe).setOnClickListener(v -> {
            startActivity(new Intent(this, InboxActivity.class));
        });

        // Filters Card -> Shows Blacklist entries in dialog
        findViewById(R.id.card_filters).setOnClickListener(v -> showFiltersDialog());

        loadAnalyticsData();
    }

    // ============ FILTERS DIALOG ============
    private void showFiltersDialog() {
        TurboExecutor.getInstance().execute(() -> {
            List<String> blacklist = spamDao.getBlacklistedNumbers();
            mainHandler.post(() -> {
                if (isDestroyed() || isFinishing()) return;

                StringBuilder sb = new StringBuilder();
                if (blacklist.isEmpty()) {
                    sb.append("<font color='#B0B0C0'>No filters added yet.<br><br>To add filters, block a sender from the Inbox or Spam Vault.</font>");
                } else {
                    sb.append("<font color='#80DEEA'>Your active spam filters:</font><br><br>");
                    for (int i = 0; i < blacklist.size(); i++) {
                        sb.append("<font color='#FF5252'>🚫 </font><font color='#FFFFFF'>").append(blacklist.get(i)).append("</font><br>");
                    }
                    sb.append("<br><font color='#FFC107'><b>Total: </b></font><font color='#FFFFFF'>").append(blacklist.size()).append(" blocked numbers</font>");
                }

                new AlertDialog.Builder(AnalyticsActivity.this, R.style.DarkAlertDialog)
                        .setTitle(android.text.Html.fromHtml("<font color='#CE93D8'><b>🛡️ Active Filters</b></font>", android.text.Html.FROM_HTML_MODE_LEGACY))
                        .setMessage(android.text.Html.fromHtml(sb.toString(), android.text.Html.FROM_HTML_MODE_LEGACY))
                        .setPositiveButton("Close", null)
                        .show();
            });
        });
    }

    // ============ SENDER DETAIL DIALOG ============
    private void showSenderDetail(String sender, int hits) {
        TurboExecutor.getInstance().execute(() -> {
            List<SmsMessage> allSpam = spamDao.getMessages(true);
            List<SmsMessage> senderMessages = new ArrayList<>();
            for (SmsMessage msg : allSpam) {
                String normalizedSender = msg.getSender().replaceAll("[^\\d+]", "");
                String normalizedTarget = sender.replaceAll("[^\\d+]", "");
                if (normalizedSender.contains(normalizedTarget) || normalizedTarget.contains(normalizedSender)
                        || msg.getSender().equalsIgnoreCase(sender)) {
                    senderMessages.add(msg);
                }
            }

            mainHandler.post(() -> {
                if (isDestroyed() || isFinishing()) return;

                StringBuilder sb = new StringBuilder();
                sb.append("<font color='#FFC107'><b>Spam blocks: </b></font><font color='#FFFFFF'>").append(hits).append("</font><br>");
                sb.append("<font color='#80DEEA'><b>Records found: </b></font><font color='#FFFFFF'>").append(senderMessages.size()).append("</font><br><br>");

                if (senderMessages.isEmpty()) {
                    sb.append("<font color='#B0B0C0'>Messages from this sender have been purged by the AI Shredder.</font>");
                } else {
                    int shown = Math.min(senderMessages.size(), 2);
                    for (int i = 0; i < shown; i++) {
                        SmsMessage m = senderMessages.get(i);
                        String preview = m.getBody();
                        if (preview.length() > 80) preview = preview.substring(0, 80) + "...";
                        sb.append("<font color='#CE93D8'><b>• </b></font><font color='#E1BEE7'>").append(preview).append("</font><br><br>");
                    }
                    if (senderMessages.size() > 2) {
                        sb.append("<font color='#B0B0C0'><i>... and ").append(senderMessages.size() - 2).append(" more hidden.</i></font>");
                    }
                }

                AlertDialog.Builder builder = new AlertDialog.Builder(AnalyticsActivity.this, R.style.DarkAlertDialog)
                        .setTitle(android.text.Html.fromHtml("<font color='#FFC107'><b>" + sender + "</b></font>", android.text.Html.FROM_HTML_MODE_LEGACY))
                        .setMessage(android.text.Html.fromHtml(sb.toString(), android.text.Html.FROM_HTML_MODE_LEGACY))
                        .setPositiveButton("Close", null);
                        
                if (senderMessages.size() > 0) {
                    builder.setNeutralButton("Open Vault", (dialog, which) -> {
                        Intent intent = new Intent(AnalyticsActivity.this, SpamActivity.class);
                        intent.putExtra("search_query", sender);
                        startActivity(intent);
                    });
                }
                builder.show();
            });
        });
    }

    // ============ CATEGORY DETAIL DIALOG ============
    private void showCategoryDetail(String category, int impact) {
        TurboExecutor.getInstance().execute(() -> {
            List<SmsMessage> allSpam = spamDao.getMessages(true);
            List<SmsMessage> matched = new ArrayList<>();

            for (SmsMessage msg : allSpam) {
                String body = msg.getBody().toLowerCase();
                boolean match = false;
                if (category.contains("Financial") && (body.contains("bank") || body.contains("account") || body.contains("blocked") || body.contains("card") || body.contains("kyc"))) {
                    match = true;
                } else if (category.contains("Identity") && (body.contains("otp") || body.contains("code") || body.contains("verify") || body.contains("login"))) {
                    match = true;
                } else if (category.contains("Delivery") && (body.contains("order") || body.contains("package") || body.contains("ship") || body.contains("track"))) {
                    match = true;
                } else if (category.contains("Promotional") && (body.contains("win") || body.contains("prize") || body.contains("lottery") || body.contains("offer"))) {
                    match = true;
                } else if (category.contains("Unknown")) {
                    if (!(body.contains("bank") || body.contains("account") || body.contains("blocked") || body.contains("card") || body.contains("kyc")
                            || body.contains("otp") || body.contains("code") || body.contains("verify") || body.contains("login")
                            || body.contains("order") || body.contains("package") || body.contains("ship") || body.contains("track")
                            || body.contains("win") || body.contains("prize") || body.contains("lottery") || body.contains("offer"))) {
                        match = true;
                    }
                }
                if (match) matched.add(msg);
            }

            mainHandler.post(() -> {
                if (isDestroyed() || isFinishing()) return;

                StringBuilder sb = new StringBuilder();
                sb.append("<font color='#FFC107'><b>Messages classified: </b></font><font color='#FFFFFF'>").append(matched.size()).append("</font><br><br>");

                if (matched.isEmpty()) {
                    sb.append("<font color='#B0B0C0'>No matching messages found in the vault.</font>");
                } else {
                    int shown = Math.min(matched.size(), 2);
                    for (int i = 0; i < shown; i++) {
                        SmsMessage m = matched.get(i);
                        String preview = m.getBody();
                        if (preview.length() > 80) preview = preview.substring(0, 80) + "...";
                        sb.append("<font color='#80DEEA'><b>• </b></font><font color='#E1BEE7'>").append(preview).append("</font><br><br>");
                    }
                    if (matched.size() > 2) {
                        sb.append("<font color='#B0B0C0'><i>... and ").append(matched.size() - 2).append(" more hidden.</i></font>");
                    }
                }

                AlertDialog.Builder builder = new AlertDialog.Builder(AnalyticsActivity.this, R.style.DarkAlertDialog)
                        .setTitle(android.text.Html.fromHtml("<font color='#CE93D8'><b>" + category + "</b></font>", android.text.Html.FROM_HTML_MODE_LEGACY))
                        .setMessage(android.text.Html.fromHtml(sb.toString(), android.text.Html.FROM_HTML_MODE_LEGACY))
                        .setPositiveButton("Close", null);
                        
                if (matched.size() > 0) {
                    builder.setNeutralButton("Open Vault", (dialog, which) -> {
                        Intent intent = new Intent(AnalyticsActivity.this, SpamActivity.class);
                        String cleanTag = category.split(" ")[1].toLowerCase();
                        intent.putExtra("search_query", "#" + cleanTag);
                        startActivity(intent);
                    });
                }
                builder.show();
            });
        });
    }

    // ============ LOAD DATA ============
    private void loadAnalyticsData() {
        TurboExecutor.getInstance().execute(() -> {
            int spamCount = spamDao.getSpamMessageCount();
            int hamCount = spamDao.getSafeMessageCount();
            int blacklistCount = spamDao.getGlobalBlacklistCount();
            
            List<Pair<String, Integer>> topSenders = spamDao.getTopBlockedSenders(3);
            List<Pair<String, Integer>> threatCategories = spamDao.getThreatCategories();

            mainHandler.post(() -> {
                if (isDestroyed() || isFinishing()) return;

                // Update Hero Cards
                textSpamCount.setText(String.valueOf(spamCount));
                textHamCount.setText(String.valueOf(hamCount));
                textBlacklistCount.setText(String.valueOf(blacklistCount));

                // Update Distribution Bar (Real Data Logic)
                int total = spamCount + hamCount;
                if (total > 0) {
                    float spamWeight = (float) spamCount / total;
                    float hamWeight = (float) hamCount / total;
                    
                    int spamPercent = Math.round(spamWeight * 100);
                    int hamPercent = 100 - spamPercent;
                    
                    textSpamPercent.setText("Spam Filtered: " + spamPercent + "%");
                    textHamPercent.setText("Safe Integrity: " + hamPercent + "%");

                    float sW = Math.max(0.05f, spamWeight);
                    float hW = Math.max(0.05f, hamWeight);
                    
                    LinearLayout.LayoutParams lpSpam = (LinearLayout.LayoutParams) barSpam.getLayoutParams();
                    lpSpam.weight = sW;
                    barSpam.setLayoutParams(lpSpam);

                    LinearLayout.LayoutParams lpHam = (LinearLayout.LayoutParams) barHam.getLayoutParams();
                    lpHam.weight = hW;
                    barHam.setLayoutParams(lpHam);
                } else {
                    textSpamPercent.setText("Spam Filtered: 0%");
                    textHamPercent.setText("Safe Integrity: 0%");
                    
                    LinearLayout.LayoutParams lpSpam = (LinearLayout.LayoutParams) barSpam.getLayoutParams();
                    lpSpam.weight = 1.0f;
                    barSpam.setLayoutParams(lpSpam);

                    LinearLayout.LayoutParams lpHam = (LinearLayout.LayoutParams) barHam.getLayoutParams();
                    lpHam.weight = 1.0f;
                    barHam.setLayoutParams(lpHam);
                }

                // Update Top Senders (Clickable)
                layoutTopSenders.removeAllViews();
                if (topSenders.isEmpty()) {
                    addSimpleTextRow(layoutTopSenders, "No spam senders blocked yet.");
                } else {
                    for (Pair<String, Integer> sender : topSenders) {
                        addClickableRow(layoutTopSenders, sender.first, sender.second + " hits",
                                () -> showSenderDetail(sender.first, sender.second));
                    }
                }

                // Update AI Spam Signatures (Clickable)
                layoutTopKeywords.removeAllViews();
                if (threatCategories.isEmpty()) {
                    addSimpleTextRow(layoutTopKeywords, "No spam signatures detected.");
                } else {
                    for (Pair<String, Integer> category : threatCategories) {
                        addClickableRow(layoutTopKeywords, category.first, "impact: " + category.second,
                                () -> showCategoryDetail(category.first, category.second));
                    }
                }
            });
        });
    }

    // ============ UI HELPERS ============
    private void addSimpleTextRow(LinearLayout parent, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(getResources().getColor(R.color.text_hint));
        tv.setTextSize(14);
        tv.setPadding(0, 8, 0, 8);
        parent.addView(tv);
    }

    private void addClickableRow(LinearLayout parent, String title, String detail, Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT));
        row.setPadding(0, 12, 0, 12);
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(getDrawable(android.R.drawable.list_selector_background));
        row.setOnClickListener(v -> onClick.run());
        
        TextView titleTv = new TextView(this);
        titleTv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        titleTv.setText(title);
        titleTv.setTextColor(getResources().getColor(R.color.text_white));
        titleTv.setTextSize(15);
        titleTv.setMaxLines(1);
        
        TextView detailTv = new TextView(this);
        detailTv.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        detailTv.setText(detail);
        detailTv.setTextColor(getResources().getColor(R.color.primary_light_purple));
        detailTv.setTextSize(14);
        
        TextView arrowTv = new TextView(this);
        arrowTv.setText("  \u203a");
        arrowTv.setTextColor(getResources().getColor(R.color.text_hint));
        arrowTv.setTextSize(18);
        
        row.addView(titleTv);
        row.addView(detailTv);
        row.addView(arrowTv);
        parent.addView(row);
    }
}
