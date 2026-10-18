import test, { afterEach, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import worker, {
  ActiveSessions,
  CommunityJokes,
  FeedbackBoard,
  GlobalChat,
  withChatTranslationBudget,
  PcSignalRoom,
  resetReleaseLookupCache,
} from "./worker.mjs";

// Suite ini tidak boleh menembak jaringan. Pencarian rilis publik kini
// memanggil API GitHub, dan hasilnya di-cache di level modul, jadi setiap tes
// mulai dari cache kosong plus fetch yang gagal. Tes yang perlu respons GitHub
// mengganti globalThis.fetch sendiri di dalam badannya.
const offlineFetch = globalThis.fetch;
beforeEach(() => {
  resetReleaseLookupCache();
  globalThis.fetch = async () => new Response("offline", { status: 503 });
});
afterEach(() => {
  globalThis.fetch = offlineFetch;
});

const releaseState = {
  service: "xydesk-remote",
  brand: "XyVerse Technology Global",
  release_state: "published",
  release_tag: "v1.0.2",
  app_build: { version: "1.0.2", version_code: 53 },
  release_url: "https://github.com/xykal/XyDesk-Remote/releases/tag/v1.0.2",
  checksums_url: "https://github.com/xykal/XyDesk-Remote/releases/download/v1.0.2/SHA256SUMS.txt",
  downloads: [
    { abi: "arm64-v8a", file: "XyDesk-arm64-v8a.apk", url: "https://github.com/xykal/XyDesk-Remote/releases/download/v1.0.2/XyDesk-arm64-v8a.apk" },
    { abi: "armeabi-v7a", file: "XyDesk-armeabi-v7a.apk", url: "https://github.com/xykal/XyDesk-Remote/releases/download/v1.0.2/XyDesk-armeabi-v7a.apk" },
    { abi: "x86_64", file: "XyDesk-x86_64.apk", url: "https://github.com/xykal/XyDesk-Remote/releases/download/v1.0.2/XyDesk-x86_64.apk" },
  ],
};

function envFor(files = {}, extra = {}) {
  return {
    ...extra,
    ASSETS: {
      async fetch(request) {
        const url = new URL(request.url);
        if (url.pathname === "/release-state.json") {
          return new Response(JSON.stringify(releaseState), {
            headers: { "content-type": "application/json; charset=utf-8" },
          });
        }
        if (Object.hasOwn(files, url.pathname)) {
          return new Response(files[url.pathname], { headers: { "content-type": "text/plain; charset=utf-8" } });
        }
        return new Response("not found", { status: 404 });
      },
    },
  };
}

function communityJokesObject(kv = null) {
  const records = new Map();
  const storage = {
    async get(key) { return records.get(key); },
    async put(key, value) { records.set(key, value); },
    async delete(key) { return records.delete(key); },
    async list({ prefix = "", limit = Infinity, reverse = false } = {}) {
      let entries = [...records].filter(([key]) => key.startsWith(prefix)).sort(([a], [b]) => a.localeCompare(b));
      if (reverse) entries = entries.reverse();
      return new Map(entries.slice(0, limit));
    },
    async transaction(callback) {
      const transaction = {
        async get(key) { return records.get(key); },
        async put(key, value) { records.set(key, value); },
        async delete(key) { return records.delete(key); },
        async list(options) { return storage.list(options); },
      };
      return callback(transaction);
    },
  };
  return { object: new CommunityJokes({ storage }, kv ? { UNIQUE_DOWNLOADS: kv } : undefined), records };
}

function mockKv() {
  const store = new Map();
  return {
    store,
    async put(key, value, opts = {}) { store.set(key, { value, metadata: opts.metadata || null }); },
    async get(key) { const e = store.get(key); return e ? e.value : null; },
    async getWithMetadata(key) { return store.get(key) || { value: null, metadata: null }; },
    async delete(key) { store.delete(key); },
  };
}

function jokesEnv(object) {
  return envFor({}, {
    COMMUNITY_JOKES: {
      idFromName(name) { assert.equal(name, "xydesk-community-jokes-v1"); return name; },
      get(id) {
        assert.equal(id, "xydesk-community-jokes-v1");
        return { fetch: (request) => object.fetch(request) };
      },
    },
  });
}

function pcSignalEnv() {
  const rooms = new Map();
  const roomState = new Map();
  const binding = {
    idFromName(name) { return name; },
    get(id) {
      if (!rooms.has(id)) {
        const values = new Map();
        let alarm = null;
        const storage = {
          async get(key) { return values.get(key); },
          async put(key, value) { values.set(key, value); },
          async delete(key) { return values.delete(key); },
          async deleteAlarm() { alarm = null; },
          async getAlarm() { return alarm; },
          async setAlarm(timestamp) { alarm = timestamp; },
          async transaction(callback) {
            const transaction = {
              async get(key) { return values.get(key); },
              async put(key, value) { values.set(key, value); },
              async delete(key) { return values.delete(key); },
            };
            return callback(transaction);
          },
        };
        const state = { storage };
        const object = new PcSignalRoom(state);
        rooms.set(id, object);
        roomState.set(id, values);
      }
      return {
        fetch: (input, init) => {
          const request = input instanceof Request ? input : new Request(input, init);
          return rooms.get(id).fetch(request);
        },
      };
    },
  };
  return { env: envFor({}, { PC_SIGNAL_ROOMS: binding }), rooms, roomState };
}

test("health endpoint returns JSON derived from release metadata", async () => {
  const response = await worker.fetch(new Request("https://rdp.xydesk.my.id/api/health"), envFor());
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type"), /application\/json/);
  const body = await response.json();
  assert.equal(body.status, "operational");
  assert.equal(body.release_state, "published");
  assert.equal(body.current_build, "1.0.2");
});

test("mobile release-status API retains legacy fields and reports publication, not countdown", async () => {
  const response = await worker.fetch(new Request("https://rdp.xydesk.my.id/api/status"), envFor());
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.version, "1.0.2");
  assert.equal(body.qaBuild, "1.0.2");
  assert.equal(body.qaVersionCode, 53);
  assert.equal(body.status, "published");
  assert.match(body.launchWib, /GitHub Releases/);
  assert.ok(body.storePortal);
});

