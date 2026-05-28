# Product Requirements Document (PRD)
# Crypto Talk

**Version:** 1.0  
**Last Updated:** March 1, 2025  
**Status:** Draft  
**Platform:** Android Native

---

## 1. Product Overview

**Crypto Talk** is an Android-native secure real-time chat application that enables one-to-one messaging with End-to-End Encryption (E2EE). Messages are encrypted on the sender’s device and decrypted only on the recipient’s device. The backend and database never see plaintext; private keys remain exclusively on the user’s device.

**Value proposition:** Private, real-time text conversations with cryptographic guarantees: only the intended recipient can read messages.

**In scope (v1):**
- Gmail + password authentication
- Real-time one-to-one text messaging
- E2EE (RSA + AES-256-GCM)
- Private key stored only in Android Keystore; public keys in Firestore
- No plaintext or private keys stored in the database

---

## 2. Problem Statement

Users lack a simple, focused way to have private one-to-one conversations on Android where:
- Messages are protected in transit and at rest
- The service provider cannot read message content
- Keys are under the user’s device control
- The experience remains real-time and chat-like

Many solutions are either not E2EE, include broad feature sets (groups, media, calls), or rely on third-party chat SDKs that increase complexity and trust surface. Crypto Talk addresses this with a minimal, E2EE-only, one-to-one Android chat product.

---

## 3. Goals & Objectives

| Goal | Objective | Success Metric |
|------|-----------|----------------|
| Security | Implement E2EE so only sender and recipient can read messages | No plaintext in Firestore; private key only in Keystore |
| Usability | Deliver a clear, real-time chat experience | Messages delivered and decrypted in &lt; 3 seconds under normal conditions |
| Trust | Minimize attack surface and data exposure | No logging of message content or keys; documented key lifecycle |
| Focus | Ship a single use case well | v1 limited to one-to-one text only; no groups, media, or calls |

---

## 4. Target Users

- **Primary:** Privacy-conscious Android users who want E2EE one-to-one text chat without extra features.
- **Secondary:** Small teams or individuals who need a simple, auditable E2EE option (Android-only).

**Assumptions:** Users have a Gmail account, Android 6.0+ (API 23+), and are willing to create a dedicated app account (email + password). No iOS or web users in v1.

---

## 5. Core Features

1. **Authentication**  
   - Sign up and sign in with email (Gmail) and password via Firebase Authentication.

2. **User identity & key discovery**  
   - Each user has a unique identity (Firebase UID).  
   - Public key is stored in Firestore for others to fetch when starting or continuing a conversation.

3. **One-to-one conversations**  
   - User can start a chat with another user (by email or user ID).  
   - Single ongoing thread per pair (no multiple threads per pair in v1).

4. **Real-time messaging**  
   - Send and receive text messages with Firestore real-time listeners.  
   - Messages are encrypted before upload and decrypted only in app memory on the recipient device.

5. **End-to-end encryption**  
   - Sender: encrypt with AES-256-GCM; encrypt AES key with recipient’s RSA public key.  
   - Recipient: decrypt AES key with own RSA private key; decrypt message with AES-GCM.  
   - No plaintext or private keys in Firestore or backend.

6. **Secure key storage**  
   - RSA private key generated and stored only in Android Keystore; never exported to app storage or backend.

---

## 6. Detailed Functional Requirements

### 6.1 Authentication

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-A1 | User can register with email (Gmail) and password. | Must |
| FR-A2 | User can sign in with same email and password. | Must |
| FR-A3 | User can sign out. | Must |
| FR-A4 | Session persists until explicit sign-out or token expiry (handled by Firebase). | Must |
| FR-A5 | Invalid credentials show a clear error; no exposure of whether email exists. | Should |

