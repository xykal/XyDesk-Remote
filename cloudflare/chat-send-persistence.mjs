// HTTP and WebSocket share one author-bound atomic idempotency boundary.
export const SEND_REPLAY_MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000;
const CLIENT_ID = /^local-[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const CONTENT_FIELDS = [
  'name','text','country','is_announcement','reply_to_id','reply_to_name','reply_to_text',
  'attachment_type','attachment_kind','attachment_name','attachment_mime','attachment_size','attachment_data','view_once',
  'e2ee_v','e2ee_cipher','e2ee_iv','e2ee_sig',
];
export async function sendPayloadFingerprint(raw) {
  // Whitelisted client intent, not server time/translation or transport metadata.
  const bytes = new TextEncoder().encode(JSON.stringify(CONTENT_FIELDS.map(k => raw?.[k] ?? null)));
  const hash = await crypto.subtle.digest('SHA-256', bytes);
  return [...new Uint8Array(hash)].map(x => x.toString(16).padStart(2, '0')).join('');
}
export function validateDurableSend(principal, raw, now = Date.now()) {
  if (raw?.outbox_v == null) return null;
  if (raw.outbox_v !== 1) return { status:400, code:'unsupported_send_protocol' };
  if (!Number.isSafeInteger(principal?.id) || principal.id <= 0) return { status:401, code:'authentication_required' };
  if (raw.outbox_author_id !== principal.id) return { status:409, code:'identity_changed' };
  if (typeof raw.client_msg_id !== 'string' || !CLIENT_ID.test(raw.client_msg_id))
    return { status:400, code:'invalid_client_message_id' };
  if (!Number.isSafeInteger(raw.outbox_created_at) || raw.outbox_created_at > now + 300_000)
    return { status:400, code:'invalid_outbox_time' };
  if (raw.outbox_created_at <= now - SEND_REPLAY_MAX_AGE_MS)
    return { status:410, code:'outbox_expired' };
  return null;
}
export async function persistChatSend(storage, message, principal, raw, now = Date.now()) {
  const invalid = validateDurableSend(principal, raw, now);
  if (invalid) return invalid;
  const localId = raw?.client_msg_id;
  const authenticated = Number.isSafeInteger(principal?.id) && principal.id > 0;
  const supportedId = typeof localId === 'string' && CLIENT_ID.test(localId);
  if (!authenticated || !supportedId) {
    // Legacy non-durable clients are not promised replay safety.
    await storage.put(`msg:${message.id}`, message);
    return { status:200, inserted:true, message, receipt:null, send_protocol:1 };
  }
  if (message.author_id !== principal.id || message.github_login?.toLowerCase() !== principal.login.toLowerCase())
    return { status:401, code:'identity_changed' };
  const payloadHash = await sendPayloadFingerprint(raw);
  const key = `send-receipt:${principal.id}:${localId.toLowerCase()}`;
  const createdAt = raw.outbox_v === 1 ? raw.outbox_created_at : null;
  const expiresAt = (createdAt ?? now) + SEND_REPLAY_MAX_AGE_MS;
  const expiryKey = `send-receipt-expiry:${String(expiresAt).padStart(16, '0')}:${principal.id}:${localId.toLowerCase()}`;
  return storage.transaction(async tx => {
    const prior = await tx.get(key);
    if (prior) {
      if (prior.created_at !== createdAt || prior.payload_hash !== payloadHash) return { status:409, code:'idempotency_conflict' };
      // Return the CURRENT revision/tombstone, never an old plaintext copy.
      const current = await tx.get(`msg:${prior.receipt.id}`);
      return { status:200, inserted:false, message:current || null, receipt:prior.receipt,
        receipt_only:!current, send_protocol:1 };
    }
    const receipt = { id:message.id, at:message.at, author_id:principal.id,
      github_login:principal.login, client_msg_id:localId };
    await tx.put(`msg:${message.id}`, message);
    await tx.put(key, { payload_hash:payloadHash, receipt, created_at:createdAt, expires_at:expiresAt });
    await tx.put(expiryKey, key);
    return { status:200, inserted:true, message, receipt, send_protocol:1 };
  });
}

/** Bounded opportunistic GC; replay expiry is enforced BEFORE storage, even before GC. */
export async function pruneChatSendReceipts(storage, now = Date.now(), limit = 32) {
  return storage.transaction(async tx => {
    const expired = await tx.list({ prefix:'send-receipt-expiry:',
      end:`send-receipt-expiry:${String(now + 1).padStart(16, '0')}:`, limit });
    for (const [expiryKey, receiptKey] of expired) {
      const receipt = await tx.get(receiptKey);
      if (receipt && receipt.expires_at <= now) await tx.delete(receiptKey);
      await tx.delete(expiryKey);
    }
    return expired.size;
  });
}
