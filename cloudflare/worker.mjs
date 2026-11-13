import { githubOAuthConfig, GITHUB_CALLBACK } from "./oauth-config.mjs";
import { exchangeGithubCode } from "./oauth-token-exchange.mjs";
import { verifyGithubIdentity, resolveGithubIdentity } from "./oauth-identity.mjs";

// Pinned fallback only; alias and checksum redirects prefer the current
// release_tag/downloads from release-state.json (see currentReleaseAssetUrl).
const RELEASE = "https://github.com/xykal/XyDesk-Remote/releases/download/v1.0.2/";

const PUBLIC_ASSETS = {
  "SHA256SUMS.txt": `${RELEASE}SHA256SUMS.txt`,
};

// Keep current production asset IDs as fallback; prefer the newest matching release asset.
// Public XyDesk Remote and legacy release aliases use GH_TOKEN for GitHub asset redirects.
// APK aliases carry `abi` so redirects follow the current release even when the
// published asset file names change between releases (v1.0.2 renamed them).
const QA_ASSETS = {
  "arm64-v8a.apk": { repo: "xykal/XyDesk-Remote", id: 606095803, abi: "arm64-v8a", fileName: "XyDesk-arm64-v8a.apk" },
  "armeabi-v7a.apk": { repo: "xykal/XyDesk-Remote", id: 606095801, abi: "armeabi-v7a", fileName: "XyDesk-armeabi-v7a.apk" },
  "x86_64.apk": { repo: "xykal/XyDesk-Remote", id: 606095795, abi: "x86_64", fileName: "XyDesk-x86_64.apk" },

  // Nama aset sejak rilis setelah v1.0.2 menyertakan "Remote" agar file yang
  // terunduh jelas berasal dari XyDesk Remote. `abi` membuat redirect mengikuti
  // rilis terbaru, jadi `id` di bawah hanya fallback bila API GitHub gagal.
  "XyDesk-Remote-arm64-v8a.apk": { repo: "xykal/XyDesk-Remote", id: 606095803, abi: "arm64-v8a", fileName: "XyDesk-Remote-arm64-v8a.apk" },
  "XyDesk-Remote-armeabi-v7a.apk": { repo: "xykal/XyDesk-Remote", id: 606095801, abi: "armeabi-v7a", fileName: "XyDesk-Remote-armeabi-v7a.apk" },
  "XyDesk-Remote-x86_64.apk": { repo: "xykal/XyDesk-Remote", id: 606095795, abi: "x86_64", fileName: "XyDesk-Remote-x86_64.apk" },
  // APK universal (semua ABI satu berkas) — baru ada sejak BUILD_UNIVERSAL=true;
  // tanpa `id` fallback, redirect sepenuhnya mengikuti rilis aktif.
  "universal.apk": { repo: "xykal/XyDesk-Remote", abi: "universal", fileName: "XyDesk-RemoteUniversal.apk" },
  "XyDesk-Remote-universal.apk": { repo: "xykal/XyDesk-Remote", abi: "universal", fileName: "XyDesk-RemoteUniversal.apk" },
  "XyDesk-RemoteUniversal.apk": { repo: "xykal/XyDesk-Remote", abi: "universal", fileName: "XyDesk-RemoteUniversal.apk" },
  "XyDesk-Remote-Host-Agent-win64.zip": { repo: "xykal/XyDesk-Remote", id: 606095797, fileName: "XyDesk-Remote-Host-Agent-win64.zip" },
  "XyDeskRemote.exe": { repo: "xykal/XyDesk-Remote", fileName: "XyDeskRemote.exe" },
  "XyDeskRemoteHost.exe": { repo: "xykal/XyDesk-Remote", fileName: "XyDeskRemote.exe" },
  "XyDeskRemoteHost-Standalone.exe": { repo: "xykal/XyDesk-Remote", fileName: "XyDeskRemote.exe" },
  "XyDesk-Host.exe": { repo: "xykal/XyDesk-Remote", fileName: "XyDeskRemote.exe" },
  "xydesk_quic.dll": { repo: "xykal/XyDesk-Remote", id: 606095799, fileName: "xydesk_quic.dll" },
  "xydesk_host_core.dll": { repo: "xykal/XyDesk-Remote", id: 606095805, fileName: "xydesk_host_core.dll" },
  "XyDesk-x64.exe": { repo: "xykal/XyDesk", id: 603985510, fileName: "XyDesk-x64.exe" },
  "XyDesk-x64.msi": { repo: "xykal/XyDesk", id: 603985511, fileName: "XyDesk-x64.msi" },
  "XyDesk-arm64-v8a.apk": { repo: "xykal/XyDesk", id: 603985508, fileName: "XyDesk-arm64-v8a.apk" },
  "XyDesk-armeabi-v7a.apk": { repo: "xykal/XyDesk", id: 603985512, fileName: "XyDesk-armeabi-v7a.apk" },
  "XyDesk.apk": { repo: "xykal/XyDesk", id: 603985508, fileName: "XyDesk-arm64-v8a.apk" },
};

const HEADERS = {
  "access-control-allow-origin": "*",
  "cache-control": "no-store",
  "content-type": "application/json; charset=utf-8",
  "x-content-type-options": "nosniff",
};
const ACTIVE_SESSION_TTL_MS = 90_000;
const ACTIVE_SESSION_MAX = 5_000;
const ACTIVE_SESSION_ID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const ACTIVE_SESSION_PREFIX = "session:";
// Coalescing di isolate untuk klien lama yang masih heartbeat/polling cepat.
// Lease sesi aktif hidup 90 detik; meneruskan heartbeat yang sama lebih
// sering dari 45 detik ke Durable Object tidak mengubah hasil, hanya
// membakar request. Presence Chat juga cukup diteruskan 45 detik sekali.
const SESSION_HEARTBEAT_COALESCE_MS = 45_000;
const SESSION_HEARTBEAT_FORWARD = new Map();
// Sesi hasil callback OAuth berlaku 15 menit (selaras batas tunggu APK).
const OAUTH_SESSION_TTL_MS = 15 * 60_000;
const OAUTH_SESSION_TTL_SECONDS = 900;
const COMMUNITY_CLIENT_ID_RE = ACTIVE_SESSION_ID_RE;
const COMMUNITY_AVATAR_MAX_BYTES = 350_000;
const COMMUNITY_ATTACHMENT_MIMES = Object.freeze([
  "image/webp", "image/gif", "image/png", "image/jpeg", "video/mp4",
]);
// Set reaksi diperluas (permintaan pemilik 2026-10-10: tombol "+" dengan
// lebih banyak emoji). Superset dari 8 emoji lama supaya hitungan reaksi
// postingan lama tetap terbaca.
const COMMUNITY_DISPLAY_NAME_MAX_CHARS = 24;

function isJsonContentType(contentType) {
  return String(contentType || "").split(";", 1)[0].trim().toLowerCase() === "application/json";
}

function jsonResponse(data, status = 200, requestMethod = "GET", extraHeaders = {}) {
  return new Response(requestMethod === "HEAD" ? null : JSON.stringify(data), {
    status,
    headers: { ...HEADERS, ...extraHeaders },
  });
}

function redirectResponse(location) {
  return new Response(null, {
    status: 302,
    headers: {
      location,
      "cache-control": "public, max-age=300",
      "referrer-policy": "no-referrer",
      "x-content-type-options": "nosniff",
    },
  });
}

function notFound(requestMethod = "GET") {
  return new Response(requestMethod === "HEAD" ? null : "Not Found", {
    status: 404,
    headers: { "content-type": "text/plain; charset=utf-8", "x-content-type-options": "nosniff" },
  });
}

function methodNotAllowed(allowed = "GET, HEAD") {
  return new Response("Method Not Allowed", {
    status: 405,
    headers: { allow: allowed, "content-type": "text/plain; charset=utf-8" },
  });
}

// Sumber utama metadata rilis adalah rilis PUBLIK terbaru di GitHub; berkas
// /release-state.json di bundle aset dipakai sebagai fallback kalau API tidak
// bisa dihubungi. Sebelumnya portal hanya membaca berkas statis itu, sehingga
// tertahan di v1.0.2 walaupun sudah ada rilis baru.
const RELEASE_LOOKUP_URL =
  "https://api.github.com/repos/xykal/XyDesk-Remote/releases?per_page=20";
const RELEASE_LOOKUP_TTL_MS = 5 * 60 * 1000;
const RELEASE_TAG_PATTERN = /^v[0-9A-Za-z._-]{1,80}$/;
let releaseLookupCache = { expiresAt: 0, release: null };

/**
 * Mengosongkan cache pencarian rilis. Dipakai tes: cache ini hidup di level
// modul, jadi tanpa reset hasil satu tes akan bocor ke tes berikutnya.
 */
export function resetReleaseLookupCache() {
  releaseLookupCache = { expiresAt: 0, release: null };
}

async function getLatestPublishedRelease(env) {
  const now = Date.now();
  if (now < releaseLookupCache.expiresAt) return releaseLookupCache.release;
  let release = null;
  try {
    // Token dipakai kalau ada (batas laju lebih tinggi); tanpa token helper ini
    // tetap memanggil API publik. Draft dan prarilis sengaja dilewati: asetnya
    // tidak bisa diunduh publik, jadi redirect akan berakhir 404.
    const response = await fetchGithubWithTokenFallback(RELEASE_LOOKUP_URL, env, {
      accept: "application/vnd.github+json",
      "user-agent": "xydesk-remote-portal",
    });
    if (response.ok) {
      const list = await response.json();
      release =
        (Array.isArray(list) ? list : []).find(
          (item) =>
            item &&
            item.draft === false &&
            item.prerelease === false &&
            typeof item.tag_name === "string" &&
            RELEASE_TAG_PATTERN.test(item.tag_name),
        ) || null;
    }
  } catch {
    release = null;
  }
  releaseLookupCache = { expiresAt: now + RELEASE_LOOKUP_TTL_MS, release };
  return release;
}

// ABI disimpulkan dari nama aset. releaseStateFromGithub membangun ulang
// `downloads` dari daftar aset rilis, dan tanpa field `abi` pencarian
// berdasarkan ABI di currentReleaseAssetUrl selalu gagal lalu jatuh ke nama
// file yang di-hardcode pada alias. Akibatnya alias /XyDesk-Remote-<abi>.apk
// mengarah ke berkas yang tidak ada pada rilis yang asetnya bernama
// XyDesk-<abi>.apk (misalnya v1.0.2) dan berakhir 404.
const ABI_NAMES = ["arm64-v8a", "armeabi-v7a", "x86_64"];

// Sejak v1.1.8 aset rilis memakai nama bit (XyDesk-Remote32bit.apk /
// XyDesk-Remote64bit.apk / XyDesk-Remote64bit-x86.apk) tanpa token ABI. Alias
// harus tetap mengikuti rilis berjalan, jadi nama bit dipetakan balik ke ABI.
const BIT_NAMES = [
  ["xydesk-remote64bit-x86", "x86_64"],
  ["xydesk-remote32bit", "armeabi-v7a"],
  ["xydesk-remote64bit", "arm64-v8a"],
];

function abiFromAssetName(name) {
  const lower = String(name).toLowerCase();
  // Universal dicek paling awal: namanya tidak memuat token ABI mana pun.
  if (lower.includes("universal")) return "universal";
  for (const abi of ABI_NAMES) {
    if (lower.includes(abi)) return abi;
  }
  // 64bit-x86 dicek sebelum 64bit agar tidak tertukar jadi arm64.
  for (const [token, abi] of BIT_NAMES) {
    if (lower.includes(token)) return abi;
  }
  return undefined;
}

