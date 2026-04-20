
<div align="center"> 


```text
 ██████╗ ███████╗██╗      █████╗ ██╗   ██╗██╗  ██╗
 ██╔══██╗██╔════╝██║     ██╔══██╗╚██╗ ██╔╝╚██╗██╔╝
 ██████╔╝█████╗  ██║     ███████║ ╚████╔╝  ╚███╔╝ 
 ██╔══██╗██╔══╝  ██║     ██╔══██║  ╚██╔╝   ██╔██╗ 
 ██║  ██║███████╗███████╗██║  ██║   ██║   ██╔╝ ██╗
 ╚═╝  ╚═╝╚══════╝╚══════╝╚═╝  ╚═╝   ╚═╝   ╚═╝  ╚═╝
```

**Seamless. Secure. Built for Scale.**

*Real-time file transfers across any two devices — no accounts, no cables, no friction.*

<br/>

![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Firebase](https://img.shields.io/badge/Firebase-FFCA28?style=for-the-badge&logo=firebase&logoColor=black)
![Supabase](https://img.shields.io/badge/Supabase-3ECF8E?style=for-the-badge&logo=supabase&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)

</div>

---

## ✦ What is RelayX?

> **Enter a code. Pick your files. Done.**

RelayX is a production-grade Android file transfer app that moves files between completely unlinked devices over the public internet — no login flows, no local network dependencies, no Bluetooth pairing hell.

A **6-character code** is all it takes. The rest happens in real time, in the background, at scale.

---

## ✦ Feature Highlights

| | Feature | What it means for you |
|---|---|---|
| ⚡ | **Real-Time Sync** | UI updates live as bytes move — sender and receiver stay in perfect sync via Firestore listeners |
| 📂 | **Multi-File Batching** | Send 1, 5, or 20 files at once using modern `ActivityResultContracts` — dispatched in parallel |
| 🛡️ | **Background Resiliency** | `WorkManager` handles uploads — swipe the app away, it still delivers |
| 💾 | **Process-Death-Proof Downloads** | Active download IDs persisted in `Preferences DataStore` — survives any crash or kill |
| 👤 | **Zero-Friction Onboarding** | No accounts. No passwords. No emails. Completely anonymous. |
| 🧠 | **True Clean Architecture** | Hard separation: Presentation → Domain → Repository → Data Source |

---

## ✦ Architecture

RelayX is built on **Clean Architecture + MVVM**, with zero shortcuts.

```
┌─────────────────────────────────────────────────────┐
│                  PRESENTATION LAYER                 │
│         Jetpack Compose  ·  ViewModel StateFlow     │
└────────────────────┬────────────────────────────────┘
                     │  MVI-style unidirectional data flow
┌────────────────────▼────────────────────────────────┐
│                   DOMAIN LAYER                      │
│     SendFileUseCase  ·  ObserveTransfersUseCase     │
│          Pure Kotlin — zero Android imports         │
└────────────────────┬────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────┐
│                    DATA LAYER                       │
│   Firestore Streams  ·  Supabase REST  ·  WorkMgr   │
│        Repository interfaces as single truth        │
└─────────────────────────────────────────────────────┘
```

---

## ✦ How a Transfer Actually Works

```
  SENDER                                        RECEIVER
    │                                               │
    │  1. App boots → anonymous 6-char code         │
    │     generated & saved to Firestore            │
    │                                               │
    │  2. Sender inputs receiver's code ──────────► │
    │                                               │
    │  3. Files picked → WorkRequest fired          │
    │     per URI into WorkManager                  │
    │                                               │
    │  4. InputStream streamed directly             │
    │     into Supabase via Ktor socket             │
    │     (no RAM caching — zero OOM risk)          │
    │                                               │
    │  5. Progress pinged to Firestore ──────────── │──► Live UI update
    │     every ~5%                                 │
    │                                               │
    │  6. Status → SENT ──────────────────────────► │
    │                                               │
    │                               7. DownloadManager invoked
    │                                  Download ID → DataStore
    │                                  File saved to device ✓
```

---

## ✦ Tech Stack

<table>
<tr>
<td><b>🟣 Kotlin</b></td>
<td>Primary language — idiomatic, functional, expressive</td>
</tr>
<tr>
<td><b>🎨 Jetpack Compose</b></td>
<td>Fully declarative UI — no XML, no compromises</td>
</tr>
<tr>
<td><b>🔥 Firebase Firestore</b></td>
<td>NoSQL real-time document streams powering live sync</td>
</tr>
<tr>
<td><b>🟢 Supabase Storage</b></td>
<td>Object storage with byte-chunked Ktor streams — no OOM</td>
</tr>
<tr>
<td><b>⚙️ WorkManager</b></td>
<td>Parallel, reliable background upload orchestration</td>
</tr>
<tr>
<td><b>📥 DownloadManager</b></td>
<td>OS-native massive file retrieval and caching</td>
</tr>
<tr>
<td><b>🌊 Coroutines & Flow</b></td>
<td>StateFlow · SharedFlow · callbackFlow — full async pipeline</td>
</tr>
<tr>
<td><b>💽 DataStore</b></td>
<td>Preferences persistence for crash-safe download pointers</td>
</tr>
<tr>
<td><b>🖼️ Coil</b></td>
<td>Memory-efficient image decoding and URI projection</td>
</tr>
</table>

---

## ✦ Screenshots

<p align="center">
  <img width="30%" alt="Home Image" src="https://github.com/user-attachments/assets/10165d25-3348-42b1-915c-9abc9a62a362" />


  <img width="30%" alt="Upload File" src="https://github.com/user-attachments/assets/bc39b814-93d1-4bf3-a9ed-079bbf21a521" />


  <img width="30%"  alt="Download page" src="https://github.com/user-attachments/assets/57db3515-6281-4a61-851a-1ddcbbbb217c" />

</p>
<p align="center">
  <sub>Home Screen &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; Upload in Progress &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; Download in Progress</sub>
</p>

---

## ✦ Setup

```bash
# 1. Clone
git clone https://github.com/yourusername/RelayX.git
```

**2. Add Firebase**
Download `google-services.json` from the Firebase Console → drop it in `/app`. Enable Firestore.

**3. Add Supabase credentials**
Create / edit `local.properties` in the project root:
```properties
SUPABASE_URL="https://your-project-id.supabase.co"
SUPABASE_KEY="your-anon-key-here"
```

**4. Build & Run**
Sync Gradle and deploy to a **physical device** — emulators bottleneck hard on heavy I/O.

> ⚠️ **Firestore Composite Indexes required.** The dual-query listener (`senderCode == user OR receiverCode == user` + `.orderBy("timestamp")`) will abort without them. Configure manually in the Firebase Console.

---

## ✦ Security

- 🔑 Supabase keys and `google-services.json` are **never committed** — injected at build time via `BuildConfig` from `local.properties`
- 🔄 Code collisions are safely mitigated on Firestore document creation

---

## ✦ What's Next

```
[ ] FCM Push Notifications   — wake receiver devices the moment a transfer is pushed
[ ] Resumable Uploads        — Ktor partial-stream recovery on hard network cutouts
[ ] End-to-End Encryption    — AES-256 wrapping the InputStream before socket dispatch
```

---

<div align="center">

**Built by Vaibhav**

*Android Developer — obsessed with structural depth, operational resilience, and native interfaces that feel right.*

<br/>

*If RelayX saved you from AirDrop, give it a ⭐*

</div>