test("download stats sum APK asset counts across all available releases only", async () => {
  const originalFetch = globalThis.fetch;
  let requestedUrl;
  let authorization;
  globalThis.fetch = async (url, init) => {
    requestedUrl = String(url);
    authorization = new Headers(init.headers).get("authorization");
    return new Response(JSON.stringify([
      {
        tag_name: "v1.0.0",
        draft: false,
        assets: [
          { name: "app-arm64-v8a-release.apk", download_count: 40 },
          { name: "app-armeabi-v7a-release.apk", download_count: 18 },
          { name: "app-x86_64-release.apk", download_count: 14 },
          { name: "XyDesk-Remote-Host-Agent-win64.zip", download_count: 90 },
        ],
      },
      { tag_name: "v0.9.0", draft: false, assets: [{ name: "xydesk-legacy.apk", download_count: 13 }] },
      { tag_name: "draft", draft: true, assets: [{ name: "unpublished.apk", download_count: 999 }] },
    ]), { headers: { "content-type": "application/json" } });
  };
  try {
    const response = await worker.fetch(
      new Request("https://rdp.xydesk.my.id/api/stats"),
      envFor({}, { GH_TOKEN: "test-token" }),
    );
    assert.equal(response.status, 200);
    assert.match(response.headers.get("cache-control"), /s-maxage=900/);
    const body = await response.json();
    assert.equal(body.status, "ok");
    assert.equal(body.total_apk_downloads, 85);
    assert.equal(body.apk_asset_count, 4);
    assert.equal(body.release_count, 2);
    assert.match(body.note, /pengunduh unik pertama/);
    assert.equal(requestedUrl, "https://api.github.com/repos/xykal/XyDesk-Remote/releases?per_page=100&page=1");
    assert.equal(authorization, "Bearer test-token");
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("download stats fall back to the public GitHub API if the optional token is rejected", async () => {
  const originalFetch = globalThis.fetch;
  const authorizations = [];
  globalThis.fetch = async (_url, init) => {
    const authorization = new Headers(init.headers).get("authorization");
    authorizations.push(authorization);
    if (authorization) return new Response("bad token", { status: 401 });
    return new Response(JSON.stringify([
      { tag_name: "v1.0.0", assets: [{ name: "app.apk", download_count: 7 }] },
    ]), { headers: { "content-type": "application/json" } });
  };
  try {
    const response = await worker.fetch(
      new Request("https://rdp.xydesk.my.id/api/stats"),
      envFor({}, { GH_TOKEN: "expired-token" }),
    );
    assert.equal(response.status, 200);
    assert.equal((await response.json()).total_apk_downloads, 7);
    assert.deepEqual(authorizations, ["Bearer expired-token", null]);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("download stats retry the read-only GH_PAT when GH_TOKEN is rejected", async () => {
  const originalFetch = globalThis.fetch;
  const authorizations = [];
  globalThis.fetch = async (_url, init) => {
    const authorization = new Headers(init.headers).get("authorization");
    authorizations.push(authorization);
    if (authorization === "Bearer expired-token") return new Response("bad token", { status: 401 });
    if (authorization === "Bearer read-only-token") {
      return new Response(JSON.stringify([
        { tag_name: "v1.0.1", assets: [{ name: "app-arm64-v8a-release.apk", download_count: 5 }] },
      ]), { headers: { "content-type": "application/json" } });
    }
    throw new Error("Unexpected unauthenticated GitHub request");
  };
  try {
    const response = await worker.fetch(
      new Request("https://rdp.xydesk.my.id/api/stats"),
      envFor({}, { GH_TOKEN: "expired-token", GH_PAT: "read-only-token" }),
    );
    assert.equal(response.status, 200);
    assert.equal((await response.json()).total_apk_downloads, 5);
    assert.deepEqual(authorizations, ["Bearer expired-token", "Bearer read-only-token"]);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("download stats fail closed when GitHub release metadata is unavailable", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response("rate limited", { status: 403 });
  try {
    const response = await worker.fetch(new Request("https://rdp.xydesk.my.id/api/stats"), envFor());
    assert.equal(response.status, 503);
    assert.equal(response.headers.get("cache-control"), "no-store");
    assert.deepEqual(await response.json(), {
      status: "unavailable",
      error: "Unduhan belum dapat dimuat dari GitHub.",
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("download stats HEAD request returns headers without a body", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response(JSON.stringify([]), {
    headers: { "content-type": "application/json" },
  });
  try {
    const response = await worker.fetch(
      new Request("https://rdp.xydesk.my.id/api/stats", { method: "HEAD" }),
      envFor(),
    );
    assert.equal(response.status, 200);
    assert.equal(await response.text(), "");
    assert.match(response.headers.get("content-type"), /application\/json/);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("active-session API exposes anonymous client-reported count and marks it non-cacheable", async () => {
  const env = envFor({}, {
    ACTIVE_SESSIONS: {
      idFromName(name) { assert.equal(name, "xydesk-remote-global"); return name; },
      get(id) {
        assert.equal(id, "xydesk-remote-global");
        return { async fetch(url) {
          assert.equal(String(url), "https://active-sessions/count");
          return new Response(JSON.stringify({ status: "ok", active_session_count: 3 }), {
            headers: { "content-type": "application/json" },
          });
        } };
      },
    },
  });
  const response = await worker.fetch(new Request("https://rdp.xydesk.my.id/api/active-sessions"), env);
  assert.equal(response.status, 200);
  assert.equal(response.headers.get("cache-control"), "no-store");
  const body = await response.json();
  assert.equal(body.active_session_count, 3);
  assert.equal(body.scope, "client-reported-anonymous-rdp-sessions");
  assert.match(body.note, /aktif secara default/);
  assert.match(body.note, /bukan jumlah pengguna unik/);
});

test("active-session count reports unavailable rather than a fake zero without Durable Objects", async () => {
  const response = await worker.fetch(new Request("https://rdp.xydesk.my.id/api/active-sessions"), envFor());
  assert.equal(response.status, 503);
  assert.deepEqual(await response.json(), { status: "unavailable", active_session_count: null });
});

test("session reporter endpoint only forwards validated aggregate lease fields", async () => {
  const originalFetch = globalThis.fetch;
  let received;
  const env = envFor({}, {
    ACTIVE_SESSIONS: {
      idFromName(name) { return name; },
      get() {
        return { async fetch(request, init) {
          received = { url: String(request), init };
          return new Response(JSON.stringify({ status: "ok", active_session_count: 1 }), {
            headers: { "content-type": "application/json" },
          });
        } };
      },
    },
  });
  try {
    const sessionId = "00000000-0000-4000-8000-000000000001";
    const response = await worker.fetch(new Request("https://rdp.xydesk.my.id/api/session", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ action: "heartbeat", session_id: sessionId, host: "private-host", username: "private-user" }),
    }), env);
    assert.equal(response.status, 200);
    assert.equal(received.url, "https://active-sessions/update");
    assert.deepEqual(JSON.parse(received.init.body), { action: "heartbeat", session_id: sessionId });
    assert.equal(received.init.headers["content-type"], "application/json; charset=utf-8");
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("session reporter rejects invalid IDs and restricts its HTTP method", async () => {
  const env = envFor({}, { ACTIVE_SESSIONS: { idFromName: () => "x", get: () => ({ fetch: async () => new Response("unexpected") }) } });
  const invalid = await worker.fetch(new Request("https://rdp.xydesk.my.id/api/session", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ action: "heartbeat", session_id: "not-a-uuid" }),
  }), env);
  assert.equal(invalid.status, 400);
  const get = await worker.fetch(new Request("https://rdp.xydesk.my.id/api/session"), env);
  assert.equal(get.status, 405);
  assert.equal(get.headers.get("allow"), "POST");
});

test("ActiveSessions leases are hashed, idempotent, endable, and expire", async () => {
  const records = new Map();
  let alarmAt = null;
  const storage = {
    async get(key) { return records.get(key); },
    async getAlarm() { return alarmAt; },
    async setAlarm(timestamp) { alarmAt = timestamp; },
    async deleteAlarm() { alarmAt = null; },
    async transaction(callback) {
      const transaction = {
        async get(key) { return records.get(key); },
        async list({ prefix = "", limit = Infinity } = {}) {
          return new Map([...records].filter(([key]) => key.startsWith(prefix)).slice(0, limit));
        },
        async put(key, value) { records.set(key, value); },
        async delete(key) { records.delete(key); },
      };
      return callback(transaction);
    },
  };
  const state = { storage };
  const object = new ActiveSessions(state);
  const originalNow = Date.now;
  let now = originalNow();
  Date.now = () => now;
  const makeUpdate = (action, sessionId) => new Request("https://active-sessions/update", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ action, session_id: sessionId }),
  });
  try {
    const first = "00000000-0000-4000-8000-000000000001";
    const second = "00000000-0000-4000-8000-000000000002";
    assert.equal((await (await object.fetch(makeUpdate("heartbeat", first))).json()).active_session_count, 1);
    assert.equal((await (await object.fetch(makeUpdate("heartbeat", first))).json()).active_session_count, 1);
    assert.equal((await (await object.fetch(makeUpdate("heartbeat", second))).json()).active_session_count, 2);
    assert.ok([...records.keys()].every((key) => !key.includes(first) && !key.includes(second)));
    assert.equal((await (await object.fetch(new Request("https://active-sessions/count"))).json()).active_session_count, 2);
    assert.equal((await (await object.fetch(makeUpdate("end", first))).json()).active_session_count, 1);
    now += 91_000;
    await object.alarm();
    assert.equal((await (await object.fetch(new Request("https://active-sessions/count"))).json()).active_session_count, 0);
    assert.equal([...records.keys()].filter((key) => key.startsWith("session:")).length, 0);
    assert.equal(records.get("meta:active-count"), 0);
    assert.equal(alarmAt, null);
  } finally {
    Date.now = originalNow;
  }
});

test("PC signaling room pairs host and viewer and relays SDP/ICE JSON only between them", async () => {
  const { env, roomState } = pcSignalEnv();
  const createdResponse = await worker.fetch(new Request("https://rdp.xydesk.my.id/api/pc/rooms", {
    method: "POST",
    headers: { "content-type": "application/json", "cf-connecting-ip": "203.0.113.20" },
    body: "{}",
  }), env);
  assert.equal(createdResponse.status, 201);
  const created = await createdResponse.json();
  assert.match(created.room_id, /^[0-9a-f-]{36}$/i);
  assert.match(created.host_token, /^[A-Za-z0-9_-]{43}$/);
  assert.match(created.pairing_code, /^[A-Za-z0-9_-]{32}$/);
  const persisted = roomState.get(created.room_id);
  assert.ok(persisted);
  assert.equal(JSON.stringify([...persisted.values()]).includes(created.host_token), false);
  assert.equal(JSON.stringify([...persisted.values()]).includes(created.pairing_code), false);

  const joinResponse = await worker.fetch(new Request(`https://rdp.xydesk.my.id/api/pc/rooms/${created.room_id}/join`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ pairing_code: created.pairing_code }),
  }), env);
  assert.equal(joinResponse.status, 200);
  const joined = await joinResponse.json();
  assert.match(joined.viewer_token, /^[A-Za-z0-9_-]{43}$/);

  const hostAuth = { authorization: `Bearer ${created.host_token}`, "content-type": "application/json" };
  const viewerAuth = { authorization: `Bearer ${joined.viewer_token}`, "content-type": "application/json" };
  const offer = { type: "offer", payload: { type: "offer", sdp: "v=0\\r\\no=xydesk 1 1 IN IP4 0.0.0.0\\r\\n" } };
  const offerResponse = await worker.fetch(new Request(`https://rdp.xydesk.my.id/api/pc/rooms/${created.room_id}/signal`, {
    method: "POST", headers: hostAuth, body: JSON.stringify(offer),
  }), env);
  assert.equal(offerResponse.status, 201);
  assert.equal((await offerResponse.json()).seq, 1);

  const viewerPoll = await worker.fetch(new Request(`https://rdp.xydesk.my.id/api/pc/rooms/${created.room_id}/signal?after=0`, {
    headers: { authorization: viewerAuth.authorization },
  }), env);
  assert.equal(viewerPoll.status, 200);
  const viewerMessages = await viewerPoll.json();
  assert.equal(viewerMessages.role, "viewer");
  assert.deepEqual(viewerMessages.events.map((event) => [event.seq, event.from, event.type]), [[1, "host", "offer"]]);
  assert.equal(viewerMessages.cursor, 1);

  const answer = { type: "answer", payload: { type: "answer", sdp: "v=0\\r\\no=xydesk 2 2 IN IP4 0.0.0.0\\r\\n" } };
  const answerResponse = await worker.fetch(new Request(`https://rdp.xydesk.my.id/api/pc/rooms/${created.room_id}/signal`, {
    method: "POST", headers: viewerAuth, body: JSON.stringify(answer),
  }), env);
  assert.equal(answerResponse.status, 201);
  assert.equal((await answerResponse.json()).seq, 2);
  const hostPoll = await worker.fetch(new Request(`https://rdp.xydesk.my.id/api/pc/rooms/${created.room_id}/signal?after=1`, {
    headers: { authorization: hostAuth.authorization },
  }), env);
  const hostMessages = await hostPoll.json();
  assert.deepEqual(hostMessages.events.map((event) => [event.seq, event.from, event.type]), [[2, "viewer", "answer"]]);
});

test("PC signaling rejects invalid credentials, malformed SDP, oversized requests, and reused pairing codes", async () => {
  const { env } = pcSignalEnv();
  const createdResponse = await worker.fetch(new Request("https://rdp.xydesk.my.id/api/pc/rooms", {
    method: "POST", headers: { "content-type": "application/json" }, body: "{}",
  }), env);
  const created = await createdResponse.json();
  const base = `https://rdp.xydesk.my.id/api/pc/rooms/${created.room_id}`;
  const unauthorized = await worker.fetch(new Request(`${base}/signal?after=0`), env);
  assert.equal(unauthorized.status, 401);

  const joinResponse = await worker.fetch(new Request(`${base}/join`, {
    method: "POST", headers: { "content-type": "application/json" },
    body: JSON.stringify({ pairing_code: created.pairing_code }),
  }), env);
  assert.equal(joinResponse.status, 200);
  const joined = await joinResponse.json();
  const reusedPairingCode = await worker.fetch(new Request(`${base}/join`, {
    method: "POST", headers: { "content-type": "application/json" },
    body: JSON.stringify({ pairing_code: created.pairing_code }),
  }), env);
  assert.equal(reusedPairingCode.status, 401);

  const malformed = await worker.fetch(new Request(`${base}/signal`, {
    method: "POST",
    headers: { authorization: `Bearer ${created.host_token}`, "content-type": "application/json" },
    body: JSON.stringify({ type: "offer", payload: { type: "answer", sdp: "bad" } }),
  }), env);
  assert.equal(malformed.status, 400);
  const oversized = await worker.fetch(new Request(`${base}/signal`, {
    method: "POST",
    headers: { authorization: `Bearer ${joined.viewer_token}`, "content-type": "application/json" },
    body: JSON.stringify({ type: "candidate", payload: { candidate: "x".repeat(30_000) } }),
  }), env);
  assert.equal(oversized.status, 413);
  const invalidCursor = await worker.fetch(new Request(`${base}/signal?after=-1`, {
    headers: { authorization: `Bearer ${created.host_token}` },
  }), env);
  assert.equal(invalidCursor.status, 400);
});

test("PC signaling room expires when the host waits too long to pair", async () => {
  const { env } = pcSignalEnv();
  const originalNow = Date.now;
  let now = originalNow();
  Date.now = () => now;
  try {
    const createdResponse = await worker.fetch(new Request("https://rdp.xydesk.my.id/api/pc/rooms", {
      method: "POST", headers: { "content-type": "application/json" }, body: "{}",
    }), env);
    assert.equal(createdResponse.status, 201);
    const created = await createdResponse.json();
    now += 10 * 60_000 + 1;
    const expired = await worker.fetch(new Request(`https://rdp.xydesk.my.id/api/pc/rooms/${created.room_id}/join`, {
      method: "POST", headers: { "content-type": "application/json" },
      body: JSON.stringify({ pairing_code: created.pairing_code }),
    }), env);
    assert.equal(expired.status, 410);
    assert.deepEqual(await expired.json(), { status: "room_expired" });
  } finally {
    Date.now = originalNow;
  }
});

test("PC signaling room creation is rate-limited per hashed client IP and validates its API methods", async () => {
  const { env } = pcSignalEnv();
  const create = () => worker.fetch(new Request("https://rdp.xydesk.my.id/api/pc/rooms", {
    method: "POST", headers: { "content-type": "application/json", "cf-connecting-ip": "198.51.100.7" }, body: "{}",
  }), env);
  for (let index = 0; index < 5; index += 1) assert.equal((await create()).status, 201);
  const limited = await create();
  assert.equal(limited.status, 429);
  const wrongMethod = await worker.fetch(new Request("https://rdp.xydesk.my.id/api/pc/rooms", { method: "GET" }), env);
  assert.equal(wrongMethod.status, 405);
  assert.equal(wrongMethod.headers.get("allow"), "POST");
  const missingBinding = await worker.fetch(new Request("https://rdp.xydesk.my.id/api/pc/rooms", {
    method: "POST", headers: { "content-type": "application/json" }, body: "{}",
  }), envFor());
  assert.equal(missingBinding.status, 503);
});

test("Android active-session reporting defaults on, remains easy to disable, and sends only a random session ID", async () => {
  const prefs = await readFile(new URL("../client/Android/Studio/app/src/main/kotlin/id/xydesk/remote/ui/AppPrefs.kt", import.meta.url), "utf8");
  const reporter = await readFile(new URL("../client/Android/Studio/app/src/main/kotlin/id/xydesk/remote/privacy/ActiveSessionStatsReporter.kt", import.meta.url), "utf8");
  const session = await readFile(new URL("../client/Android/Studio/app/src/main/kotlin/id/xydesk/remote/ui/XyDeskSession.kt", import.meta.url), "utf8");
  const settings = await readFile(new URL("../client/Android/Studio/app/src/main/kotlin/id/xydesk/remote/ui/Screens.kt", import.meta.url), "utf8");
  assert.match(prefs, /getBoolean\(KEY_SHARE_ACTIVE_SESSION_STATS,\s*true\)/);
  assert.match(settings, /appPrefs\.shareActiveSessionStats = it/);
  assert.match(settings, /Sakelar ini dapat dimatikan kapan saja/);
  assert.match(session, /if \(!appPrefs\.shareActiveSessionStats\) return@LaunchedEffect/);
  assert.match(reporter, /\.put\("session_id",\s*sessionId\.toString\(\)\)/);
  assert.doesNotMatch(reporter, /\.put\("(?:host|profile|username|password|clipboard)"/i);
});

test("public joke API accepts an anonymous submission and serves it in the shared feed", async () => {
  const { object } = communityJokesObject();
  const env = jokesEnv(object);
  const clientId = "00000000-0000-4000-8000-000000000011";
  const submit = await worker.fetch(new Request("https://rdp.xydesk.my.id/api/jokes", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ client_id: clientId, text: "RDP tersambung. Kerjaan? Masih loading." }),
  }), env);
  assert.equal(submit.status, 201);
  assert.equal(submit.headers.get("cache-control"), "no-store");
  const posted = await submit.json();
  assert.equal(posted.status, "created");
  assert.equal(posted.joke.text, "RDP tersambung. Kerjaan? Masih loading.");
  assert.equal(posted.joke.viewer_reaction, null);

  const feed = await worker.fetch(new Request("https://rdp.xydesk.my.id/api/jokes", {
    headers: { "x-xydesk-client-id": clientId },
  }), env);
  assert.equal(feed.status, 200);
  const body = await feed.json();
  assert.equal(body.items.length, 1);
  assert.equal(body.items[0].id, posted.joke.id);
  assert.equal(body.items[0].viewer_reaction, null);
});

test("community feed skips hidden newest posts across the full stored window", async () => {
  const { object, records } = communityJokesObject();
  const reactions = { "😂": 0, "😭": 0, "💀": 0, "🔥": 0, "👍": 0, "❤️": 0, "🎉": 0, "🤔": 0 };
  for (let index = 0; index < 110; index += 1) {
    const createdAt = index + 1;
    const id = `00000000-0000-4000-8000-${String(createdAt).padStart(12, "0")}`;
    const key = `joke:${String(createdAt).padStart(13, "0")}:${id}`;
    records.set(key, {
      id,
      text: `post ${createdAt}`,
      created_at: createdAt,
      reactions,
      hidden: index >= 9,
    });
  }
  const response = await object.fetch(new Request("https://community-jokes/feed"));
  const feed = await response.json();
  assert.equal(feed.items.length, 9);
  assert.equal(feed.items[0].text, "post 9");
  assert.equal(feed.items.at(-1).text, "post 1");
});

test("community joke reactions toggle and switch one emoji per anonymous install", async () => {
  const { object } = communityJokesObject();
  const author = "00000000-0000-4000-8000-000000000021";
  const reactor = "00000000-0000-4000-8000-000000000022";
  const submit = await object.fetch(new Request("https://community-jokes/submit", {
    method: "POST", headers: { "content-type": "application/json" },
    body: JSON.stringify({ client_id: author, text: "Koneksi stabil pas lagi nggak dipakai." }),
  }));
  const id = (await submit.json()).joke.id;
  const react = (emoji) => object.fetch(new Request(`https://community-jokes/reaction/${id}`, {
    method: "POST", headers: { "content-type": "application/json" },
    body: JSON.stringify({ client_id: reactor, emoji }),
  }));
  let response = await react("😂");
  let data = await response.json();
  assert.equal(data.joke.reactions["😂"], 1);
  assert.equal(data.joke.viewer_reaction, "😂");
  response = await react("😂");
  data = await response.json();
  assert.equal(data.joke.reactions["😂"], 0);
  assert.equal(data.joke.viewer_reaction, null);
  await react("😂");
  response = await react("💀");
  data = await response.json();
  assert.equal(data.joke.reactions["😂"], 0);
  assert.equal(data.joke.reactions["💀"], 1);
  assert.equal(data.joke.viewer_reaction, "💀");
  response = await react("👍");
  data = await response.json();
  assert.equal(data.joke.reactions["💀"], 0);
  assert.equal(data.joke.reactions["👍"], 1);
  assert.equal(data.joke.viewer_reaction, "👍");
  response = await react("🚀");
  assert.equal(response.status, 400);
});

test("community joke submissions are length-limited and rate-limited per random client ID", async () => {
  const { object } = communityJokesObject();
  const clientId = "00000000-0000-4000-8000-000000000031";
  const post = (text, id = clientId) => object.fetch(new Request("https://community-jokes/submit", {
    method: "POST", headers: { "content-type": "application/json" },
    body: JSON.stringify({ client_id: id, text }),
  }));
  assert.equal((await post("ok")).status, 400);
  assert.equal((await post("link https://example.com")).status, 400);
  const originalNow = Date.now;
  let now = originalNow();
  Date.now = () => now;
  try {
    assert.equal((await post("Joke yang cukup panjang")).status, 201);
    const limited = await post("Joke kedua juga cukup panjang");
    assert.equal(limited.status, 429);
    assert.equal((await limited.json()).status, "rate_limited");
    now += 60_001;
    assert.equal((await post("Joke kedua juga cukup panjang")).status, 201);
  } finally {
    Date.now = originalNow;
  }
});

test("three unique reports hide a public joke; duplicate reports do not count twice", async () => {
  const { object } = communityJokesObject();
  const ids = [
    "00000000-0000-4000-8000-000000000041",
    "00000000-0000-4000-8000-000000000042",
    "00000000-0000-4000-8000-000000000043",
    "00000000-0000-4000-8000-000000000044",
  ];
  const submit = await object.fetch(new Request("https://community-jokes/submit", {
    method: "POST", headers: { "content-type": "application/json" },
    body: JSON.stringify({ client_id: ids[0], text: "A test joke that can be reported." }),
  }));
  const id = (await submit.json()).joke.id;
  const report = (clientId) => object.fetch(new Request(`https://community-jokes/report/${id}`, {
    method: "POST", headers: { "content-type": "application/json" },
    body: JSON.stringify({ client_id: clientId }),
  }));
  assert.equal((await (await report(ids[1])).json()).hidden, false);
  assert.equal((await (await report(ids[1])).json()).status, "already_reported");
  assert.equal((await (await report(ids[2])).json()).hidden, false);
  assert.equal((await (await report(ids[3])).json()).hidden, true);
  const feed = await object.fetch(new Request("https://community-jokes/feed"));
  assert.equal((await feed.json()).items.length, 0);
  const reactHidden = await object.fetch(new Request(`https://community-jokes/reaction/${id}`, {
    method: "POST", headers: { "content-type": "application/json" },
    body: JSON.stringify({ client_id: ids[1], emoji: "😂" }),
  }));
  assert.equal(reactHidden.status, 404);
});

test("public joke routes reject bad clients and methods", async () => {
  const { object } = communityJokesObject();
  const env = jokesEnv(object);
  const badId = await worker.fetch(new Request("https://rdp.xydesk.my.id/api/jokes", {
    method: "POST", headers: { "content-type": "application/json" },
    body: JSON.stringify({ client_id: "not-a-uuid", text: "This is a valid-length joke." }),
  }), env);
  assert.equal(badId.status, 400);
  const wrongMethod = await worker.fetch(new Request("https://rdp.xydesk.my.id/api/jokes", { method: "PUT" }), env);
  assert.equal(wrongMethod.status, 405);
  assert.equal(wrongMethod.headers.get("allow"), "GET, HEAD, POST");
  const oversized = await worker.fetch(new Request("https://rdp.xydesk.my.id/api/jokes", {
    method: "POST", headers: { "content-type": "application/json" },
    // Batas body naik ke 2,4MB (lampiran base64); 413 harus tetap muncul di atasnya.
    body: JSON.stringify({ client_id: "00000000-0000-4000-8000-000000000051", text: "x".repeat(2_500_000) }),
  }), env);
  assert.equal(oversized.status, 413);
});

test("current XyDesk Remote APK alias tetap jalan tanpa token GitHub saat API gagal", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => { throw new Error("GitHub API unavailable"); };
  try {
    const response = await worker.fetch(
      new Request("https://rdp.xydesk.my.id/arm64-v8a.apk"),
      envFor(),
    );
    assert.equal(response.status, 302);
    assert.equal(
      response.headers.get("location"),
      "https://github.com/xykal/XyDesk-Remote/releases/download/v1.0.2/XyDesk-arm64-v8a.apk",
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("APK alias follows the ABI when a release renames its asset files", async () => {
  // v1.0.2 renamed app-<abi>-release.apk -> XyDesk-<abi>.apk; the alias must
  // track the current release via abi, not the legacy file name.
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => { throw new Error("ABI-matched alias must not call the GitHub API"); };
  try {
    const response = await worker.fetch(
      new Request("https://rdp.xydesk.my.id/x86_64.apk"),
      envFor(),
    );
    assert.equal(response.status, 302);
    assert.equal(
      response.headers.get("location"),
      "https://github.com/xykal/XyDesk-Remote/releases/download/v1.0.2/XyDesk-x86_64.apk",
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("alias nama aset XyDesk-Remote-<abi>.apk ikut ABI rilis berjalan", async () => {
  // Sejak rilis setelah v1.0.2, APK dipublikasikan sebagai
  // XyDesk-Remote-<abi>.apk. Alias barunya membawa `abi`, jadi seperti alias
  // shorthand, ia mengikuti rilis berjalan lewat metadata rilis — bukan nama
  // file yang di-hardcode. Fixture di sini masih v1.0.2 (nama lama), sehingga
  // yang benar adalah alias baru menunjuk aset v1.0.2 yang nyata.
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => { throw new Error("alias ber-abi tidak boleh memanggil API GitHub"); };
  try {
    for (const [path, expected] of [
      [
        "/XyDesk-Remote-arm64-v8a.apk",
        "https://github.com/xykal/XyDesk-Remote/releases/download/v1.0.2/XyDesk-arm64-v8a.apk",
      ],
      [
        "/XyDesk-Remote-armeabi-v7a.apk",
        "https://github.com/xykal/XyDesk-Remote/releases/download/v1.0.2/XyDesk-armeabi-v7a.apk",
      ],
      [
        "/XyDesk-Remote-x86_64.apk",
        "https://github.com/xykal/XyDesk-Remote/releases/download/v1.0.2/XyDesk-x86_64.apk",
      ],
    ]) {
      const response = await worker.fetch(
        new Request("https://rdp.xydesk.my.id" + path),
        envFor(),
      );
      assert.equal(response.status, 302, path);
      assert.equal(response.headers.get("location"), expected, path);
    }
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("alias XyDesk-Remote-<abi>.apk menunjuk berkas nyata saat metadata dari GitHub", async () => {
  // Regresi produksi. releaseStateFromGithub membangun ulang `downloads` dari
  // daftar aset rilis dan awalnya tidak menyertakan `abi`, sehingga pencarian
  // berdasarkan ABI di currentReleaseAssetUrl selalu gagal dan alias jatuh ke
  // nama file hardcoded XyDesk-Remote-<abi>.apk — berkas yang tidak ada pada
  // rilis bernama XyDesk-<abi>.apk. Tes alias lain men-stub API GitHub agar
  // melempar, jadi hanya jalur fallback release-state.json yang teruji dan
  // bug ini lolos sampai alias baru terpasang di produksi dan mengembalikan 404.
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (url) => {
    if (String(url).startsWith("https://api.github.com/repos/xykal/XyDesk-Remote/releases")) {
      const asset = (name) => ({
        name,
        browser_download_url: `https://github.com/xykal/XyDesk-Remote/releases/download/v1.0.2/${name}`,
      });
      return new Response(JSON.stringify([{
        tag_name: "v1.0.2",
        html_url: "https://github.com/xykal/XyDesk-Remote/releases/tag/v1.0.2",
        published_at: "2026-10-03T20:22:09Z",
        draft: false,
        prerelease: false,
        assets: [
          asset("XyDesk-arm64-v8a.apk"),
          asset("XyDesk-armeabi-v7a.apk"),
          asset("XyDesk-x86_64.apk"),
          asset("SHA256SUMS.txt"),
        ],
      }]), { status: 200, headers: { "content-type": "application/json" } });
    }
    return new Response("offline", { status: 503 });
  };
  try {
    for (const [path, expected] of [
      ["/XyDesk-Remote-arm64-v8a.apk", "XyDesk-arm64-v8a.apk"],
      ["/XyDesk-Remote-armeabi-v7a.apk", "XyDesk-armeabi-v7a.apk"],
      ["/XyDesk-Remote-x86_64.apk", "XyDesk-x86_64.apk"],
      ["/arm64-v8a.apk", "XyDesk-arm64-v8a.apk"],
    ]) {
      const response = await worker.fetch(
        new Request("https://rdp.xydesk.my.id" + path),
        envFor(),
      );
      assert.equal(response.status, 302, path);
      assert.equal(
        response.headers.get("location"),
        `https://github.com/xykal/XyDesk-Remote/releases/download/v1.0.2/${expected}`,
        path,
      );
    }
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("cloud sync setelan: PUT lalu GET per ID akun, tolak ID/blob tidak valid", async () => {
  const store = new Map();
  const kv = {
    async get(key) { return store.has(key) ? store.get(key) : null; },
    async put(key, value) { store.set(key, String(value)); },
    async delete(key) { store.delete(key); },
  };
  const env = envFor({}, { UNIQUE_DOWNLOADS: kv });
  const id = "00000000-0000-4000-8000-00000000abcd";

  const missing = await worker.fetch(
    new Request(`https://rdp.xydesk.my.id/api/account/settings?id=${id}`),
    env,
  );
  assert.equal(missing.status, 404);

  const put = await worker.fetch(new Request("https://rdp.xydesk.my.id/api/account/settings", {
    method: "PUT",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ id, blob: JSON.stringify({ hud: { size: 44 }, prefs: { theme: "dark" } }) }),
  }), env);
  assert.equal(put.status, 200);
  const putBody = await put.json();
  assert.equal(putBody.status, "ok");
  assert.ok(putBody.updated_at);

  const get = await worker.fetch(
    new Request(`https://rdp.xydesk.my.id/api/account/settings?id=${id}`),
    env,
  );
  assert.equal(get.status, 200);
  const getBody = await get.json();
  assert.equal(JSON.parse(getBody.blob).prefs.theme, "dark");

  const badId = await worker.fetch(new Request("https://rdp.xydesk.my.id/api/account/settings", {
    method: "PUT",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ id: "bukan-uuid", blob: "{}" }),
  }), env);
  assert.equal(badId.status, 400);

  const badBlob = await worker.fetch(new Request("https://rdp.xydesk.my.id/api/account/settings", {
    method: "PUT",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ id, blob: "" }),
  }), env);
  assert.equal(badBlob.status, 400);
});

test("relay ID PC: heartbeat, resolve kandidat, throttle, filter IP privat, rate-limit", async () => {
  const kv = mockKv();
  const env = envFor({}, { UNIQUE_DOWNLOADS: kv });
  const id = "XY1A2B3C4D";

  const missing = await worker.fetch(
    new Request(`https://api.xydeskremote.biz.id/api/pc/relay?id=${id}`, {
      headers: { "cf-connecting-ip": "203.0.113.9" },
    }),
    env,
  );
  assert.equal(missing.status, 404);

  const reg = await worker.fetch(
    new Request("https://api.xydeskremote.biz.id/api/pc/relay", {
      method: "POST",
      headers: { "content-type": "application/json", "cf-connecting-ip": "198.51.100.7" },
      body: JSON.stringify({ id, hostname: "PC-GAMING", rdp_port: 3389, tailnet: "pc-gaming.tailnet.ts.net" }),
    }),
    env,
  );
  assert.equal(reg.status, 200);

  const res = await worker.fetch(
    new Request(`https://api.xydeskremote.biz.id/api/pc/relay?id=${id}`, {
      headers: { "cf-connecting-ip": "203.0.113.9" },
    }),
    env,
  );
  assert.equal(res.status, 200);
  const body = await res.json();
  assert.equal(body.hostname, "PC-GAMING");
  assert.equal(body.candidates.length, 2);
  assert.equal(body.candidates[0].host, "198.51.100.7");
  assert.equal(body.candidates[0].kind, "public");
  assert.equal(body.candidates[1].kind, "tailnet");

  const reg2 = await worker.fetch(
    new Request("https://api.xydeskremote.biz.id/api/pc/relay", {
      method: "POST",
      headers: { "content-type": "application/json", "cf-connecting-ip": "198.51.100.7" },
      body: JSON.stringify({ id, hostname: "PC-GAMING", rdp_port: 3389 }),
    }),
    env,
  );
  assert.equal(reg2.status, 200);
  assert.equal((await reg2.json()).throttled, true);

  const badId = await worker.fetch(
    new Request("https://api.xydeskremote.biz.id/api/pc/relay?id=123-456-7890"),
    env,
  );
  assert.equal(badId.status, 400);

  const badPort = await worker.fetch(
    new Request("https://api.xydeskremote.biz.id/api/pc/relay", {
      method: "POST",
      headers: { "content-type": "application/json", "cf-connecting-ip": "198.51.100.7" },
      body: JSON.stringify({ id: "XY00000002", hostname: "h", rdp_port: 70000 }),
    }),
    env,
  );
  assert.equal(badPort.status, 400);

  const kvPriv = mockKv();
  const envPriv = envFor({}, { UNIQUE_DOWNLOADS: kvPriv });
  await worker.fetch(
    new Request("https://api.xydeskremote.biz.id/api/pc/relay", {
      method: "POST",
      headers: { "content-type": "application/json", "cf-connecting-ip": "192.168.1.50" },
      body: JSON.stringify({ id: "XY00000001", hostname: "h", rdp_port: 3390 }),
    }),
    envPriv,
  );
  const resPriv = await worker.fetch(
    new Request("https://api.xydeskremote.biz.id/api/pc/relay?id=XY00000001", {
      headers: { "cf-connecting-ip": "203.0.113.9" },
    }),
    envPriv,
  );
  assert.equal(resPriv.status, 200);
  assert.equal((await resPriv.json()).candidates.length, 0);

  const kvRl = mockKv();
  const envRl = envFor({}, { UNIQUE_DOWNLOADS: kvRl });
  let last;
  for (let i = 0; i < 31; i++) {
    last = await worker.fetch(
      new Request("https://api.xydeskremote.biz.id/api/pc/relay?id=XY1A2B3C4D", {
        headers: { "cf-connecting-ip": "203.0.113.9" },
      }),
      envRl,
    );
  }
  assert.equal(last.status, 429);
});

test("alias universal.apk mengikuti rilis aktif yang memuat XyDesk-RemoteUniversal.apk", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (url) => {
    if (String(url).startsWith("https://api.github.com/repos/xykal/XyDesk-Remote/releases")) {
      const asset = (name) => ({
        name,
        browser_download_url: `https://github.com/xykal/XyDesk-Remote/releases/download/v1.1.9/${name}`,
      });
      return new Response(JSON.stringify([{
        tag_name: "v1.1.9",
        html_url: "https://github.com/xykal/XyDesk-Remote/releases/tag/v1.1.9",
        published_at: "2026-10-12T00:00:00Z",
        draft: false,
        prerelease: false,
        assets: [
          asset("XyDesk-Remote32bit.apk"),
          asset("XyDesk-Remote64bit.apk"),
          asset("XyDesk-Remote64bit-x86.apk"),
          asset("XyDesk-RemoteUniversal.apk"),
          asset("SHA256SUMS.txt"),
        ],
      }]), { status: 200, headers: { "content-type": "application/json" } });
    }
    return new Response("offline", { status: 503 });
  };
  try {
    for (const path of ["/universal.apk", "/XyDesk-Remote-universal.apk", "/XyDesk-RemoteUniversal.apk"]) {
      const response = await worker.fetch(
        new Request("https://rdp.xydesk.my.id" + path),
        envFor(),
      );
      assert.equal(response.status, 302, path);
      assert.equal(
        response.headers.get("location"),
        "https://github.com/xykal/XyDesk-Remote/releases/download/v1.1.9/XyDesk-RemoteUniversal.apk",
        path,
      );
    }
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("universal.apk fallback ke arm64-v8a saat rilis aktif tidak punya aset universal", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (url) => {
    if (String(url).startsWith("https://api.github.com/repos/xykal/XyDesk-Remote/releases")) {
      const asset = (name) => ({
        name,
        browser_download_url: `https://github.com/xykal/XyDesk-Remote/releases/download/v1.0.2/${name}`,
      });
      return new Response(JSON.stringify([{
        tag_name: "v1.0.2",
        html_url: "https://github.com/xykal/XyDesk-Remote/releases/tag/v1.0.2",
        published_at: "2026-10-03T20:22:09Z",
        draft: false,
        prerelease: false,
        assets: [
          asset("XyDesk-arm64-v8a.apk"),
          asset("XyDesk-armeabi-v7a.apk"),
          asset("XyDesk-x86_64.apk"),
        ],
      }]), { status: 200, headers: { "content-type": "application/json" } });
    }
    return new Response("offline", { status: 503 });
  };
  try {
    const response = await worker.fetch(
      new Request("https://rdp.xydesk.my.id/universal.apk"),
      envFor(),
    );
    assert.equal(response.status, 302);
    assert.equal(
      response.headers.get("location"),
      "https://github.com/xykal/XyDesk-Remote/releases/download/v1.0.2/XyDesk-arm64-v8a.apk",
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("host alias pakai tag rilis PUBLIK terakhir, dan fallback ke release-state tanpa token", async () => {
  const response = await worker.fetch(
    new Request("https://rdp.xydesk.my.id/XyDesk-Remote-Host-Agent-win64.zip"),
    envFor(),
  );
  assert.equal(response.status, 302);
  assert.equal(
    response.headers.get("location"),
    "https://github.com/xykal/XyDesk-Remote/releases/download/v1.0.2/XyDesk-Remote-Host-Agent-win64.zip",
  );
});

test("alias host standalone 1-exe redirect ke aset rilis aktif", async () => {
  // Ronde 43: /XyDeskRemoteHost-Standalone.exe dan /XyDesk-Host.exe jadi
  // jalur utama unduhan host agent (1 file, tanpa DLL).
  for (const alias of ["XyDeskRemoteHost-Standalone.exe", "XyDesk-Host.exe"]) {
    const response = await worker.fetch(
      new Request(`https://rdp.xydesk.my.id/${alias}`),
      envFor(),
    );
    assert.equal(response.status, 302, alias);
    assert.equal(
      response.headers.get("location"),
      "https://github.com/xykal/XyDesk-Remote/releases/download/v1.0.2/XyDeskRemoteHost-Standalone.exe",
      alias,
    );
  }
});

test("portal mengikuti rilis PUBLIK terbaru, bukan tag statis di bundle", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (url) => {
    if (String(url).endsWith("/releases?per_page=20")) {
      return new Response(JSON.stringify([
        { tag_name: "v1.0.8", draft: true, prerelease: false, assets: [] },
        {
          tag_name: "v1.0.7",
          draft: false,
          prerelease: false,
          published_at: "2026-10-05T00:00:00Z",
          html_url: "https://github.com/xykal/XyDesk-Remote/releases/tag/v1.0.7",
          assets: [
            {
              name: "XyDesk-Remote-Host-Agent-win64.zip",
              browser_download_url:
                "https://github.com/xykal/XyDesk-Remote/releases/download/v1.0.7/XyDesk-Remote-Host-Agent-win64.zip",
            },
            {
              name: "SHA256SUMS.txt",
              browser_download_url:
                "https://github.com/xykal/XyDesk-Remote/releases/download/v1.0.7/SHA256SUMS.txt",
            },
          ],
        },
        { tag_name: "v1.0.2", draft: false, prerelease: false, assets: [] },
      ]), { headers: { "content-type": "application/json" } });
    }
    return new Response("unexpected url", { status: 500 });
  };
  try {
    // v1.0.8 dilewati karena masih draft (asetnya 404 untuk publik).
    const alias = await worker.fetch(
      new Request("https://rdp.xydesk.my.id/XyDesk-Remote-Host-Agent-win64.zip"),
      envFor(),
    );
    assert.equal(alias.status, 302);
    assert.equal(
      alias.headers.get("location"),
      "https://github.com/xykal/XyDesk-Remote/releases/download/v1.0.7/XyDesk-Remote-Host-Agent-win64.zip",
    );

    const checksums = await worker.fetch(
      new Request("https://rdp.xydesk.my.id/SHA256SUMS.txt"),
      envFor(),
    );
    assert.equal(checksums.status, 302);
    assert.equal(
      checksums.headers.get("location"),
      "https://github.com/xykal/XyDesk-Remote/releases/download/v1.0.7/SHA256SUMS.txt",
    );

    const health = await worker.fetch(new Request("https://rdp.xydesk.my.id/api/health"), envFor());
    const body = await health.json();
    assert.equal(body.version, "v1.0.7");
    assert.equal(body.qaBuild, "1.0.7");
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("portal jatuh ke release-state.json saat API GitHub tidak bisa dihubungi", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => {
    throw new Error("network down");
  };
  try {
    const alias = await worker.fetch(
      new Request("https://rdp.xydesk.my.id/arm64-v8a.apk"),
      envFor(),
    );
    assert.equal(alias.status, 302);
    assert.equal(
      alias.headers.get("location"),
      "https://github.com/xykal/XyDesk-Remote/releases/download/v1.0.2/XyDesk-arm64-v8a.apk",
    );
    const health = await worker.fetch(new Request("https://rdp.xydesk.my.id/api/health"), envFor());
    assert.equal((await health.json()).version, "v1.0.2");
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("legacy aliases retry GH_PAT when GH_TOKEN is rejected and choose the newest matching release", async () => {
  const originalFetch = globalThis.fetch;
  const requests = [];
  globalThis.fetch = async (url, init) => {
    const value = String(url);
    const authorization = new Headers(init.headers).get("authorization");
    requests.push({ url: value, authorization });
    if (value.endsWith("/releases?per_page=10")) {
      if (authorization === "token expired-token") return new Response("bad token", { status: 401 });
      if (authorization === "token read-only-token") {
        return new Response(JSON.stringify([
          { tag_name: "v2.0.0", draft: false, assets: [{ name: "XyDesk-x64.exe", id: 707000001 }] },
          { tag_name: "v1.0.0", draft: false, assets: [{ name: "XyDesk-x64.exe", id: 603985510 }] },
        ]), { headers: { "content-type": "application/json" } });
      }
    }
    if (value.endsWith("/assets/707000001") && authorization === "token read-only-token") {
      return new Response(null, { status: 302, headers: { location: "https://release-assets.githubusercontent.com/current-legacy.exe" } });
    }
    throw new Error(`Unexpected GitHub request: ${value}`);
  };
  try {
    const response = await worker.fetch(
      new Request("https://rdp.xydesk.my.id/XyDesk-x64.exe"),
      envFor({}, { GH_TOKEN: "expired-token", GH_PAT: "read-only-token" }),
    );
    assert.equal(response.status, 302);
    assert.equal(response.headers.get("location"), "https://release-assets.githubusercontent.com/current-legacy.exe");
    assert.deepEqual(requests.map((item) => item.authorization), [
      "token expired-token", "token read-only-token", "token expired-token", "token read-only-token",
    ]);
    assert.ok(requests[2].url.endsWith("/assets/707000001"));
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("legacy asset alias falls back to its pinned asset ID when release metadata is unavailable", async () => {
  const originalFetch = globalThis.fetch;
  const requested = [];
  globalThis.fetch = async (url) => {
    const value = String(url);
    requested.push(value);
    if (value.endsWith("/releases?per_page=10")) return new Response("temporarily unavailable", { status: 503 });
    if (value.endsWith("/assets/603985510")) {
      return new Response(null, { status: 302, headers: { location: "https://release-assets.githubusercontent.com/fallback-legacy.exe" } });
    }
    throw new Error(`Unexpected GitHub request: ${value}`);
  };
  try {
    const response = await worker.fetch(
      new Request("https://rdp.xydesk.my.id/XyDesk-x64.exe"),
      envFor({}, { GH_TOKEN: "test-token" }),
    );
    assert.equal(response.status, 302);
    assert.equal(response.headers.get("location"), "https://release-assets.githubusercontent.com/fallback-legacy.exe");
    assert.ok(requested.some((url) => url.endsWith("/releases?per_page=10")));
    assert.ok(requested.some((url) => url.endsWith("/assets/603985510")));
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("legacy asset aliases preserve the existing missing-secret 404 behavior", async () => {
  const response = await worker.fetch(new Request("https://rdp.xydesk.my.id/XyDesk-x64.exe"), envFor());
  assert.equal(response.status, 404);
});

test("checksum alias redirects to the checksum asset of the current release", async () => {
  const response = await worker.fetch(new Request("https://rdp.xydesk.my.id/SHA256SUMS.txt"), envFor());
  assert.equal(response.status, 302);
  assert.equal(
    response.headers.get("location"),
    "https://github.com/xykal/XyDesk-Remote/releases/download/v1.0.2/SHA256SUMS.txt",
  );
});

test("checksum alias falls back to the pinned release URL when metadata omits checksums_url", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => { throw new Error("checksum fallback must not call the GitHub API"); };
  const env = envFor();
  const stateWithoutChecksums = { ...releaseState };
  delete stateWithoutChecksums.checksums_url;
  env.ASSETS = {
    async fetch() {
      return new Response(JSON.stringify(stateWithoutChecksums), {
        headers: { "content-type": "application/json; charset=utf-8" },
      });
    },
  };
  try {
    const response = await worker.fetch(new Request("https://rdp.xydesk.my.id/SHA256SUMS.txt"), env);
    assert.equal(response.status, 302);
    assert.equal(
      response.headers.get("location"),
      "https://github.com/xykal/XyDesk-Remote/releases/download/v1.0.2/SHA256SUMS.txt",
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("PowerShell source is downloadable as text and is not executed by the Worker", async () => {
  const response = await worker.fetch(new Request("https://rdp.xydesk.my.id/host.ps1"), envFor({ "/host.ps1": "Write-Output 'review me'" }));
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type"), /text\/plain/);
  assert.match(response.headers.get("content-disposition"), /xydesk-host\.ps1/);
  assert.equal(await response.text(), "Write-Output 'review me'");
});

test("agent batch helper downloads and opens the script for review without piping it to execution", async () => {
  const batch = await readFile(new URL("../web/agent.bat", import.meta.url), "utf8");
  assert.doesNotMatch(batch, /\|\s*iex/i);
  assert.match(batch, /NOT run/i);
  const response = await worker.fetch(new Request("https://rdp.xydesk.my.id/agent.bat"), envFor({ "/agent.bat": batch }));
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-disposition"), /XyDesk-Remote-Agent-Setup\.bat/);
  assert.equal(await response.text(), batch);
});

test("Google verification file falls through to static assets byte-for-byte", async () => {
  const expected = await readFile(new URL("../web/googleb2a7847179613098.html", import.meta.url));
  const response = await worker.fetch(
    new Request("https://rdp.xydesk.my.id/googleb2a7847179613098.html"),
    envFor({ "/googleb2a7847179613098.html": expected.toString("utf8") }),
  );
  assert.equal(response.status, 200);
  assert.deepEqual(Buffer.from(await response.arrayBuffer()), expected);
});

test("unknown paths delegate to static assets and remain 404", async () => {
  const response = await worker.fetch(new Request("https://rdp.xydesk.my.id/no-such-page"), envFor());
  assert.equal(response.status, 404);
});

test("mutating HTTP methods are rejected", async () => {
  const response = await worker.fetch(new Request("https://rdp.xydesk.my.id/", { method: "POST" }), envFor());
  assert.equal(response.status, 405);
  assert.equal(response.headers.get("allow"), "GET, HEAD");
});

test("community comments attach to a joke, show in feed, and carry display names", async () => {
  const { object } = communityJokesObject();
  const env = jokesEnv(object);
  const author = "00000000-0000-4000-8000-000000000021";
  const commenter = "00000000-0000-4000-8000-000000000022";
  const submit = await worker.fetch(new Request("https://rdp.xydesk.my.id/api/jokes", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ client_id: author, text: "Ping 200ms di data seluler, semangat.", display_name: "Budi Seluler" }),
  }), env);
  assert.equal(submit.status, 201);
  const posted = await submit.json();
  assert.equal(posted.joke.author_name, "Budi Seluler");
  assert.equal(posted.joke.comments_total, 0);

  const comment = await worker.fetch(new Request(`https://rdp.xydesk.my.id/api/jokes/${posted.joke.id}/comment`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ client_id: commenter, text: "Sabar bang, namanya juga 4G.", display_name: "Tamu 4G" }),
  }), env);
  assert.equal(comment.status, 201);
  const commented = await comment.json();
  assert.equal(commented.comment.name, "Tamu 4G");
  assert.equal(commented.comments_total, 1);

  const feed = await worker.fetch(new Request("https://rdp.xydesk.my.id/api/jokes", {
    headers: { "x-xydesk-client-id": author },
  }), env);
  const body = await feed.json();
  assert.equal(body.items[0].author_name, "Budi Seluler");
  assert.equal(body.items[0].comments_total, 1);
  assert.equal(body.items[0].comments[0].text, "Sabar bang, namanya juga 4G.");
  assert.equal(body.items[0].comments[0].name, "Tamu 4G");

  const tooLong = await worker.fetch(new Request(`https://rdp.xydesk.my.id/api/jokes/${posted.joke.id}/comment`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ client_id: commenter, text: "x".repeat(141) }),
  }), env);
  assert.equal(tooLong.status, 400);
});

test("community comment delete: only the author, is_own flag in feed", async () => {
  const { object } = communityJokesObject();
  const env = jokesEnv(object);
  const author = "00000000-0000-4000-8000-000000000031";
  const commenter = "00000000-0000-4000-8000-000000000032";
  const submit = await worker.fetch(new Request("https://rdp.xydesk.my.id/api/jokes", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ client_id: author, text: "Kabel UTP vs WiFi round 2." }),
  }), env);
  const posted = await submit.json();

  const comment = await worker.fetch(new Request(`https://rdp.xydesk.my.id/api/jokes/${posted.joke.id}/comment`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ client_id: commenter, text: "WiFi menang kalau kabelnya ketinggalan." }),
  }), env);
  const commented = await comment.json();
  const commentId = commented.comment.id;

  // is_own: komentar sendiri terlihat sebagai milik sendiri.
  const feedOwn = await worker.fetch(new Request("https://rdp.xydesk.my.id/api/jokes", {
    headers: { "x-xydesk-client-id": commenter },
  }), env);
  const ownBody = await feedOwn.json();
  assert.equal(ownBody.items[0].comments[0].is_own, true);

  const feedOther = await worker.fetch(new Request("https://rdp.xydesk.my.id/api/jokes", {
    headers: { "x-xydesk-client-id": author },
  }), env);
  const otherBody = await feedOther.json();
  assert.equal(otherBody.items[0].comments[0].is_own, false);

  // Orang lain tidak boleh hapus.
  const forbidden = await worker.fetch(new Request(`https://rdp.xydesk.my.id/api/jokes/${posted.joke.id}/comment-delete`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ client_id: author, comment_id: commentId }),
  }), env);
  assert.equal(forbidden.status, 403);

  // Penulis komentar boleh hapus.
  const deleted = await worker.fetch(new Request(`https://rdp.xydesk.my.id/api/jokes/${posted.joke.id}/comment-delete`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ client_id: commenter, comment_id: commentId }),
  }), env);
  assert.equal(deleted.status, 200);
  const deletedBody = await deleted.json();
  assert.equal(deletedBody.comments_total, 0);

  // Hapus kedua kali → not found.
  const gone = await worker.fetch(new Request(`https://rdp.xydesk.my.id/api/jokes/${posted.joke.id}/comment-delete`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ client_id: commenter, comment_id: commentId }),
  }), env);
  assert.equal(gone.status, 404);
});

test("expanded reaction set accepts new emoji and /me stores display names", async () => {
  const { object } = communityJokesObject();
  const env = jokesEnv(object);
  const clientId = "00000000-0000-4000-8000-000000000031";
  const submit = await worker.fetch(new Request("https://rdp.xydesk.my.id/api/jokes", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ client_id: clientId, text: "Emoji baru masuk bro." }),
  }), env);
  const posted = await (await submit.json()).joke;

  const trophy = await worker.fetch(new Request(`https://rdp.xydesk.my.id/api/jokes/${posted.id}/reaction`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ client_id: clientId, emoji: "🏆" }),
  }), env);
  assert.equal(trophy.status, 200);
  const reacted = await trophy.json();
  assert.equal(reacted.joke.reactions["🏆"], 1);

  const putMe = await object.fetch(new Request("https://community-jokes/me", {
    method: "PUT",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ client_id: clientId, display_name: "xy-772727 fan" }),
  }));
  assert.equal(putMe.status, 200);
  const getMe = await object.fetch(new Request("https://community-jokes/me", {
    headers: { "x-xydesk-client-id": clientId },
  }));
  assert.equal((await getMe.json()).display_name, "xy-772727 fan");

  const badMe = await object.fetch(new Request("https://community-jokes/me", {
    method: "PUT",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ client_id: clientId, display_name: "https://spam.link" }),
  }));
  assert.equal(badMe.status, 400);
});
test("feed attachment foto/stiker, edit & hapus milik sendiri, avatar profil", async () => {
  const kv = mockKv();
  const { object } = communityJokesObject(kv);
  const env = { ...jokesEnv(object), UNIQUE_DOWNLOADS: kv };
  const owner = "00000000-0000-4000-8000-000000000041";
  const other = "00000000-0000-4000-8000-000000000042";
  const pixels = "QUJDRA=="; // "ABCD"

  const submit = await worker.fetch(new Request("https://rdp.xydesk.my.id/api/jokes", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      client_id: owner,
      text: "Stiker webp perdana di feed.",
      attachment: { mime: "image/webp", data: pixels },
    }),
  }), env);
  assert.equal(submit.status, 201);
  const posted = (await submit.json()).joke;
  assert.equal(posted.is_own, true);
  assert.equal(posted.attachment.mime, "image/webp");

  const att = await worker.fetch(new Request(`https://rdp.xydesk.my.id${posted.attachment.url}`), env);
  assert.equal(att.status, 200);
  assert.equal(att.headers.get("content-type"), "image/webp");
  assert.equal(Buffer.from(await att.arrayBuffer()).toString("base64"), pixels);

  const feed = await worker.fetch(new Request("https://rdp.xydesk.my.id/api/jokes", {
    headers: { "x-xydesk-client-id": other },
  }), env);
  const item = (await feed.json()).items[0];
  assert.equal(item.is_own, false);

  const foreignEdit = await worker.fetch(new Request(`https://rdp.xydesk.my.id/api/jokes/${posted.id}/edit`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ client_id: other, text: "Ambil alih postingan orang." }),
  }), env);
  assert.equal(foreignEdit.status, 403);

  const ownEdit = await worker.fetch(new Request(`https://rdp.xydesk.my.id/api/jokes/${posted.id}/edit`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ client_id: owner, text: "Stiker webp perdana, sudah diedit." }),
  }), env);
  assert.equal(ownEdit.status, 200);
  assert.equal((await ownEdit.json()).joke.edited_at > 0, true);

  const putAvatar = await worker.fetch(new Request("https://rdp.xydesk.my.id/api/jokes/me", {
    method: "PUT",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ client_id: owner, avatar: pixels, avatar_mime: "image/png" }),
  }), env);
  assert.equal(putAvatar.status, 200);
  const avatarUrl = (await putAvatar.json()).avatar_url;
  assert.ok(avatarUrl);
  const avatar = await worker.fetch(new Request(`https://rdp.xydesk.my.id${avatarUrl}`), env);
  assert.equal(avatar.status, 200);
  assert.equal(avatar.headers.get("content-type"), "image/png");

  const foreignDelete = await worker.fetch(new Request(`https://rdp.xydesk.my.id/api/jokes/${posted.id}/delete`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ client_id: other }),
  }), env);
  assert.equal(foreignDelete.status, 403);

  const ownDelete = await worker.fetch(new Request(`https://rdp.xydesk.my.id/api/jokes/${posted.id}/delete`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ client_id: owner }),
  }), env);
  assert.equal(ownDelete.status, 200);
  const after = await worker.fetch(new Request("https://rdp.xydesk.my.id/api/jokes"), env);
  assert.equal((await after.json()).items.length, 0);
  const attGone = await worker.fetch(new Request(`https://rdp.xydesk.my.id${posted.attachment.url}`), env);
  assert.equal(attGone.status, 404);
});