function releaseStateFromGithub(base, release) {
  const tag = release.tag_name;
  const assets = Array.isArray(release.assets) ? release.assets : [];
  const checksums = assets.find((item) => item?.name === "SHA256SUMS.txt");
  return {
    ...base,
    release_state: "published",
    release_tag: tag,
    release_url:
      typeof release.html_url === "string" && release.html_url
        ? release.html_url
        : `https://github.com/xykal/XyDesk-Remote/releases/tag/${encodeURIComponent(tag)}`,
    published_at: release.published_at || base?.published_at || "",
    release_updated_at: release.published_at || base?.release_updated_at || "",
    // Dibangun ulang dari aset rilis ini. Entri downloads lama menunjuk tag
    // sebelumnya, dan currentReleaseAssetUrl menolak URL yang path-nya tidak
    // cocok dengan tag aktif.
    downloads: assets
      .filter((item) => item && typeof item.name === "string")
      .map((item) => ({ abi: abiFromAssetName(item.name), file: item.name, url: item.browser_download_url })),
    checksums_url:
      typeof checksums?.browser_download_url === "string"
        ? checksums.browser_download_url
        : base?.checksums_url,
    // version_code tidak tersedia di API rilis. Memakai angka milik tag lama
    // akan salah, jadi hanya dipakai kalau tag-nya memang sama.
    app_build: {
      version: String(tag).replace(/^v/i, ""),
      version_code:
        typeof base?.app_build?.version_code === "number" && base.release_tag === tag
          ? base.app_build.version_code
          : 0,
    },
  };
}

async function getReleaseMetadata(request, env) {
  const stateUrl = new URL("/release-state.json", request.url);
  const response = await env.ASSETS.fetch(new Request(stateUrl, { method: "GET" }));
  if (!response.ok) throw new Error("Release metadata unavailable");
  const base = await response.json();
  const release = await getLatestPublishedRelease(env);
  return release ? releaseStateFromGithub(base, release) : base;
}

function githubTokenCandidates(env) {
  return [...new Set([env.GH_TOKEN, env.GH_PAT].filter((token) => typeof token === "string" && token.trim()))];
}

async function fetchGithubWithTokenFallback(url, env, headers, options = {}, authScheme = "Bearer") {
  let lastError;
  for (const token of githubTokenCandidates(env)) {
    try {
      const response = await fetch(url, {
        ...options,
        headers: { ...headers, Authorization: `${authScheme} ${token}` },
      });
      if (response.ok || response.headers.get("location")) return response;
      try {
        await response.body?.cancel();
      } catch {
        // Continue to the other configured token or the public API.
      }
    } catch (error) {
      lastError = error;
    }
  }
  try {
    return await fetch(url, { ...options, headers });
  } catch (error) {
    throw lastError || error;
  }
}

async function currentReleaseAssetUrl(asset, request, env) {
  if (asset.repo !== "xykal/XyDesk-Remote") return null;
  try {
    const state = await getReleaseMetadata(request, env);
    const tag = String(state.release_tag || "");
    if (!/^v[0-9A-Za-z._-]{1,80}$/.test(tag)) return null;
    // Prefer the downloads entry for this ABI so alias redirects follow the
    // current release even when published asset file names change; fall back
    // to matching by file name for host-agent assets that keep stable names.
    const entries = Array.isArray(state.downloads) ? state.downloads : [];
    let entry = asset.abi
      ? entries.find((item) => item?.abi === asset.abi)
      : entries.find((item) => item?.file === asset.fileName);
    // Rilis lama (<= v1.1.8) tidak punya aset universal. Alih-alih 302 ke
    // berkas yang tidak ada (404 di GitHub), universal jatuh ke arm64-v8a —
    // APK yang benar untuk mayoritas perangkat modern.
    if (!entry && asset.abi === "universal") {
      entry = entries.find((item) => item?.abi === "arm64-v8a");
    }
    const fileName = String(entry?.file || asset.fileName || "");
    if (!/^[A-Za-z0-9._-]{1,160}$/.test(fileName)) return null;
    const expectedPath = `/xykal/XyDesk-Remote/releases/download/${encodeURIComponent(tag)}/${encodeURIComponent(fileName)}`;
    const configured = entry?.url;
    if (typeof configured === "string") {
      const parsed = new URL(configured);
      if (parsed.origin === "https://github.com" && parsed.pathname === expectedPath) return parsed.href;
    }
    return `https://github.com${expectedPath}`;
  } catch {
    return null;
  }
}

// Checksums follow the release recorded in release-state.json; the pinned
// RELEASE constant is only a last-resort fallback when metadata is broken.
async function checksumsResponse(request, env) {
  try {
    const state = await getReleaseMetadata(request, env);
    const tag = String(state.release_tag || "");
    if (/^v[0-9A-Za-z._-]{1,80}$/.test(tag) && typeof state.checksums_url === "string") {
      const expectedPath = `/xykal/XyDesk-Remote/releases/download/${encodeURIComponent(tag)}/SHA256SUMS.txt`;
      const parsed = new URL(state.checksums_url);
      if (parsed.origin === "https://github.com" && parsed.pathname === expectedPath) {
        return redirectResponse(parsed.href);
      }
    }
  } catch {
    // Fall through to the pinned fallback below.
  }
  return redirectResponse(PUBLIC_ASSETS["SHA256SUMS.txt"]);
}

async function healthResponse(request, env) {
  try {
    const state = await getReleaseMetadata(request, env);
    return jsonResponse({
      status: "operational",
      service: state.service || "xydesk-remote",
      app: "XyDesk Remote",
      vendor: state.brand || "XyVerse Technology Global",
      version: state.release_tag || "",
      target_version: state.release_tag || "",
      qaBuild: state.app_build?.version || "",
      current_build: state.app_build?.version || "",
      qaVersionCode: state.app_build?.version_code || 0,
      version_code: state.app_build?.version_code || 0,
      release_state: state.release_state || "unknown",
      release_url: state.release_url || "",
      checked_at: new Date().toISOString(),
    }, 200, request.method);
  } catch {
    return jsonResponse({ status: "degraded", service: "xydesk-remote", error: "release metadata unavailable" }, 503, request.method);
  }
}

// Android clients consume these legacy status field names.
// Keep that response shape while replacing the expired countdown with published status.
async function statusResponse(request, env) {
  try {
    const state = await getReleaseMetadata(request, env);
    return jsonResponse({
      app: "XyDesk Remote",
      vendor: state.brand || "XyVerse Technology Global",
      version: String(state.release_tag || "").replace(/^v/i, ""),
      qaBuild: state.app_build?.version || "",
      qaVersionCode: state.app_build?.version_code || 0,
      xydeskHostBuild: "6.11.1",
      status: state.release_state || "unknown",
      launchIso: state.published_at || "",
      launchWib: "Rilis publik tersedia di GitHub Releases",
      storePortal: state.rilisin?.url || "https://rilisin.xyverse.my.id/",
      releaseUrl: state.release_url || "",
    }, 200, request.method);
  } catch {
    return jsonResponse({ status: "degraded", error: "release metadata unavailable" }, 503, request.method);
  }
}

/**
 * Hitung pengunduh unik: hash(IP + User-Agent + file) disimpan sekali;
 * unduhan berulang dari sumber yang sama tidak menambah total. Statistik
 * tidak boleh pernah menghalangi unduhan, jadi semua kegagalan ditelan.
 *
 * KUOTA: sebelumnya memakai 2 KV write + 2 KV read per unduhan unik, dan itu
 * membuat namespace UNIQUE_DOWNLOADS menembus batas gratis 1.000 write/hari
 * (terukur 1.355/hari). Sekarang memakai D1 (XYDESK_DB) dengan batas
 * 100.000 rows written/hari. KV hanya dipakai sebagai fallback kalau
 * binding D1 tidak tersedia.
 */
const downloadStatsTableReady = new WeakMap();

function ensureDownloadStatsTable(d1) {
  const cached = downloadStatsTableReady.get(d1);
  if (cached) return cached;
  const ready = d1
    .prepare(
      `CREATE TABLE IF NOT EXISTS download_hits (
         hash TEXT PRIMARY KEY,
         file_name TEXT NOT NULL,
         first_seen INTEGER NOT NULL
       )`,
    )
    .run()
    .then(() => true)
    .catch(() => {
      downloadStatsTableReady.delete(d1);
      return false;
    });
  downloadStatsTableReady.set(d1, ready);
  return ready;
}

async function recordUniqueDownload(env, request, fileName) {
  try {
    const ip = String(request.headers.get("cf-connecting-ip") || "unknown").toLowerCase();
    const ua = String(request.headers.get("user-agent") || "unknown").toLowerCase();
    const digest = await crypto.subtle.digest(
      "SHA-256",
      new TextEncoder().encode(`${ip}|${ua}|${fileName}`),
    );
    const hex = [...new Uint8Array(digest)]
      .map((b) => b.toString(16).padStart(2, "0"))
      .join("");

    // Jalur utama: D1 (kuota jauh lebih lega, dan atomik via PRIMARY KEY).
    const d1 = env?.XYDESK_DB;
    if (d1 && typeof d1.prepare === "function" && (await ensureDownloadStatsTable(d1))) {
      try {
        await d1
          .prepare("INSERT OR IGNORE INTO download_hits (hash, file_name, first_seen) VALUES (?, ?, ?)")
          .bind(hex, String(fileName).slice(0, 160), Date.now())
          .run();
        return;
      } catch {
        // lanjut ke fallback KV
      }
    }

    // Fallback lama (KV) — tetap dipakai kalau D1 tidak ter-bind/bermasalah.
    const kv = env?.UNIQUE_DOWNLOADS;
    if (!kv) return;
    const key = `dl:${fileName}:${hex}`;
    if (await kv.get(key)) return;
    await kv.put(key, String(Date.now()), {
      expirationTtl: 60 * 60 * 24 * 365 * 5,
    });
    const current = Number(await kv.get("dl:total")) || 0;
    await kv.put("dl:total", String(current + 1));
  } catch {
    // Statistik belaka: kegagalan pencatatan tidak boleh memutus unduhan.
  }
}

/**
 * Total pengunduh unik. D1 kalau ada (COUNT ringan, tanpa KV write),
 * fallback ke counter KV lama.
 */
async function readUniqueDownloadTotal(env) {
  let d1Total = null;
  const d1 = env?.XYDESK_DB;
  if (d1 && typeof d1.prepare === "function" && (await ensureDownloadStatsTable(d1))) {
    try {
      const row = await d1.prepare("SELECT COUNT(*) AS total FROM download_hits").first();
      if (row && Number.isFinite(Number(row.total))) d1Total = Number(row.total);
    } catch {
      // lanjut ke bawah
    }
  }
  let kvTotal = null;
  try {
    const raw = await env?.UNIQUE_DOWNLOADS?.get("dl:total");
    if (raw != null) kvTotal = Number(raw) || 0;
  } catch {
    // lanjut ke bawah
  }
  // D1 mencatat unduhan sejak migrasi; KV menyimpan total historis sebelumnya.
  // Penulisan hanya memakai SATU jalur per kejadian, jadi periode keduanya
  // tidak tumpang tindih — total wajar = jumlah keduanya.
  if (d1Total == null && kvTotal == null) return null;
  return (d1Total || 0) + (kvTotal || 0);
}

/**
 * Statistik "online sekarang": heartbeat anonim (halaman web; aplikasi boleh
 * ikut mengirim) disimpan di D1 dengan jendela 24 jam. Statistik belaka —
 * kegagalan pencatatan tidak boleh memutus permintaan.
 */
async function ensurePresenceTable(d1) {
  try {
    await d1
      .prepare(
        "CREATE TABLE IF NOT EXISTS web_presence (id TEXT PRIMARY KEY, seen_at INTEGER NOT NULL)",
      )
      .run();
    return true;
  } catch {
    return false;
  }
}

// Jendela "online sekarang": id yang ber-detak dalam 5 menit terakhir
// (heartbeat app lama tiap ~8 detik; halaman web tiap 120 detik).
const PRESENCE_WINDOW_MS = 5 * 60 * 1000;
// Coalescing per isolate: satu id cukup menulis D1 maksimal 60 detik sekali —
// menulis lebih sering hanya membakar kuota tulis tanpa mengubah hitungan.
const PRESENCE_WRITE_COALESCE_MS = 60 * 1000;
const presenceWrites = new Map();
let presenceLastPruneAt = 0;

