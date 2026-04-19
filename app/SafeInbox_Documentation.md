# SafeInbox Documentation

## 1. Overview
**SafeInbox** is an advanced, high-performance Android application designed to secure your messaging experience by identifying and filtering out SMS, MMS, and RCS spam. It provides a secure environment requiring authentication, complete with a hybrid, multi-layered machine learning spam detection engine and seamless, deduplication-aware message management.

## 2. Key Features

- **Authentication System**: Secure login leveraging Firebase Phone Authentication and OTP verification to protect user data, with biometric support.
- **Unified Multi-Protocol Support**: Intelligently handles standard SMS, MMS, and modern RCS messages simultaneously. Contains a robust deduplication system to ensure users don't see duplicate messages from multiple notification delivery paths.
- **6-Layer Hybrid Spam Engine**: Calculates a risk profile for every message based on:
    1. **System Blacklist**: Checks against blocked numbers.
    2. **Keyword Engine**: Looks for suspicious patterns, urgent phrasing, and dangerous links.
    3. **Machine Learning Model**: Naïve Bayes text classifier that improves over time.
    4. **Sender History**: Tracks how often a sender triggers spam algorithms.
    5. **User Feedback**: Tracks user's block/report actions for persistent sender reputation scores.
    6. **Contact Verification**: Heavily trusts messages from saved contacts when no suspicious links are present.
- **Optimistic UI with Turbo-Boost Architecture**: Provides a near-instant user experience when blocking or deleting messages. The UI updates instantly while heavy database and ML training operations fall back to an asynchronous `TurboExecutor` thread pool.
- **Content-Aware Deduplication**: A specialized deduplication system tracks message fingerprints (`dedup_id`). When you delete or block a message, SafeInbox automatically sweeps the database and applies the action to all hidden duplicate copies.
- **Dark Mode Aesthetic**: A premium dark-themed UI featuring Google fonts (`Inter`/`Roboto`), purple gradients, and glassmorphic elements for excellent readability and modern aesthetics.

## 3. Project Architecture

The codebase is built on Java and strongly relies on the standard Android SDK APIs. It's structured modularly:

### UI Layer (`com.example.safeinbox.activities`)
- `LoginActivity` / `OTPActivity`: Flow for phone-based authentication via Firebase.
- `MainActivity`: Serves as the secure hub bridging the Contacts View and Messaging Access.
- `InboxActivity`: The main view for all safe messages (Ham). Supports advanced search, message viewing, and blocking.
- `SpamActivity`: Dedicated space for reviewing blocked/flagged messages, with options to delete or restore.

### Spam Detection & ML Layer (`com.example.safeinbox.detection`)
- `SpamDetector`: Coordinates the different spam detection strategies.
- `MLClassifier`: The heart of the on-device AI. Implements a Naïve Bayes probability algorithm. It calculates the likelihood of text being spam based on word frequencies.
- `SpamScoreEngine`: The weighted logic engine that combines ML probabilities, Keyword hits, and historical reputation to make the final "Spam or Ham" verdict.

### Database Layer (`com.example.safeinbox.database`)
- Uses `SQLiteDatabase` for localized, on-device data persistence.
- `DBHelper`: Manages schema creation and migrations (Currently at `DB_VERSION = 10`).
- `SpamDao`: Handles messages, blocklists, deleted message blacklists, user feedback tracking, and sender reputation scores. Features optimized indexed queries and atomic batch transactions.
- `MLDao`: Dedicated persistence for the ML engine's vocabulary, saving word-count frequencies.

### System Interception (`com.example.safeinbox.services` & `sms`)
- `SmsReader`: Acts as a bridge to Android's native content providers (`content://sms` & `content://mms`).
- `SmsReceiver`: A traditional `BroadcastReceiver` catching incoming SMS intents.
- `SmsNotificationListener`: An advanced service that reads incoming notifications to intercept RCS (Rich Communication Services) messages, which standard Android SMS receivers cannot easily hook into.

## 4. Technology Stack
- **Language**: Java
- **UI System**: Android XML Layouts, Material Components (RecyclerView, SwipeRefreshLayout)
- **Database**: SQLite (Write-Ahead Logging enabled for speed)
- **Authentication**: Firebase Auth (Phone/OTP)
- **Concurrency**: Custom `TurboExecutor` (Fixed thread pool based on CPU core availability) for parallel computation.

## 5. Security & Privacy
- **Local AI Processing**: All spam detection and machine learning happen strictly on-device. Your messages are never sent to a remote server for text analysis.
- **Deleted Message Persistence**: The system uses a fingerprint-based blacklist. If a message is deleted, its fingerprint is saved, ensuring the local sync engine ignores that message even if it remains on the device's system-level SIM/Internal storage.

## 6. Setup & Build Instructions
1. Ensure you have the latest Android SDK and Android Studio (Koala or newer recommended).
2. Sync the project Gradle files.
3. To use Firebase Auth, ensure you have correctly configured the `google-services.json` file in the `app/` directory and connected your application signature (SHA-1) to the Firebase project console.
4. Run the app on a physical device or emulator (Android API 24+ supported, API 33+ recommended for notification interception).
