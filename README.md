# LINKS — M-PESA Transaction Manager

> **L**edger & **I**ntelligent **N**etwork for **K**iryan **S**tations  
> Built for Shell Mangu Road Service Station, Nairobi, Kenya 🇰🇪  
> Operated by **Kiryan Energy Ltd**

---

## Overview

LINKS is a production-grade Android app that captures, parses, and reconciles M-PESA transactions in real time for petrol stations. It replaces manual M-PESA statement reviews with an automated system that assigns transactions to CSAs, manages shifts, and syncs everything to the cloud.

**The problem it solves:** At a busy 24-hour station, CSAs receive M-PESA payments on behalf of the station. Reconciling who collected what, when, and whether it matches the closing balance — previously done manually with pen and paper — is now automated.

---

## Features

| Feature | Description |
|--------|-------------|
| 📩 **SMS Capture** | Intercepts M-PESA SMS in real time via foreground service |
| 🔍 **Auto Parsing** | Extracts amount, code, sender, type from raw SMS (99.8%+ accuracy) |
| 👷 **CSA Assignment** | Assign transactions to Customer Service Attendants per shift |
| 📊 **Shift Management** | Open, freeze, and close shifts with full reconciliation |
| ☁️ **Supabase Sync** | Cloud backup with offline retry queue via WorkManager |
| 🏪 **Multi-Station** | One APK serves multiple stations — each configured independently |
| 🔁 **Manual Review** | Queue for SMS that failed to parse — review and assign manually |
| 🔒 **PIN Lock** | 4-digit PIN screen on every launch (SHA-256 hashed) |
| 📦 **Bulk Assign** | Assign thousands of unassigned transactions in one tap |

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM (ViewModel + LiveData) |
| Local DB | Room (SQLite) v5 |
| Cloud | Supabase (PostgreSQL) |
| Background | WorkManager (offline retry) + Foreground Service |
| Auth | SHA-256 PIN (local) + PBKDF2 supervisor password |
| Build | Gradle (Kotlin DSL) |

---

## Project Structure

```
app/src/main/java/com/githow/links/
├── MainActivity.kt                         # Navigation host, Screen enum
├── SupabaseClient.kt                       # Supabase initialisation
│
├── config/
│   └── StationConfig.kt                   # Station identity (SharedPreferences)
│
├── data/
│   ├── dao/                               # Room DAOs
│   │   ├── RawSmsDao.kt
│   │   ├── TransactionDao.kt
│   │   ├── ShiftDao.kt
│   │   ├── PersonDao.kt
│   │   ├── ManualReviewQueueDao.kt
│   │   └── UserDao.kt
│   ├── database/
│   │   ├── LinksDatabase.kt               # Room DB v5, migration chain
│   │   ├── DatabaseMigration.kt           # MIGRATION_2_3
│   │   ├── Migration_3_4.kt              # MIGRATION_3_4 (supabase sync columns)
│   │   └── Migration_4_5.kt              # MIGRATION_4_5 (schema index fixes)
│   └── entity/
│       ├── RawSms.kt
│       ├── Transaction.kt
│       ├── Shift.kt
│       ├── Person.kt
│       ├── ShiftAssignment.kt
│       ├── ManualReviewQueue.kt
│       └── User.kt
│
├── receiver/
│   ├── SmsReceiver.kt                     # Intercepts incoming SMS
│   └── BootReceiver.kt                    # Restarts service after reboot
│
├── service/
│   ├── SmsForegroundService.kt            # Persistent background service
│   └── AuthenticationService.kt          # PBKDF2 supervisor auth
│
├── sync/
│   └── CloudSyncManager.kt               # Supabase backup logic
│
├── worker/
│   └── SupabaseSyncWorker.kt             # WorkManager offline retry
│
├── utils/
│   └── MpesaParser.kt                    # M-PESA SMS parser
│
├── viewmodel/
│   ├── ShiftViewModel.kt
│   ├── TransactionViewModel.kt
│   ├── SmsViewModel.kt
│   ├── ManualReviewViewModel.kt
│   └── UnparsedSmsViewModel.kt
│
└── ui/
    ├── screens/
    │   ├── PinScreen.kt                   # 4-digit PIN lock screen
    │   ├── HomeScreen.kt
    │   ├── SmsScreen.kt
    │   ├── UnparsedSmsScreen.kt
    │   ├── ManualReviewScreen.kt
    │   ├── TransactionListScreen.kt
    │   ├── TransactionAssignmentScreen.kt # Refresh button added
    │   ├── OpenShiftScreen.kt
    │   ├── CloseShiftScreen.kt            # Bulk assign feature
    │   ├── ShiftDashboardScreen.kt
    │   ├── ShiftReportScreen.kt
    │   ├── ClosedShiftsHistoryScreen.kt
    │   └── PersonManagementScreen.kt
    └── components/
        ├── ManualEntryCard.kt
        ├── ParseStatusBadge.kt
        └── SupervisorPasswordDialog.kt
```

---

## Database Schema (Room v5)

