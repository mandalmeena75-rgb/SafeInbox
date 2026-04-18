package com.example.safeinbox.adapters;

import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.safeinbox.R;
import com.example.safeinbox.models.SmsMessage;
import com.example.safeinbox.utils.ContactUtils;

import java.util.ArrayList;
import java.util.List;

public class SmsAdapter extends RecyclerView.Adapter<SmsAdapter.SmsViewHolder> implements Filterable {

    private static final int VIEW_TYPE_NORMAL = 0;
    private static final int VIEW_TYPE_SPAM = 1;

    private List<SmsMessage> messages;
    private List<SmsMessage> messagesFull; // Full list for filtering
    private final OnMessageActionListener listener;

    public interface OnMessageActionListener {
        void onMessageClick(SmsMessage message, int position);
        void onBlockReport(SmsMessage message, int position);
        void onMarkNotSpam(SmsMessage message, int position);
        void onDelete(SmsMessage message, int position);
        void onArchive(SmsMessage message, int position);
        void onHelpFeedback(SmsMessage message, int position);
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
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                int adapterPosition = holder.getAdapterPosition();
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    listener.onMessageClick(messages.get(adapterPosition), adapterPosition);
                }
            }
        });

        if (holder.btnMenu != null) {
            holder.btnMenu.setOnClickListener(v -> showPopupMenu(v, message, holder.getAdapterPosition()));
        }
    }

    private void showPopupMenu(View view, SmsMessage message, int position) {
        PopupMenu popup = new PopupMenu(view.getContext(), view);
        popup.getMenuInflater().inflate(R.menu.item_menu, popup.getMenu());
        
        popup.setOnMenuItemClickListener(item -> {
            if (listener == null || position == RecyclerView.NO_POSITION) return false;
            
            int id = item.getItemId();
            if (id == R.id.action_block) {
                listener.onBlockReport(message, position);
                return true;
            } else if (id == R.id.action_not_spam) {
                // The label in menu XML is still action_not_spam for ID stability, 
                // but we will ensure it displays "Ham" if not set in XML.
                listener.onMarkNotSpam(message, position);
                return true;
            } else if (id == R.id.action_delete) {
                listener.onDelete(message, position);
                return true;
            } else if (id == R.id.action_details) {
                listener.onMessageClick(message, position);
                return true;
            } else if (id == R.id.action_archives) {
                listener.onArchive(message, position);
                return true;
            } else if (id == R.id.action_help) {
                listener.onHelpFeedback(message, position);
                return true;
            }
            return false;
        });
        popup.show();
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
            // Search in full list by ID to be extra safe
            for (int i = 0; i < messagesFull.size(); i++) {
                if (messagesFull.get(i).getId() == removed.getId()) {
                    messagesFull.remove(i);
                    break;
                }
            }
            notifyItemRemoved(position);
        }
    }

    public int removeMessageById(long id) {
        int indexInCurrent = -1;
        // Search in visible list
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getId() == id) {
                messages.remove(i);
                indexInCurrent = i;
                notifyItemRemoved(i);
                break;
            }
        }
        // Search and remove from full list
        for (int i = 0; i < messagesFull.size(); i++) {
            if (messagesFull.get(i).getId() == id) {
                messagesFull.remove(i);
                break;
            }
        }
        return indexInCurrent;
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
        private final ImageButton btnMenu;

        SmsViewHolder(View itemView) {
            super(itemView);
            senderText = itemView.findViewById(R.id.text_sender);
            bodyText = itemView.findViewById(R.id.text_body);
            dateText = itemView.findViewById(R.id.text_date);
            btnMenu = itemView.findViewById(R.id.btn_menu);
        }

        void bind(SmsMessage message) {
            String currentName = message.getSenderName();
            if (currentName == null) {
                // Set temporary text
                senderText.setText(message.getSender());
                
                // Fetch name in background
                com.example.safeinbox.utils.TurboExecutor.getInstance().execute(() -> {
                    String fetchedName = ContactUtils.getContactName(itemView.getContext(), message.getSender());
                    message.setSenderName(fetchedName);
                    
                    String displayName;
                    if (fetchedName != null && !fetchedName.equals(message.getSender())) {
                        displayName = fetchedName + " (" + message.getSender() + ")";
                    } else {
                        displayName = message.getSender();
                    }
                    
                    if (message.isBlocked()) {
                        displayName = "🚫 [BLOCKED] " + displayName;
                    }
                    
                    final String finalDisplay = displayName;
                    itemView.post(() -> senderText.setText(finalDisplay));
                });
            } else {
                String displayName;
                if (!currentName.equals(message.getSender())) {
                    displayName = currentName + " (" + message.getSender() + ")";
                } else {
                    displayName = message.getSender();
                }
                
                if (message.isBlocked()) {
                    displayName = "🚫 [BLOCKED] " + displayName;
                }
                senderText.setText(displayName);
            }
            
            bodyText.setText(message.getBody());
            dateText.setText(message.getFormattedDate());
        }
    }
}
