# RelayX 🚀

RelayX is a modern, real-time file transfer application for Android using Jetpack Compose, Firebase Firestore, and Supabase Storage.

## 🔒 Security & Local Setup Guide

This project is configured to **never** expose API keys in version control.
If you are cloning this repository to build locally, you must provide your own credentials:

### 1. Firebase configuration
Place your `google-services.json` inside the `/app` directory.
Firebase handles real-time syncing of the transfer states.

### 2. Supabase Storage API Keys
This project uses the Supabase Storage REST API out of a bucket named `files`.
To safely inject your keys, open the project root and create a file exactly mapped to the `local.properties.example` file:

**Create `local.properties` at the root directory:**
```properties
SUPABASE_URL=https://your-supabase-project.supabase.co
SUPABASE_ANON_KEY=your_supabase_anon_key_here
```

**Important:** Never commit `local.properties` or `google-services.json` to GitHub!

## Architecture Details
- **UI:** Jetpack Compose (Material 3)
- **Design Pattern:** MVVM Clean Architecture
- **State Management:** Kotlin Coroutines (`StateFlow` & `combine`)
- **Remote:** Ktor Client for streaming file uploads.
