import test, { afterEach, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import worker, {
  ActiveSessions,
  CommunityJokes,
  FeedbackBoard,
  withChatTranslationBudget,
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
    // Explicit test-only OAuth configuration; production has NO source fallback secret.
    GITHUB_OAUTH_CLIENT_ID: "test-client", GITHUB_OAUTH_CLIENT_SECRET: "test-config-not-real",
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

test("active-session API exposes anonymous client-reported count and is edge-cacheable", async () => {
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
  // Agregat global (bukan data per pengguna) → aman di-cache 15 detik di
  // edge. Ini memangkas ratusan polling tab menjadi satu jawaban / 15 detik.
  assert.equal(response.headers.get("cache-control"), "public, max-age=15");
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

test("session heartbeat coalesces repeated reports inside the coalesce window", async () => {
  let calls = 0;
  const env = envFor({}, {
    ACTIVE_SESSIONS: {
      idFromName(name) { return name; },
      get() {
        return { async fetch() {
          calls += 1;
          return new Response(JSON.stringify({ status: "ok", active_session_count: 7 }), {
            headers: { "content-type": "application/json" },
          });
        } };
      },
    },
  });
  const sessionId = "00000000-0000-4000-8000-000000000099";
  const post = (action) => worker.fetch(new Request("https://rdp.xydesk.my.id/api/session", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ action, session_id: sessionId }),
  }), env);

  const first = await post("heartbeat");
  const second = await post("heartbeat");
  assert.equal(first.status, 200);
  assert.equal(second.status, 200);
  assert.equal((await second.json()).active_session_count, 7);
  assert.equal(calls, 1);

  const ended = await post("end");
  assert.equal(ended.status, 200);
  assert.equal(calls, 2);
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
  // Sejak build single-binary, semua alias host menunjuk ke aset kanonik
  // XyDeskRemote.exe (lihat QA_ASSETS di worker.mjs dan workflow build-apk.yml
  // bagian "Build and verify single static Windows Host Agent").
  for (const alias of ["XyDeskRemoteHost-Standalone.exe", "XyDesk-Host.exe"]) {
    const response = await worker.fetch(
      new Request(`https://rdp.xydesk.my.id/${alias}`),
      envFor(),
    );
    assert.equal(response.status, 302, alias);
    assert.equal(
      response.headers.get("location"),
      "https://github.com/xykal/XyDesk-Remote/releases/download/v1.0.2/XyDeskRemote.exe",
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
  assert.match(batch, /for review only/i);
  assert.match(batch, /notepad\.exe/i);
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

test("/me menyimpan nama tampilan akun anonim dan menolak nama spam", async () => {
  const { object } = communityJokesObject();
  const clientId = "00000000-0000-4000-8000-000000000031";

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
test("avatar profil tersimpan lewat /me dan endpoint feed/leaderboard mati (404)", async () => {
  const kv = mockKv();
  const { object } = communityJokesObject(kv);
  const env = { ...jokesEnv(object), UNIQUE_DOWNLOADS: kv };
  const owner = "00000000-0000-4000-8000-000000000041";
  const pixels = "QUJDRA=="; // "ABCD"

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

  // Feed komunitas & leaderboard sudah dihapus total: route-nya 404.
  for (const path of ["/api/jokes", "/api/leaderboard", "/api/notifications", "/api/chat/presence", "/api/voice-stage"]) {
    const res = await worker.fetch(new Request(`https://rdp.xydesk.my.id${path}`), env);
    assert.equal(res.status, 404, path);
  }
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

test("cutover firebase: jalur pesan chat Durable Object mati (410) + guard manifest/log", async () => {
  const env = envFor({}, {});
  for (const [method, path] of [
    ["POST", "/api/chat/send"],
    ["GET", "/api/chat/history"],
    ["POST", "/api/chat/pin"],
    ["POST", "/api/chat/delete"],
    ["POST", "/api/chat/edit"],
    ["GET", "/api/chat/send-capabilities"],
  ]) {
    const res = await worker.fetch(new Request(`https://api.xydeskremote.biz.id${path}`, { method }), env);
    assert.equal(res.status, 410, `${method} ${path}`);
    const body = await res.json();
    assert.equal(body.code, "chat_moved_to_firebase");
  }

  // Guard Play Protect Enhanced Fraud Protection: manifest tidak boleh memuat BIND_ACCESSIBILITY_SERVICE.
  const coreManifest = await readFile(new URL("../client/Android/Studio/freeRDPCore/src/main/AndroidManifest.xml", import.meta.url), "utf8");
  assert.ok(!coreManifest.includes("android.permission.BIND_ACCESSIBILITY_SERVICE"));
  assert.ok(!coreManifest.includes("android:name=\".presentation.KeyboardAccessibilityService\""));

  // Guard Log XyDesk: tidak ada lagi blok terpisah "=== LOG NATIVE (FreeRDP) ===" di XyDeskHome.kt.
  const homeKt = await readFile(new URL("../client/Android/Studio/app/src/main/kotlin/id/xydesk/remote/ui/XyDeskHome.kt", import.meta.url), "utf8");
  assert.ok(!homeKt.includes("=== LOG NATIVE (FreeRDP) ==="));
  assert.ok(homeKt.includes("ConnectionLog.formattedXyDeskLog"));
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

test("OAuth complete writes only provider identity", async () => {
  const stored = [];
  const env = envFor({}, {
    UNIQUE_DOWNLOADS: { put: async (key, value) => { stored.push([key, JSON.parse(value)]); } },
  });
  globalThis.fetch = async () => Response.json({ id: 123, login: "Alice", name: "Verified Provider", avatar_url: "https://avatars.githubusercontent.com/u/123" });
  const response = await worker.fetch(new Request("https://example.test/api/github/oauth/complete", {
    method: "POST", body: JSON.stringify({ session_id: "test-canonical", token: "test-only", login: "alice", name: "Forged Founder", avatar_url: "https://example.test/forged" }),
  }), env);
  assert.equal(response.status, 200);
  assert.equal(stored.length, 1);
  assert.equal(stored[0][1].login, "Alice");
  assert.equal(stored[0][1].name, "Verified Provider");
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