async function presenceUpsert(d1, id, now, remove = false) {
  try {
    if (remove) {
      await d1.prepare("DELETE FROM web_presence WHERE id = ?").bind(id).run();
      presenceWrites.delete(id);
      return;
    }
    const last = presenceWrites.get(id);
    if (last && now - last < PRESENCE_WRITE_COALESCE_MS) return;
    await d1
      .prepare(
        "INSERT INTO web_presence (id, seen_at) VALUES (?, ?) " +
          "ON CONFLICT(id) DO UPDATE SET seen_at = excluded.seen_at",
      )
      .bind(id, now)
      .run();
    presenceWrites.set(id, now);
    if (now - presenceLastPruneAt > 10 * 60 * 1000) {
      presenceLastPruneAt = now;
      await d1
        .prepare("DELETE FROM web_presence WHERE seen_at < ?")
        .bind(now - 24 * 60 * 60 * 1000)
        .run();
    }
  } catch {
    // statistik belaka
  }
}

async function presenceCount(d1, now) {
  try {
    const row = await d1
      .prepare("SELECT COUNT(*) AS n FROM web_presence WHERE seen_at > ?")
      .bind(now - PRESENCE_WINDOW_MS)
      .first();
    return Number(row?.n) || 0;
  } catch {
    return 0;
  }
}

/**
 * POST /api/session — heartbeat anonim gaya klien lama:
 * {"action": "heartbeat"|"end", "session_id": "<uuid>"}.
 */
async function sessionReportResponse(request, env) {
  const now = Date.now();
  const checkedAt = new Date(now).toISOString();
  const contentType = request.headers.get("content-type") || "";
  if (!contentType.includes("application/json")) {
    return jsonResponse({ status: "invalid_content_type" }, 415, request.method);
  }
  let body = null;
  try {
    const raw = await request.text();
    if (raw.length > 512) {
      return jsonResponse({ status: "payload_too_large" }, 413, request.method);
    }
    body = JSON.parse(raw);
  } catch {
    return jsonResponse({ status: "invalid_json" }, 400, request.method);
  }
  const sessionId = String(body?.session_id || "");
  const action = body?.action;
  if (
    !/^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/.test(sessionId) ||
    (action !== "heartbeat" && action !== "end")
  ) {
    return jsonResponse({ status: "invalid_session_report" }, 400, request.method);
  }
  const d1 = env?.XYDESK_DB;
  if (!d1 || typeof d1.prepare !== "function" || !(await ensurePresenceTable(d1))) {
    return jsonResponse(
      { status: "ok", active_session_count: 0, checked_at: checkedAt },
      200,
      request.method,
      { "cache-control": "no-store" },
    );
  }
  await presenceUpsert(d1, sessionId.toLowerCase(), now, action === "end");
  const count = await presenceCount(d1, now);
  return jsonResponse(
    { status: "ok", active_session_count: count, checked_at: checkedAt },
    200,
    request.method,
    { "cache-control": "no-store" },
  );
}

async function activeSessionsResponse(request, env) {
  if (request.method !== "GET" && request.method !== "HEAD" && request.method !== "POST") {
    return methodNotAllowed("GET, HEAD, POST");
  }
  const now = Date.now();
  const checkedAt = new Date(now).toISOString();
  const d1 = env?.XYDESK_DB;
  if (!d1 || typeof d1.prepare !== "function" || !(await ensurePresenceTable(d1))) {
    return jsonResponse(
      { status: "ok", active_session_count: 0, checked_at: checkedAt },
      200,
      request.method,
    );
  }
  // Id anonim: header X-Presence-Id (halaman) atau body {id} (klien lain).
  // Tidak ada id sama sekali -> identitas longgar dari IP + User-Agent agar
  // heartbeat lama tetap terhitung. Format salah diabaikan — angka tetap ada.
  let id = String(request.headers.get("X-Presence-Id") || "").trim();
  if (!id && request.method === "POST") {
    try {
      const body = await request.clone().json();
      id = String(body?.id || body?.session_id || "").trim();
    } catch {
      id = "";
    }
  }
  if (!id) {
    const ip =
      request.headers.get("cf-connecting-ip") ||
      request.headers.get("x-forwarded-for") ||
      "unknown";
    const ua = request.headers.get("user-agent") || "unknown";
    const digest = await crypto.subtle.digest(
      "SHA-256",
      new TextEncoder().encode(`${ip}|${ua}`),
    );
    id = [...new Uint8Array(digest)]
      .map((b) => b.toString(16).padStart(2, "0"))
      .join("")
      .slice(0, 32);
  }
  if (/^[A-Za-z0-9_-]{8,64}$/.test(id)) {
    await presenceUpsert(d1, id, now);
  }
  const count = await presenceCount(d1, now);
  return jsonResponse(
    { status: "ok", active_session_count: count, checked_at: checkedAt },
    200,
    request.method,
    { "cache-control": "public, max-age=15" },
  );
}

async function redirectLegacyAsset(asset, env, request) {
  if (!asset) {
    return new Response("Not Found", {
      status: 404,
      headers: { "content-type": "text/plain; charset=utf-8", "cache-control": "no-store" },
    });
  }

  // Current XyDesk Remote downloads already have a validated official URL in
  // release-state.json. Use it directly so public APK links do not depend on a
  // long-lived GitHub API token or its rate limit.
  if (asset.repo === "xykal/XyDesk-Remote" && String(asset.fileName || "").endsWith(".apk")) {
    await recordUniqueDownload(env, request, asset.fileName);
  }

  const currentUrl = await currentReleaseAssetUrl(asset, request, env);
  if (currentUrl) return redirectResponse(currentUrl);

  if (githubTokenCandidates(env).length === 0) {
    return new Response("Not Found", {
      status: 404,
      headers: { "content-type": "text/plain; charset=utf-8", "cache-control": "no-store" },
    });
  }

  const metadataHeaders = {
    Accept: "application/vnd.github+json",
    "User-Agent": "XyDesk-Remote-Worker",
  };
  const assetHeaders = {
    Accept: "application/octet-stream",
    "User-Agent": "XyDesk-Remote-Worker",
  };

  // For legacy repo aliases, try the configured token(s), then the public API.
  const assetIds = [];
  try {
    const releasesResponse = await fetchGithubWithTokenFallback(
      `https://api.github.com/repos/${asset.repo}/releases?per_page=10`,
      env,
      metadataHeaders,
      {},
      "token",
    );
    if (releasesResponse.ok) {
      const releases = await releasesResponse.json();
      if (Array.isArray(releases)) {
        for (const release of releases) {
          if (release.draft) continue;
          const found = (release.assets || []).find((candidate) => candidate.name === asset.fileName);
          if (found && Number.isSafeInteger(found.id)) assetIds.push(found.id);
        }
      }
    }
  } catch {
    // Use the saved asset ID if GitHub release metadata is temporarily unavailable.
  }
  if (Number.isSafeInteger(asset.id)) assetIds.push(asset.id);

  for (const id of [...new Set(assetIds)]) {
    try {
      const response = await fetchGithubWithTokenFallback(
        `https://api.github.com/repos/${asset.repo}/releases/assets/${id}`,
        env,
        assetHeaders,
        { redirect: "manual" },
        "token",
      );
      const location = response.headers.get("location");
      if (location) return redirectResponse(location);
    } catch {
      // Try the next candidate ID.
    }
  }

  return new Response("Asset stream error", {
    status: 502,
    headers: { "content-type": "text/plain; charset=utf-8", "cache-control": "no-store" },
  });
}

async function serveScriptAsset(request, env, fileName, downloadName, contentType) {
  const assetUrl = new URL(`/${fileName}`, request.url);
  const assetRequest = new Request(assetUrl, { method: request.method, headers: request.headers });
  const response = await env.ASSETS.fetch(assetRequest);
  if (!response.ok) return response;
  const headers = new Headers(response.headers);
  headers.set("content-type", contentType);
  headers.set("content-disposition", `attachment; filename="${downloadName}"`);
  headers.set("x-content-type-options", "nosniff");
  return new Response(request.method === "HEAD" ? null : response.body, {
    status: response.status,
    statusText: response.statusText,
    headers,
  });
}

async function downloadStatsResponse(request, env) {
  const cache = globalThis.caches?.default;
  const cacheKey = new Request(new URL("/api/stats", request.url), { method: "GET" });
  if (cache) {
    try {
      const cached = await cache.match(cacheKey);
      if (cached) {
        return request.method === "HEAD"
          ? new Response(null, { status: cached.status, headers: cached.headers })
          : cached;
      }
    } catch {
      // Cache is an optimization; a cache outage must not hide public release stats.
    }
  }

  const headers = {
    Accept: "application/vnd.github+json",
    "User-Agent": "XyDesk-Remote-Web-Stats",
    "X-GitHub-Api-Version": "2022-11-28",
  };

  try {
    const releases = [];
    let page = 1;
    for (; page <= 20; page += 1) {
      const releasesUrl = `https://api.github.com/repos/xykal/XyDesk-Remote/releases?per_page=100&page=${page}`;
      const githubResponse = await fetchGithubWithTokenFallback(releasesUrl, env, headers);
      if (!githubResponse.ok) throw new Error(`GitHub API returned ${githubResponse.status}`);
      const items = await githubResponse.json();
      if (!Array.isArray(items)) throw new Error("Invalid GitHub releases response");
      releases.push(...items.filter((release) => !release.draft));
      if (items.length < 100) break;
    }
    if (page > 20) throw new Error("Release list exceeds the supported page limit");

    const apkAssets = releases.flatMap((release) =>
      (release.assets || [])
        .filter((asset) => typeof asset.name === "string" && asset.name.toLowerCase().endsWith(".apk"))
        .map((asset) => ({
          tag: String(release.tag_name || "").slice(0, 100),
          name: asset.name.slice(0, 160),
          downloads: Math.max(0, Number(asset.download_count) || 0),
        })),
    );
    const releaseTags = new Set(apkAssets.map((asset) => asset.tag).filter(Boolean));
    const uniqueApkDownloaders = await readUniqueDownloadTotal(env);
    const body = {
      unique_apk_downloaders: uniqueApkDownloaders,
      status: "ok",
      scope: "available-public-releases",
      total_apk_downloads: apkAssets.reduce((sum, asset) => sum + asset.downloads, 0),
      apk_asset_count: apkAssets.length,
      release_count: releaseTags.size,
      source: "GitHub Releases API",
      note: "unique_apk_downloaders = pengunduh unik pertama (unduhan ulang tidak dihitung); total_apk_downloads = unduhan mentah GitHub untuk rilis yang masih tersedia.",
      updated_at: new Date().toISOString(),
    };
    const response = jsonResponse(body, 200, "GET", {
      "cache-control": "public, max-age=300, s-maxage=900",
    });
    if (cache) {
      try {
        await cache.put(cacheKey, response.clone());
      } catch {
        // Ignore cache write failures; the response is still valid.
      }
    }
    return request.method === "HEAD"
      ? new Response(null, { status: response.status, headers: response.headers })
      : response;
  } catch {
    return jsonResponse(
      { status: "unavailable", error: "Unduhan belum dapat dimuat dari GitHub." },
      503,
      request.method,
      { "cache-control": "no-store" },
    );
  }
}

function durableObjectJson(data, status = 200, requestMethod = "GET") {
  return new Response(requestMethod === "HEAD" ? null : JSON.stringify(data), {
    status,
    headers: {
      "access-control-allow-origin": "*",
      "cache-control": "no-store",
      "content-type": "application/json; charset=utf-8",
      "x-content-type-options": "nosniff",
    },
  });
}