test("update-check API reports the newest published tag with changelog excerpt", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (input) => {
    const url = String(input?.url || input);
    if (url.includes("api.github.com")) {
      return new Response(
        JSON.stringify([
          {
            tag_name: "v9.9.9",
            html_url: "https://github.com/xykal/XyDesk-Remote/releases/tag/v9.9.9",
            published_at: "2026-10-12T00:00:00Z",
            draft: false,
            prerelease: false,
            body: "## v9.9.9\n- fitur baru mantap\n\n## v9.9.8\n- versi lama",
            assets: [],
          },
        ]),
        { status: 200, headers: { "content-type": "application/json" } },
      );
    }
    return originalFetch(input);
  };
  try {
    const response = await worker.fetch(
      new Request("https://rdp.xydesk.my.id/api/update-check"),
      envFor(),
    );
    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.status, "ok");
    assert.equal(body.version, "9.9.9");
    assert.equal(body.tag, "v9.9.9");
    assert.match(body.changelog, /fitur baru mantap/);
    assert.ok(!body.changelog.includes("versi lama"));
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("feedback board: submit, list, reply, staff-only status & hide", async () => {
  const records = new Map();
  const storage = {
    async get(key) { return records.get(key); },
    async put(key, value) { records.set(key, value); },
    async delete(key) { return records.delete(key); },
    async list({ prefix = "", limit = Infinity, reverse = false } = {}) {
      let entries = [...records].filter(([key]) => key.startsWith(prefix)).sort(([a], [b]) => a.localeCompare(b));
      if (reverse) entries = entries.reverse();
      return new Map(entries.slice(0, limit));
    },
    async transaction(callback) {
      const transaction = {
        async get(key) { return records.get(key); },
        async put(key, value) { records.set(key, value); },
        async delete(key) { return records.delete(key); },
        async list(options) { return storage.list(options); },
      };
      return callback(transaction);
    },
  };
  const board = new FeedbackBoard({ storage }, {});
  const env = envFor({}, {
    FEEDBACK_BOARD: {
      idFromName(name) { assert.equal(name, "xydesk-feedback-board-v1"); return name; },
      get(id) {
        assert.equal(id, "xydesk-feedback-board-v1");
        return { fetch: (request) => board.fetch(request) };
      },
    },
  });

  const user = "00000000-0000-4000-8000-000000000771";
  const submit = await worker.fetch(new Request("https://www.xydeskremote.biz.id/api/feedback", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ client_id: user, type: "bug", text: "Keyboard aneh di Android 15.", name: "Bang Jago" }),
  }), env);
  assert.equal(submit.status, 201);
  const posted = await submit.json();
  assert.equal(posted.item.type, "bug");
  assert.equal(posted.item.status, "open");
  const fbId = posted.item.id;

  const list = await worker.fetch(new Request("https://www.xydeskremote.biz.id/api/feedback"), env);
  const listBody = await list.json();
  assert.equal(list.status, 200);
  assert.equal(listBody.items.length, 1);
  assert.equal(listBody.items[0].name, "Bang Jago");

  const reply = await worker.fetch(new Request(`https://www.xydeskremote.biz.id/api/feedback/${fbId}/reply`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ client_id: user, text: "Fixnya udah jalan, tunggu rilis ya." }),
  }), env);
  assert.equal(reply.status, 201);
  const replied = await reply.json();
  assert.equal(replied.reply.staff, false);

  // Tanpa token staff: status & hide harus 403.
  const badStatus = await worker.fetch(new Request(`https://www.xydeskremote.biz.id/api/feedback/${fbId}/status`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ status: "progress" }),
  }), env);
  assert.equal(badStatus.status, 403);
  const badHide = await worker.fetch(new Request(`https://www.xydeskremote.biz.id/api/feedback/${fbId}/hide`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({}),
  }), env);
  assert.equal(badHide.status, 403);

  // Balasan staff: token benar -> badge staff + status bisa diubah.
  const staffEnv = envFor({}, {
    FEEDBACK_STAFF_TOKEN: "test-staff-token",
    FEEDBACK_BOARD: {
      idFromName(name) { return name; },
      get() { return { fetch: (request) => board.fetch(request) }; },
    },
  });
  const staffReply = await worker.fetch(new Request(`https://www.xydeskremote.biz.id/api/feedback/${fbId}/reply`, {
    method: "POST",
    headers: { "content-type": "application/json", "x-xydesk-staff-token": "test-staff-token" },
    body: JSON.stringify({ client_id: user, text: "Sudah kami perbaiki di v1.1.14, coba update ya." }),
  }), staffEnv);
  assert.equal(staffReply.status, 201);
  const staffBody = await staffReply.json();
  assert.equal(staffBody.reply.staff, true);
  assert.equal(staffBody.reply.name, "XyDesk Team");

  const setStatus = await worker.fetch(new Request(`https://www.xydeskremote.biz.id/api/feedback/${fbId}/status`, {
    method: "POST",
    headers: { "content-type": "application/json", "x-xydesk-staff-token": "test-staff-token" },
    body: JSON.stringify({ status: "done" }),
  }), staffEnv);
  assert.equal(setStatus.status, 200);

  const list2 = await worker.fetch(new Request("https://www.xydeskremote.biz.id/api/feedback"), env);
  const list2Body = await list2.json();
  assert.equal(list2Body.items[0].status, "done");
  assert.equal(list2Body.items[0].reply_count, 2);

  // Hide staff -> hilang dari list.
  const hide = await worker.fetch(new Request(`https://www.xydeskremote.biz.id/api/feedback/${fbId}/hide`, {
    method: "POST",
    headers: { "content-type": "application/json", "x-xydesk-staff-token": "test-staff-token" },
    body: JSON.stringify({}),
  }), staffEnv);
  assert.equal(hide.status, 200);
  const list3 = await worker.fetch(new Request("https://www.xydeskremote.biz.id/api/feedback"), env);
  const list3Body = await list3.json();
  assert.equal(list3Body.items.length, 0);

  // Rate limit submit (30 dtk).
  const spam = await worker.fetch(new Request("https://www.xydeskremote.biz.id/api/feedback", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ client_id: user, type: "idea", text: "spam" }),
  }), env);
  assert.equal(spam.status, 429);
});

