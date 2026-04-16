package com.example.safeinbox.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.safeinbox.R;
import com.example.safeinbox.adapters.SmsAdapter;
import com.example.safeinbox.database.SpamDao;
import com.example.safeinbox.detection.SpamDetector;
import com.example.safeinbox.models.SmsModel;
import com.example.safeinbox.utils.Constants;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SpamActivity extends Activity implements SmsAdapter.OnMessageActionListener {

    private RecyclerView recyclerView;
    private SmsAdapter adapter;
    private TextView emptyText;
    private SpamDao spamDao;
    private SpamDetector spamDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spam);

        spamDao = new SpamDao(this);
        spamDetector = new SpamDetector(this);

        recyclerView = findViewById(R.id.recycler_spam);
        emptyText = findViewById(R.id.text_empty_spam);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SmsAdapter(new ArrayList<SmsModel>(), this);
        recyclerView.setAdapter(adapter);

        // Back button
        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        loadSpamMessages();
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
                final List<SmsModel> messages = spamDao.getMessages(true);

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
                    }
                });
            }
        }).start();
    }

    // ==================== Module 7: Feedback Dialog ====================

    @Override
    public void onMessageClick(SmsModel message, int position) {
        showMessageDetailDialog(message, position);
    }

    private void showMessageDetailDialog(final SmsModel message, final int position) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_message_detail, null);

        TextView senderView = dialogView.findViewById(R.id.detail_sender);
        TextView dateView = dialogView.findViewById(R.id.detail_date);
        TextView bodyView = dialogView.findViewById(R.id.detail_body);

        senderView.setText(message.getSender());
        bodyView.setText(message.getBody());

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
        dateView.setText(sdf.format(new Date(message.getDate())));

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setPositiveButton("Not Spam", (dialog, which) -> {
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
