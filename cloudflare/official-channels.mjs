import { resolveGithubIdentity } from './oauth-identity.mjs';
import { RevisionedHistoryWindow, chatStorageFailure } from './chat-storage-policy.mjs';

// Verified via GET https://api.github.com/users/xykal; names/body claims grant nothing.
export const FOUNDER_GITHUB_ID = 297800307;
export const OFFICIAL_CHANNELS = Object.freeze([
  { id: 'panduan-pengguna', tag: 'PanduanPengguna', title: 'Panduan Pengguna', description: 'Panduan resmi dan cara menggunakan XyDesk Remote.' },
  { id: 'informasi', tag: 'Informasi', title: 'Informasi', description: 'Informasi layanan dan pembaruan yang perlu diketahui.' },
  { id: 'announcement', tag: 'Announcement', title: 'Announcement', description: 'Pengumuman resmi founder, admin, dan tim.' },
]);

export function channelRole(principal, env = {}) {
  if (!Number.isSafeInteger(principal?.id) || principal.id <= 0) return null;
  if (principal.id === FOUNDER_GITHUB_ID) return 'founder';
  // Explicit deploy-time numeric GitHub IDs. Never use claimed display names.
  // Only the founder is a built-in owner; malformed configuration fails closed.
  let roles;
  try { roles = JSON.parse(env.CHAT_CHANNEL_ROLE_IDS || '{}'); } catch { return null; }
  for (const role of ['admin', 'team']) {
    if (Array.isArray(roles?.[role]) && roles[role].some(id => Number.isSafeInteger(id) && id === principal.id)) return role;
  }
  return null;
}
function json(body, status = 200, extra = {}) {
  return new Response(JSON.stringify(body), { status, headers: {
    'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store',
    'access-control-allow-origin': '*', 'x-content-type-options': 'nosniff', ...extra,
  } });
}
function denied(code, status) { return json({ status: 'error', code }, status); }
async function identity(request, required) {
  const header = request.headers.get('authorization');
  if (!header && !required) return { ok: true, principal: null };
  const match = header?.match(/^Bearer (\S+)$/);
  return match ? resolveGithubIdentity(match[1]) : { ok: false, status: 401, code: 'authentication_required' };
}
export class OfficialChannels {
  constructor(storage, env = {}) {
    this.storage = storage; this.env = env; this.windows = new Map(); this.rates = new Map();
  }
  async fetch(request) {
    try { return await this.handle(request); } catch (error) { return chatStorageFailure(error, request.method); }
  }
  async handle(request) {
    const url = new URL(request.url);
    const match = url.pathname.match(/^\/channels(?:\/([a-z-]+)\/posts)?$/);
    if (!match) return denied('channel_not_found', 404);
    const slug = match[1];
    const channel = OFFICIAL_CHANNELS.find(x => x.id === slug);
    if (slug && !channel) return denied('channel_not_found', 404);
    if (request.method === 'OPTIONS') return new Response(null, { status: 204, headers: {
      'access-control-allow-origin': '*', 'access-control-allow-methods': 'GET, POST, OPTIONS',
      'access-control-allow-headers': 'Authorization, Content-Type',
    } });
    if (request.method !== 'GET' && !(slug && request.method === 'POST')) return denied('method_not_allowed', 405);
    const auth = await identity(request, request.method === 'POST');
    if (!auth.ok) return denied(auth.code, auth.status);
    const role = channelRole(auth.principal, this.env);
    if (!slug) return json({ status: 'ok', channels: OFFICIAL_CHANNELS, role, can_post: !!role });
    const prefix = `channel:${slug}:post:`;
    if (request.method === 'GET') {
      const cursor = url.searchParams.get('cursor') || '';
      if (cursor && (!cursor.startsWith(prefix) || !/^channel:[a-z-]+:post:\d{13}:[0-9a-f-]{36}$/.test(cursor))) return denied('invalid_cursor', 400);
      const read = async () => [...(await this.storage.list({ prefix, reverse: true, limit: 31, ...(cursor ? { end: cursor } : {}) })).entries()];
      let entries;
      if (cursor) entries = await read();
      else {
        if (!this.windows.has(slug)) this.windows.set(slug, new RevisionedHistoryWindow({ ttlMs: 15_000 }));
        entries = await this.windows.get(slug).load(read);
      }
      const page = entries.slice(0, 30);
      return json({ status: 'ok', channel, posts: page.map(([, value]) => value),
        next_cursor: entries.length > 30 ? page[page.length - 1][0] : null, can_post: !!role, role });
    }
    // Authorization precedes body/storage work, including forged founder/admin fields.
    if (!role) return denied('channel_post_forbidden', 403);
    if (Number(request.headers.get('content-length') || 0) > 20_000) return denied('body_too_large', 413);
    let bytes = 0, chunks = [], reader = request.body?.getReader();
    if (!reader) return denied('invalid_body', 400);
    try {
      while (true) { const { value, done } = await reader.read(); if (done) break;
        bytes += value.byteLength; if (bytes > 20_000) { await reader.cancel(); return denied('body_too_large', 413); } chunks.push(value);
      }
    } finally { reader.releaseLock(); }
    let body;
    try { const all = new Uint8Array(bytes); let offset=0; for (const chunk of chunks) { all.set(chunk, offset); offset+=chunk.length; }
      body = JSON.parse(new TextDecoder().decode(all)); } catch { return denied('invalid_json', 400); }
    const text = typeof body.text === 'string' ? body.text.trim() : '';
    const nonce = body.client_post_id;
    if (!text || text.length > 4000 || /[\x00-\x08\x0b\x0c\x0e-\x1f]/.test(text)
        || typeof nonce !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(nonce)) return denied('invalid_post', 400);
    if (body.expected_login !== undefined && (typeof body.expected_login !== 'string' || body.expected_login.toLowerCase() !== auth.principal.login.toLowerCase())) return denied('identity_changed', 409);
    const now = Date.now(); const who = auth.principal;
    // bounded in-memory flood protection; clients also use explicit Retry-After.
    if (this.rates.size >= 1024 && !this.rates.has(who.id)) this.rates.delete(this.rates.keys().next().value);
    const rate = this.rates.get(who.id);
    if (rate && now - rate.at < 60_000 && rate.count >= 20) return json({ status: 'error', code: 'rate_limited' }, 429, { 'retry-after': '60' });
    this.rates.set(who.id, { at: rate && now-rate.at<60_000 ? rate.at : now, count: rate && now-rate.at<60_000 ? rate.count+1 : 1 });
    // Public identity is server-generated; a staff member must not shadow another
    // author's visible post by reusing its public client nonce/ID.
    const serverId = crypto.randomUUID();
    const key = `${prefix}${String(now).padStart(13, '0')}:${serverId}`;
    const nonceKey = `channel:${slug}:nonce:${who.id}:${nonce.toLowerCase()}`;
    const result = await this.storage.transaction(async tx => {
      const oldKey = await tx.get(nonceKey);
      if (oldKey) {
        const old = await tx.get(oldKey);
        if (!old || old.text !== text) return { conflict: true };
        return { post: old, inserted: false };
      }
      const post = { id: serverId, channel_id: slug, text, at: now,
        author_id: who.id, github_login: who.login, author_role: role };
      await tx.put(key, post); await tx.put(nonceKey, key);
      return { post, inserted: true };
    });
    if (result.conflict) return denied('idempotency_conflict', 409);
    this.windows.get(slug)?.invalidate();
    return json({ status: 'ok', post: result.post }, result.inserted ? 201 : 200);
  }
}
