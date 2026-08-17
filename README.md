# LINKS — M-PESA Transaction Manager

> **L**edger & **I**ntelligent **N**etwork for **K**enyan **S**tations  
> Built for Kiryan Energy Ltd petrol stations, Kenya 🇰🇪  
> Operated by **Kiryan Energy Ltd**

---

## Overview

LINKS is a production-grade Android app that captures, parses, and reconciles M-PESA transactions in real time for petrol stations. It replaces manual M-PESA statement reviews with an automated system that lets managers assign every transaction a role, manages shifts, and syncs everything to a shared cloud backend across multiple stations.

**The problem it solves:** At a busy 24-hour station, CSAs receive M-PESA payments on behalf of the station, transfer money between tills, make withdrawals, and occasionally face reversals. Reconciling who collected what, what was transferred or withdrawn, and whether it all matches the closing balance — previously done manually with pen and paper — is now automated and centrally backed up, station by station.

---

## Features

| Feature | Description |
|--------|-------------|
| 📩 **SMS Capture** | Intercepts M-PESA SMS in real time via foreground service |
| 🔍 **Auto Parsing** | Extracts amount, code, sender, type from raw SMS (99.8%+ accuracy) |
| 🏷️ **Role-Based Assignment** | Every transaction — receipts, transfers, withdrawals, reversals — arrives unassigned and is manually reviewed and tagged with a role by the manager |
| 👷 **CSA Assignment** | Assign customer receipts to Customer Service Attendants per shift |
| 📊 **Shift Management** | Open, freeze, and close shifts with full reconciliation |
| ☁️ **Supabase Sync** | Cloud backup with offline retry queue, plus a manual "Sync Now" option that runs immediately without waiting on background scheduling |
| 🏪 **Multi-Station** | One shared Supabase backend serves multiple stations — each phone is configured independently and its data is fully isolated by `station_id` |
| 🔁 **Manual Review** | Queue for SMS that failed to parse — review and assign manually |
| 🔒 **PIN Lock** | 4-digit PIN screen on every launch (SHA-256 hashed) |
| 📦 **Bulk Assign** | Assign thousands of unassigned customer receipts to a CSA in one tap |

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM (ViewModel + LiveData) |
| Local DB | Room (SQLite) v6 |
| Cloud | Supabase (PostgreSQL) |
| Background | WorkManager (offline retry) + Foreground Service |
| Auth | SHA-256 PIN (local) + PBKDF2 supervisor password |
| Build | Gradle (Kotlin DSL) |

---

## Project Structure

```
app/src/main/java/com/githow/links/
├── MainActivity.kt                         # Navigation host, Screen enum, Settings screen
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
│   │   ├── LinksDatabase.kt               # Room DB v6, migration chain
│   │   ├── DatabaseMigration.kt           # MIGRATION_2_3
│   │   ├── Migration_3_4.kt              # MIGRATION_3_4 (supabase sync columns)
│   │   ├── Migration_4_5.kt              # MIGRATION_4_5 (schema index fixes)
│   │   └── Migration_5_6.kt              # MIGRATION_5_6 (role-based assignment system)
│   └── entity/
│       ├── RawSms.kt
│       ├── Transaction.kt                 # TransactionRole, TransactionDirection enums
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
│   └── CloudSyncManager.kt               # Supabase backup logic, station UUID resolution
│
├── worker/
│   └── SupabaseSyncWorker.kt             # WorkManager offline retry
│
├── utils/
│   └── MpesaParser.kt                    # M-PESA SMS parser
│
├── viewmodel/
│   ├── ShiftViewModel.kt                  # Role-based assignment, shift lifecycle
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
    │   ├── TransactionAssignmentScreen.kt # Role assignment for every transaction
    │   ├── OpenShiftScreen.kt
    │   ├── CloseShiftScreen.kt            # Role-aware unassigned-count gating, bulk assign
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

## Database Schema (Room v6)

| Table | Purpose |
|-------|---------|
| `raw_sms` | Every intercepted SMS, raw and parsed |
| `transactions` | Parsed M-PESA transactions, each carrying a `role`, `direction`, and reconciliation flag |
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
v5 → v6  Migration_5_6.kt       Added role-based transaction classification (role, direction, included_in_reconciliation, last_modified_at), frozen_at on shifts
```

