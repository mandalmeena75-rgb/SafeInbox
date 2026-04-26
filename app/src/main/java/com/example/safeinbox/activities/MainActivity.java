package com.example.safeinbox.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.example.safeinbox.adapters.ContactsAdapter;
import com.example.safeinbox.databinding.ActivityMainBinding;
import com.example.safeinbox.utils.ContactUtils;
import com.example.safeinbox.utils.SessionManager;
import com.example.safeinbox.R;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.List;
import com.example.safeinbox.utils.Constants;

/**
 * MainActivity is the primary secure hub after authentication.
 * It features a Tabbed interface for Contacts and Messages.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private SessionManager sessionManager;
    private static final int CONTACTS_PERMISSION_CODE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = new SessionManager(this);

        setupViewPager();
        
        // --- INDUSTRIAL PERMISSION & INGESTION FLOW ---
        requestAppPermissions();

        binding.fabLogout.setOnClickListener(v -> {
            sessionManager.logout();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CONTACTS}, CONTACTS_PERMISSION_CODE);
        }
    }

    private void requestAppPermissions() {
        String[] permissions = {
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_CONTACTS
        };

        List<String> missingPermissions = new ArrayList<>();
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(p);
            }
        }

        if (!missingPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toArray(new String[0]), Constants.PERMISSION_REQUEST_CODE);
        } else {
            // Already have permissions, check for forensic rescan or first ingestion
            checkAndRescanProject();
        }
    }

    private void checkAndRescanProject() {
        android.content.SharedPreferences prefs = getSharedPreferences(com.example.safeinbox.utils.Constants.PREFS_NAME, MODE_PRIVATE);
        int lastRescannedVersion = prefs.getInt("last_engine_rescan_version", 0);
        boolean isFirstRun = prefs.getBoolean("is_first_ingestion_complete", false);

        if (!isFirstRun || lastRescannedVersion < com.example.safeinbox.utils.Constants.CURRENT_ENGINE_VERSION) {
            Toast.makeText(this, "🛡️ Starting Industrial Forensic Audit...", Toast.LENGTH_SHORT).show();
            
            com.example.safeinbox.utils.TurboExecutor.getInstance().execute(() -> {
                android.util.Log.i("MainActivity", "Starting Project-Wide Audit & Ingestion...");
                com.example.safeinbox.database.SpamDao dao = new com.example.safeinbox.database.SpamDao(this);
                com.example.safeinbox.detection.SpamDetector detector = com.example.safeinbox.detection.SpamDetector.getInstance(this);
                
                // 1. Ingest existing system messages if first run
                if (!isFirstRun) {
                    dao.ingestSystemMessages(detector);
                    prefs.edit().putBoolean("is_first_ingestion_complete", true).apply();
                }

                // 2. Reclassify existing project messages
                dao.reclassifyProjectWide(detector);
                
                prefs.edit().putInt("last_engine_rescan_version", com.example.safeinbox.utils.Constants.CURRENT_ENGINE_VERSION).apply();
                android.util.Log.i("MainActivity", "Audit Completed.");
                
                // --- NOTIFY UI TO REFRESH ---
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    Toast.makeText(this, "✅ Forensic Audit Complete. Protection Active.", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(com.example.safeinbox.utils.Constants.ACTION_DATABASE_RESCANNED);
                    androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                });
            });
        }
    }

    private void setupViewPager() {
        binding.viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public androidx.fragment.app.Fragment createFragment(int position) {
                return position == 0 ? new ContactsFragment() : new MessagesFragment();
            }

            @Override
            public int getItemCount() {
                return 2;
            }
        });

        new TabLayoutMediator(binding.tabLayout, binding.viewPager, (tab, position) -> {
            tab.setText(position == 0 ? "Contacts" : "Security");
        }).attach();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == Constants.PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int res : grantResults) {
                if (res != PackageManager.PERMISSION_GRANTED) allGranted = false;
            }
            
            if (allGranted) {
                Toast.makeText(this, "✅ Permissions Granted", Toast.LENGTH_SHORT).show();
                checkAndRescanProject();
                setupViewPager();
            } else {
                Toast.makeText(this, "⚠️ App needs SMS and Contacts permissions to function properly.", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Inner Fragment for Contacts List
     */
    public static class ContactsFragment extends androidx.fragment.app.Fragment {
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            RecyclerView recyclerView = new RecyclerView(requireContext());
            recyclerView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            recyclerView.setPadding(0, 16, 0, 100);
            recyclerView.setClipToPadding(false);

            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                recyclerView.setAdapter(new ContactsAdapter(ContactUtils.getAllContacts(requireContext())));
            } else {
                Toast.makeText(getContext(), "Permission denied to view contacts", Toast.LENGTH_SHORT).show();
            }
            return recyclerView;
        }
    }

    /**
     * Inner Fragment for existing SMS functionality access
     */
    public static class MessagesFragment extends androidx.fragment.app.Fragment {
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.fragment_messages_access, container, false);
            view.findViewById(R.id.btn_open_inbox).setOnClickListener(v -> {
                startActivity(new Intent(requireActivity(), InboxActivity.class));
            });
            view.findViewById(R.id.btn_view_spam_main).setOnClickListener(v -> {
                startActivity(new Intent(requireActivity(), SpamActivity.class));
            });
            view.findViewById(R.id.btn_view_archive).setOnClickListener(v -> {
                startActivity(new Intent(requireActivity(), ArchiveActivity.class));
            });
            return view;
        }
    }
}
