# Crypto Talk – Setup

## 1. Firebase

1. Create a project at [Firebase Console](https://console.firebase.google.com).
2. Add an Android app with package name `com.application.cryptotalk`.
3. Download `google-services.json` and replace `app/google-services.json` in this project.
4. In the Firebase project, enable **Authentication** → **Sign-in method** → **Email/Password**.
5. Create a **Firestore Database** (start in test mode for development; then deploy rules below).
6. Deploy Firestore rules from the project root:
   ```bash
   firebase deploy --only firestore:rules
   ```
   (Or copy the contents of `firestore.rules` into the Firebase Console → Firestore → Rules.)

## 2. Build and run

- Open the project in Android Studio.
- Sync Gradle and run on an emulator or device (min SDK 23).

## 3. First use

- Register with an email and password (e.g. Gmail).
- The app generates an RSA key pair and stores the public key in Firestore.
- Start a new conversation by entering another user’s email (they must already be registered).
- Messages are encrypted with AES-256-GCM; the AES key is encrypted with the recipient’s RSA public key.