---

## Transaction Roles

Every transaction — whatever its parsed type — arrives in the app as **UNASSIGNED**. The manager reviews each one on the assignment screen and tags it with a role:

| Role | Direction | Included in Reconciliation |
|------|-----------|------------------------------|
| Customer Receipt | IN | ✅ |
| Till Transfer In | IN | ✅ |
| Withdrawal | OUT | ✅ |
| Reversal | OUT | ✅ |
| Till Transfer Out | OUT | ✅ |
| Duplicate | — | ❌ (excluded) |

This keeps every inbound and outbound movement visible and intentional — nothing is auto-classified or hidden from review. A shift cannot be closed until every transaction has a role.

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

LINKS uses a single shared Supabase backend for all stations. Each station's data is fully isolated by `station_id`, and each phone is configured independently via the Settings screen.

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
- UUID is resolved from Supabase and cached on first successful sync
- The Settings screen shows live UUID resolution status — "UUID Resolved" or "UUID Not Resolved" — so you can confirm a new station is correctly connected
- A **Sync Now** button triggers an immediate sync without waiting for the periodic background worker — useful for confirming a new station's connection right away
- If Settings shows "UUID Not Resolved" after a sync attempt, confirm the station code matches a row in the Supabase `stations` table exactly
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

WorkManager runs a periodic sync every 15 minutes and a one-time retry on failure, both requiring network connectivity. The Settings screen also offers a manual **Sync Now** button that runs immediately and reports exactly how many SMS and transactions were pushed.

---

## PIN Security

- 4-digit PIN set on first launch
- Stored as SHA-256 hash in `SharedPreferences`
- Every app launch requires PIN entry
- No back button bypass
- Supervisor actions (shift close, manual review) use a separate PBKDF2 password via `AuthenticationService`, set up by each station's supervisor on first run

---

## Reconciliation Formula

```
Expected Float = Closing Balance − Opening Balance + Money Out
Variance = Expected Float − Grand Total (assigned customer receipts)
```

Money Out is the sum of all transactions assigned an OUT-direction role (Withdrawal, Reversal, Till Transfer Out). Duplicate transactions are excluded from reconciliation entirely to avoid double-counting till-to-till transfers.

---

## Build & Install

### Configure Supabase Credentials
Before building, create or edit `local.properties` in the project root:
```
SUPABASE_URL=https://your-project-ref.supabase.co
SUPABASE_ANON_KEY=your-anon-public-key
sdk.dir=<path to your Android SDK>
```
These values come from Supabase Dashboard → Settings → API. Do not commit this file to version control.

### Debug APK
```
Android Studio → Build → Build Bundle(s)/APK(s) → Build APK(s)
Output: app/build/outputs/apk/debug/app-debug.apk
```

### Install on Station Phone
1. Copy `app-debug.apk` to phone (USB / WhatsApp / Google Drive)
2. Open file on phone → Install
3. Allow "Install from unknown sources" if prompted
4. Launch → set PIN → create supervisor account → go to Settings → enter station info → Sync Now

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
- WorkManager's background network constraint can be unreliable on some budget Android ROMs — the manual Sync Now button is the most reliable way to confirm a sync on those devices

---

## Station Deployments

| Station | Code | Location |
|---------|------|----------|
| Shell Mangu Road | `MANGU` | Kiambu |
| TotalEnergies Gataka Road | `GATAKA` | Kajiado |

---

## License

```
Copyright (c) 2026 Kiryan Energy Ltd. All rights reserved.

This software and its source code are proprietary and confidential.
Unauthorized copying, distribution, modification, or use of this software,
in whole or in part, is strictly prohibited without prior written permission
from Kiryan Energy Ltd.

For licensing inquiries, contact the development team.
```

---

## Developer

**(John Gitau)** — Station Manager & Data Analyst, Kiryan Energy Ltd  
BSc Applied Statistics with IT  
MSc Artificial Intelligence (in progress)  
Built and maintained independently alongside station operations.

---

*LINKS is built for real operational use at Kenyan petrol stations. It handles production M-PESA data daily across multiple sites.*