test("ronde 48: global chat direct reply (/api/chat/send) + OneSignal push + Play Protect manifest guard + XyDesk unified log", async () => {
  const records = new Map();
  const storage = {
    async get(key) { return records.get(key); },
    async put(key, value) { records.set(key, value); },
    async delete(key) { return records.delete(key); },
    async list(options = {}) {
      const prefix = options.prefix || "";
      const entries = [...records.entries()].filter(([k]) => k.startsWith(prefix));
      return new Map(entries);
    },
  };

  let pushedBody = null;
  globalThis.fetch = async (url, init) => {
    if (String(url).includes("api.onesignal.com/notifications")) {
      pushedBody = JSON.parse(init.body);
      return new Response(JSON.stringify({ id: "os-notif-1" }), { status: 200 });
    }
    return new Response("offline", { status: 503 });
  };

  const chat = new GlobalChat({ storage }, {
    ONESIGNAL_APP_ID: "ffaa87d6-8e1d-42b6-bb4f-ebbbfc0bccbb",
    ONESIGNAL_REST_API_KEY: "os_v2_app_test_key",
  });
  const env = envFor({}, {
    GLOBAL_CHAT: {
      idFromName(name) { assert.equal(name, "xydesk-global-chat-v1"); return name; },
      get() { return { fetch: (req) => chat.fetch(req) }; },
    },
  });

  const sendRes = await worker.fetch(new Request("https://api.xydeskremote.biz.id/api/chat/send", {
    method: "POST",
    headers: { "Content-Type": "application/json", "cf-ipcountry": "ID" },
    body: JSON.stringify({ name: "Tamu-8899", text: "Balas langsung dari notifikasi!" }),
  }), env);
  assert.equal(sendRes.status, 200);
  const sendJson = await sendRes.json();
  assert.equal(sendJson.status, "ok");
  assert.equal(sendJson.message.name, "Tamu-8899");
  assert.equal(sendJson.message.country, "ID");
  assert.equal(sendJson.message.flag, "🇮🇩");
  assert.equal(sendJson.message.text, "Balas langsung dari notifikasi!");
  assert.equal(sendJson.message.target_lang, "EN");
  assert.ok(sendJson.message.translated.toLowerCase().includes("reply"));

  // Pastikan OneSignal terpanggil dengan data MessagingStyle + bendera + terjemahan.
  assert.ok(pushedBody);
  assert.equal(pushedBody.app_id, "ffaa87d6-8e1d-42b6-bb4f-ebbbfc0bccbb");
  assert.equal(pushedBody.data.type, "global_chat");
  assert.ok(pushedBody.data.sender_name.includes("Tamu-8899"));
  assert.equal(pushedBody.data.country, "ID");
  assert.equal(pushedBody.data.text, "Balas langsung dari notifikasi!");
  assert.ok(pushedBody.data.translated.toLowerCase().includes("reply"));

  // Tes pengirim dari negara lain (US -> diterjemahkan ke ID).
  const sendUs = await worker.fetch(new Request("https://api.xydeskremote.biz.id/api/chat/send", {
    method: "POST",
    headers: { "Content-Type": "application/json", "cf-ipcountry": "US" },
    body: JSON.stringify({ name: "Alex", text: "Hello everyone, thank you!" }),
  }), env);
  assert.equal(sendUs.status, 200);
  const sendUsJson = await sendUs.json();
  assert.equal(sendUsJson.message.country, "US");
  assert.equal(sendUsJson.message.flag, "🇺🇸");
  assert.equal(sendUsJson.message.target_lang, "ID");
  assert.ok(sendUsJson.message.translated.toLowerCase().includes("halo"));

  // Tes pengirim Founder (xykal) -> mendapat flag is_founder = true + label [FOUNDER] di push notif.
  const sendFounder = await worker.fetch(new Request("https://api.xydeskremote.biz.id/api/chat/send", {
    method: "POST",
    headers: { "Content-Type": "application/json", "cf-ipcountry": "ID" },
    body: JSON.stringify({ name: "xykal", text: "Halo semua dari Founder!" }),
  }), env);
  assert.equal(sendFounder.status, 200);
  const sendFounderJson = await sendFounder.json();
  assert.equal(sendFounderJson.message.is_founder, true);
  assert.ok(pushedBody.data.sender_name.includes("[FOUNDER]"));

  // Riwayat harus memuat ketiga pesan lengkap dengan bendera, terjemahan, & status founder.
  const histRes = await worker.fetch(new Request("https://api.xydeskremote.biz.id/api/chat/history"), env);
  assert.equal(histRes.status, 200);
  const histJson = await histRes.json();
  assert.equal(histJson.messages.length, 3);
  assert.equal(histJson.messages[0].text, "Balas langsung dari notifikasi!");
  assert.equal(histJson.messages[1].country, "US");
  assert.equal(histJson.messages[2].is_founder, true);

  // Guard Play Protect Enhanced Fraud Protection: manifest tidak boleh memuat BIND_ACCESSIBILITY_SERVICE.
  const coreManifest = await readFile(new URL("../client/Android/Studio/freeRDPCore/src/main/AndroidManifest.xml", import.meta.url), "utf8");
  assert.ok(!coreManifest.includes("android.permission.BIND_ACCESSIBILITY_SERVICE"));
  assert.ok(!coreManifest.includes("android:name=\".presentation.KeyboardAccessibilityService\""));

  // Guard Log XyDesk: tidak ada lagi blok terpisah "=== LOG NATIVE (FreeRDP) ===" di XyDeskHome.kt.
  const homeKt = await readFile(new URL("../client/Android/Studio/app/src/main/kotlin/id/xydesk/remote/ui/XyDeskHome.kt", import.meta.url), "utf8");
  assert.ok(!homeKt.includes("=== LOG NATIVE (FreeRDP) ==="));
  assert.ok(homeKt.includes("ConnectionLog.formattedXyDeskLog"));

  // Ronde 52: uji deteksi negara akurat (tidak melabeli pesan Bahasa Indonesia sebagai US meski cf-ipcountry=US),
  // serta fitur reply_to_* dan attachment_* (foto/file/stiker).
  const sendIdOnUsVpn = await worker.fetch(new Request("https://api.xydeskremote.biz.id/api/chat/send", {
    method: "POST",
    headers: { "Content-Type": "application/json", "cf-ipcountry": "US" },
    body: JSON.stringify({
      name: "Andi",
      github_login: "andi-dev",
      text: "Halo teman-teman, gimana cara setting RDP supaya cepat?",
      reply_to_id: sendFounderJson.message.id,
      reply_to_name: "xykal",
      reply_to_text: "Halo semua dari Founder!",
      attachment_kind: "sticker",
      attachment_data: "xydesk_120hz",
      attachment_name: "120Hz Turbo",
    }),
  }), env);
  assert.equal(sendIdOnUsVpn.status, 200);
  const sendIdOnUsVpnJson = await sendIdOnUsVpn.json();
  assert.equal(sendIdOnUsVpnJson.message.country, "ID");
  assert.equal(sendIdOnUsVpnJson.message.flag, "🇮🇩");
  assert.equal(sendIdOnUsVpnJson.message.github_login, "andi-dev");
  assert.equal(sendIdOnUsVpnJson.message.reply_to_name, "xykal");
  assert.equal(sendIdOnUsVpnJson.message.attachment_kind, "sticker");
  assert.equal(sendIdOnUsVpnJson.message.attachment_data, "xydesk_120hz");

  // Uji deteksi aksara Thailand -> TH
  const sendThai = await worker.fetch(new Request("https://api.xydeskremote.biz.id/api/chat/send", {
    method: "POST",
    headers: { "Content-Type": "application/json", "cf-ipcountry": "SG" },
    body: JSON.stringify({
      name: "Somchai",
      github_login: "somchai-th",
      text: "สวัสดีครับ ขอบคุณมาก",
    }),
  }), env);
  assert.equal(sendThai.status, 200);
  const sendThaiJson = await sendThai.json();
  assert.equal(sendThaiJson.message.country, "TH");
  assert.equal(sendThaiJson.message.flag, "🇹🇭");

  // Ronde 57: Kirim media/stiker tanpa caption tidak menghasilkan teks "[Foto: ...]" atau "[Stiker: ...]".
  const sendSilentMedia = await worker.fetch(new Request("https://api.xydeskremote.biz.id/api/chat/send", {
    method: "POST",
    headers: { "Content-Type": "application/json", "cf-ipcountry": "ID" },
    body: JSON.stringify({
      name: "xykal",
      github_login: "xykal",
      text: "",
      attachment_type: "sticker",
      attachment_kind: "sticker",
      attachment_data: "sticker:xy_gg",
    }),
  }), env);
  assert.equal(sendSilentMedia.status, 200);
  const sendSilentMediaJson = await sendSilentMedia.json();
  assert.equal(sendSilentMediaJson.message.text, "");

  // Ronde 57: XyShield Anti-Virus menolak pengiriman file/link berbahaya (.exe/.apk/grabify).
  const sendVirus = await worker.fetch(new Request("https://api.xydeskremote.biz.id/api/chat/send", {
    method: "POST",
    headers: { "Content-Type": "application/json", "cf-ipcountry": "ID" },
    body: JSON.stringify({
      name: "Attacker",
      text: "Download cheat https://evil.example/trojan.exe",
    }),
  }), env);
  assert.equal(sendVirus.status, 400);
  const sendVirusJson = await sendVirus.json();
  assert.equal(sendVirusJson.code, "malicious_link_blocked");

  // Ronde 57: Leaderboard Top 1–100 tersedia & mencatat durasi pemakaian APK/bermain.
  const lbPost = await worker.fetch(new Request("https://api.xydeskremote.biz.id/api/leaderboard", {
    method: "POST",
    headers: { "Content-Type": "application/json", "cf-ipcountry": "ID" },
    body: JSON.stringify({
      client_id: "550e8400-e29b-41d4-a716-446655440000",
      github_login: "xykal",
      name: "xykal",
      delta_seconds: 600,
      total_seconds: 500000,
      sessions_count: 150,
    }),
  }), env);
  assert.equal(lbPost.status, 200);
  const lbJson = await lbPost.json();
  assert.equal(lbJson.status, "ok");
  assert.ok(Array.isArray(lbJson.items));
  assert.ok(lbJson.items.length >= 1 && lbJson.items[0].github_login === "xykal");
  assert.equal(lbJson.items[0].rank, 1);

  // Ronde 57: OG Preview memblokir URL berbahaya dengan XyShield & mengizinkan URL publik (GitHub/YouTube).
  const ogBad = await worker.fetch(new Request("https://api.xydeskremote.biz.id/api/og?url=https%3A%2F%2Fevil.test%2Fmalware.exe"), env);
  assert.equal(ogBad.status, 403);
  const ogBadJson = await ogBad.json();
  assert.equal(ogBadJson.safe, false);

  const ogGood = await worker.fetch(new Request("https://api.xydeskremote.biz.id/api/og?url=https%3A%2F%2Fgithub.com%2Fxykal%2FXyDesk-Remote"), env);
  assert.equal(ogGood.status, 200);
  const ogGoodJson = await ogGood.json();
  assert.equal(ogGoodJson.safe, true);
  assert.ok(ogGoodJson.image.includes("opengraph.githubassets.com"));
});



