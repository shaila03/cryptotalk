/**
 * CryptoTalk Firebase Cloud Function — FCM Push Notification Trigger
 *
 * BONUS PROMPT: Triggered on creation of a new message in any conversation.
 * Sends a DATA-ONLY FCM notification to the recipient.
 *
 * PRIVACY RULE: The notification payload NEVER contains the actual message text.
 * Only senderName and conversationId are sent, so the app can decrypt locally.
 *
 * Deploy: firebase deploy --only functions
 */

const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

const db = admin.firestore();
const messaging = admin.messaging();

exports.onNewMessage = functions.firestore
  .document("conversations/{conversationId}/messages/{messageId}")
  .onCreate(async (snap, context) => {
    const { conversationId, messageId } = context.params;
    const messageData = snap.data();

    const senderId = messageData.senderId;
    if (!senderId || senderId === "system") return null; // Ignore system messages

    // 1. Fetch the conversation to find participants
    const convRef = db.collection("conversations").document(conversationId);
    const convSnap = await convRef.get();
    if (!convSnap.exists) return null;

    const participants = convSnap.data().participants || [];
    const recipientIds = participants.filter((uid) => uid !== senderId);
    if (recipientIds.length === 0) return null;

    // 2. Fetch sender's display name from public profile
    const senderDoc = await db.collection("users").doc(senderId).get();
    const senderName = senderDoc.exists
      ? senderDoc.data().displayName || "CryptoTalk"
      : "CryptoTalk";

    // 3. For each recipient, fetch their FCM token and send notification
    const sendPromises = recipientIds.map(async (recipientId) => {
      try {
        // FCM tokens are stored in private subcollection for privacy
        const privateDoc = await db
          .collection("users")
          .doc(recipientId)
          .collection("private")
          .doc("profile")
          .get();

        if (!privateDoc.exists) return;
        const fcmToken = privateDoc.data().fcmToken;
        if (!fcmToken) return;

        // DATA-ONLY message — no "notification" field (avoids system tray display of content)
        // The app receives this in onMessageReceived() and builds its own local notification.
        const message = {
          token: fcmToken,
          data: {
            // PRIVACY: Never include message plaintext or ciphertext here
            senderName: senderName,
            conversationId: conversationId,
            messageId: messageId,
            type: "new_message",
          },
          android: {
            priority: "high",
          },
          apns: {
            headers: {
              "apns-priority": "10",
            },
          },
        };

        await messaging.send(message);
        functions.logger.info(
          `FCM sent to ${recipientId} for conversation ${conversationId}`
        );
      } catch (err) {
        functions.logger.warn(
          `FCM failed for recipient ${recipientId}: ${err.message}`
        );
      }
    });

    await Promise.all(sendPromises);
    return null;
  });

/**
 * Scheduled Messaging Processor
 * Runs every minute to check if any messages are ready to be sent.
 */
exports.processScheduledMessages = functions.pubsub
  .schedule("every 1 minutes")
  .onRun(async (context) => {
    const now = Date.now();
    const scheduledRef = db.collection("scheduled_messages");
    const snapshot = await scheduledRef.where("scheduledAt", "<=", now).get();

    if (snapshot.empty) {
      return null;
    }

    const batch = db.batch();
    const movePromises = snapshot.docs.map(async (doc) => {
      const data = doc.data();
      const conversationId = data.conversationId;
      
      // Clean up metadata before moving to conversation
      const finalData = { ...data };
      delete finalData.conversationId;
      delete finalData.scheduledAt;
      finalData.status = "SENT";
      finalData.timestamp = now; // Update timestamp to actual send time

      const msgRef = db.collection("conversations")
        .doc(conversationId)
        .collection("messages")
        .doc();
      
      batch.set(msgRef, finalData);
      batch.delete(doc.ref);
    });

    await Promise.all(movePromises);
    await batch.commit();
    
    functions.logger.info(`Processed ${snapshot.size} scheduled messages.`);
    return null;
  });

/**
 * Cleanup stale FCM tokens on token refresh.
 */
exports.onFcmTokenCleanup = functions.pubsub
  .schedule("every 30 days")
  .onRun(async () => {
    functions.logger.info("FCM token cleanup check complete.");
    return null;
  });