### 6.2 Key setup & profile

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-K1 | On first successful registration/sign-in, generate an RSA 2048-bit key pair. | Must |
| FR-K2 | Store private key in Android Keystore; never write to disk or send to server. | Must |
| FR-K3 | Upload public key to Firestore under the user’s document. | Must |
| FR-K4 | If user has no key (e.g. new device), generate and store key pair and upload public key. | Must |
| FR-K5 | User profile in Firestore includes: userId (Firebase UID), email, publicKey, and optional display name. | Must |

### 6.3 Conversations & messaging

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-M1 | User can initiate a conversation by entering recipient email or selecting from a list. | Must |
| FR-M2 | User can send a text message in a conversation. | Must |
| FR-M3 | User receives new messages in real time (Firestore listener). | Must |
| FR-M4 | Sent messages show in sender’s UI immediately (optimistic update allowed). | Must |
| FR-M5 | Messages display in chronological order with sender and timestamp. | Must |
| FR-M6 | User can view only their own conversations (enforced by Firestore rules + app logic). | Must |
| FR-M7 | Message length limited (e.g. 4 KB) to avoid abuse and performance issues. | Must |

### 6.4 Encryption (behavioral)

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-E1 | Every message is encrypted with a unique AES-256-GCM key and IV. | Must |
| FR-E2 | The AES key is encrypted with the recipient’s RSA public key before storage. | Must |
| FR-E3 | Only the recipient can decrypt (using their Keystore-held private key). | Must |
| FR-E4 | Plaintext exists only in app memory for display; never in SharedPreferences, DB, or logs. | Must |

---

## 7. End-to-End Encryption Model

### 7.1 Overview

Crypto Talk uses a **hybrid** scheme: **RSA 2048** for encrypting per-message **AES-256-GCM** keys, and **AES-256-GCM** for message bodies. This keeps encryption/decryption efficient while ensuring each message uses a fresh symmetric key.

### 7.2 On user registration / first key generation

1. Generate an RSA 2048-bit key pair (e.g. `RSA/ECB/OAEPWithSHA-256AndMGF1Padding` or equivalent).
2. Store the **private key** in **Android Keystore** (hardware-backed when available), associated with the app and user.
3. **Upload only the public key** to Firestore (e.g. in `users/{userId}`).
4. Do not store the private key in Firestore, in app storage, or in logs.

### 7.3 When sending a message

1. **Fetch** the recipient’s **public key** from Firestore (e.g. `users/{recipientId}.publicKey`).
2. **Generate** a random **AES-256 key** and a random **IV** (e.g. 12 bytes for GCM).
3. **Encrypt** the message body with **AES-256-GCM** using this key and IV.
4. **Encrypt** the AES key with the recipient’s **RSA public key** (e.g. OAEP).
5. **Store in Firestore** only:
   - `encryptedMessage` (ciphertext)
   - `encryptedAESKey` (encrypted symmetric key)
   - `iv` (initialization vector)
   - `senderId`
   - `timestamp`
   - (and any other metadata needed for ordering; no plaintext.)

### 7.4 When receiving a message

1. **Read** from Firestore: `encryptedMessage`, `encryptedAESKey`, `iv`, `senderId`, `timestamp`.
2. **Load** the device’s **RSA private key** from Android Keystore.
3. **Decrypt** `encryptedAESKey` with the private key to get the **AES key**.
4. **Decrypt** `encryptedMessage` with **AES-256-GCM** using that key and `iv`.
5. **Display** the resulting plaintext only in UI memory; do not persist plaintext to disk or send to any server.

### 7.5 Security rules (design principles)

- **No plaintext** in Firestore or any backend storage.
- **No private key** ever leaves the device or is sent to the backend.
- **No logging** of message content, AES keys, or private keys.
- **Authenticated access:** only authenticated users can read/write their own data and the conversations they are part of (enforced via Firestore Security Rules).

---

## 8. Key Management Strategy