test("chat translation shares a deadline even when a provider ignores abort", async () => {
  let signal;
  const result = await withChatTranslationBudget((s) => {
    signal = s;
    return new Promise(() => {});
  }, () => "fallback", 20);
  assert.equal(result, "fallback");
  assert.equal(signal.aborted, true);
});

test("chat translation keeps fast results and falls back on provider errors", async () => {
  assert.equal(await withChatTranslationBudget(async () => "translated", () => "fallback", 500), "translated");
  assert.equal(await withChatTranslationBudget(async () => { throw new Error("offline"); }, () => "fallback", 500), "fallback");
});


test("empty durable leaderboard never fabricates founder or placeholder podium users", async () => {
  const chat = new GlobalChat({ storage: { get: async () => undefined } }, {});
  const response = await chat.handleLeaderboard(new Request("https://example.test/leaderboard"));
  const body = await response.json();
  assert.deepEqual(body.items, []);
  assert.equal(body.total_ranked, 0);
});

test("OAuth complete rejects mismatched provider identity before any storage or DO write", async () => {
  let writes = 0;
  const env = envFor({}, {
    UNIQUE_DOWNLOADS: { put: async () => { writes++; } },
    GLOBAL_CHAT: { idFromName() { writes++; throw new Error("must not forward"); } },
  });
  globalThis.fetch = async () => Response.json({ id: 123, login: "alice" });
  const response = await worker.fetch(new Request("https://example.test/api/github/oauth/complete", {
    method: "POST", body: JSON.stringify({ session_id: "test-mismatch", token: "test-only", login: "bob" }),
  }), env);
  assert.equal(response.status, 401);
  assert.deepEqual(await response.json(), { status: "error", code: "invalid_identity" });
  assert.equal(writes, 0);
});

