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
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.graphics.Color;
import android.graphics.Typeface;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

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
    private static final int VIEW_TYPE_SUSPICIOUS = 2;

    private List<SmsMessage> messages;
    private List<SmsMessage> messagesFull; // Full list for filtering
    private String currentQuery = ""; // Tracks search term for highlighting
    private final OnMessageActionListener listener;
    private OnFilterResultsListener filterListener;
    
    private final Set<Long> selectedIds = new HashSet<>();
    private boolean isSelectionMode = false;
    
    // Industrial Cache: Fast memory lookup for contact names to prevent thread thrashing
    private static final android.util.LruCache<String, String> nameCache = new android.util.LruCache<>(500);

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
        void onLinkClick(SmsMessage message, String url);
    }

    public SmsAdapter(List<SmsMessage> messages, OnMessageActionListener listener) {
        this.messages = messages;
        this.messagesFull = new ArrayList<>(messages);
        this.listener = listener;
        setHasStableIds(true); // Boost: Smoother scrolling and less flickering
    }

    @Override
    public int getItemViewType(int position) {
        SmsMessage msg = messages.get(position);
        if (msg == null) return VIEW_TYPE_NORMAL;
        String status = msg.getClassificationStatus();
        if ("SPAM".equals(status)) return VIEW_TYPE_SPAM;
        if ("SUSPICIOUS".equals(status)) return VIEW_TYPE_SUSPICIOUS;
        return VIEW_TYPE_NORMAL;
    }

    @Override
    public SmsViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view;
        if (viewType == VIEW_TYPE_SPAM) {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_sms_spam, parent, false);
        } else if (viewType == VIEW_TYPE_SUSPICIOUS) {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_sms_suspicious, parent, false);
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
        if (!message.isBlocked()) {
            popup.getMenu().findItem(R.id.action_unblock).setVisible(false);
        } else {
            popup.getMenu().findItem(R.id.action_block).setVisible(false); // Can't block a blocked sender
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

    @Override
    public long getItemId(int position) {
        // Use database ID or timestamp for stable identity
        SmsMessage msg = messages.get(position);
        return msg != null ? msg.getId() : position;
    }

    public void setMessages(List<SmsMessage> newMessages) {
        // Always update the full backing list
        this.messagesFull = new ArrayList<>(newMessages);
        
        // If a search filter is active, re-apply it against the new data
        if (currentQuery != null && !currentQuery.isEmpty()) {
            // Re-trigger the filter which will use the updated messagesFull
            getFilter().filter(currentQuery);
            return;
        }
        
        if (this.messages == null || this.messages.isEmpty()) {
            // Initial load: skip expensive DiffUtil entirely and just notify
            this.messages = new ArrayList<>(newMessages);
            notifyDataSetChanged();
            return;
        }

        // Offload expensive DiffUtil calculation to background thread to prevent UI freezing
        final List<SmsMessage> oldMessages = new ArrayList<>(this.messages);
        com.example.safeinbox.utils.TurboExecutor.getInstance().execute(() -> {
            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new SmsDiffCallback(oldMessages, newMessages));
            
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                this.messages = new ArrayList<>(newMessages);
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
                        // ULTRA SIMPLE SEARCH: Check everything without regex or splits
                        String resolvedName = "";
                        try {
                            String resolved = com.example.safeinbox.utils.PrincipalEntityResolver.resolve(item.getSender());
                            if (resolved != null) resolvedName = resolved.toLowerCase();
                        } catch (Exception ignored) {}
                        
                        String bodyResolved = "";
                        try {
                            String bRes = com.example.safeinbox.utils.PrincipalEntityResolver.resolveFromBody(item.getBody());
                            if (bRes != null) bodyResolved = bRes.toLowerCase();
                        } catch (Exception ignored) {}
                        
                        // Extremely resilient check
                        match = sender.contains(filterPattern) 
                             || name.contains(filterPattern) 
                             || body.contains(filterPattern) 
                             || resolvedName.contains(filterPattern) 
                             || bodyResolved.contains(filterPattern);
                             
                        // Fallback purely for numbers ignoring characters (like dashes)
                        if (!match && filterPattern.matches(".*\\d.*")) {
                             String safeSenderDigits = "";
                             for (char c : sender.toCharArray()) if (Character.isDigit(c)) safeSenderDigits += c;
                             
                             String safeQueryDigits = "";
                             for (char c : filterPattern.toCharArray()) if (Character.isDigit(c)) safeQueryDigits += c;
                             
                             if (!safeQueryDigits.isEmpty() && safeSenderDigits.contains(safeQueryDigits)) {
                                 match = true;
                             }
                        }
                    }
                    
                    if (match) {
                        filteredList.add(item);
                    }
                }
            }

            FilterResults results = new FilterResults();
            results.values = filteredList;
            results.count = filteredList.size();
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
        private final TextView avatarText;
        private final ImageView imgAvatarPerson;
        private final TextView typeLabel; // SERVICE tag
        private final TextView verdictBadge;

        SmsViewHolder(View itemView) {
            super(itemView);
            senderText = itemView.findViewById(R.id.text_sender);
            bodyText = itemView.findViewById(R.id.text_body);
            dateText = itemView.findViewById(R.id.text_date);
            btnMenu = itemView.findViewById(R.id.btn_menu);
            avatarText = itemView.findViewById(R.id.text_avatar);
            imgAvatarPerson = itemView.findViewById(R.id.img_avatar_person);
            imgSelected = itemView.findViewById(R.id.img_selected);
            typeLabel = itemView.findViewById(R.id.text_type_label);
            verdictBadge = itemView.findViewById(R.id.text_verdict_badge);
            cardView = (androidx.cardview.widget.CardView) itemView;
        }

        void bind(SmsMessage message, String query) {
            boolean isItemSelected = isSelected(message.getId());
            if (isItemSelected) {
                cardView.setCardBackgroundColor(itemView.getContext().getResources().getColor(R.color.selection_highlight));
                imgSelected.setVisibility(View.VISIBLE);
                if (btnMenu != null) btnMenu.setVisibility(View.GONE);
            } else {
                int bgColor;
                String status = message.getClassificationStatus();
                if ("SPAM".equals(status)) {
                    bgColor = itemView.getContext().getResources().getColor(R.color.card_bg_dark); 
                } else if ("SUSPICIOUS".equals(status)) {
                    bgColor = itemView.getContext().getResources().getColor(R.color.suspicious_background);
                } else {
                    bgColor = itemView.getContext().getResources().getColor(R.color.item_background);
                }
                cardView.setCardBackgroundColor(bgColor);
                imgSelected.setVisibility(View.GONE);
                if (btnMenu != null) btnMenu.setVisibility(View.VISIBLE);
            }

            // Bind Verdict Badge
            if (verdictBadge != null) {
                String status = message.getClassificationStatus();
                int badgeColor;
                
                if (message.isBlocked()) {
                    verdictBadge.setText("BLOCKED");
                    badgeColor = itemView.getContext().getResources().getColor(R.color.error_red);
                } else {
                    String reason = message.getClassificationReason();
                    if (reason != null && (reason.contains("VERIFIED") || reason.contains("OTP"))) {
                        verdictBadge.setText("VERIFIED");
                        badgeColor = itemView.getContext().getResources().getColor(R.color.success_green);
                    } else {
                        verdictBadge.setText(status);
                        if ("SPAM".equals(status)) {
                            badgeColor = itemView.getContext().getResources().getColor(R.color.error_red);
                        } else if ("SUSPICIOUS".equals(status)) {
                            badgeColor = itemView.getContext().getResources().getColor(R.color.warning_orange);
                        } else {
                            badgeColor = itemView.getContext().getResources().getColor(R.color.success_green);
                        }
                    }
                }
                
                android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                gd.setColor(badgeColor);
                gd.setCornerRadius(10f); // Match the label radius
                verdictBadge.setBackground(gd);
                verdictBadge.setVisibility(View.VISIBLE);
            }

            // Show/Hide \"SERVICE\" tag based on sender type
            if (typeLabel != null) {
                if (com.example.safeinbox.utils.PrincipalEntityResolver.isAlphanumericSender(message.getSender())) {
                    typeLabel.setVisibility(View.VISIBLE);
                } else {
                    typeLabel.setVisibility(View.GONE);
                }
            }

            String currentName = message.getSenderName();
            if (currentName == null) {
                // Set temporary text (sender number or ID)
                String tempDisplay;
                if (com.example.safeinbox.utils.PrincipalEntityResolver.isAlphanumericSender(message.getSender())) {
                    tempDisplay = message.getSender(); // Show the ID immediately
                } else {
                    tempDisplay = (message.getSender() == null || message.getSender().trim().isEmpty() || message.getSender().equalsIgnoreCase("Unknown")) ? "Unknown Sender" : message.getSender();
                }
                if (message.isBlocked()) {
                    tempDisplay = "🚫 [BLOCKED] " + tempDisplay;
                }
                highlightOrSetText(message, senderText, tempDisplay, query);
                updateAvatar(message.getSender());
                
                // 1. Check Memory Cache First (FASTEST)
                String cachedName = nameCache.get(message.getSender());
                if (cachedName != null) {
                    message.setSenderName(cachedName);
                    // Name is already in memory, continue with regular logic
                } else {
                    // Fetch name in background (ASYNC)
                    com.example.safeinbox.utils.TurboExecutor.getInstance().execute(() -> {
                        String fetchedName = ContactUtils.getContactName(itemView.getContext(), message.getSender());
                        String nameToStore = fetchedName != null ? fetchedName : "";
                        nameCache.put(message.getSender(), nameToStore);
                        message.setSenderName(nameToStore);
                    
                    String resolvedName = com.example.safeinbox.utils.PrincipalEntityResolver.resolve(message.getSender());
                    String displayName;
                    
                    if (fetchedName != null && !fetchedName.isEmpty()) {
                        displayName = fetchedName;
                    } else if (resolvedName != null && !resolvedName.isEmpty()) {
                        displayName = resolvedName;
                    } else {
                        // Fallback to raw ID or Number, but if both missing, try body
                        if (message.getSender() == null || message.getSender().trim().isEmpty() || message.getSender().equalsIgnoreCase("Unknown Sender") || message.getSender().equalsIgnoreCase("Unknown")) {
                            displayName = com.example.safeinbox.utils.PrincipalEntityResolver.resolveFromBody(message.getBody());
                        } else {
                            displayName = message.getSender();
                        }
                    }
                    
                    if (displayName == null) displayName = "Unknown Sender";
                    
                    final String finalDisplay = displayName;
                    final String avatarName = (fetchedName != null && !fetchedName.isEmpty()) ? fetchedName : 
                                             (displayName.equals("Unknown Sender") ? message.getSender() : displayName);
                    
                    String finalDisplayWithBlock = finalDisplay;
                    if (message.isBlocked()) {
                        finalDisplayWithBlock = "🚫 [BLOCKED] " + finalDisplay;
                    }
                    
                    final String uiDisplay = finalDisplayWithBlock;
                    
                    itemView.post(() -> {
                        highlightOrSetText(message, senderText, uiDisplay, query);
                        updateAvatar(avatarName);
                    });
                });
            }
        } else {
                String resolvedName = com.example.safeinbox.utils.PrincipalEntityResolver.resolve(message.getSender());
                String displayName;
                
                if (!currentName.isEmpty()) {
                    displayName = currentName;
                } else if (resolvedName != null && !resolvedName.isEmpty()) {
                    displayName = resolvedName;
                } else {
                    if (message.getSender() == null || message.getSender().trim().isEmpty() || message.getSender().equalsIgnoreCase("Unknown Sender") || message.getSender().equalsIgnoreCase("Unknown")) {
                        displayName = com.example.safeinbox.utils.PrincipalEntityResolver.resolveFromBody(message.getBody());
                    } else {
                        displayName = message.getSender();
                    }
                }
                
                if (displayName == null) displayName = "Unknown Sender";
                
                String finalDisplayWithBlock = displayName;
                if (message.isBlocked()) {
                    finalDisplayWithBlock = "🚫 [BLOCKED] " + displayName;
                }
                
                highlightOrSetText(message, senderText, finalDisplayWithBlock, query);
                updateAvatar(currentName.isEmpty() ? (displayName.equals("Unknown Sender") ? message.getSender() : displayName) : currentName);
            }
            
            highlightOrSetText(message, bodyText, message.getBody(), query);
            dateText.setText(message.getFormattedDate());

            // Link highlighting and safety are now handled centrally in highlightOrSetText via LinkDetector
            bodyText.setOnClickListener(null); 
        }

        private void updateAvatar(String nameOrNumber) {
            if (avatarText == null || imgAvatarPerson == null) return;
            
            if (nameOrNumber == null || nameOrNumber.isEmpty()) {
                avatarText.setVisibility(View.GONE);
                imgAvatarPerson.setVisibility(View.VISIBLE);
                setAvatarColor("unknown", imgAvatarPerson.getBackground());
                return;
            }
            
            String cleanName = nameOrNumber.replace("🚫 [BLOCKED] ", "").trim();
            if (cleanName.isEmpty()) {
                avatarText.setVisibility(View.GONE);
                imgAvatarPerson.setVisibility(View.VISIBLE);
                setAvatarColor("unknown", imgAvatarPerson.getBackground());
                return;
            }
            
            avatarText.setVisibility(View.VISIBLE);
            imgAvatarPerson.setVisibility(View.GONE);
            
            // Look for the first letter if possible, otherwise first character
            String initial = cleanName.substring(0, 1).toUpperCase();
            for (int i = 0; i < cleanName.length(); i++) {
                if (Character.isLetter(cleanName.charAt(i))) {
                    initial = String.valueOf(cleanName.charAt(i)).toUpperCase();
                    break;
                }
            }
            
            avatarText.setText(initial);
            setAvatarColor(cleanName, avatarText.getBackground());
        }
        
        private void setAvatarColor(String key, android.graphics.drawable.Drawable background) {
            int hash = key.hashCode();
            int[] colors = {
                0xFFE57373, 0xFFF06292, 0xFFBA68C8, 0xFF9575CD, 0xFF7986CB,
                0xFF64B5F6, 0xFF4FC3F7, 0xFF4DD0E1, 0xFF4DB6AC, 0xFF81C784,
                0xFFAED581, 0xFFFF8A65, 0xFFA1887F, 0xFF90A4AE
            };
            int color = colors[Math.abs(hash) % colors.length];
            
            if (background instanceof android.graphics.drawable.GradientDrawable) {
                ((android.graphics.drawable.GradientDrawable) background.mutate()).setColor(color);
            }
        }

        private String extractFirstUrl(String body) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "(https?://|www\\\\.)[a-zA-Z0-9\\\\-\\\\.]+\\\\.[a-zA-Z]{2,}(/\\\\S*)?",
                java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher m = p.matcher(body);
            if (m.find()) return m.group();
            return "";
        }

        private void highlightOrSetText(SmsMessage message, TextView textView, String fullText, String query) {
            if (fullText == null) {
                textView.setText("");
                return;
            }

            if (query == null || query.isEmpty()) {
                com.example.safeinbox.utils.LinkDetector.applyLinkHighlighting(textView, fullText, textView.getContext(), v -> {
                    String url = (String) v.getTag(R.id.tag_link_url);
                    if (listener != null) {
                        listener.onLinkClick(message, url);
                    }
                });
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
                
                // AFTER highlighting query, apply link highlighting on the SAME spannable
                List<com.example.safeinbox.utils.LinkDetector.DetectedLink> links = com.example.safeinbox.utils.LinkDetector.findLinks(fullText);
                int linkColor = textView.getContext().getResources().getColor(R.color.accent_blue);
                for (com.example.safeinbox.utils.LinkDetector.DetectedLink link : links) {
                    spannable.setSpan(new android.text.style.ForegroundColorSpan(linkColor), link.start, link.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    spannable.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), link.start, link.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    spannable.setSpan(new android.text.style.UnderlineSpan(), link.start, link.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    spannable.setSpan(new ClickableSpan() {
                        @Override public void onClick(View widget) {
                            if (listener != null) listener.onLinkClick(message, link.url);
                        }
                    }, link.start, link.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                textView.setMovementMethod(LinkMovementMethod.getInstance());
                textView.setText(spannable);
            } else {
                com.example.safeinbox.utils.LinkDetector.applyLinkHighlighting(textView, fullText, textView.getContext(), v -> {
                    if (listener != null) listener.onLinkClick(message, (String)v.getTag(R.id.tag_link_url));
                });
            }
        }
    }
}
