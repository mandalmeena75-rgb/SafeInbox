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

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh data every time user returns to Insights
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

    // --- REMOVED OLD DIALOG LOGIC ---
    // User requested direct navigation to pages exactly.

    // --- REMOVED OLD DIALOG LOGIC ---
    // User requested direct navigation to pages exactly.

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

                // Update Top Senders (DIRECT NAVIGATION)
                layoutTopSenders.removeAllViews();
                if (topSenders.isEmpty()) {
                    addSimpleTextRow(layoutTopSenders, "No spam senders blocked yet.");
                } else {
                    for (Pair<String, Integer> sender : topSenders) {
                        addClickableRow(layoutTopSenders, 0, sender.first, sender.second + " hits", 
                                () -> shiftToVault("sender", sender.first));
                    }
                }

                // Update AI Spam Signatures (DIRECT NAVIGATION + ICONS)
                layoutTopKeywords.removeAllViews();
                if (threatCategories.isEmpty()) {
                    addSimpleTextRow(layoutTopKeywords, "No spam signatures detected.");
                } else {
                    for (Pair<String, Integer> category : threatCategories) {
                        int iconRes = getIconForCategory(category.first);
                        addClickableRow(layoutTopKeywords, iconRes, category.first, "impact: " + category.second,
                                () -> shiftToVault("category", category.first));
                    }
                }
            });
        });
    }

    private int getIconForCategory(String category) {
        if (category.contains("Financial")) return R.drawable.ic_bank;
        if (category.contains("Identity")) return R.drawable.ic_security_lock;
        if (category.contains("Delivery")) return R.drawable.ic_delivery_box;
        if (category.contains("Promotional")) return R.drawable.ic_marketing_megaphone;
        return R.drawable.ic_fingerprint; // Default
    }

    private void shiftToVault(String filterType, String value) {
        Intent intent = new Intent(this, SpamActivity.class);
        if ("sender".equals(filterType)) {
            intent.putExtra("filter_sender", value);
        } else {
            // Category filter - map display text to search keywords
            String k = value.split(" ")[0].toLowerCase();
            intent.putExtra("filter_query", k);
        }
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
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

    private void addClickableRow(LinearLayout parent, int iconRes, String title, String detail, Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 4, 0, 4);
        row.setLayoutParams(rowParams);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, 16, 0, 16);
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(getDrawable(android.R.drawable.list_selector_background));
        row.setOnClickListener(v -> onClick.run());
        
        if (iconRes != 0) {
            android.widget.ImageView iconIv = new android.widget.ImageView(this);
            int size = (int) (24 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(0, 0, 16, 0);
            iconIv.setLayoutParams(lp);
            iconIv.setImageResource(iconRes);
            row.addView(iconIv);
        }

        TextView titleTv = new TextView(this);
        titleTv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        titleTv.setText(title);
        titleTv.setTextColor(getResources().getColor(R.color.text_white));
        titleTv.setTextSize(15);
        titleTv.setTypeface(null, android.graphics.Typeface.BOLD);
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
