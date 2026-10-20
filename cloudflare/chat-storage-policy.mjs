/** In-memory coalescing only: storage remains authoritative; failures are never cached. */
export class RevisionedHistoryWindow {
  constructor({ now = Date.now, ttlMs = 5_000 } = {}) {
    this.now = now; this.ttlMs = ttlMs; this.epoch = 0; this.snapshot = null; this.pending = null;
  }
  invalidate() { this.epoch += 1; this.snapshot = null; }
  async load(loader) {
    if (this.snapshot?.epoch === this.epoch && this.now() - this.snapshot.at < this.ttlMs) return this.snapshot.rows;
    if (this.pending?.epoch === this.epoch) return this.pending.promise;
    const epoch = this.epoch;
    const pending = { epoch, promise: null };
    pending.promise = Promise.resolve().then(loader).then(rows => {
      if (this.epoch === epoch) this.snapshot = { epoch, at: this.now(), rows };
      return rows;
    }).finally(() => { if (this.pending === pending) this.pending = null; });
    this.pending = pending;
    return pending.promise;
  }
}

export function chatStorageFailure(error, method = 'GET') {
  const quota = /exceeded allowed rows (?:read|written)|durable objects? (?:free tier|storage).*quota/i.test(String(error?.message || ''));
  return new Response(method === 'HEAD' ? null : JSON.stringify({
    status: 'error', code: quota ? 'chat_storage_quota_exceeded' : 'chat_temporarily_unavailable',
  }), { status: 503, headers: {
    'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store',
    'access-control-allow-origin': '*', 'retry-after': '300', 'x-content-type-options': 'nosniff',
  } });
}

/** Explicit failure event: never disguise unavailable storage as empty history. */
export async function chatStorageFailureEvent(error) {
  const response = chatStorageFailure(error);
  return { ...(await response.json()), type: 'history_unavailable',
    http_status: response.status, retry_after: response.headers.get('retry-after') };
}
