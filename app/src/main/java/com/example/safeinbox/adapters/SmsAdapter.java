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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import android.widget.ImageView;

public class SmsAdapter extends RecyclerView.Adapter<SmsAdapter.SmsViewHolder> implements Filterable {

    private static final int VIEW_TYPE_NORMAL = 0;
    private static final int VIEW_TYPE_SPAM = 1;

    private List<SmsMessage> messages;
    private List<SmsMessage> messagesFull; // Full list for filtering
    private String currentQuery = ""; // Tracks search term for highlighting
    private final OnMessageActionListener listener;
    private OnFilterResultsListener filterListener;
    
    private final Set<Long> selectedIds = new HashSet<>();
    private boolean isSelectionMode = false;

    public interface OnFilterResultsListener {
        void onFilterResults(int count);
    }

    public interface OnMessageActionListener {
        void onMessageClick(SmsMessage message, int position);
        void onBlockReport(SmsMessage message, int position);
        void onMarkSpam(SmsMessage message, int position);
        void onMarkNotSpam(SmsMessage message, int position);
        void onUnblock(SmsMessage message, int position);
        void onDelete(SmsMessage message, int position);
        void onArchive(SmsMessage message, int position);
        void onUnarchive(SmsMessage message, int position);
        void onHelpFeedback(SmsMessage message, int position);
        void onSelectionChanged(int count);
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
        holder.bind(message, currentQuery);
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                int adapterPosition = holder.getAdapterPosition();
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    if (isSelectionMode) {
                        toggleSelection(adapterPosition);
                    } else {
                        listener.onMessageClick(messages.get(adapterPosition), adapterPosition);
                    }
                }
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null && !isSelectionMode) {
                int adapterPosition = holder.getAdapterPosition();
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    setSelectionMode(true);
                    toggleSelection(adapterPosition);
                    return true;
                }
            }
            return false;
        });

        if (holder.btnMenu != null) {
            holder.btnMenu.setOnClickListener(v -> showPopupMenu(v, message, holder.getAdapterPosition()));
        }
    }

    private void showPopupMenu(View view, SmsMessage message, int position) {
        PopupMenu popup = new PopupMenu(view.getContext(), view);
        popup.getMenuInflater().inflate(R.menu.item_menu, popup.getMenu());
        
        // Context-aware menu hiding
        if (view.getContext() instanceof com.example.safeinbox.activities.SpamActivity) {
            popup.getMenu().findItem(R.id.action_block).setVisible(false);
            popup.getMenu().findItem(R.id.action_mark_spam).setVisible(false);
            popup.getMenu().findItem(R.id.action_unarchive).setVisible(false);
        } else if (view.getContext() instanceof com.example.safeinbox.activities.ArchiveActivity) {
            // Archive Screen: Show only Unarchive and Delete
            popup.getMenu().findItem(R.id.action_archive).setVisible(false);
            popup.getMenu().findItem(R.id.action_mark_spam).setVisible(false);
            popup.getMenu().findItem(R.id.action_block).setVisible(false);
            popup.getMenu().findItem(R.id.action_not_spam).setVisible(false);
            popup.getMenu().findItem(R.id.action_unblock).setVisible(false);
            popup.getMenu().findItem(R.id.action_help).setVisible(false);
        } else {
            popup.getMenu().findItem(R.id.action_not_spam).setVisible(false);
            popup.getMenu().findItem(R.id.action_unarchive).setVisible(false);
        }
        
        // Always show Unblock if the message is currently blocked, even in Inbox
        if (!message.isBlocked()) {
            popup.getMenu().findItem(R.id.action_unblock).setVisible(false);
        }
        
        popup.setOnMenuItemClickListener(item -> {
            if (listener == null || position == RecyclerView.NO_POSITION) return false;
            
            int id = item.getItemId();
            if (id == R.id.action_mark_spam) {
                listener.onMarkSpam(message, position);
                return true;
            } else if (id == R.id.action_block) {
                listener.onBlockReport(message, position);
                return true;
            } else if (id == R.id.action_not_spam) {
                listener.onMarkNotSpam(message, position);
                return true;
            } else if (id == R.id.action_unblock) {
                listener.onUnblock(message, position);
                return true;
            } else if (id == R.id.action_delete) {
                listener.onDelete(message, position);
                return true;
            } else if (id == R.id.action_details) {
                listener.onMessageClick(message, position);
                return true;
            } else if (id == R.id.action_archive) {
                listener.onArchive(message, position);
                return true;
            } else if (id == R.id.action_unarchive) {
                listener.onUnarchive(message, position);
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
        if (this.messages == null || this.messages.isEmpty()) {
            // Initial load: skip expensive DiffUtil entirely and just notify
            this.messages = new ArrayList<>(newMessages);
            this.messagesFull = new ArrayList<>(newMessages);
            notifyDataSetChanged();
            return;
        }

        // Offload expensive DiffUtil calculation to background thread to prevent UI freezing
        final List<SmsMessage> oldMessages = new ArrayList<>(this.messages);
        com.example.safeinbox.utils.TurboExecutor.getInstance().execute(() -> {
            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new SmsDiffCallback(oldMessages, newMessages));
            
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                this.messages = new ArrayList<>(newMessages);
                this.messagesFull = new ArrayList<>(newMessages);
                diffResult.dispatchUpdatesTo(this);
            });
        });
    }

    public void setSelectionMode(boolean active) {
        this.isSelectionMode = active;
        if (!active) {
            selectedIds.clear();
        }
        notifyDataSetChanged();
    }

    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    public void toggleSelection(int position) {
        if (position < 0 || position >= messages.size()) return;
        long id = messages.get(position).getId();
        if (selectedIds.contains(id)) {
            selectedIds.remove(id);
        } else {
            selectedIds.add(id);
        }
        notifyItemChanged(position);
        if (listener != null) {
            listener.onSelectionChanged(selectedIds.size());
        }
        if (selectedIds.isEmpty() && isSelectionMode) {
            setSelectionMode(false);
        }
    }

    public void clearSelection() {
        selectedIds.clear();
        setSelectionMode(false);
    }

    public Set<Long> getSelectedIds() {
        return new HashSet<>(selectedIds);
    }

    public List<SmsMessage> getSelectedMessages() {
        List<SmsMessage> selected = new ArrayList<>();
        for (SmsMessage msg : messagesFull) {
            if (selectedIds.contains(msg.getId())) {
                selected.add(msg);
            }
        }
        return selected;
    }

    private boolean isSelected(long id) {
        return selectedIds.contains(id);
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

    public void setOnFilterResultsListener(OnFilterResultsListener filterListener) {
        this.filterListener = filterListener;
    }

    public String getCurrentQuery() {
        return currentQuery;
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
                    String sender = item.getSender() != null ? item.getSender().toLowerCase() : "";
                    String name = item.getSenderName() != null ? item.getSenderName().toLowerCase() : "";
                    String body = item.getBody() != null ? item.getBody().toLowerCase() : "";

                    boolean match = false;
                    
                    if (filterPattern.equals("#financial")) {
                        match = body.contains("bank") || body.contains("account") || body.contains("blocked") || body.contains("card") || body.contains("kyc");
                    } else if (filterPattern.equals("#identity")) {
                        match = body.contains("otp") || body.contains("code") || body.contains("verify") || body.contains("login");
                    } else if (filterPattern.equals("#delivery")) {
                        match = body.contains("order") || body.contains("package") || body.contains("ship") || body.contains("track");
                    } else if (filterPattern.equals("#promotional")) {
                        match = body.contains("win") || body.contains("prize") || body.contains("lottery") || body.contains("offer");
                    } else if (filterPattern.equals("#unknown")) {
                        match = !(body.contains("bank") || body.contains("account") || body.contains("blocked") || body.contains("card") || body.contains("kyc")
                            || body.contains("otp") || body.contains("code") || body.contains("verify") || body.contains("login")
                            || body.contains("order") || body.contains("package") || body.contains("ship") || body.contains("track")
                            || body.contains("win") || body.contains("prize") || body.contains("lottery") || body.contains("offer"));
                    } else {
                        match = sender.contains(filterPattern) || name.contains(filterPattern) || body.contains(filterPattern);
                    }
                    
                    if (match) {
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
            currentQuery = (constraint != null) ? constraint.toString().toLowerCase().trim() : "";
            messages.clear();
            messages.addAll((List) results.values);
            notifyDataSetChanged();
            if (filterListener != null) {
                filterListener.onFilterResults(results.count);
            }
        }
    };

    class SmsViewHolder extends RecyclerView.ViewHolder {
        private final TextView senderText;
        private final TextView bodyText;
        private final TextView dateText;
        private final ImageButton btnMenu;
        private final ImageView imgSelected;
        private final androidx.cardview.widget.CardView cardView;

        SmsViewHolder(View itemView) {
            super(itemView);
            senderText = itemView.findViewById(R.id.text_sender);
            bodyText = itemView.findViewById(R.id.text_body);
            dateText = itemView.findViewById(R.id.text_date);
            btnMenu = itemView.findViewById(R.id.btn_menu);
            imgSelected = itemView.findViewById(R.id.img_selected);
            cardView = (androidx.cardview.widget.CardView) itemView;
        }

        void bind(SmsMessage message, String query) {
            boolean isItemSelected = isSelected(message.getId());
            if (isItemSelected) {
                cardView.setCardBackgroundColor(itemView.getContext().getResources().getColor(R.color.selection_highlight));
                imgSelected.setVisibility(View.VISIBLE);
                if (btnMenu != null) btnMenu.setVisibility(View.GONE);
            } else {
                cardView.setCardBackgroundColor(itemView.getContext().getResources().getColor(R.color.item_background));
                imgSelected.setVisibility(View.GONE);
                if (btnMenu != null) btnMenu.setVisibility(View.VISIBLE);
            }

            String currentName = message.getSenderName();
            if (currentName == null) {
                // Set temporary text (sender number)
                highlightOrSetText(senderText, message.getSender(), query);
                
                // Fetch name in background
                com.example.safeinbox.utils.TurboExecutor.getInstance().execute(() -> {
                    String fetchedName = ContactUtils.getContactName(itemView.getContext(), message.getSender());
                    // CACHE NEGATIVE LOOKUP: Use empty string if not found so we don't query again
                    message.setSenderName(fetchedName != null ? fetchedName : "");
                    
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
                    itemView.post(() -> highlightOrSetText(senderText, finalDisplay, query));
                });
            } else {
                String displayName;
                // If we cached an empty string, it means no contact was found
                if (!currentName.isEmpty() && !currentName.equals(message.getSender())) {
                    displayName = currentName + " (" + message.getSender() + ")";
                } else {
                    displayName = message.getSender();
                }
                
                if (message.isBlocked()) {
                    displayName = "🚫 [BLOCKED] " + displayName;
                }
                highlightOrSetText(senderText, displayName, query);
            }
            
            highlightOrSetText(bodyText, message.getBody(), query);
            dateText.setText(message.getFormattedDate());
        }

        private void highlightOrSetText(TextView textView, String fullText, String query) {
            if (fullText == null) {
                textView.setText("");
                return;
            }

            if (query == null || query.isEmpty()) {
                textView.setText(fullText);
                return;
            }

            android.text.SpannableString spannable = new android.text.SpannableString(fullText);
            String lowerFull = fullText.toLowerCase();
            int startPos = lowerFull.indexOf(query);

            if (startPos != -1) {
                int colorHex = textView.getContext().getResources().getColor(R.color.primary_light_purple);
                while (startPos != -1) {
                    int endPos = startPos + query.length();
                    spannable.setSpan(new android.text.style.ForegroundColorSpan(colorHex), 
                            startPos, endPos, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    spannable.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 
                            startPos, endPos, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    startPos = lowerFull.indexOf(query, endPos);
                }
                textView.setText(spannable);
            } else {
                textView.setText(fullText);
            }
        }
    }
}