async function readRequestTextLimited(request, maxBytes) {
  if (!request.body) return { text: "" };
  const reader = request.body.getReader();
  const decoder = new TextDecoder();
  let byteLength = 0;
  let text = "";
  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      byteLength += value.byteLength;
      if (byteLength > maxBytes) {
        try {
          await reader.cancel();
        } catch {
          // The request is already over the limit; cancellation is best-effort.
        }
        return { tooLarge: true };
      }
      text += decoder.decode(value, { stream: true });
    }
    text += decoder.decode();
    return { text };
  } catch {
    return { invalid: true };
  } finally {
    try {
      reader.releaseLock();
    } catch {
      // A failed/cancelled stream may already have released its reader.
    }
  }
}

function randomBase64Url(byteLength) {
  const bytes = crypto.getRandomValues(new Uint8Array(byteLength));
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

async function pcSignalSecretHash(secret) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(secret));
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
}
// Cloud sync setelan & kontrol kustom (permintaan pemilik sejak 2026-10,
// dieksekusi 2026-10-13). Blob JSON disetel aplikasi; TIDAK boleh memuat
// kredensial (kata sandi/token disaring di sisi app). Disimpan di KV dengan
// kunci per ID akun anonim; kode pemulihan memulihkan ID yang sama sehingga
// pasang ulang + restore = setelan kembali.
const ACCOUNT_SETTINGS_MAX_BYTES = 262_144;

// ---------------------------------------------------------------------------
// Relay direktori ID PC global (Tahap ② "Koneksi PC nyata"):
//  - Host agent POST heartbeat tiap 60 detik: {id: "XY"+8 hex, hostname,
//    rdp_port, tailnet?}. Rekaman KV berumur 300 detik (TTL) sehingga PC yang
//    mati otomatis hilang dari direktori.
//  - HP GET ?id=XY… → kandidat alamat (IP publik hasil CF-Connecting-IP +
//    tailnet opsional). IP privat/CGNAT tidak ditawarkan sebagai kandidat.
//  - Resolve dibatasi 30/menit per IP sumber: ID hanya 32-bit, tanpa batas
//    laju seluruh ruang ID bisa dipetakan penyerang.
//  - Catatan jujur: KV eventually-consistent — registrasi PERTAMA sebuah PC
//    bisa butuh beberapa detik sampai terlihat di semua edge; heartbeat
//    berikutnya membuat rekaman tersebar normal.
// ---------------------------------------------------------------------------
const PC_SIGNAL_NEXT_POLL_IDLE_MS = 3_000;
const PC_SIGNAL_NEXT_POLL_BUSY_MS = 500;

const PC_RELAY_ID_RE = /^XY[0-9A-F]{8}$/;
const PC_RELAY_TTL_SECONDS = 300;
const PC_RELAY_RESOLVE_LIMIT_PER_MINUTE = 30;

function isPrivateAddress(ip) {
  if (typeof ip !== "string") return true;
  if (ip.includes(":")) return false; // IPv4 saja yang difilter di sini
  const parts = ip.split(".").map((p) => Number(p));
  if (parts.length !== 4 || parts.some((p) => !Number.isInteger(p) || p < 0 || p > 255)) return true;
  const [a, b] = parts;
  return a === 10 || a === 127 || (a === 192 && b === 168) || (a === 172 && b >= 16 && b <= 31) ||
    (a === 169 && b === 254) || (a === 100 && b >= 64 && b <= 127);
}

async function pcRelayResponse(request, env) {
  const kv = env.UNIQUE_DOWNLOADS;
  if (!kv) return jsonResponse({ status: "unavailable" }, 503, request.method);
  const url = new URL(request.url);

  if (request.method === "GET" || request.method === "HEAD") {
    const id = (url.searchParams.get("id") || "").toUpperCase();
    if (!PC_RELAY_ID_RE.test(id)) return jsonResponse({ status: "invalid_id" }, 400, request.method);
    const ip = String(request.headers.get("cf-connecting-ip") || "unknown");
    const ipHash = await pcSignalSecretHash(`xydesk-pc-relay-ip:${ip}`);
    const minute = Math.floor(Date.now() / 60000);
    const rlKey = `xy-pc-relay-rl:${ipHash}:${minute}`;
    const seen = Number(await kv.get(rlKey)) || 0;
    if (seen >= PC_RELAY_RESOLVE_LIMIT_PER_MINUTE) {
      return jsonResponse({ status: "rate_limited" }, 429, request.method);
    }
    await kv.put(rlKey, String(seen + 1), { expirationTtl: 120 });
    // cacheTtl kecil: KV default meng-cache hasil (termasuk miss) 60 detik di
    // edge; registrasi perdana PC harus terlihat secepat mungkin oleh HP.
    const raw = await kv.get(`xy-pc-relay:${id}`, { cacheTtl: 10 });
    if (raw === null) return jsonResponse({ status: "not_found" }, 404, request.method);
    let rec;
    try {
      rec = JSON.parse(raw);
    } catch {
      return jsonResponse({ status: "not_found" }, 404, request.method);
    }
    const candidates = [];
    if (rec.public_ip && !isPrivateAddress(rec.public_ip)) {
      candidates.push({ host: rec.public_ip, port: rec.rdp_port, kind: "public" });
    }
    if (rec.tailnet) candidates.push({ host: rec.tailnet, port: rec.rdp_port, kind: "tailnet" });
    if (rec.lan_ip) candidates.push({ host: rec.lan_ip, port: rec.rdp_port, kind: "lan" });
    return jsonResponse(
      { status: "ok", id, hostname: rec.hostname || "", candidates, updated_at: rec.updated_at },
      200,
      request.method,
      { "cache-control": "no-store" },
    );
  }

  if (request.method === "POST") {
    if (!isJsonContentType(request.headers.get("content-type"))) {
      return jsonResponse({ status: "invalid_content_type" }, 415, request.method);
    }
    const bounded = await readRequestTextLimited(request, 1024);
    if (bounded.tooLarge) return jsonResponse({ status: "payload_too_large" }, 413, request.method);
    if (bounded.invalid) return jsonResponse({ status: "invalid_request" }, 400, request.method);
    let body;
    try {
      body = JSON.parse(bounded.text);
    } catch {
      return jsonResponse({ status: "invalid_json" }, 400, request.method);
    }
    const id = String(body?.id || "").toUpperCase();
    if (!PC_RELAY_ID_RE.test(id)) return jsonResponse({ status: "invalid_id" }, 400, request.method);
    const hostname = typeof body?.hostname === "string" ? body.hostname.slice(0, 128) : "";
    const rdpPort = Number(body?.rdp_port ?? body?.port);
    if (!Number.isInteger(rdpPort) || rdpPort < 1 || rdpPort > 65535) {
      return jsonResponse({ status: "invalid_port" }, 400, request.method);
    }
    const tailnet = typeof body?.tailnet === "string" ? body.tailnet.trim().toLowerCase() : "";
    if (tailnet.length > 253 || !/^[a-z0-9.-]*$/.test(tailnet)) {
      return jsonResponse({ status: "invalid_tailnet" }, 400, request.method);
    }
    const lanIp = typeof body?.lan_ip === "string" && /^[0-9.]{7,15}$/.test(body.lan_ip.trim()) ? body.lan_ip.trim() : null;
    const key = `xy-pc-relay:${id}`;
    const prevRaw = await kv.get(key);
    if (prevRaw !== null) {
      try {
        const prev = JSON.parse(prevRaw);
        // Hemat kuota tulis KV: heartbeat < 20 detik dari yang terakhir di-skip.
        if (Date.now() - Date.parse(prev.updated_at || "") < 20000) {
          return jsonResponse(
            { status: "ok", updated_at: prev.updated_at, throttled: true },
            200,
            request.method,
            { "cache-control": "no-store" },
          );
        }
      } catch {
        // rekaman rusak — timpa saja
      }
    }
    const updatedAt = new Date().toISOString();
    await kv.put(
      key,
      JSON.stringify({
        hostname,
        rdp_port: rdpPort,
        public_ip: String(request.headers.get("cf-connecting-ip") || ""),
        tailnet: tailnet || null,
        lan_ip: lanIp,
        updated_at: updatedAt,
      }),
      { expirationTtl: PC_RELAY_TTL_SECONDS },
    );
    return jsonResponse({ status: "ok", updated_at: updatedAt }, 200, request.method, { "cache-control": "no-store" });
  }

  return methodNotAllowed("GET, POST");
}

async function accountSettingsResponse(request, env) {
  const kv = env.UNIQUE_DOWNLOADS;
  if (!kv) return jsonResponse({ status: "unavailable" }, 503, request.method);
  const url = new URL(request.url);

  if (request.method === "GET" || request.method === "HEAD") {
    const id = url.searchParams.get("id") || "";
    if (!COMMUNITY_CLIENT_ID_RE.test(id)) {
      return jsonResponse({ status: "invalid_id" }, 400, request.method);
    }
    const blob = await kv.get(`xy-settings:${id}`);
    if (blob === null) return jsonResponse({ status: "not_found" }, 404, request.method);
    const updatedAt = (await kv.get(`xy-settings-meta:${id}`)) || "";
    return jsonResponse(
      { status: "ok", blob, updated_at: updatedAt },
      200,
      request.method,
      { "cache-control": "no-store" },
    );
  }

  if (request.method === "PUT" || request.method === "POST") {
    if (!isJsonContentType(request.headers.get("content-type"))) {
      return jsonResponse({ status: "invalid_content_type" }, 415, request.method);
    }
    const length = Number(request.headers.get("content-length") || 0);
    if (length > ACCOUNT_SETTINGS_MAX_BYTES) {
      return jsonResponse({ status: "payload_too_large" }, 413, request.method);
    }
    const bounded = await readRequestTextLimited(request, ACCOUNT_SETTINGS_MAX_BYTES);
    if (bounded.tooLarge) return jsonResponse({ status: "payload_too_large" }, 413, request.method);
    if (bounded.invalid) return jsonResponse({ status: "invalid_request" }, 400, request.method);
    let body;
    try {
      body = JSON.parse(bounded.text);
    } catch {
      return jsonResponse({ status: "invalid_json" }, 400, request.method);
    }
    const id = String(body?.id || "");
    if (!COMMUNITY_CLIENT_ID_RE.test(id)) {
      return jsonResponse({ status: "invalid_id" }, 400, request.method);
    }
    const blob = typeof body?.blob === "string" ? body.blob : null;
    if (blob === null || blob.length === 0 || blob.length > ACCOUNT_SETTINGS_MAX_BYTES) {
      return jsonResponse({ status: "invalid_blob" }, 400, request.method);
    }
    const updatedAt = new Date().toISOString();
    await kv.put(`xy-settings:${id}`, blob);
    await kv.put(`xy-settings-meta:${id}`, updatedAt);
    return jsonResponse({ status: "ok", updated_at: updatedAt }, 200, request.method, { "cache-control": "no-store" });
  }

  return methodNotAllowed("GET, PUT, POST");
}