| Table | Purpose |
|-------|---------|
| `raw_sms` | Every intercepted SMS, raw and parsed |
| `transactions` | Parsed M-PESA transactions |
| `shifts` | Shift records (open/frozen/closed) |
| `shift_assignments` | Which CSAs worked a shift |
| `persons` | CSA profiles |
| `manual_review_queue` | Failed parses awaiting manual review |
| `users` | Supervisor accounts (PBKDF2 hashed) |

### Migration History
```
v2 → v3  DatabaseMigration.kt   Added parse_status, webhook sync, manual_review_queue, users
v3 → v4  Migration_3_4.kt       Added supabase_synced columns to transactions
v4 → v5  Migration_4_5.kt       Fixed index mismatches (index_raw_sms_synced_to_webhook, index_transactions_supabase_synced)
```

---

## Supabase Schema

Tables in Supabase mirror the local DB and are keyed by `station_id`:

```
stations          — one row per station
raw_sms           — station_id + device raw SMS id
transactions      — station_id + mpesa_code (unique per station)
shifts            — station_id + device shift_id
shift_assignments — shift_id + person_name
sync_log          — audit trail
```

All upserts use `onConflict` to ensure idempotency — safe to retry.

---

## Multi-Station Setup

### Add a New Station (Supabase)
```sql
INSERT INTO stations (station_code, station_name, till_number, paybill_number)
VALUES ('JOSKA', 'Shell Joska - Kagundo Rd', '0', '0');
```

### Configure the Phone (Settings Screen)
| Field | Example |
|-------|---------|
| Station Code | `JOSKA` |
| Station Name | `Shell Joska - Kagundo Rd` |
| Till Number | `0` |
| Paybill Number | `0` |

**Notes:**
- Station code is auto-uppercased on save
- UUID is resolved and cached on first sync
- If Settings shows "UUID not yet resolved" after first sync → wrong station code
- Each station's data is fully isolated by `station_id` in Supabase

---

## Offline Sync

LINKS never loses data due to network issues:

```
SMS arrives → saved to Room ✅
           → Supabase backup attempted
           → fails (no network) → WorkManager retry scheduled
           → network returns → SupabaseSyncWorker pushes all unsynced records
```

WorkManager runs a periodic sync every 15 minutes and a one-time retry on failure, both requiring network connectivity.

---

## PIN Security

- 4-digit PIN set on first launch
- Stored as SHA-256 hash in `SharedPreferences`
- Every app launch requires PIN entry
- No back button bypass
- Supervisor actions (shift close, manual review) use a separate PBKDF2 password via `AuthenticationService`

**Default supervisor password:** `admin123` — change after first login.

---

## Reconciliation Formula

```
Expected Customer Receipts = (Closing Balance − Opening Balance) + Money Sent Out
Variance = Expected Receipts − Actual Receipts
```

Internal M-PESA transfers between the station's own paybill numbers are auto-assigned as **Neutral** and excluded from the reconciliation to avoid double-counting.

---

## Build & Install

### Debug APK
```
Android Studio → Build → Build Bundle(s)/APK(s) → Build APK(s)
Output: app/build/outputs/apk/debug/app-debug.apk
```

### Install on Station Phone
1. Copy `app-debug.apk` to phone (USB / WhatsApp / Google Drive)
2. Open file on phone → Install
3. Allow "Install from unknown sources" if prompted
4. Launch → set PIN → go to Settings → enter station info

---

## Permissions

```xml
RECEIVE_SMS / READ_SMS          — capture M-PESA messages
INTERNET / ACCESS_NETWORK_STATE — Supabase sync
FOREGROUND_SERVICE              — persistent SMS listener
FOREGROUND_SERVICE_SPECIAL_USE  — Android 14+ requirement
RECEIVE_BOOT_COMPLETED          — restart service after reboot
WAKE_LOCK                       — keep CPU awake during SMS processing
REQUEST_IGNORE_BATTERY_OPTIMIZATIONS — prevent OS from killing SMS listener
POST_NOTIFICATIONS              — foreground service notification
```

---

## Known Limitations

- No restore from Supabase — uninstalling loses local data not yet synced
- Shift report is view-only — no PDF export yet
- No push notifications for large transactions

---

## Station Deployments

| Station | Code | Location | Till/Paybill |
|---------|------|----------|--------------|
| Shell Mangu Road | `MANGU` | Nairobi |  |
| Shell Joska | `JOSKA` | Kagundo Rd |  |

---

## License

```
Copyright (c) 2025 Kiryan Energy Ltd. All rights reserved.

This software and its source code are proprietary and confidential.
Unauthorized copying, distribution, modification, or use of this software,
in whole or in part, is strictly prohibited without prior written permission
from Kiryan Energy Ltd.

For licensing inquiries, contact the development team.
```

---

## Developer

**Knee** — Station Manager, Kiryan Energy Ltd  
BSc Applied Statistics with IT
MSc of Science in Artificial Intelligence
Built and maintained independently alongside station operations.

---

*LINKS is built for real operational use at Kenyan petrol stations. It handles production M-PESA data daily.*
