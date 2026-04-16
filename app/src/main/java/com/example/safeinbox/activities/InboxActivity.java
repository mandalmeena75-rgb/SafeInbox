package com.example.safeinbox.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.example.safeinbox.sms.SmsReader;
import com.example.safeinbox.utils.Constants;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class InboxActivity extends AppCompatActivity implements SmsAdapter.OnMessageActionListener {

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private SmsAdapter adapter;
    private TextView emptyText;
    private SpamDao spamDao;
    private SpamDetector spamDetector;
    private SmsObserver smsObserver;

    private class SmsObserver extends ContentObserver {
        public SmsObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            // Trigger background sync when phone's SMS database changes
            new Thread(() -> syncNewMessages()).start();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inbox);

        spamDao = new SpamDao(this);
        spamDetector = new SpamDetector(this);

        recyclerView = findViewById(R.id.recycler_inbox);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh);
        emptyText = findViewById(R.id.text_empty);
        EditText searchEdit = findViewById(R.id.edit_search);

        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                new Thread(() -> syncNewMessages()).start();
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

        // Background Sync: Fetch new messages since last run
        new Thread(this::syncNewMessages).start();

        // View Spam button
        findViewById(R.id.btn_view_spam).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(InboxActivity.this, SpamActivity.class);
                startActivity(intent);
            }
        });

        loadMessages();

        // Register the live listener
        smsObserver = new SmsObserver(new Handler(Looper.getMainLooper()));
        getContentResolver().registerContentObserver(
                Uri.parse("content://sms"), true, smsObserver);
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
        loadMessages();
    }

    private void loadMessages() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<SmsMessage> messages = spamDao.getMessages(false);

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
        showMessageDetailDialog(message, position);
    }

    private void showMessageDetailDialog(final SmsMessage message, final int position) {
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
        builder.setPositiveButton("Mark as Spam", (dialog, which) -> {
            // Save feedback and update spam status
            spamDao.insertFeedback(message.getId(), Constants.LABEL_SPAM);
            spamDetector.trainFromFeedback(message.getBody(), true);

            // Remove from inbox list and refresh
            adapter.removeMessage(position);
            if (adapter.getItemCount() == 0) {
                emptyText.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            }
        });
        builder.setNegativeButton("Not Spam", (dialog, which) -> {
            // Confirm it's not spam (already in inbox, so just record feedback)
            spamDao.insertFeedback(message.getId(), Constants.LABEL_NOT_SPAM);
            spamDetector.trainFromFeedback(message.getBody(), false);
            dialog.dismiss();
        });
        builder.setNeutralButton("Close", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void syncNewMessages() {
        long lastTimestamp = spamDao.getLatestMessageTimestamp();
        SmsReader reader = new SmsReader(this);
        List<SmsMessage> newMessages = reader.readMessagesSince(lastTimestamp);

        if (!newMessages.isEmpty()) {
            for (SmsMessage msg : newMessages) {
                boolean isSpam = spamDetector.isSpam(msg.getSender(), msg.getBody());
                msg.setSpam(isSpam);
                spamDao.insertMessage(msg);
            }
        }
        // Always refresh list because SmsReceiver might have inserted the message directly
        loadMessages();
    }
}