// Pemeriksaan pembaruan untuk app: tag rilis publik terbaru + ringkasan
// changelog + dukungan sistem OTA "Download Data" untuk update ringan.
async function updateCheckResponse(request, env) {
  try {
    const release = await getLatestPublishedRelease(env);
    if (!release) return jsonResponse({ status: "unavailable" }, 503, request.method);
    const tag = String(release.tag_name || "");
    if (!RELEASE_TAG_PATTERN.test(tag)) {
      return jsonResponse({ status: "unavailable" }, 503, request.method);
    }
    const cleanVersion = tag.replace(/^v/i, "");
    const body = String(release.body || "");
    const sections = body.split(/\n## |^## /);
    const latest = sections.find((part) => /^v?\d+\.\d+/.test(part.trim())) || sections[0] || "";
    const excerpt = latest.trim().slice(0, 2000);
    return jsonResponse(
      {
        status: "ok",
        tag,
        version: cleanVersion,
        data_pack_version: LIVE_DATA_PACK_VERSION,
        min_apk_version: cleanVersion,
        apk_64bit_url: "https://api.xydeskremote.biz.id/XyDesk-Remote64bit.apk",
        apk_32bit_url: "https://api.xydeskremote.biz.id/XyDesk-Remote32bit.apk",
        apk_universal_url: "https://api.xydeskremote.biz.id/XyDesk-RemoteUniversal.apk",
        data_pack_url: "https://api.xydeskremote.biz.id/api/data-pack",
        url:
          typeof release.html_url === "string" && release.html_url
            ? release.html_url
            : `https://github.com/xykal/XyDesk-Remote/releases/tag/${encodeURIComponent(tag)}`,
        published_at: release.published_at || "",
        changelog: excerpt,
      },
      200,
      request.method,
    );
  } catch {
    return jsonResponse({ status: "unavailable" }, 503, request.method);
  }
}

async function dataPackResponse(request, env) {
  try {
    const release = await getLatestPublishedRelease(env).catch(() => null);
    const tag = String(release?.tag_name || "v1.1.20");
    const cleanVersion = tag.replace(/^v/i, "");
    const payload = {
      status: "ok",
      data_version: LIVE_DATA_PACK_VERSION,
      apk_version: cleanVersion,
      min_apk_version: cleanVersion,
      updated_at: new Date().toISOString(),
      e2ee_protocol: "XY-E2EE-GCM-v1",
      rdp_runners: [
        { id: "windows-2022", label: "Windows Server 2022 (Stabil · Desktop & Studio 3D)", family: "windows" },
        { id: "windows-2025", label: "Windows Server 2025 (Kernel Terbaru)", family: "windows" },
        { id: "windows-latest", label: "Windows Latest (Default GitHub)", family: "windows" },
        { id: "ubuntu-24.04", label: "Ubuntu 24.04 LTS Desktop (XFCE4 + XRDP :3389)", family: "linux" },
        { id: "ubuntu-22.04", label: "Ubuntu 22.04 LTS Desktop (XFCE4 LTS Klasik)", family: "linux" },
        { id: "ubuntu-latest", label: "Ubuntu Latest Desktop (Otomatis Terbaru)", family: "linux" },
      ],
      stickers: [
        { code: "xy_gg", name: "XyDesk GG!", res: "sticker_xy_gg" },
        { code: "xy_laugh", name: "Ngakak Gaming", res: "sticker_xy_laugh" },
        { code: "xy_fire", name: "Gaskeun On Fire!", res: "sticker_xy_fire" },
        { code: "xy_chill", name: "Santai Ngopi", res: "sticker_xy_chill" },
        { code: "xy_sad", name: "Ping 999ms", res: "sticker_xy_sad" },
        { code: "xy_king", name: "Sovereign Boss", res: "sticker_xy_king" },
      ],
      bubble_anim_config: {
        version: "2026.10.18.63",
        default_preset: "SPARKLES",
        founder_preset: "CRYSTAL",
        speed_multiplier: 1.35,
        intensity: 1.6,
        aurora_waves: true,
        shooting_stars: true,
        neon_border_sweep: true,
        founder_colors: ["#14052B", "#2E1065", "#581C87", "#7E22CE", "#A855F7", "#E879F9"],
        accent_color: "#D8B4FE",
        secondary_color: "#A855F7",
      },
      special_roles: {
        xykal: "FOUNDER",
        xyverse: "XYTEAM",
        xyteam: "XYTEAM",
        projectkal: "XYTEAM",
        xytester: "TESTER",
        betatester: "TESTER",
      },
      changelog_summary:
        "Paket Data OTA XyDesk 2026.10.18.62: Upgrade Animasi Bubble Aurora Plasma + Bintang Jatuh Meteor + Kristal Amethyst 3D (Server-Driven OTA tanpa perlu update APK), Badge Khusus XYTEAM & TESTER, Link Biru Bisa Diklik, dan Notifikasi Pesan Gaya WhatsApp.",
    };
    return jsonResponse(payload, 200, request.method);
  } catch {
    return jsonResponse({ status: "unavailable" }, 503, request.method);
  }
}

// --- Lapisan anti-penyalahgunaan (DDoS/flood) tingkat worker -------------
// Jendela tetap per IP per kelompok endpoint. Batas sengaja longgar untuk
// pemakaian massal yang sah (banyak user di balik satu NAT), tapi memangkas
// flood otomatis ribuan req/detik menjadi maks ratusan per menit per IP.
const API_ABUSE_TIERS = [
  ["/api/push-update", 5],
  ["/api/ui/translate", 30],
  ["/api/og", 60],
];
const API_ABUSE_DEFAULT_PER_MIN = 3000;
const API_ABUSE_WINDOW_MS = 60_000;
const apiAbuseBuckets = new Map();

/** Kembalikan detik Retry-After bila IP melampaui batas, selain itu null. */
function apiAbuseLimit(path, ip) {
  const tier = API_ABUSE_TIERS.find(([prefix]) => path.startsWith(prefix));
  const limit = tier ? tier[1] : API_ABUSE_DEFAULT_PER_MIN;
  const bucketKey = `${tier ? tier[0] : "/api/"}|${ip}`;
  const now = Date.now();
  let bucket = apiAbuseBuckets.get(bucketKey);
  if (!bucket || now - bucket.windowAt > API_ABUSE_WINDOW_MS) {
    bucket = { windowAt: now, count: 0 };
    apiAbuseBuckets.set(bucketKey, bucket);
  }
  bucket.count += 1;
  if (apiAbuseBuckets.size > 5000) {
    for (const [key, value] of apiAbuseBuckets) {
      if (now - value.windowAt > API_ABUSE_WINDOW_MS) apiAbuseBuckets.delete(key);
    }
  }
  if (bucket.count > limit) {
    return Math.max(1, Math.ceil((bucket.windowAt + API_ABUSE_WINDOW_MS - now) / 1000));
  }
  return null;
}

// --- Terjemahan UI massal (batch) ---------------------------------------
// APK mengirim daftar string EN sekaligus; hasil diterjemahkan per item
// memakai helper Google yang sama dengan chat, dicache di memori worker
// per target bahasa supaya string UI yang sama tidak diterjemahkan ulang.
const UI_TRANSLATE_TARGETS = new Set([
  "ms", "es", "pt", "de", "fr", "ru", "ar", "hi", "ja", "ko",
  "zh-CN", "vi", "th", "tr", "it", "nl", "pl", "uk",
]);
const uiTranslateCache = new Map();

async function uiTranslateResponse(request) {
  let body;
  try {
    body = await request.json();
  } catch {
    return jsonResponse({ status: "error", code: "invalid_json" }, 400, request.method);
  }
  const target = String(body?.target || "").trim();
  if (!UI_TRANSLATE_TARGETS.has(target)) {
    return jsonResponse({ status: "error", code: "unsupported_target" }, 400, request.method);
  }
  const rawItems = Array.isArray(body?.items) ? body.items : [];
  const items = rawItems.slice(0, 40).map((v) => String(v || "").slice(0, 240));
  const out = new Array(items.length).fill("");
  const pending = [];
  items.forEach((text, index) => {
    if (!text.trim()) return;
    const key = target + "|" + text;
    const hit = uiTranslateCache.get(key);
    if (hit) out[index] = hit;
    else pending.push([index, text, key]);
  });
  await Promise.all(pending.map(async ([index, text, key]) => {
    const got = await fetchGoogleTranslate(text, "en", target).catch(() => null)
      || await fetchGoogleClients5Translate(text, "en", target).catch(() => null);
    const value = got && got.translated ? String(got.translated).trim() : "";
    if (value) {
      if (uiTranslateCache.size > 4000) uiTranslateCache.clear();
      uiTranslateCache.set(key, value);
      out[index] = value;
    }
  }));
  return jsonResponse({ status: "ok", target, items: out }, 200, request.method);
}

// --- Relay push untuk chat mode Firebase --------------------------------
// Pesan mode Firebase ditulis klien langsung ke Firestore, jadi jalur
// Durable Object tidak melihatnya. Klien memanggil endpoint ini sekali
// setelah kirim berhasil; identitas pengirim WAJIB dari verifikasi
// GitHub PAT (body tidak dipercaya), lalu push OneSignal disiarkan
// dengan format data yang sama seperti pushChatNotification di DO.

const FEEDBACK_TYPES = new Set(["bug", "idea", "question", "other"]);
const FEEDBACK_STATUSES = new Set(["open", "progress", "done"]);
const FEEDBACK_TEXT_MAX_CHARS = 2000;
const FEEDBACK_REPLY_MAX_CHARS = 1000;
const FEEDBACK_NAME_MAX_CHARS = 30;
const FEEDBACK_MAX_ITEMS = 400;
const FEEDBACK_MAX_REPLIES = 60;
const FEEDBACK_SUBMIT_INTERVAL_MS = 30_000;
const FEEDBACK_REPLY_INTERVAL_MS = 10_000;

const LIVE_DATA_PACK_VERSION = "2026.10.20.66";
const EDGE_KV_STORES = new Map();
const EDGE_KV_WRAP_CACHE = new WeakMap();
const EDGE_KV_MEMORY_FALLBACK = new Map();
const EDGE_KV_LAST_RAW_PUT = new Map();

function bytesToBase64Fast(bytes) {
  const u8 = bytes instanceof Uint8Array
    ? bytes
    : bytes instanceof ArrayBuffer
      ? new Uint8Array(bytes)
      : ArrayBuffer.isView(bytes)
        ? new Uint8Array(bytes.buffer, bytes.byteOffset, bytes.byteLength)
        : new TextEncoder().encode(String(bytes || ""));
  let binary = "";
  const chunk = 0x8000;
  for (let i = 0; i < u8.length; i += chunk) {
    binary += String.fromCharCode.apply(null, u8.subarray(i, i + chunk));
  }
  return btoa(binary);
}

function base64ToArrayBufferFast(b64) {
  const bin = atob(b64);
  const u8 = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) {
    u8[i] = bin.charCodeAt(i);
  }
  return u8.buffer;
}