test("OAuth complete writes only provider identity and forwards canonical metadata", async () => {
  const stored = [];
  const forwarded = [];
  const env = envFor({}, {
    UNIQUE_DOWNLOADS: { put: async (key, value) => { stored.push([key, JSON.parse(value)]); } },
    GLOBAL_CHAT: {
      idFromName: () => "test-chat",
      get: () => ({ fetch: async request => { forwarded.push(await request.json()); return Response.json({ status: "ok" }); } }),
    },
  });
  globalThis.fetch = async () => Response.json({ id: 123, login: "Alice", name: "Verified Provider", avatar_url: "https://avatars.githubusercontent.com/u/123" });
  const response = await worker.fetch(new Request("https://example.test/api/github/oauth/complete", {
    method: "POST", body: JSON.stringify({ session_id: "test-canonical", token: "test-only", login: "alice", name: "Forged Founder", avatar_url: "https://example.test/forged" }),
  }), env);
  assert.equal(response.status, 200);
  assert.equal(stored.length, 1);
  assert.equal(stored[0][1].login, "Alice");
  assert.equal(stored[0][1].name, "Verified Provider");
  assert.equal(forwarded.length, 1);
  assert.equal(forwarded[0].name, "Verified Provider");
  assert.equal(forwarded[0].avatar_url, "https://avatars.githubusercontent.com/u/123");
});