| Aspect | Strategy |
|--------|----------|
| **Generation** | RSA key pair generated on device at first use (registration or first sign-in with no key). |
| **Private key storage** | Android Keystore only. Key alias tied to user (e.g. userId) so multi-account on same device is supported. |
| **Public key distribution** | Stored in Firestore `users/{userId}`; other users fetch it when needed for encryption. |
| **Key lifecycle** | Private key lives until app uninstall or user-initiated “clear data” / account removal. No server-side revocation in v1. |
| **Backup** | Private keys are not backed up to the cloud (Keystore is device-bound). User must re-register or use key recovery flow if implemented later. |
| **Rotation** | v1: no automatic rotation. Future: new key pair can be generated and new public key uploaded; old messages remain decryptable with old key if retained. |

---

## 9. Firestore Database Structure

### 9.1 Collections

**`users`** (document ID = Firebase UID)

| Field | Type | Description |
|-------|------|-------------|
| `email` | string | User’s email (Gmail). |
| `publicKey` | string | Base64 or equivalent encoding of RSA public key for encryption. |
| `displayName` | string | Optional display name. |
| `createdAt` | timestamp | Account creation time. |

**`conversations`** (document ID = deterministic ID from sorted pair of userIds, e.g. `userId1_userId2`)

| Field | Type | Description |
|-------|------|-------------|
| `participants` | array | [userId1, userId2]. |
| `createdAt` | timestamp | Conversation creation. |
| `lastMessageAt` | timestamp | For sorting conversation list. |

**`conversations/{conversationId}/messages`** (subcollection)

| Field | Type | Description |
|-------|------|-------------|
| `encryptedMessage` | string | Base64 ciphertext (AES-GCM). |
| `encryptedAESKey` | string | Base64 encrypted AES key (RSA). |
| `iv` | string | Base64 IV for GCM. |
| `senderId` | string | Firebase UID of sender. |
| `timestamp` | timestamp | Server timestamp or client timestamp validated by rules. |

### 9.2 Security rules (principles)

- Only authenticated users can read/write.
- Users can read/write `users/{userId}` only when `request.auth.uid == userId`.
- Users can read/write a `conversations` document only if their `userId` is in `participants`.
- Users can read/write `messages` only in conversations where they are a participant.
- No field may contain plaintext message content; validation can be structural (e.g. presence of `encryptedMessage`, `encryptedAESKey`, `iv`).

---

## 10. App Screens & UX Requirements

| Screen | Purpose | Key elements |
|--------|--------|--------------|
| **Splash / Launch** | Check auth state; route to Login or Home. | No sensitive data; short delay only if needed. |
| **Login** | Sign in or navigate to Register. | Email, password, Sign In, “Create account”. |
| **Register** | Create account. | Email, password, confirm password, Register. |
| **Conversation list** | List of one-to-one chats. | Ordered by last message time; show other user’s name/email and last message timestamp (not content). |
| **Conversation (chat)** | Single thread view. | Message bubbles (sent/received), timestamps, input field, send button. |
| **New conversation / Add contact** | Start chat with another user. | Input for email or user lookup; start conversation. |
| **Settings / Profile** | Account and app settings. | Sign out, optional display name; no display of keys. |

**UX rules:**

- Errors (auth, network, decryption failure) must be user-friendly and must not leak crypto or system details.
- No plaintext in notifications (e.g. “New message” only).
- Loading and decryption states should be clear (e.g. “Decrypting…” then content or “Couldn’t load message”).

---

## 11. Security Requirements

| ID | Requirement |
|----|-------------|
| SR-1 | Private keys only in Android Keystore; never in app storage, Firestore, or logs. |
| SR-2 | No plaintext message storage in Firestore, Realtime Database, or local DB. |
| SR-3 | TLS for all network traffic (Firebase default). |
| SR-4 | No logging of message content, AES keys, IVs, or private keys. |
| SR-5 | Firestore Security Rules enforce that only conversation participants access conversation and message documents. |
| SR-6 | Authentication required for all reads/writes; no public access to message or key data. |
| SR-7 | Sensitive data in memory cleared when no longer needed where feasible (e.g. overwrite char arrays for passwords if used). |
| SR-8 | ProGuard/R8 rules: avoid obfuscating crypto-related classes if it breaks Keystore or crypto APIs; strip logs in release. |