function wrapKvWithEdgeCache(rawKv, d1Db = null) {
  if (!rawKv || typeof rawKv.get !== "function" || (!globalThis.caches?.default && !d1Db)) {
    return rawKv;
  }
  const existing = EDGE_KV_WRAP_CACHE.get(rawKv);
  if (existing && (!d1Db || existing._d1Db === d1Db)) return existing;

  const cache = globalThis.caches?.default || null;
  const cacheReqFor = (key) => new Request(`https://api.xydeskremote.biz.id/__kv_cache__/v1/${encodeURIComponent(String(key))}`, { method: "GET" });

  const parseBodyAs = async (res, type) => {
    if (type === "arrayBuffer") return await res.arrayBuffer();
    if (type === "json") return await res.json().catch(() => null);
    return await res.text();
  };

  const decodeD1Value = (rawStr, type) => {
    if (typeof rawStr !== "string") return null;
    if (rawStr.startsWith("__b64__:")) {
      const buf = base64ToArrayBufferFast(rawStr.slice(8));
      if (type === "arrayBuffer") return buf;
      const txt = new TextDecoder().decode(buf);
      if (type === "json") {
        try { return JSON.parse(txt); } catch { return null; }
      }
      return txt;
    }
    if (type === "arrayBuffer") return new TextEncoder().encode(rawStr).buffer;
    if (type === "json") {
      try { return JSON.parse(rawStr); } catch { return null; }
    }
    return rawStr;
  };

  const wrapped = {
    _d1Db: d1Db,
    async get(key, options) {
      const k = String(key);
      const type = typeof options === "string" ? options : options?.type || "text";
      if (d1Db && !k.startsWith("dl:")) {
        try {
          const row = await d1Db.prepare("SELECT v FROM xydesk_kv_store WHERE k = ?1").bind(k).first();
          if (row && typeof row.v === "string") {
            return decodeD1Value(row.v, type);
          }
          if (k.startsWith("do_fallback:") || k.startsWith("oauth:") || k === "chat:prod_backup_v1") {
            return null;
          }
        } catch {}
      }
      if (cache) {
        try {
          const hit = await cache.match(cacheReqFor(k));
          if (hit && hit.status === 200) {
            return await parseBodyAs(hit.clone(), type);
          }
        } catch {}
      }
      const mem = EDGE_KV_MEMORY_FALLBACK.get(k);
      if (mem && (!mem.expiresAt || Date.now() < mem.expiresAt)) {
        if (type === "arrayBuffer") {
          if (mem.value instanceof ArrayBuffer) return mem.value;
          if (ArrayBuffer.isView(mem.value)) return mem.value.buffer.slice(mem.value.byteOffset, mem.value.byteOffset + mem.value.byteLength);
          return new TextEncoder().encode(String(mem.value)).buffer;
        }
        if (type === "json") {
          try { return JSON.parse(typeof mem.value === "string" ? mem.value : new TextDecoder().decode(mem.value)); } catch { return null; }
        }
        return typeof mem.value === "string" ? mem.value : new TextDecoder().decode(mem.value);
      }
      try {
        const val = await rawKv.get(k, options);
        if (val !== null && val !== undefined) {
          const body = type === "json" ? JSON.stringify(val) : val;
          EDGE_KV_MEMORY_FALLBACK.set(k, { value: body, metadata: null, expiresAt: 0 });
          if (cache) {
            await cache.put(
              cacheReqFor(k),
              new Response(body, {
                status: 200,
                headers: {
                  "Content-Type": "application/octet-stream",
                  "Cache-Control": "public, max-age=604800",
                },
              }),
            ).catch(() => {});
          }
        }
        return val;
      } catch {
        return null;
      }
    },

    async getWithMetadata(key, options) {
      const k = String(key);
      const type = typeof options === "string" ? options : options?.type || "text";
      if (d1Db && !k.startsWith("dl:")) {
        try {
          const row = await d1Db.prepare("SELECT v, meta FROM xydesk_kv_store WHERE k = ?1").bind(k).first();
          if (row && typeof row.v === "string") {
            let metadata = null;
            if (row.meta) {
              try { metadata = JSON.parse(row.meta); } catch {}
            }
            return { value: decodeD1Value(row.v, type), metadata };
          }
        } catch {}
      }
      if (cache) {
        try {
          const hit = await cache.match(cacheReqFor(k));
          if (hit && hit.status === 200) {
            const metaRaw = hit.headers.get("X-KV-Meta");
            let metadata = null;
            if (metaRaw) {
              try { metadata = JSON.parse(metaRaw); } catch {}
            }
            const value = await parseBodyAs(hit.clone(), type);
            return { value, metadata };
          }
        } catch {}
      }
      const mem = EDGE_KV_MEMORY_FALLBACK.get(k);
      if (mem && (!mem.expiresAt || Date.now() < mem.expiresAt)) {
        let value = mem.value;
        if (type === "arrayBuffer") {
          if (value instanceof ArrayBuffer) {
            // ok
          } else if (ArrayBuffer.isView(value)) {
            value = value.buffer.slice(value.byteOffset, value.byteOffset + value.byteLength);
          } else {
            value = new TextEncoder().encode(String(value)).buffer;
          }
        }
        return { value, metadata: mem.metadata || null };
      }
      try {
        if (typeof rawKv.getWithMetadata === "function") {
          return await rawKv.getWithMetadata(k, options);
        }
        const value = await rawKv.get(k, options);
        return { value, metadata: null };
      } catch {
        return { value: null, metadata: null };
      }
    },

    async put(key, value, options) {
      const k = String(key);
      const ttl = Math.max(60, Number(options?.expirationTtl) || 604800);
      const metadata = options?.metadata || null;
      let storeVal = value;
      if (ArrayBuffer.isView(value)) {
        storeVal = value.buffer.slice(value.byteOffset, value.byteOffset + value.byteLength);
      }
      EDGE_KV_MEMORY_FALLBACK.set(k, {
        value: storeVal,
        metadata,
        expiresAt: options?.expirationTtl ? Date.now() + ttl * 1000 : 0,
      });
      if (d1Db && !k.startsWith("dl:")) {
        try {
          const d1Val = (storeVal instanceof ArrayBuffer || ArrayBuffer.isView(storeVal))
            ? `__b64__:${bytesToBase64Fast(storeVal)}`
            : String(storeVal ?? "");
          const metaStr = metadata ? JSON.stringify(metadata) : null;
          await d1Db
            .prepare("INSERT OR REPLACE INTO xydesk_kv_store (k, v, meta, updated_at) VALUES (?1, ?2, ?3, ?4)")
            .bind(k, d1Val, metaStr, Date.now())
            .run();
        } catch {}
      }
      if (cache) {
        try {
          const headers = {
            "Content-Type": "application/octet-stream",
            "Cache-Control": `public, max-age=${ttl}`,
          };
          if (metadata) headers["X-KV-Meta"] = JSON.stringify(metadata);
          await cache.put(cacheReqFor(k), new Response(storeVal, { status: 200, headers }));
        } catch {}
      }
      // Jangan habiskan kuota 1.000 KV Write/hari untuk counter download dl:*
      if (k.startsWith("dl:")) return;
      // Throttle penulisan rawKv untuk key frekuensi tinggi (maks 1x per 60 detik)
      if (k.startsWith("do_fallback:")) {
        const now = Date.now();
        const lastPut = EDGE_KV_LAST_RAW_PUT.get(k) || 0;
        if (now - lastPut < 60_000) return;
        EDGE_KV_LAST_RAW_PUT.set(k, now);
      }
      try {
        await rawKv.put(k, value, options);
      } catch {}
    },

    async delete(key) {
      const k = String(key);
      EDGE_KV_MEMORY_FALLBACK.delete(k);
      if (d1Db && !k.startsWith("dl:")) {
        try {
          await d1Db.prepare("DELETE FROM xydesk_kv_store WHERE k = ?1").bind(k).run();
        } catch {}
      }
      if (cache) {
        try { await cache.delete(cacheReqFor(k)); } catch {}
      }
      try {
        if (typeof rawKv.delete === "function") {
          await rawKv.delete(k);
        }
      } catch {}
    },
  };

  EDGE_KV_WRAP_CACHE.set(rawKv, wrapped);
  return wrapped;
}

let EDGE_FALLBACK_JOKES_INSTANCE = null;
let EDGE_FALLBACK_FEEDBACK_INSTANCE = null;
const DEFAULT_ONESIGNAL_APP_ID = "e3d5adea-1c0f-4986-9ce5-e088d65f0998";

