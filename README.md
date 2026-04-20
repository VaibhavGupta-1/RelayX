# 🚀 RelayX

**Seamless, Secure, and Built for Scale: Real-Time File Transfers Simplified.**

---

## 📖 Overview

RelayX is a robust, real-time Android file transfer application designed to securely transport files across completely unlinked devices over the public internet. Instead of forcing users through cumbersome login flows or local-only network constraints (like Bluetooth or Wi-Fi Direct), RelayX uses a dead-simple **6-character unique pairing code**. 

Enter the code, select your files, and watch them stream over the cloud in real-time. Built from the ground up prioritizing modern Android development standards, RelayX survives process death, streams bytes to avoid Out-Of-Memory (OOM) crashes, and operates on a strictly separated Clean Architecture model.

---

## ✨ Features

* **⚡ Real-Time File Transfer**: Instantaneous UI synchronization between sender and receiver via Firebase Firestore listeners.
* **📂 Multi-File Batching**: Select 1, 5, or 20 files instantly using modern `ActivityResultContracts` and dispatch them in parallel.
* **🛡️ Background Upload Resiliency**: Uploads are delegated to Android's `WorkManager`, guaranteeing file delivery even if the user swipes the app away or switches to another task.
* **💾 Persistent Downloading**: Receivers ingest files directly via Android's native `DownloadManager`. Active download IDs are committed to `Preferences DataStore`, guaranteeing recovery from process death without data loss.
* **📱 Anonymous Onboarding**: 100% frictionless. No user accounts, passwords, or emails.
* **🧠 True Clean Architecture**: Strict separation of concerns (Presentation → Domain UseCases → Repositories → Data Sources).

---

## 🧠 Architecture

RelayX strictly adheres to **Clean Architecture** patterns married with the **MVVM** presentation model. 

* **Presentation Layer**: Built completely in Jetpack Compose, state is observed via unidirectional MVI-style `StateFlow` structures from `ViewModel`.
* **Domain Layer**: Houses pure business logic (`SendFileUseCase`, `ObserveTransfersUseCase`) isolated completely from Android Context imports.
* **Data Layer**: Repositories abstract network implementations (Firestore streams, Supabase REST wrappers, WorkManager delegates) providing a single source of truth.

**Data Flow Sequence:**
*(Jetpack Compose UI) → (ViewModel StateFlow) → (Domain UseCase) → (Repository Interface) → (Remote / Local Data Sources).*

---

## ⚙️ Tech Stack

* **Kotlin**: Primary language, utilizing maximum idiomatic functional paradigms.
* **Jetpack Compose**: Completely declarative, dynamic UI toolkit for Android.
* **Firebase Firestore**: Powering blistering fast NoSQL document updates and snapshot listeners.
* **Supabase Storage**: Object storage combined with byte-chunked Ktor POST streams for massive file uploads without memory bloat.
* **Android WorkManager**: Orchestrates parallel reliable background uploads.
* **Android DownloadManager**: OS-level handling of massive file retrieval and caching.
* **Coroutines & Flow**: Complete asynchronous pipeline utilizing `StateFlow`, `SharedFlow`, and `callbackFlow` wrappers for callback-heavy SDKs.
* **DataStore (Preferences)**: Preserving critical download pointers.
* **Coil**: Image decoding and memory-efficient URI projection.

---

## 🔄 How It Works

1. **Initialization:** The app anonymously negotiates a randomly generated 6-character user code and saves it to Firestore.
2. **Pairing:** Senders input their intended target's 6-character code into the Home Screen.
3. **Dispatch:** The Sender picks extensive files. For each URI, a `WorkRequest` is fired into `WorkManager`.
4. **Streaming:** The device skips RAM-caching and natively streams the `InputStream` directly into Supabase Storage using Ktor sockets, pinging progress out to Firestore every ~5%.
5. **Real-time Read:** The Receiver runs an identical instance querying `Filter.or(...)`. They instantaneously see the upload happening live.
6. **OS Download:** Once marked `SENT`, the Receiver invokes the OS `DownloadManager`. The ID is committed to `DataStore`, and the file writes safely to local device storage.

---

## 📸 Screenshots

*(Replace placeholders with actual project screenshots)*

<p align="center">
  <img src="screenshots/home.png" width="30%" alt="Home Screen"/>
  &nbsp;&nbsp;&nbsp;
  <img src="screenshots/transfers_uploading.png" width="30%" alt="Upload in Progress"/>
  &nbsp;&nbsp;&nbsp;
  <img src="screenshots/transfers_downloading.png" width="30%" alt="Download in Progress"/>
</p>

---

## ⚠️ Important Notes

* **Firestore Composite Indexes**: The app natively relies on dual-query operators (`senderCode == user OR receiverCode == user` paired with `.orderBy("timestamp")`). This absolutely requires configuring Composite Indexes manually in the Firebase Console. Without them, the real-time listener will abort.
* **Active Internet Connection:** RelayX negotiates payloads over external cloud resources. Performance binds exactly to the device's uplink/downlink capacities.

---

## 🛠️ Setup Instructions

To get RelayX running locally:

1. **Clone the repository:**
   ```bash
   git clone https://github.com/yourusername/RelayX.git
   ```
2. **Inject Firebase:**
   Download your unique `google-services.json` file from the Firebase Console and place it natively inside the `/app` directory. Ensure Firestore is enabled in the cloud.
3. **Provide Supabase Credentials:** 
   RelayX utilizes `local.properties` to abstract sensitive remote tokens. Create (or edit) your `local.properties` file in the project root:
   ```properties
   SUPABASE_URL="https://your-project-id.supabase.co"
   SUPABASE_KEY="your-anon-key-here"
   ```
4. **Build & Run:** 
   Sync Gradle and deploy to a physical device (Emulators may bottleneck heavily on heavy I/O operations).

---

## 🔐 Security Considerations

* **Key Abstraction**: Neither Supabase keys nor Google Services descriptors are committed to Version Control, leveraging `BuildConfig` string injection via local environment variable mapping.
* **Ephemeral Persistence**: Code collisions are mitigated safely upon Firestore doc creations.

---

## 🚀 Future Improvements

1. **FCM Push Notifications**: Awaken receiver devices via Firebase Cloud Messaging exclusively when a transfer is pushed.
2. **Resumable Uploads**: Extend Ktor to interpret partial-streams in the event of hard network cutouts.
3. **End-to-End Encryption**: Wrap the native `InputStream` in symmetric key AES-256 before socket dispatch. The receiver must decrypt manually when pulling from Supabase.

---

### 👨‍💻 Author

**Vaibhav** 
*Android Developer*
Passionate about deeply structural application design, low-level operational resilience, and delightful native interface implementation.
