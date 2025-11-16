
# LINKS – Financial and Shift Management App

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Kotlin Version](https://img.shields.io/badge/Kotlin-1.9.0-blue.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-1.6.0-brightgreen.svg)](https://developer.android.com/jetpack/compose)

## 🌟 Overview

LINKS is a powerful yet intuitive Android application crafted with **Kotlin** and **Jetpack Compose**. It's designed to streamline financial tracking and shift management for small businesses. The app automatically captures and parses incoming SMS payment notifications, with a specific focus on **M-Pesa messages**, providing managers with a real-time overview of financial activities during work shifts. This enables efficient transaction assignment and monitoring, ensuring financial clarity and accountability.

## ✨ Key Features

- **🤖 Automatic SMS Parsing**: Intelligently detects and extracts crucial information from mobile money and banking SMS messages, including amounts, sender details, timestamps, and transaction codes. It includes a dedicated `MpesaParser.kt` for high-accuracy parsing of M-Pesa notifications.
- **🕒 Shift Management**: Seamlessly create, start, and end work shifts using screens like `OpenShiftScreen.kt` and `CloseShiftScreen.kt`. The app ensures that only one shift is active at a time, preventing overlaps and confusion.
- **💳 Transaction Assignment**: Effortlessly assign parsed transactions to employees or predefined categories. `TransactionAssignmentScreen.kt` provides a dedicated interface for this purpose.
- **📊 Real-Time Monitoring**: A dynamic dashboard (`ShiftDashboardScreen.kt`) provides a live view of both assigned and unassigned transaction totals, with updates happening in real-time.
- **👥 Personnel Management**: A comprehensive module (`PersonManagementScreen.kt`) to add, edit, and maintain employee records, ensuring that your team's information is always up-to-date.
- **🔒 Local Data Storage**: All data is securely stored on the device using the **Room persistence library**. The database schema is defined in `data/entity/` and accessed via DAOs in `data/dao/`. This ensures the app works perfectly offline.
- **☁️ Cloud Sync**: Sync your shift data to Google Sheets for backup and further analysis. Configure your Google Apps Script webhook URL in `CloudSyncManager.kt` to get started.
- **🎨 Modern UI**: A clean, modern, and reactive user interface built entirely with **Jetpack Compose** and **Material 3**. The theme and color palette are defined in `ui/theme/` and `res/values/colors.xml`.

## 📂 Project Structure

The project follows a standard Android architecture pattern, separating concerns for better maintainability.

```
app/src/main/java/com/githow/links/
 │
 ├─ data/
 │   ├─ dao/              # Data Access Objects for Room (PersonDao, ShiftDao, TransactionDao)
 │   ├─ database/         # LinksDatabase Room database setup
 │   └─ entity/           # Room @Entity classes (Person, Shift, Transaction)
 │
 ├─ receiver/
 │   └─ SmsReceiver.kt    # BroadcastReceiver for intercepting and handling incoming SMS.
 │
 ├─ sync/
 │   └─ CloudSyncManager.kt # Manager for handling cloud synchronization logic.
 │
 ├─ ui/
 │   ├─ screens/          # Composable screens for app features (OpenShiftScreen, ShiftDashboardScreen, etc.)
 │   └─ theme/            # App's theme, colors, and typography (Theme.kt, Color.kt)
 │
 ├─ utils/
 │   └─ MpesaParser.kt    # Utility for parsing M-Pesa SMS transaction messages.
 │
 ├─ viewmodel/
 │   ├─ ShiftViewModel.kt
 │   └─ TransactionViewModel.kt # ViewModels to hold and manage UI-related data.
 │
 └─ MainActivity.kt       # Main entry point of the application.
```

## 🛠️ Tech Stack

- **Kotlin**: The primary programming language, utilizing features like Coroutines for async operations.
- **Jetpack Compose**: For building a reactive and modern UI.
- **Room Database**: For robust, local data persistence.
- **Android Architecture Components**: Leveraging `ViewModel` for UI logic and `LiveData`/`Flow` for data observation.
- **Google Apps Script**: For cloud data backup to Google Sheets.
- **Gradle Kotlin DSL**: For managing dependencies and build configurations.
- **Material 3**: For the design system and UI components.

## 🚀 Getting Started

### Prerequisites

- Android Studio (Giraffe or newer recommended)
- An Android device or emulator
- A Google Account to set up the Google Apps Script webhook.

### Installation and Setup

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/your-repo/links.git
    ```
2.  **Open in Android Studio:**
    Open the cloned project in Android Studio.
3.  **Configure Cloud Sync:**
    - Open `app/src/main/java/com/githow/links/sync/CloudSyncManager.kt`.
    - Replace the placeholder `WEBHOOK_URL` with your own Google Apps Script deployment URL.
4.  **Sync Gradle:**
    Let Android Studio sync the Gradle files.
5.  **Run the app:**
    Run the app on a physical device or an emulator.
6.  **Grant Permissions:**
    When prompted, grant the necessary SMS permissions to enable the app to read and parse financial transaction SMS.

### Testing SMS Parsing

You can test the SMS parsing functionality in an emulator using the following adb command:

```bash
adb emu sms send <number> "<message>"
```

Make sure the message format matches the M-Pesa format expected by `MpesaParser.kt`.

## 🛣️ Future Roadmap

- **📄 Data Export**: Add functionality to export shift summaries and transaction lists to CSV or PDF formats.
- **📈 Analytics Dashboard**: Develop a detailed analytics dashboard to provide deeper insights into financial trends.
- **🧪 Testing**: Increase test coverage with more unit, integration, and UI tests.
- **🌐 Expanded SMS Support**: Add support for a wider range of SMS formats from different financial institutions.

## 🤝 Contributing

Contributions are welcome! If you have any ideas, suggestions, or bug reports, please open an issue or submit a pull request.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
