# LINKS – Financial and Shift Management App

## Overview
LINKS is an Android application built with **Kotlin** and **Jetpack Compose** designed to help small businesses and managers track financial transactions in real time during active work shifts.  
It automatically parses incoming **SMS payment notifications**, organizes them, and allows managers to assign each transaction to employees or categories.

## Key Features
- **Automatic SMS Parsing**  
  Detects and parses mobile money or banking SMS messages, extracting amounts, senders, timestamps, and transaction codes.

- **Shift Management**  
  Create, start, and close work shifts. Only one shift may be active at any time.

- **Transaction Assignment**  
  Assign parsed transactions to employees or categories such as “Debt Paid”.

- **Real‑Time Monitoring**  
  View totals of assigned and unassigned transactions with live updates.

- **Personnel Management**  
  Add, edit, and maintain records for employees.

- **Local Data Storage with Room**  
  All data is persisted on‑device, requiring no internet connection.

- **Modern UI with Jetpack Compose**  
  Clean and reactive UI built entirely with Compose and Material 3.

## Project Structure
```
app/
 ├─ data/
 │   ├─ local/        # Room DB, DAOs, Entities
 │   ├─ repository/   # Repository implementations
 ├─ domain/
 │   ├─ model/        # Core business models
 │   ├─ repository/   # Repository interfaces
 ├─ presentation/
 │   ├─ components/   # Reusable Compose UI components
 │   ├─ screens/      # App screens (Shift, Transactions, Personnel, etc.)
 ├─ receiver/
 │   └─ SmsReceiver.kt    # BroadcastReceiver handling SMS parsing
 ├─ utils/                # Helpers and formatting utilities
```

## Tech Stack
- **Kotlin**
- **Jetpack Compose**
- **Room Database**
- **Android Architecture Components**
- **Gradle Kotlin DSL**
- **Material 3**

## Permissions
The application requires:
- `RECEIVE_SMS` – to detect incoming financial transaction SMS
- `READ_SMS` – to parse SMS content

These permissions must be granted at runtime on modern Android versions.

## Building & Running
1. Clone or download this repository.
2. Open it in **Android Studio** (Giraffe or newer recommended).
3. Sync Gradle and build the project.
4. Run on physical device or emulator.
5. When prompted, grant SMS permissions.

### Testing SMS Parsing
In an emulator, use:
```
adb emu sms send <number> "<message>"
```
Ensure the message matches the expected financial SMS format.

## Future Roadmap
- Cloud sync (Firebase / Supabase)
- CSV/PDF data export
- Detailed analytics dashboard
- Cross-device support
- Supporting more SMS formats
- End‑to‑end and UI test coverage

## License
This project is currently closed-source unless otherwise stated by the author.
