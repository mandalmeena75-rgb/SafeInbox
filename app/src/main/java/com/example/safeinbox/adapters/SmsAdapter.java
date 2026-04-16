package com.example.safeinbox.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.safeinbox.R;
import com.example.safeinbox.models.SmsMessage;
import com.example.safeinbox.utils.ContactUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SmsAdapter extends RecyclerView.Adapter<SmsAdapter.SmsViewHolder> implements Filterable {

    private static final int VIEW_TYPE_NORMAL = 0;
    private static final int VIEW_TYPE_SPAM = 1;

    private List<SmsMessage> messages;
    private List<SmsMessage> messagesFull; // Full list for filtering
    private final OnMessageActionListener listener;

    public interface OnMessageActionListener {
        void onMessageClick(SmsMessage message, int position);
    }

    public SmsAdapter(List<SmsMessage> messages, OnMessageActionListener listener) {
        this.messages = messages;
        this.messagesFull = new ArrayList<>(messages);
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
        SmsMessage message = messages.get(position);
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

    public void setMessages(List<SmsMessage> newMessages) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new SmsDiffCallback(this.messages, newMessages));
        this.messages = new ArrayList<>(newMessages);
        this.messagesFull = new ArrayList<>(newMessages);
        diffResult.dispatchUpdatesTo(this);
    }

    public void removeMessage(int position) {
        if (position >= 0 && position < messages.size()) {
            SmsMessage removed = messages.remove(position);
            messagesFull.remove(removed);
            notifyItemRemoved(position);
        }
    }

    @Override
    public Filter getFilter() {
        return messageFilter;
    }

    private final Filter messageFilter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            List<SmsMessage> filteredList = new ArrayList<>();

            if (constraint == null || constraint.length() == 0) {
                filteredList.addAll(messagesFull);
            } else {
                String filterPattern = constraint.toString().toLowerCase().trim();

                for (SmsMessage item : messagesFull) {
                    if (item.getSender().toLowerCase().contains(filterPattern) ||
                        item.getBody().toLowerCase().contains(filterPattern)) {
                        filteredList.add(item);
                    }
                }
            }

            FilterResults results = new FilterResults();
            results.values = filteredList;
            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            messages.clear();
            messages.addAll((List) results.values);
            notifyDataSetChanged();
        }
    };

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

        void bind(SmsMessage message) {
            // Live Lookup Fallback: If DB doesn't have a name, try a quick cached lookup
            String currentName = message.getSenderName();
            if (currentName == null) {
                currentName = ContactUtils.getContactName(itemView.getContext(), message.getSender());
                message.setSenderName(currentName); // Cache it in the object for this session
            }

            String displayName;
            // If we have a name that's different from the number/address, show both
            if (currentName != null && !currentName.equals(message.getSender())) {
                displayName = currentName + " (" + message.getSender() + ")";
            } else {
                displayName = message.getSender();
            }
            
            senderText.setText(displayName);
            bodyText.setText(message.getBody());

            dateText.setText(message.getFormattedDate());
        }
    }
}
