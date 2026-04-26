package com.example.safeinbox.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.View;
import android.widget.TextView;
import com.example.safeinbox.R;

/**
 * Robust utility for detecting links in text, including those without protocols
 * or using common short domains.
 */
public class LinkDetector {

    // Advanced regex provided by user: detects http, https, www, and raw domains like i.airtel.in
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(https?://[\\w.-]+|www\\.[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}|[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})(/\\S*)?",
            Pattern.CASE_INSENSITIVE
    );

    // Common URL shorteners found in spam
    private static final Set<String> SHORTENER_DOMAINS = new HashSet<>(Arrays.asList(
            "bit.ly", "tinyurl.com", "t.co", "goo.gl", "bit.do", "ow.ly", "is.gd", "buff.ly", "rb.gy"
    ));

    public static class DetectedLink {
        public final String url;
        public final int start;
        public final int end;
        public final boolean isShortened;

        public DetectedLink(String url, int start, int end, boolean isShortened) {
            this.url = url;
            this.start = start;
            this.end = end;
            this.isShortened = isShortened;
        }
    }

    public static class LinkAnalysisResult {
        public int overallScore = 0;
        public boolean hasLink = false;
        public boolean hasShortener = false;
        public boolean hasRiskyLink = false;
        public List<String> linksFound = new ArrayList<>();
    }

    /**
     * Performs a forensic analysis of links within text.
     */
    public static LinkAnalysisResult analyzeLinks(String text) {
        LinkAnalysisResult result = new LinkAnalysisResult();
        List<DetectedLink> links = findLinks(text);
        
        if (links.isEmpty()) return result;
        
        result.hasLink = true;
        for (DetectedLink link : links) {
            result.linksFound.add(link.url);
            if (link.isShortened) {
                result.hasShortener = true;
                result.overallScore += 25; // High risk for shortened URLs in SMS
            }
            
            // Check for raw IP addresses or suspicious domains
            String domain = getDomain(link.url);
            if (domain.matches(".*\\d+\\.\\d+\\.\\d+\\.\\d+.*")) {
                result.hasRiskyLink = true;
                result.overallScore += 40;
            }
        }
        
        if (result.overallScore > 0) result.hasRiskyLink = true;
        result.overallScore = Math.min(60, result.overallScore);
        
        return result;
    }

    /**
     * Finds all links in the given text.
     */
    public static List<DetectedLink> findLinks(String text) {
        List<DetectedLink> links = new ArrayList<>();
        if (text == null || text.isEmpty()) return links;

        Matcher matcher = URL_PATTERN.matcher(text);
        while (matcher.find()) {
            String url = matcher.group();
            String domain = getDomain(url).toLowerCase();
            boolean isShortened = SHORTENER_DOMAINS.contains(domain);
            
            // If it starts with a protocol, we trust it more. 
            // If raw domain, it MUST have a dot and not be just an IP-like number.
            boolean hasProtocol = url.toLowerCase().startsWith("http");
            if (hasProtocol || (domain.contains(".") && !domain.matches(".*\\d+$"))) {
                links.add(new DetectedLink(url, matcher.start(), matcher.end(), isShortened));
            }
        }
        return links;
    }

    /**
     * Extracts the core domain from a URL for analysis.
     */
    public static String getDomain(String url) {
        String domain = url;
        if (domain.startsWith("http://")) domain = domain.substring(7);
        else if (domain.startsWith("https://")) domain = domain.substring(8);
        
        if (domain.startsWith("www.")) domain = domain.substring(4);
        
        int slashIndex = domain.indexOf('/');
        if (slashIndex != -1) domain = domain.substring(0, slashIndex);
        
        return domain;
    }

    /**
     * Applies premium link highlighting and click handling to a TextView.
     * Ensures "100% Coverage" and consistency across list items and dialogs.
     */
    public static void applyLinkHighlighting(TextView textView, String text, Context context, 
                                           android.view.View.OnClickListener onLinkClickAction) {
        if (text == null || text.isEmpty()) {
            textView.setText("");
            return;
        }

        List<DetectedLink> links = findLinks(text);
        if (links.isEmpty()) {
            textView.setText(text);
            return;
        }

        SpannableString spannable = new SpannableString(text);
        int linkColor = context.getResources().getColor(R.color.accent_blue);

        for (DetectedLink link : links) {
            // Style: Blue color, Bold, and Underline
            spannable.setSpan(new ForegroundColorSpan(linkColor), 
                    link.start, link.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new StyleSpan(Typeface.BOLD), 
                    link.start, link.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new UnderlineSpan(), 
                    link.start, link.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            
            // Interactivity: ClickableSpan
            spannable.setSpan(new ClickableSpan() {
                @Override
                public void onClick(View widget) {
                    // This creates a custom action for when the link is clicked
                    // Usually this triggers the showLinkSafetyPopup
                    textView.setTag(R.id.tag_link_url, link.url); 
                    if (onLinkClickAction != null) {
                        onLinkClickAction.onClick(widget);
                    }
                }
                
                @Override
                public void updateDrawState(android.text.TextPaint ds) {
                    super.updateDrawState(ds);
                    ds.setUnderlineText(true);
                    ds.setColor(linkColor);
                }
            }, link.start, link.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        textView.setText(spannable, TextView.BufferType.SPANNABLE);
    }

    /**
     * Shows a premium security warning popup before opening any link.
     * Ensures offline safety and consistent UX across the app.
     */
    public static void showLinkSafetyPopup(Context context, String url) {
        String displayUrl = url;
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            displayUrl = "http://" + url;
        }
        final String finalUrl = displayUrl;

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(context)
            .setTitle("⚠️ Security Warning")
            .setMessage("This link may be unsafe. SafeInbox helps you avoid phishing. Do you want to proceed to this address?\n\nURL: " + url)
            .setPositiveButton("Proceed", (d, which) -> {
                try {
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(finalUrl));
                    context.startActivity(intent);
                } catch (Exception e) {
                    android.widget.Toast.makeText(context, "Cannot open browser", android.widget.Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", (d, which) -> d.dismiss())
            .setIcon(android.R.drawable.ic_dialog_alert)
            .create();

        dialog.show();
        // UI Fix: Ensure buttons are visible on dark themes
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setTextColor(android.graphics.Color.WHITE);
        dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setTextColor(android.graphics.Color.WHITE);
    }
}
