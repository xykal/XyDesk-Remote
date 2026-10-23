// XyDesk Remote — Chat Global backend (Firebase Cloud Functions).
//
// Pengganti Durable Object `GlobalChat` (cloudflare/worker.mjs):
// - sendChatMessage: callable, satu-satunya jalur tulis pesan klien.
//   Memvalidasi identitas (Firebase Auth), bentuk pesan, dan batas ukuran;
//   teks E2EE hanya diteruskan (ciphertext), tidak pernah dibuka di server.
// - pushOnMessageCreate: trigger Firestore -> FCM ke topic "global-chat"
//   (pengganti OneSignal). Payload hanya metadata + teks fallback display,
//   tidak membocorkan plaintext di luar yang memang tampil di chat.
//
// Moderasi lanjutan (regex tautan berbahaya, rate-limit per penulis,
// leaderboard) dipindah bertahap dari worker.mjs sebelum cutover.

const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");

admin.initializeApp();

const db = admin.firestore();

const TEXT_MAX = 500;
const NAME_MAX = 28;

function cleanText(value, max) {
  return typeof value === "string" ? value.trim().slice(0, max) : "";
}

exports.sendChatMessage = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "authentication_required");
  }
  const data = request.data || {};
  const text = cleanText(data.text, TEXT_MAX);
  const attachmentType = cleanText(data.attachment_type, 16);
  if (!text && !attachmentType) {
    throw new HttpsError("invalid-argument", "empty_text");
  }

  const name = cleanText(data.name, NAME_MAX) || "Tamu";
  const msg = {
    clientMsgId: cleanText(data.client_msg_id, 80),
    authorUid: request.auth.uid,
    name,
    githubLogin: cleanText(data.github_login, 39).replace(/^@/, ""),
    country: cleanText(data.country, 8) || "ID",
    flag: cleanText(data.flag, 8),
    text,
    // E2EE apa adanya dari klien (XY-E2EE-GCM-v1). Server tidak membuka ini.
    e2ee: {
      protocol: cleanText(data.e2ee_v, 24) || "XY-E2EE-GCM-v1",
      cipher: typeof data.e2ee_cipher === "string" ? data.e2ee_cipher : "",
      iv: typeof data.e2ee_iv === "string" ? data.e2ee_iv : "",
      sig: typeof data.e2ee_sig === "string" ? data.e2ee_sig : "",
    },
    translated: cleanText(data.translated, TEXT_MAX),
    targetLang: cleanText(data.target_lang, 12),
    sourceLang: cleanText(data.source_lang, 12),
    replyToId: cleanText(data.reply_to_id, 80),
    replyToName: cleanText(data.reply_to_name, NAME_MAX),
    replyToText: cleanText(data.reply_to_text, 140),
    replyDepth: Number.isFinite(data.reply_depth) ? Math.max(0, Math.min(12, data.reply_depth | 0)) : 0,
    attachment: {
      type: attachmentType,
      name: cleanText(data.attachment_name, 120),
      mime: cleanText(data.attachment_mime, 80),
      size: Number.isFinite(data.attachment_size) ? Math.max(0, data.attachment_size | 0) : 0,
      data: typeof data.attachment_data === "string" ? data.attachment_data : "",
    },
    viewOnce: data.view_once === true,
    revision: 0,
    editedAt: 0,
    deleted: false,
    deletedAt: 0,
    at: admin.firestore.FieldValue.serverTimestamp(),
  };

  const ref = await db.collection("rooms/global/messages").add(msg);
  return { status: "ok", id: ref.id };
});

exports.pushOnMessageCreate = onDocumentCreated(
  "rooms/global/messages/{messageId}",
  async (event) => {
    const msg = event.data && event.data.data();
    if (!msg || msg.deleted) return;
    const sender = msg.name || "User";
    const body = msg.text || (msg.attachment && msg.attachment.type ? `[Media: ${msg.attachment.type}]` : "Pesan baru");
    await admin.messaging().send({
      topic: "global-chat",
      notification: { title: `${sender} (${msg.country || "ID"})`, body: body.slice(0, 120) },
      data: {
        type: "global_chat",
        message_id: event.params.messageId,
        sender_name: String(sender),
        country: String(msg.country || "ID"),
      },
      Android: { priority: "high" },
    });
  },
);