test("OAuth complete provider failure cannot write session", async () => {
  let writes = 0;
  globalThis.fetch = async () => new Response("provider unavailable", { status: 503 });
  const response = await worker.fetch(new Request("https://example.test/api/github/oauth/complete", {
    method: "POST", body: JSON.stringify({ session_id: "test-unavailable", token: "test-only", login: "alice" }),
  }), envFor({}, { UNIQUE_DOWNLOADS: { put: async () => { writes++; } } }));
  assert.equal(response.status, 503);
  assert.equal(writes, 0);
  assert.deepEqual(await response.json(), { status: "error", code: "identity_provider_unavailable" });
});

test("OAuth complete rejects malformed session rather than aliasing it", async () => {
  let calls = 0;
  globalThis.fetch = async () => { calls++; throw new Error("must not fetch"); };
  const response = await worker.fetch(new Request("https://example.test/api/github/oauth/complete", {
    method: "POST", body: JSON.stringify({ session_id: "test!aliased", token: "test-only", login: "alice" }),
  }), envFor());
  assert.equal(response.status, 400);
  assert.equal(calls, 0);
});

test("unsigned legacy cookie cannot create a polling session or token deep link", async () => {
  let writes = 0;
  let providerCalls = 0;
  globalThis.fetch = async () => { providerCalls++; throw new Error("no provider request during authorization redirect"); };
  const cookie = encodeURIComponent(btoa(JSON.stringify({ token: "forged-cookie-token", login: "xykal", name: "Forged Founder" })));
  const response = await worker.fetch(new Request("https://example.test/api/github/oauth/login?session_id=cookie-guard", {
    headers: { cookie: `xy_gh_session=${cookie}` },
  }), envFor({}, { UNIQUE_DOWNLOADS: { put: async () => { writes++; } } }));
  assert.equal(response.status, 302);
  const destination = new URL(response.headers.get("location"));
  assert.equal(destination.origin, "https://github.com");
  assert.equal(destination.pathname, "/login/oauth/authorize");
  assert.equal(destination.searchParams.has("access_token"), false);
  assert.equal(response.headers.get("location").includes("forged-cookie-token"), false);
  assert.equal(writes, 0);
  assert.equal(providerCalls, 0);
  assert.match(response.headers.get("set-cookie"), /xy_oauth_state=.*HttpOnly; Secure; SameSite=Lax/);
  const poll = await worker.fetch(new Request("https://example.test/api/github/oauth/poll?session_id=cookie-guard"), envFor());
  assert.equal((await poll.json()).ready, false);
});

