# LINKS - Financial and Shift Management App

## Description

LINKS is an Android application designed to streamline business operations by automatically tracking financial transactions from SMS messages and providing tools for managing employee shifts. The app listens for incoming SMS alerts, parses them to record transaction details, and allows for the assignment of these transactions to specific employees or categories within an active work shift.

This tool is ideal for small businesses or managers who need to correlate incoming payments with staff activity in real-time.

## Features

- **Automated SMS Transaction Parsing:** Automatically detects and parses incoming financial SMS messages to capture details like amount, sender, and transaction code.
- **Shift Management:** Create and manage work shifts. Only one shift can be active at a time, providing a clear operational context.
- **Transaction Assignment:** Assign parsed transactions to specific employees or categories (e.g., "Debt Paid").
- **Real-time Summaries:** View a live summary of assigned vs. unassigned transactions and the total value of unassigned funds.
- **Local Data Persistence:** All data (transactions, shifts, personnel) is stored locally on the device using a Room database.
- **Modern UI:** Built entirely with Jetpack Compose for a clean and responsive user interface.

## Core Components

- **SmsReceiver:** A `BroadcastReceiver` that listens for `android.provider.Telephony.SMS_RECEIVED` intents. It's the entry point for parsing SMS messages and adding them to the database.
- **LinksDatabase:** A Room database that holds all the application's data, including `Transaction`, `Shift`, `Person`, and `ShiftAssignment` entities.
- **ViewModels (`TransactionViewModel`, `ShiftViewModel`):** These ViewModels handle the business logic, expose data from the database to the UI, and process user interactions.
- **Compose Screens:** The UI is structured into several composable screens, including:
    - `TransactionListScreen`: Displays a historical list of all parsed transactions.
    - `TransactionAssignmentScreen`: The core screen for assigning transactions to employees within the active shift.
    - Other screens for managing shifts and personnel.

## How It Works

1.  **Permissions:** The app requests `RECEIVE_SMS` and `READ_SMS` permissions upon first launch.
2.  **SMS Reception:** When an SMS arrives, the `SmsReceiver` is triggered. It includes logic to filter for relevant financial messages (e.g., from a specific bank or mobile money provider).
3.  **Data Parsing & Storage:** If the SMS is identified as a valid transaction, its content is parsed to extract key details. A new `Transaction` entity is created and saved into the Room database.
4.  **Shift Activation:** The user must manually start a "shift" from within the app. This creates a `Shift` record.
5.  **Transaction Assignment:** In the `TransactionAssignmentScreen`, the app displays all transactions that have not yet been assigned to anyone. The user can select one or more transactions and assign them to a person on the current shift.
6.  **Data Synchronization:** All assignments and transaction statuses are updated in the local database, ensuring data integrity and providing an accurate, real-time view of business operations.

## Technologies Used

- **UI:** [Jetpack Compose](https://developer.android.com/jetpack/compose) for the entire user interface.
- **Database:** [Room Persistence Library](https://developer.android.com/training/data-storage/room) for local data storage.
- **Architecture:** MVVM (Model-View-ViewModel).
- **Asynchronous Programming:** Kotlin Coroutines and `Flow` for managing background tasks and data streams.
- **Dependency Injection (Implicit):** ViewModels are used to inject data and logic into composable functions.
- **Background Processing:** [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) for reliable background tasks.

## Setup

1.  Clone the repository:
    ```bash
    git clone <your-repository-url>
    ```
2.  Open the project in Android Studio.
3.  Build and run the project on an Android device or emulator.
4.  Grant the requested SMS permissions when prompted.

To test the core functionality, you can use the Android emulator's extended controls to send a mock SMS to the device with a format that the `SmsReceiver` expects.

## Future Improvements

- **Cloud Sync:** Implement a backend service (e.g., Firebase Firestore) to sync data across multiple devices and provide a web dashboard.
- **Data Export:** Add functionality to export transaction and shift data to CSV or PDF formats for reporting.
- **Enhanced Analytics:** Create a dedicated dashboard with charts and graphs to visualize transaction trends, employee performance, and peak hours.
- **User Authentication:** Add user login to secure access to the application.
- **More SMS Formats:** Expand the parsing logic to support a wider variety of transaction SMS formats from different banks and financial institutions.
- **End-to-end testing:** Create a full suite of tests to validate the app's functionality.