/** Header keamanan standar untuk semua respons statis/web. */
function withSecurityHeaders(res) {
  const out = new Response(res.body, res);
  out.headers.set("X-Content-Type-Options", "nosniff");
  out.headers.set("X-Frame-Options", "SAMEORIGIN");
  out.headers.set("Referrer-Policy", "strict-origin-when-cross-origin");
  out.headers.set("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
  out.headers.set("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
  const ct = out.headers.get("Content-Type") || "";
  if (ct.includes("text/html")) {
    out.headers.set("Content-Security-Policy",
      "default-src 'self'; script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; " +
      "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
      "font-src 'self' data: https://fonts.gstatic.com; " +
      "img-src 'self' data: blob: https:; " +
      "connect-src 'self' https://*.xydeskremote.biz.id wss://*.xydeskremote.biz.id https://api.github.com; " +
      "frame-ancestors 'self'; base-uri 'self'; object-src 'none'");
  }
  return out;
}

function normalizeCountryCode(raw, fallback = "ID") {
  const code = String(raw || "").trim().toUpperCase();
  if (/^[A-Z]{2}$/.test(code) && code !== "XX" && code !== "T1") return code;
  const fb = String(fallback || "ID").trim().toUpperCase();
  return /^[A-Z]{2}$/.test(fb) ? fb : "ID";
}


const ID_WORD_HINT = /\b(halo|hai|apa|gimana|bagaimana|bisa|bang|bre|bro|kak|gan|aku|kamu|saya|kita|yang|ini|itu|dari|buat|untuk|udah|sudah|belum|tidak|tak|gak|nggak|konek|koneksi|langsung|terima|kasih|makasih|mantap|keren|tolong|cara|pakai|pake|kenapa|dimana|kapan|siapa|boleh|dong|aja|saja|kok|nih|tuh|lagi|sama|kalau|kalo|bukan|dengan|di|ke|jir|anjir|wkwk|wkwkwk|gasken|gas|sip|oke|mantul)\b/i;

function offlineTranslateFallback(text, country = "ID") {
  const clean = String(text || "").trim();
  if (!clean) return { translated: "", targetLang: "EN", sourceLang: "id" };
  const isId = ID_WORD_HINT.test(clean) || normalizeCountryCode(country) === "ID";
  if (isId) {
    const mapIdToEn = [
      [/\bhalo semuanya\b/gi, "hello everyone"],
      [/\bhalo\b/gi, "hello"],
      [/\bhai\b/gi, "hi"],
      [/\bselamat pagi\b/gi, "good morning"],
      [/\bselamat siang\b/gi, "good afternoon"],
      [/\bselamat sore\b/gi, "good evening"],
      [/\bselamat malam\b/gi, "good night"],
      [/\bterima kasih\b/gi, "thank you"],
      [/\bmakasih\b/gi, "thanks"],
      [/\bbalas langsung dari notifikasi\b/gi, "reply directly from notification"],
      [/\bdari notifikasi\b/gi, "from notification"],
      [/\bbalas langsung\b/gi, "reply directly"],
      [/\bbalas\b/gi, "reply"],
      [/\bnotifikasi\b/gi, "notification"],
      [/\bcara konek\b/gi, "how to connect"],
      [/\bkoneksi rdp nya lancar banget\b/gi, "the RDP connection is super smooth"],
      [/\baplikasi xydesk remote keren banget\b/gi, "the XyDesk Remote app is awesome"],
      [/\blancar banget\b/gi, "very smooth"],
      [/\bkeren banget\b/gi, "really cool"],
      [/\bgimana\b/gi, "how"],
      [/\bbagaimana\b/gi, "how"],
      [/\bkenapa\b/gi, "why"],
      [/\bbisa\b/gi, "can"],
      [/\btidak\b/gi, "not"],
      [/\bgak\b/gi, "not"],
      [/\bsudah\b/gi, "already"],
      [/\budah\b/gi, "already"],
      [/\bbelum\b/gi, "not yet"],
      [/\bmantap\b/gi, "awesome"],
      [/\bkeren\b/gi, "cool"],
      [/\blancar\b/gi, "smooth"],
      [/\bcepat\b/gi, "fast"],
      [/\bsemuanya\b/gi, "everyone"],
      [/\bsemua\b/gi, "everyone"],
      [/\bbang\b/gi, "bro"],
      [/\bkonek\b/gi, "connect"],
    ];
    let out = clean;
    for (const [re, rep] of mapIdToEn) out = out.replace(re, rep);
    return { translated: out, targetLang: "EN", sourceLang: "id" };
  }
  const mapEnToId = [
    [/\bhello everyone\b/gi, "halo semuanya"],
    [/\bhello\b/gi, "halo"],
    [/\bhi\b/gi, "hai"],
    [/\bgood morning\b/gi, "selamat pagi"],
    [/\bgood night\b/gi, "selamat malam"],
    [/\bthank you\b/gi, "terima kasih"],
    [/\bthanks\b/gi, "makasih"],
    [/\bhow to connect\b/gi, "cara konek"],
    [/\bhow\b/gi, "bagaimana"],
    [/\bwhy\b/gi, "kenapa"],
    [/\bawesome\b/gi, "mantap"],
    [/\bcool\b/gi, "keren"],
    [/\bfast\b/gi, "cepat"],
    [/\beveryone\b/gi, "semuanya"],
  ];
  let out = clean;
  for (const [re, rep] of mapEnToId) out = out.replace(re, rep);
  return { translated: out, targetLang: "ID", sourceLang: "en" };
}

async function fetchGoogleClients5Translate(text, sourceLang, targetLang, signal) {
  const q = encodeURIComponent(text);
  const sl = sourceLang || "auto";
  const url = `https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=${sl}&tl=${targetLang}&q=${q}`;
  const res = await fetch(url, {
    signal,
    headers: {
      "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
      Accept: "application/json,text/plain,*/*",
    },
  });
  if (!res.ok) throw new Error(`clients5_http_${res.status}`);
  const data = await res.json();
  const first = Array.isArray(data) && data.length > 0 ? data[0] : null;
  if (Array.isArray(first) && first[0]) {
    return {
      translated: String(first[0]).trim(),
      detected: typeof first[1] === "string" ? first[1].toLowerCase() : sl,
    };
  }
  if (typeof first === "string" && first.trim()) {
    return { translated: first.trim(), detected: sl };
  }
  throw new Error("clients5_bad_payload");
}

async function fetchGoogleTranslate(text, sourceLang, targetLang, signal) {
  const q = encodeURIComponent(text);
  const sl = sourceLang || "auto";
  const url = `https://translate.googleapis.com/translate_a/single?client=dict-chrome-ex&sl=${sl}&tl=${targetLang}&dt=t&q=${q}`;
  const res = await fetch(url, {
    signal,
    headers: {
      "User-Agent": "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
      Accept: "application/json,text/plain,*/*",
    },
  });
  if (!res.ok) throw new Error(`translate_http_${res.status}`);
  const data = await res.json();
  if (!Array.isArray(data) || !Array.isArray(data[0])) throw new Error("translate_bad_payload");
  const translated = data[0].map((part) => (Array.isArray(part) && part[0] ? String(part[0]) : "")).join("").trim();
  const detected = typeof data[2] === "string" ? data[2].toLowerCase() : sl;
  if (!translated) throw new Error("translate_empty");
  return { translated, detected };
}

function guessSourceLang(text, country = "") {
  const clean = String(text || "").trim();
  if (/[\u0e00-\u0e7f]/.test(clean)) return "th";
  if (/[\u3040-\u30ff]/.test(clean)) return "ja";
  if (/[\uac00-\ud7af]/.test(clean)) return "ko";
  if (/[\u4e00-\u9fff]/.test(clean)) return "zh-CN";
  if (/[\u0400-\u04ff]/.test(clean)) return "ru";
  if (/[\u0600-\u06ff]/.test(clean)) return "ar";
  if (/[\u0900-\u097f]/.test(clean)) return "hi";
  if (/[ăâđêôơưĂÂĐÊÔƠƯ]/i.test(clean)) return "vi";
  if (ID_WORD_HINT.test(clean)) return "id";
  if (/\b(the|and|you|are|how|what|why|where|when|this|that|with|from|have|has|for|not|can|will|good|nice|cool|hello|hi|thanks|thank)\b/i.test(clean)) {
    return "en";
  }
  const cc = String(country || "").trim().toUpperCase();
  const byCountry = {
    ID: "id", MY: "ms", JP: "ja", KR: "ko", CN: "zh-CN", TW: "zh-TW",
    RU: "ru", UA: "uk", ES: "es", MX: "es", AR: "es", BR: "pt", PT: "pt",
    FR: "fr", DE: "de", IT: "it", NL: "nl", TR: "tr",
    VN: "vi", TH: "th", PH: "tl", SA: "ar", AE: "ar", EG: "ar", IN: "hi",
  };
  return byCountry[cc] || "en";
}

async function fetchMyMemoryTranslate(text, sourceLang, targetLang, signal) {
  const q = encodeURIComponent(text);
  let sl = !sourceLang || sourceLang === "auto" ? guessSourceLang(text, "") : sourceLang;
  if (sl.toLowerCase() === String(targetLang || "").toLowerCase()) {
    sl = String(targetLang).toLowerCase() === "id" ? "en" : "id";
  }
  const url = `https://api.mymemory.translated.net/get?q=${q}&langpair=${encodeURIComponent(sl)}%7C${encodeURIComponent(targetLang)}`;
  const res = await fetch(url, {
    signal,
    headers: {
      "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
      Accept: "application/json",
    },
  });
  if (!res.ok) throw new Error(`mymemory_http_${res.status}`);
  const data = await res.json();
  const translated = String(data?.responseData?.translatedText || "").trim();
  if (!translated || translated.includes("MYMEMORY WARNING")) throw new Error("mymemory_empty");
  return translated;
}

// One deadline for all translation providers, including response body reads.
// Receipt/storage must not wait indefinitely on external translation services.
export async function withChatTranslationBudget(task, fallback, timeoutMs = 2_000) {
  const controller = new AbortController();
  let timer;
  const deadline = new Promise((resolve) => {
    timer = setTimeout(() => {
      controller.abort();
      resolve(fallback());
    }, timeoutMs);
  });
  try {
    return await Promise.race([
      Promise.resolve().then(() => task(controller.signal)).catch(() => fallback()),
      deadline,
    ]);
  } finally {
    clearTimeout(timer);
    controller.abort();
  }
}

async function releasesListResponse(request, env) {
  const cacheKey = new Request(new URL("/api/releases", request.url), { method: "GET" });
  const cache = globalThis.caches?.default;
  if (cache) {
    const cached = await cache.match(cacheKey).catch(() => null);
    if (cached) return cached;
  }
  const headers = { Accept: "application/vnd.github+json", "User-Agent": "xydesk-remote-worker" };
  const tokens = githubTokenCandidates(env);
  if (tokens.length) headers.Authorization = `Bearer ${tokens[0]}`;
  let data = null;
  try {
    const res = await fetch("https://api.github.com/repos/xykal/XyDesk-Remote/releases?per_page=20", { headers });
    if (!res.ok) return jsonResponse({ status: "error" }, 502, request.method);
    data = await res.json();
  } catch {
    return jsonResponse({ status: "error" }, 502, request.method);
  }
  const releases = (Array.isArray(data) ? data : [])
    .filter((r) => r && !r.draft && !r.prerelease)
    .slice(0, 10)
    .map((r) => ({
      tag: r.tag_name,
      name: r.name || r.tag_name,
      date: String(r.published_at || "").slice(0, 10),
      url: r.html_url,
    }));
  const out = jsonResponse({ status: "ok", releases }, 200, request.method);
  out.headers.set("Cache-Control", "public, max-age=300");
  if (cache) await cache.put(cacheKey, out.clone()).catch(() => {});
  return out;
}

const EDGE_OAUTH_SESSIONS = new Map();

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname;

    // Lapisan anti-penyalahgunaan paling depan: limiter per IP di isolate.
    // Request yang ditolak di sini adalah yang paling murah; pola flood
    // yang lolos tetap ditahan rate-limit Durable Object & (bila aktif)
    // aturan edge Cloudflare.
    if (path.startsWith("/api/") || path === "/ws/chat") {
      const ip = request.headers.get("cf-connecting-ip") || "unknown";
      const limit = apiAbuseLimit(path, ip);
      if (limit) {
        return jsonResponse(
          { status: "error", code: "rate_limited", retry_after: limit },
          429,
          request.method,
          { "retry-after": String(limit) },
        );
      }
    }

    if (env && env.UNIQUE_DOWNLOADS && (globalThis.caches?.default || env.XYDESK_DB)) {
      env = { ...env, UNIQUE_DOWNLOADS: wrapKvWithEdgeCache(env.UNIQUE_DOWNLOADS, env.XYDESK_DB || null) };
    }

    // CUTOVER FIREBASE (2026-10-24, keputusan pemilik): chat global resmi
    // pindah ke Firebase Firestore. Jalur relay Durable Object untuk PESAN
    // dimatikan total (410) supaya tidak ada lagi penyimpanan chat di
    // Cloudflare; riwayat resmi ada di Firestore + arsip lokal HP pengguna.
    // Setelah perombakan RDP-only (2026-11): yang bertahan hanya terjemahan
    // UI dan kanal resmi. Sisa fitur komunitas/notifikasi/pelaporan sesi
    // ikut dicabut dan dijawab 410 di bawah.
    if (
      path === "/ws/chat" ||
      path === "/api/chat/history" ||
      path === "/api/chat/send" ||
      path === "/api/chat/send-capabilities" ||
      path === "/api/chat/pin" ||
      path === "/api/chat/delete" ||
      path === "/api/chat/edit"
    ) {
      return jsonResponse(
        {
          status: "gone",
          code: "chat_moved_to_firebase",
          message: "Chat global telah pindah ke Firebase. Perbarui aplikasi XyDesk Remote.",
        },
        410,
        request.method,
      );
    }
    // Fitur komunitas/feedback/notifikasi/pelaporan sesi sudah dicabut
    // (produk RDP-only). Endpoint lama menjawab 410 permanen supaya klien
    // lama mendapat sinyal jelas, bukan 404 yang memicu retry.
    if (
      path === "/api/firebase/token" ||
      path === "/api/push-update" ||
      path === "/api/chat/push-relay" ||
      path === "/api/chat/translate" ||
      path.startsWith("/api/jokes") ||
      path.startsWith("/api/feedback")
    ) {
      return jsonResponse(
        { status: "gone", code: "feature_removed", message: "Fitur ini sudah tidak tersedia di XyDesk Remote." },
        410,
        request.method,
      );
    }
    if (path === "/api/github/oauth/login" || path === "/api/auth/login") {
      if (request.method === "OPTIONS") return corsPreflight("GET, OPTIONS");
      if (request.method !== "GET") return methodNotAllowed("GET, OPTIONS");
      const sid = String(url.searchParams.get("session_id") || "").trim().replace(/[^a-zA-Z0-9_-]/g, "").slice(0, 64);
      const scope = String(url.searchParams.get("scope") || "repo workflow read:user user:email");
      const configured = githubOAuthConfig(env);
      if (!configured) return durableObjectJson({ status:"error", code:"oauth_unconfigured" }, 503, request.method);
      // Ignore legacy client selectors. Android/browser login always returns to our API.
      const activeClientId = configured.client_id;
      const activeRedirectUri = GITHUB_CALLBACK;
      const isDash = url.searchParams.get("dash") === "1";
      const statePrefix = isDash ? "dash" : "apk";
      const state = sid
        ? `${statePrefix}_${sid}_${crypto.randomUUID().replace(/-/g, "").slice(0, 12)}`
        : `${statePrefix}_anon_${crypto.randomUUID().replace(/-/g, "").slice(0, 12)}`;
      const authorizeUrl = "https://github.com/login/oauth/authorize"
        + `?client_id=${encodeURIComponent(activeClientId)}`
        + `&redirect_uri=${encodeURIComponent(activeRedirectUri)}`
        + `&scope=${encodeURIComponent(scope)}`
        + `&state=${encodeURIComponent(state)}&allow_signup=true`;
      return new Response(null, {
        status: 302,
        headers: {
          "Location": authorizeUrl,
          "Cache-Control": "no-store",
          "Set-Cookie": `xy_oauth_state=${state}; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=900`,
        },
      });
    }
    if (
      path === "/api/github/oauth/callback" ||
      path === "/api/auth/callback/github" ||
      path === "/api/auth/callback" ||
      path === "/oauth/callback" ||
      path === "/callback" ||
      path === "/api/github/oauth/exchange" ||
      (request.method === "GET" && url.searchParams.has("code") && (String(url.searchParams.get("state") || "").startsWith("apk_") || String(url.searchParams.get("state") || "").startsWith("dash_")))
    ) {
      if (request.method === "OPTIONS") return corsPreflight("GET, OPTIONS");
      if (request.method !== "GET") return methodNotAllowed("GET, OPTIONS");
      const code = String(url.searchParams.get("code") || "").trim();
      const state = String(url.searchParams.get("state") || "").trim();
      const explicitSid = String(url.searchParams.get("session_id") || "").trim().replace(/[^a-zA-Z0-9_-]/g, "").slice(0, 64);
      if (!code) return durableObjectJson({ status:"error", code:"invalid_oauth_code" }, 400, request.method);
      const configured = githubOAuthConfig(env);
      if (!configured) return durableObjectJson({ status:"error", code:"oauth_unconfigured" }, 503, request.method);
      const exchange = await exchangeGithubCode(code, [configured]);
      if (!exchange.ok) {
        return durableObjectJson({ status: "error", code: exchange.code }, exchange.status, request.method);
      }
      const accessToken = exchange.accessToken;

      // Token exchange alone is not a verified provider identity. Never persist,
      // redirect or emit a deep link after a failed/malformed profile lookup.
      const verified = await resolveGithubIdentity(accessToken);
      if (!verified.ok) {
        return durableObjectJson({ status: "error", code: verified.code }, verified.status, request.method);
      }
      const login = verified.principal.login;
      const name = verified.principal.name;
      const avatarUrl = verified.principal.avatar_url;
      const parts = state.split("_");
      const sid = explicitSid || ((state.startsWith("apk_") || state.startsWith("dash_")) && parts.length >= 2 ? parts[1] : "");
      const sessionPayload = {
        token: accessToken,
        login,
        name,
        avatar_url: avatarUrl,
        at: Date.now(),
      };
      if (sid && login) {
        EDGE_OAUTH_SESSIONS.set(sid, sessionPayload);
        if (env?.UNIQUE_DOWNLOADS) {
          try {
            await env.UNIQUE_DOWNLOADS.put(`oauth:${sid}`, JSON.stringify(sessionPayload), { expirationTtl: OAUTH_SESSION_TTL_SECONDS });
          } catch {}
        }
      }
      if (state.startsWith("dash_")) {
        const dashCb = `https://xyrdp-dash.vercel.app/api/auth/callback?bridge_token=${encodeURIComponent(accessToken)}&state=${encodeURIComponent(state)}`;
        return new Response(null, {
          status: 302,
          headers: { "Location": dashCb, "Cache-Control": "no-store" },
        });
      }
      if (path === "/api/github/oauth/exchange" || url.searchParams.get("format") === "json") {
        return durableObjectJson({ status: "ok", ready: true, ...sessionPayload }, 200, request.method);
      }
      const deepLink = `xydesk://github-auth?token=${encodeURIComponent(accessToken)}&login=${encodeURIComponent(login)}&name=${encodeURIComponent(name)}&avatar=${encodeURIComponent(avatarUrl)}`;
      const intentLink = `intent://github-auth?token=${encodeURIComponent(accessToken)}&login=${encodeURIComponent(login)}&name=${encodeURIComponent(name)}&avatar=${encodeURIComponent(avatarUrl)}#Intent;scheme=xydesk;package=id.xydesk.remote;end`;
      const html = `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>GitHub Terhubung · XyDesk Remote</title><style>body{background:#09090b;color:#fafafa;font-family:system-ui,-apple-system,sans-serif;display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0;padding:24px;text-align:center}.card{max-width:380px;background:#141418;border:1px solid rgba(255,255,255,.12);border-radius:16px;padding:28px 22px}.btn{display:inline-block;margin-top:18px;padding:12px 24px;border-radius:10px;background:#fafafa;color:#09090b;font-weight:700;text-decoration:none;font-size:14px}</style></head><body><div class="card"><div style="font-size:17px;font-weight:700;margin-bottom:8px">GitHub Terhubung</div><div style="font-size:13px;color:#a1a1aa">Masuk sebagai <b>@${login.replace(/[<>&"']/g, "")}</b>. Mengembalikan otomatis ke aplikasi XyDesk Remote...</div><a class="btn" href="${deepLink}">Kembali ke XyDesk Remote</a></div><script>setTimeout(function(){window.location.replace(${JSON.stringify(deepLink)});},80);setTimeout(function(){window.location.replace(${JSON.stringify(intentLink)});},600);</script></body></html>`;
      return new Response(html, {
        status: 200,
        headers: {
          "Content-Type": "text/html; charset=utf-8",
          "Cache-Control": "no-store",
          "Referrer-Policy": "no-referrer",
          // Retire the unused bearer-token cookie instead of retaining it for 30 days.
          "Set-Cookie": "xy_gh_session=; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=0",
        },
      });
    }
    if (path === "/api/github/oauth/complete") {
      if (request.method === "OPTIONS") return corsPreflight("POST, OPTIONS");
      if (request.method !== "POST") return methodNotAllowed("POST, OPTIONS");
      const rawBody = await request.text().catch(() => "");
      let parsed = null;
      try { parsed = JSON.parse(rawBody); } catch {}
      const sid = typeof parsed?.session_id === "string" ? parsed.session_id.trim() : "";
      const tok = String(parsed?.token || "").trim();
      const login = String(parsed?.login || "").trim().slice(0, 40);
      if (!/^[a-zA-Z0-9_-]{1,64}$/.test(sid) || !tok || !login) {
        return durableObjectJson({ status: "error", code: "invalid_payload" }, 400, request.method);
      }
      // This verifies the provider principal, not ownership of the polling session.
      // Session/state binding remains a separate prerequisite before reward grants.
      const verified = await verifyGithubIdentity(tok, login);
      if (!verified.ok) {
        return durableObjectJson({ status: "error", code: verified.code }, verified.status, request.method);
      }
      const sessionPayload = {
        token: tok,
        login: verified.principal.login,
        name: verified.principal.name,
        avatar_url: verified.principal.avatar_url,
        at: Date.now(),
      };
      EDGE_OAUTH_SESSIONS.set(sid, sessionPayload);
      if (env?.UNIQUE_DOWNLOADS) {
        try {
          await env.UNIQUE_DOWNLOADS.put(`oauth:${sid}`, JSON.stringify(sessionPayload), { expirationTtl: OAUTH_SESSION_TTL_SECONDS });
        } catch {}
      }
      return durableObjectJson({ status: "ok" }, 200, request.method);
    }
    if (path === "/api/github/oauth/poll") {
      if (request.method === "OPTIONS") return corsPreflight("GET, OPTIONS");
      if (request.method !== "GET") return methodNotAllowed("GET, OPTIONS");
      const sid = String(url.searchParams.get("session_id") || "").trim().replace(/[^a-zA-Z0-9_-]/g, "").slice(0, 64);
      if (!sid) {
        return durableObjectJson({ status: "ok", ready: false }, 200, request.method);
      }
      const edgeHit = EDGE_OAUTH_SESSIONS.get(sid);
      if (edgeHit && edgeHit.token && Date.now() - Number(edgeHit.at || 0) < OAUTH_SESSION_TTL_MS) {
        EDGE_OAUTH_SESSIONS.delete(sid);
        if (env?.UNIQUE_DOWNLOADS) {
          try { await env.UNIQUE_DOWNLOADS.delete(`oauth:${sid}`); } catch {}
        }
        return durableObjectJson({ status: "ok", ready: true, ...edgeHit }, 200, request.method);
      }
      if (env?.UNIQUE_DOWNLOADS) {
        try {
          const kvRaw = await env.UNIQUE_DOWNLOADS.get(`oauth:${sid}`);
          if (kvRaw) {
            const kvData = JSON.parse(kvRaw);
            if (kvData && kvData.token) {
              await env.UNIQUE_DOWNLOADS.delete(`oauth:${sid}`).catch(() => {});
              return durableObjectJson({ status: "ok", ready: true, ...kvData }, 200, request.method);
            }
          }
        } catch {}
      }
      return durableObjectJson({ status: "ok", ready: false }, 200, request.method);
    }
    if (path === "/api/data-pack") {
      if (request.method === "OPTIONS") return corsPreflight("GET, OPTIONS");
      return dataPackResponse(request, env);
    }
    if (path === "/api/ui/translate") {
      if (request.method === "OPTIONS") return corsPreflight("POST, OPTIONS");
      if (request.method !== "POST") return methodNotAllowed("POST, OPTIONS");
      return uiTranslateResponse(request);
    }
    if (path === "/api/account/settings") {
      return accountSettingsResponse(request, env);
    }
    if (path === "/api/pc/relay") {
      return pcRelayResponse(request, env);
    }
    if (path === "/api/active-sessions") {
      return activeSessionsResponse(request, env);
    }
    if (path === "/api/session") {
      if (request.method !== "POST") return methodNotAllowed("POST");
      return sessionReportResponse(request, env);
    }
    if (request.method !== "GET" && request.method !== "HEAD") return methodNotAllowed();

    if (path === "/api/health") return healthResponse(request, env);
    if (path === "/api/status") return statusResponse(request, env);
    if (path === "/api/stats") return downloadStatsResponse(request, env);
    if (path === "/api/releases") return releasesListResponse(request, env);
    if (path === "/api/update-check") return updateCheckResponse(request, env);
    if (path.startsWith("/api/")) return jsonResponse({ status: "not_found" }, 404, request.method);

    if (path === "/host" || path === "/host.ps1") {
      return serveScriptAsset(request, env, "host.ps1", "xydesk-host.ps1", "text/plain; charset=utf-8");
    }
    if (path === "/agent.ps1") {
      return serveScriptAsset(request, env, "XyDesk-Remote-Agent-Setup.ps1", "XyDesk-Remote-Agent-Setup.ps1", "text/plain; charset=utf-8");
    }
    if (path === "/agent.bat") {
      return serveScriptAsset(request, env, "agent.bat", "XyDesk-Remote-Agent-Setup.bat", "application/octet-stream");
    }
    if (path === "/XyDeskHost-Setup.bat") {
      return serveScriptAsset(request, env, "XyDeskHost-Setup.bat", "XyDeskHost-Setup.bat", "application/octet-stream");
    }

    const key = path.slice(1);
    if (key === "SHA256SUMS.txt") return checksumsResponse(request, env);
    if (PUBLIC_ASSETS[key]) return redirectResponse(PUBLIC_ASSETS[key]);
    if (QA_ASSETS[key]) return redirectLegacyAsset(QA_ASSETS[key], env, request);

    const qaPrefix = "/_qa/xykal-qa-latest/";
    if (path.startsWith(qaPrefix)) {
      const qaKey = path.slice(qaPrefix.length);
      if (qaKey === "SHA256SUMS.txt") return checksumsResponse(request, env);
      if (QA_ASSETS[qaKey]) return redirectLegacyAsset(QA_ASSETS[qaKey], env, request);
      if (PUBLIC_ASSETS[qaKey]) return redirectResponse(PUBLIC_ASSETS[qaKey]);
      return notFound(request.method);
    }

    // Domain khusus papan umpan balik: root-nya adalah feedback.html.
    const host = url.hostname.toLowerCase();
    if ((host === "report.xydeskremote.biz.id" || host === "ask.xydeskremote.biz.id") &&
        (path === "/" || path === "/index.html")) {
      const fbRes = await env.ASSETS.fetch(new Request(new URL("/feedback.html", request.url), request));
      return withSecurityHeaders(fbRes);
    }

    // Domain khusus API & Signaling (api/wss/cpc): TIDAK BOLEH merender halaman web utama!
    if (host === "api.xydeskremote.biz.id" || host === "wss.xydeskremote.biz.id" || host === "cpc.xydeskremote.biz.id") {
      if (path === "/" || path === "/index.html") {
        return healthResponse(request, env);
      }
      return jsonResponse(
        {
          status: "not_found",
          service: "xydesk-remote-api",
          host,
          web_portal: "https://www.xydeskremote.biz.id",
        },
        404,
        request.method,
      );
    }

    // Domain web utama resmi hanyalah www.xydeskremote.biz.id (apex redirect ke www)
    if (host === "xydeskremote.biz.id") {
      return Response.redirect(`https://www.xydeskremote.biz.id${url.pathname}${url.search}`, 301);
    }

    const assetRes = await env.ASSETS.fetch(request);
    return withSecurityHeaders(assetRes);
  },
};
