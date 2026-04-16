package com.example.safeinbox.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SpamActivity extends AppCompatActivity implements SmsAdapter.OnMessageActionListener {

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private SmsAdapter adapter;
    private TextView emptyText;
    private SpamDao spamDao;
    private SpamDetector spamDetector;
    private SmsObserver smsObserver;

    private class SmsObserver extends android.database.ContentObserver {
        public SmsObserver(android.os.Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            // Trigger background UI refresh when SMS DB changes
            loadSpamMessages();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spam);

        spamDao = new SpamDao(this);
        spamDetector = new SpamDetector(this);

        recyclerView = findViewById(R.id.recycler_spam);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_spam);
        emptyText = findViewById(R.id.text_empty_spam);
        EditText searchEdit = findViewById(R.id.edit_search_spam);

        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                loadSpamMessages();
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SmsAdapter(new ArrayList<SmsMessage>(), this);
        recyclerView.setAdapter(adapter);

        searchEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.getFilter().filter(s);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Back button
        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        loadSpamMessages();

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
        loadSpamMessages();
    }

    private void loadSpamMessages() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<SmsMessage> messages = spamDao.getMessages(true);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
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
                });
            }
        }).start();
    }

    // ==================== Module 7: Feedback Dialog ====================

    @Override
    public void onMessageClick(SmsMessage message, int position) {
        showSpamDetailDialog(message, position);
    }

    private void showSpamDetailDialog(final SmsMessage message, final int position) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_message_detail, null);

        TextView senderView = dialogView.findViewById(R.id.detail_sender);
        TextView dateView = dialogView.findViewById(R.id.detail_date);
        TextView bodyView = dialogView.findViewById(R.id.detail_body);

        senderView.setText(message.getSender());
        bodyView.setText(message.getBody());

        dateView.setText(message.getFormattedDate());

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setPositiveButton("Restore to Inbox", (dialog, which) -> {
            // Mark as not spam — move back to inbox
            spamDao.insertFeedback(message.getId(), Constants.LABEL_NOT_SPAM);
            spamDetector.trainFromFeedback(message.getBody(), false);

            // Remove from spam list and refresh
            adapter.removeMessage(position);
            if (adapter.getItemCount() == 0) {
                emptyText.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            }
        });
        builder.setNegativeButton("Mark as Spam", (dialog, which) -> {
            // Confirm it's spam (already flagged, just record feedback)
            spamDao.insertFeedback(message.getId(), Constants.LABEL_SPAM);
            spamDetector.trainFromFeedback(message.getBody(), true);
            dialog.dismiss();
        });
        builder.setNeutralButton("Close", (dialog, which) -> dialog.dismiss());
        builder.show();
    }
}
