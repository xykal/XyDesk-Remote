import test, { afterEach, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import worker, {
  ActiveSessions,
  CommunityJokes,
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

function communityJokesObject() {
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
  return { object: new CommunityJokes({ storage }), records };
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
    assert.match(body.note, /bukan jumlah pengguna unik/);
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
  assert.match(settings, /Matikan sakelar ini kapan saja/);
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
    body: JSON.stringify({ client_id: "00000000-0000-4000-8000-000000000051", text: "x".repeat(3_000) }),
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
