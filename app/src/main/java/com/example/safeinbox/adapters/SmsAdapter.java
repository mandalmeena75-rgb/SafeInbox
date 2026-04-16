package com.example.safeinbox.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.example.safeinbox.R;
import com.example.safeinbox.models.SmsModel;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SmsAdapter extends RecyclerView.Adapter<SmsAdapter.SmsViewHolder> {

    private static final int VIEW_TYPE_NORMAL = 0;
    private static final int VIEW_TYPE_SPAM = 1;

    private List<SmsModel> messages;
    private final OnMessageActionListener listener;

    public interface OnMessageActionListener {
        void onMessageClick(SmsModel message, int position);
    }

    public SmsAdapter(List<SmsModel> messages, OnMessageActionListener listener) {
        this.messages = messages;
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).isSpam() ? VIEW_TYPE_SPAM : VIEW_TYPE_NORMAL;
    }

    @Override
    public SmsViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view;
        if (viewType == VIEW_TYPE_SPAM) {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_sms_spam, parent, false);
        } else {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_sms, parent, false);
        }
        return new SmsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(SmsViewHolder holder, int position) {
        SmsModel message = messages.get(position);
        holder.bind(message);
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    int adapterPosition = holder.getAdapterPosition();
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        listener.onMessageClick(messages.get(adapterPosition), adapterPosition);
                    }
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void setMessages(List<SmsModel> messages) {
        this.messages = messages;
        notifyDataSetChanged();
    }

    public void removeMessage(int position) {
        if (position >= 0 && position < messages.size()) {
            messages.remove(position);
            notifyItemRemoved(position);
        }
    }

    static class SmsViewHolder extends RecyclerView.ViewHolder {
        private final TextView senderText;
        private final TextView bodyText;
        private final TextView dateText;

        SmsViewHolder(View itemView) {
            super(itemView);
            senderText = itemView.findViewById(R.id.text_sender);
            bodyText = itemView.findViewById(R.id.text_body);
            dateText = itemView.findViewById(R.id.text_date);
        }

        void bind(SmsModel message) {
            senderText.setText(message.getSender());
            bodyText.setText(message.getBody());

            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());
            dateText.setText(sdf.format(new Date(message.getDate())));
        }
    }
}