test("malformed legacy cookie follows same provider flow as no cookie on login aliases", async () => {
  for (const path of ["/api/github/oauth/login", "/api/auth/login"]) {
    for (const cookie of ["", "xy_gh_session=not-valid-base64", `xy_gh_session=${encodeURIComponent(btoa(JSON.stringify({ token: "legacy-test", login: "alice" })))}`]) {
      const response = await worker.fetch(new Request(`https://example.test${path}?session_id=legacy-alias`, {
        headers: cookie ? { cookie } : {},
      }), envFor());
      assert.equal(response.status, 302);
      assert.equal(new URL(response.headers.get("location")).origin, "https://github.com");
      assert.equal((await response.text()).includes("legacy-test"), false);
    }
  }
});

test("HTML OAuth callback expires legacy bearer cookie instead of minting it", async () => {
  globalThis.fetch = async url => {
    if (String(url) === "https://github.com/login/oauth/access_token") return Response.json({ access_token: "test-callback-token" });
    if (String(url) === "https://api.github.com/user") return Response.json({ id: 123, login: "alice", name: "Alice" });
    throw new Error("unexpected external request");
  };
  const response = await worker.fetch(new Request("https://example.test/api/github/oauth/callback?code=test-code&state=apk_cookie-expire_nonce"), envFor({}, {
    GITHUB_OAUTH_CLIENT_ID: "test-client", GITHUB_OAUTH_CLIENT_SECRET: "test-config-not-real",
  }));
  assert.equal(response.status, 200);
  assert.equal(response.headers.get("set-cookie"), "xy_gh_session=; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=0");
  assert.equal(response.headers.get("set-cookie").includes("test-callback-token"), false);
  // Existing application return mechanism is unchanged in this step.
  assert.ok((await response.text()).includes("xydesk://github-auth?token=test-callback-token"));
});

test("OAuth callback never persists or redirects after unverified provider profile", async () => {
  const cases = [
    { label: "unauthorized", response: () => new Response(null, { status: 401 }), status: 401 },
    { label: "unavailable", response: () => new Response(null, { status: 503 }), status: 503 },
    { label: "missing-id", response: () => Response.json({ login: "alice" }), status: 503 },
    { label: "missing-login", response: () => Response.json({ id: 123 }), status: 503 },
    { label: "broken-json", response: () => new Response("not json"), status: 503 },
    { label: "network", response: () => { throw new Error("offline"); }, status: 503 },
  ];
  for (const item of cases) {
    for (const prefix of ["apk", "dash"]) {
      let writes = 0;
      globalThis.fetch = async url => {
        if (String(url) === "https://github.com/login/oauth/access_token") return Response.json({ access_token: "callback-test-only" });
        if (String(url) === "https://api.github.com/user") return item.response();
        throw new Error("unexpected external request");
      };
      const env = envFor({}, {
        GITHUB_OAUTH_CLIENT_ID: "test-client", GITHUB_OAUTH_CLIENT_SECRET: "test-config-not-real",
        UNIQUE_DOWNLOADS: { put: async () => { writes++; }, get: async () => null },
        GLOBAL_CHAT: { idFromName() { writes++; throw new Error("must not forward"); } },
      });
      const sid = `fail-${prefix}-${item.label}`;
      const response = await worker.fetch(new Request(`https://example.test/api/github/oauth/callback?code=test-code&state=${prefix}_${sid}_nonce`), env);
      assert.equal(response.status, item.status, `${prefix} ${item.label}`);
      assert.equal(response.headers.get("location"), null);
      assert.equal(response.headers.get("set-cookie"), null);
      const body = await response.text();
      assert.equal(body.includes("callback-test-only"), false);
      assert.equal(body.includes("xydesk://"), false);
      assert.equal(writes, 0);
      // Check edge cache separately without a DO fallback.
      const poll = await worker.fetch(new Request(`https://example.test/api/github/oauth/poll?session_id=${sid}`), envFor());
      assert.equal((await poll.json()).ready, false);
    }
  }
});

test("verified callback preserves JSON profile, storage and dashboard return shape", async () => {
  const stored = [];
  globalThis.fetch = async url => {
    if (String(url) === "https://github.com/login/oauth/access_token") return Response.json({ access_token: "verified-test-only" });
    if (String(url) === "https://api.github.com/user") return Response.json({ id: 123, login: "Alice", name: "Provider Name", avatar_url: "https://avatars.githubusercontent.com/u/123" });
    throw new Error("unexpected external request");
  };
  const env = envFor({}, {
    GITHUB_OAUTH_CLIENT_ID: "test-client", GITHUB_OAUTH_CLIENT_SECRET: "test-config-not-real",
    UNIQUE_DOWNLOADS: { put: async (key, value) => { stored.push([key, JSON.parse(value)]); } },
  });
  const response = await worker.fetch(new Request("https://example.test/api/github/oauth/exchange?code=test-code&state=apk_verified-json_nonce"), env);
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.ready, true);
  assert.equal(body.login, "Alice");
  assert.equal(body.name, "Provider Name");
  assert.equal(stored[0][1].login, "Alice");
  const dash = await worker.fetch(new Request("https://example.test/api/auth/callback/github?code=test-code&state=dash_verified-dash_nonce"), env);
  assert.equal(dash.status, 302);
  const destination = new URL(dash.headers.get("location"));
  assert.equal(destination.origin, "https://xyrdp-dash.vercel.app");
  assert.equal(destination.searchParams.get("bridge_token"), "verified-test-only");
});

test("OAuth token exchange provider outage cannot create or forward a callback session", async () => {
  let writes = 0;
  let identityCalls = 0;
  globalThis.fetch = async url => {
    if (String(url) === "https://github.com/login/oauth/access_token") return new Response(null, { status: 503 });
    identityCalls++;
    throw new Error("identity must not be queried without a token");
  };
  const response = await worker.fetch(new Request("https://example.test/api/github/oauth/callback?code=test-code&state=apk_exchange-failure_nonce"), envFor({}, {
    GITHUB_OAUTH_CLIENT_ID: "test-client", GITHUB_OAUTH_CLIENT_SECRET: "test-config-not-real",
    UNIQUE_DOWNLOADS: { put: async () => { writes++; } },
    GLOBAL_CHAT: { idFromName() { writes++; throw new Error("must not forward"); } },
  }));
  assert.equal(response.status, 503);
  assert.deepEqual(await response.json(), { status: "error", code: "oauth_provider_unavailable" });
  assert.equal(response.headers.get("location"), null);
  assert.equal(writes, 0);
  assert.equal(identityCalls, 0);
});