---

## 12. Non-Functional Requirements

| Category | Requirement |
|----------|-------------|
| **Performance** | Message send: encryption + upload &lt; 2 s under normal conditions. Receive: listener + decrypt + display &lt; 3 s. |
| **Availability** | Depends on Firebase availability; no offline message queue requirement in v1. |
| **Compatibility** | Android 6.0 (API 23) minimum; target latest stable API. |
| **Stability** | No crash on decryption failure; show generic error and optionally “Message unavailable”. |
| **Accessibility** | Basic TalkBack support and sufficient contrast for core flows. |
| **Localization** | English required; structure strings for future i18n. |

---

## 13. Architecture Overview

- **Platform:** Android Native  
- **Language:** Kotlin  
- **UI:** Jetpack Compose  
- **Architecture:** MVVM (ViewModel, UI State, single source of truth per screen)  
- **Backend:** Firebase (Authentication, Firestore)  
- **Auth:** Firebase Email/Password  
- **Data:** Firestore with real-time listeners for messages and conversation list  
- **Crypto:** RSA 2048 (key encapsulation), AES-256-GCM (message encryption), Android Keystore (private key storage)

**Layers:**

- **UI:** Compose screens and components; observe ViewModel state.  
- **ViewModel:** Expose state and events; call use cases or repositories.  
- **Domain (optional):** Use cases for send message, receive message, key generation, etc.  
- **Data:** Repositories for Auth, Firestore (users, conversations, messages), and local Keystore/crypto.  
- **Crypto:** Module for RSA key generation, Keystore access, AES-GCM encrypt/decrypt, and RSA encrypt (AES key).

No group chat, voice/video, media, web, or third-party chat SDKs; one-to-one text only.

---

## 14. Risks & Mitigation

| Risk | Impact | Mitigation |
|------|--------|------------|
| Lost device / uninstall | User loses private key; cannot decrypt history. | Document that keys are device-bound; future: optional key backup/recovery. |
| Recipient’s public key missing or wrong | Cannot encrypt or recipient cannot decrypt. | Validate public key presence before send; show clear error; avoid overwriting existing key without user action. |
| Weak or compromised device | Keystore or app memory could be extracted. | Rely on Keystore hardware backing where available; recommend device security (PIN, etc.). |
| Firestore misconfiguration | Data exposed to wrong users. | Strict Security Rules; code review and testing of rules. |
| Logging or crash reports leaking data | Plaintext or keys in logs. | No logging of sensitive fields; sanitize crash reports. |

---

## 15. Future Enhancements (Out of Scope for v1)

- Group chat  
- Voice or video calls  
- Media or file sharing  
- Web or desktop client  
- Cross-platform frameworks (e.g. Flutter, React Native)  
- Third-party chat SDKs (e.g. SendBird, Twilio)  
- Key backup / recovery  
- Message editing or deletion (beyond local UI)  
- Read receipts or typing indicators  
- Push notification payload decryption (notifications remain generic)

These may be considered in later versions after v1 is stable and audited.

---

## 16. Definition of Done

A feature or the v1 release is “done” when:

- [ ] Implementation matches this PRD and agreed technical design.  
- [ ] E2EE flow is implemented as specified (RSA + AES-GCM; Keystore; no plaintext in Firestore).  
- [ ] Firestore structure and Security Rules are implemented and tested.  
- [ ] Authentication (register, sign in, sign out) works with Firebase Email/Password.  
- [ ] One-to-one conversation and real-time messaging work with real-time listeners.  
- [ ] No sensitive data in logs or crash reports.  
- [ ] Code review completed; known security and NFR gaps documented.  
- [ ] Manual QA on at least one device (send/receive, new user, key generation).  
- [ ] PRD and any architecture notes updated if scope or design changed.

---

*End of PRD. For implementation details, refer to technical design docs and this project’s codebase.*
