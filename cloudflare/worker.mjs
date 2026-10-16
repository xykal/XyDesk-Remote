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
  "XyDeskRemoteHost.exe": { repo: "xykal/XyDesk-Remote", id: 606095804, fileName: "XyDeskRemoteHost.exe" },
  "XyDeskRemoteHost-Standalone.exe": { repo: "xykal/XyDesk-Remote", fileName: "XyDeskRemoteHost-Standalone.exe" },
  "XyDesk-Host.exe": { repo: "xykal/XyDesk-Remote", fileName: "XyDeskRemoteHost-Standalone.exe" },
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
const COMMUNITY_CLIENT_ID_RE = ACTIVE_SESSION_ID_RE;
const COMMUNITY_JOKE_MAX_CHARS = 280;
const COMMUNITY_ATTACHMENT_MAX_BYTES = 1_500_000; // foto/stiker webp/gif di feed
const COMMUNITY_AVATAR_MAX_BYTES = 200_000;
const COMMUNITY_ATTACHMENT_MIMES = Object.freeze([
  "image/webp", "image/gif", "image/png", "image/jpeg",
]);
const COMMUNITY_JOKE_FEED_SIZE = 20;
const COMMUNITY_JOKE_MAX_ITEMS = 1_000;
const COMMUNITY_JOKE_POST_INTERVAL_MS = 60_000;
const COMMUNITY_JOKE_REPORT_THRESHOLD = 3;
// Set reaksi diperluas (permintaan pemilik 2026-10-10: tombol "+" dengan
// lebih banyak emoji). Superset dari 8 emoji lama supaya hitungan reaksi
// postingan lama tetap terbaca.
const COMMUNITY_JOKE_REACTIONS = Object.freeze(["😂", "😭", "💀", "🔥", "👍", "👎", "❤️", "🎉", "🤔", "😍", "🥳", "😎", "🙏", "👏", "💯", "🤣", "😡", "😱", "🥶", "🤯", "😴", "🍿", "⚡", "🏆"]);
const COMMUNITY_COMMENT_MAX_CHARS = 140;
const COMMUNITY_COMMENT_INTERVAL_MS = 20_000;
const COMMUNITY_COMMENT_MAX_PER_JOKE = 50;
const COMMUNITY_COMMENT_FEED_SIZE = 5;
const COMMUNITY_DISPLAY_NAME_MAX_CHARS = 24;
const PC_SIGNAL_ROOM_ID_RE = ACTIVE_SESSION_ID_RE;
const PC_SIGNAL_WAIT_TTL_MS = 10 * 60_000;
const PC_SIGNAL_ACTIVE_TTL_MS = 30 * 60_000;
const PC_SIGNAL_EVENT_TTL_MS = 2 * 60_000;
const PC_SIGNAL_MAX_EVENTS = 128;
const PC_SIGNAL_MAX_BODY_BYTES = 24_576;
const PC_SIGNAL_JOIN_CODE_BYTES = 24;
const PC_SIGNAL_TOKEN_BYTES = 32;
const PC_SIGNAL_JOIN_FAILURE_LIMIT = 15;
const PC_SIGNAL_CREATE_PER_IP_PER_HOUR = 5;
const PC_SIGNAL_TYPES = new Set(["offer", "answer", "candidate", "bye"]);

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
 * Hitung pengunduh unik: hash(IP + User-Agent + file) disimpan di KV sekali;
 * unduhan berulang dari sumber yang sama tidak menambah total. Statistik
 * tidak boleh pernah menghalangi unduhan, jadi semua kegagalan ditelan.
 */
async function recordUniqueDownload(env, request, fileName) {
  const kv = env.UNIQUE_DOWNLOADS;
  if (!kv) return;
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
    const key = `dl:${fileName}:${hex}`;
    if (await kv.get(key)) return;
    await kv.put(key, String(Date.now()), {
      expirationTtl: 60 * 60 * 24 * 365 * 5,
    });
    const current = Number(await kv.get("dl:total")) || 0;
    await kv.put("dl:total", String(current + 1));
  } catch {
    // Statistik belaka: kegagalan KV tidak boleh memutus unduhan.
  }
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
    let uniqueApkDownloaders = null;
    try {
      uniqueApkDownloaders = Number(await env.UNIQUE_DOWNLOADS?.get("dl:total")) || 0;
    } catch {
      uniqueApkDownloaders = null;
    }
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

async function hashedSessionKey(sessionId) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(sessionId));
  const hex = Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
  return `${ACTIVE_SESSION_PREFIX}${hex}`;
}

async function hashedCommunityClientId(clientId) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(clientId));
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
}

function sanitizeCommunityDisplayName(value) {
  if (typeof value !== "string") return null;
  const clean = value.replace(/[\u0000-\u001F\u007F]/g, "").replace(/\s+/g, " ").trim();
  const chars = [...clean];
  if (chars.length < 1 || chars.length > COMMUNITY_DISPLAY_NAME_MAX_CHARS) return null;
  if (/(?:https?:\/\/|www\.)/i.test(clean)) return null;
  return chars.join("");
}

function communityJokeListKey(createdAt, id) {
  return `joke:${String(createdAt).padStart(13, "0")}:${id}`;
}

function communityCommentPublicView(comment, viewerHash = null) {
  return {
    id: comment.id,
    name: comment.name || null,
    text: comment.text,
    created_at: comment.created_at,
    is_own: Boolean(viewerHash && comment.author_hash && comment.author_hash === viewerHash),
  };
}

function communityJokePublicView(joke, viewerReaction = null, comments = null, extra = {}) {
  return {
    id: joke.id,
    text: joke.text,
    created_at: joke.created_at,
    edited_at: Number(joke.edited_at) > 0 ? Number(joke.edited_at) : null,
    author_name: joke.author_name || null,
    author_avatar_url: extra.avatarUrl || null,
    is_own: Boolean(extra.isOwn),
    attachment: joke.attachment?.url
      ? { mime: joke.attachment.mime || "image/webp", url: joke.attachment.url }
      : null,
    reactions: Object.fromEntries(COMMUNITY_JOKE_REACTIONS.map((emoji) => [emoji, Math.max(0, Number(joke.reactions?.[emoji]) || 0)])),
    viewer_reaction: viewerReaction,
    comments_total: Math.max(0, Number(joke.comment_count) || 0),
    comments: comments || [],
  };
}

// Dekode base64 jadi Uint8Array; null jika tidak valid.
function decodeBase64Limited(value, maxBytes) {
  if (typeof value !== "string" || value.length === 0) return null;
  if (!/^[A-Za-z0-9+/]+={0,2}$/.test(value) || Math.floor(value.length * 0.75) > maxBytes) return null;
  try {
    const binary = atob(value);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    return bytes.length > maxBytes ? null : bytes;
  } catch {
    return null;
  }
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

async function readCommunityJson(request, maxBytes = 2_048) {
  if (!isJsonContentType(request.headers.get("content-type"))) {
    return { error: durableObjectJson({ status: "invalid_content_type" }, 415, request.method) };
  }
  const contentLength = Number(request.headers.get("content-length") || 0);
  if (contentLength > maxBytes) {
    return { error: durableObjectJson({ status: "payload_too_large" }, 413, request.method) };
  }
  const bounded = await readRequestTextLimited(request, maxBytes);
  if (bounded.tooLarge) {
    return { error: durableObjectJson({ status: "payload_too_large" }, 413, request.method) };
  }
  if (bounded.invalid) {
    return { error: durableObjectJson({ status: "invalid_request" }, 400, request.method) };
  }
  try {
    return { body: JSON.parse(bounded.text) };
  } catch {
    return { error: durableObjectJson({ status: "invalid_json" }, 400, request.method) };
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

function constantTimeStringEqual(left, right) {
  if (typeof left !== "string" || typeof right !== "string" || left.length !== right.length) return false;
  let mismatch = 0;
  for (let index = 0; index < left.length; index += 1) {
    mismatch |= left.charCodeAt(index) ^ right.charCodeAt(index);
  }
  return mismatch === 0;
}

async function readPcSignalJson(request, maxBytes = PC_SIGNAL_MAX_BODY_BYTES) {
  if (!isJsonContentType(request.headers.get("content-type"))) {
    return { error: durableObjectJson({ status: "invalid_content_type" }, 415, request.method) };
  }
  const contentLength = Number(request.headers.get("content-length") || 0);
  if (contentLength > maxBytes) {
    return { error: durableObjectJson({ status: "payload_too_large" }, 413, request.method) };
  }
  const bounded = await readRequestTextLimited(request, maxBytes);
  if (bounded.tooLarge) {
    return { error: durableObjectJson({ status: "payload_too_large" }, 413, request.method) };
  }
  if (bounded.invalid) {
    return { error: durableObjectJson({ status: "invalid_request" }, 400, request.method) };
  }
  try {
    return { body: JSON.parse(bounded.text) };
  } catch {
    return { error: durableObjectJson({ status: "invalid_json" }, 400, request.method) };
  }
}

function validatePcSignalPayload(type, payload) {
  if (type === "offer" || type === "answer") {
    return payload && typeof payload === "object" && payload.type === type &&
      typeof payload.sdp === "string" && payload.sdp.length > 0 && payload.sdp.length <= 20_000;
  }
  if (type === "candidate") {
    if (payload === null) return true;
    return payload && typeof payload === "object" &&
      typeof payload.candidate === "string" && payload.candidate.length <= 4_096 &&
      (payload.sdpMid == null || (typeof payload.sdpMid === "string" && payload.sdpMid.length <= 64)) &&
      (payload.sdpMLineIndex == null || Number.isSafeInteger(payload.sdpMLineIndex)) &&
      (payload.usernameFragment == null || (typeof payload.usernameFragment === "string" && payload.usernameFragment.length <= 256));
  }
  if (type === "bye") {
    return payload == null || (payload && typeof payload === "object" &&
      (payload.reason == null || (typeof payload.reason === "string" && payload.reason.length <= 128)));
  }
  return false;
}

/**
 * Ephemeral WebRTC rendezvous/signaling room. It relays bounded SDP/ICE JSON only;
 * media packets never pass through this object. Media encryption is negotiated
 * peer-to-peer by WebRTC and is not an E2EE claim for the whole XyDesk product.
 */
export class PcSignalRoom {
  constructor(state) {
    this.state = state;
  }

  async fetch(request) {
    const path = new URL(request.url).pathname;
    if (path === "/limit/create" && request.method === "POST") return this.limitCreate(request);
    if (path === "/create" && request.method === "POST") return this.createRoom();
    if (path === "/join" && request.method === "POST") return this.joinRoom(request);
    if (path === "/signal" && request.method === "GET") return this.readSignals(request);
    if (path === "/signal" && request.method === "POST") return this.appendSignal(request);
    return durableObjectJson({ status: "not_found" }, 404, request.method);
  }

  async limitCreate(request) {
    const ipHash = request.headers.get("x-xydesk-ip-hash") || "";
    if (!/^[0-9a-f]{64}$/i.test(ipHash)) {
      return durableObjectJson({ status: "invalid_request" }, 400, request.method);
    }
    const now = Date.now();
    const outcome = await this.state.storage.transaction(async (transaction) => {
      const current = await transaction.get("create-limit");
      const window = current && Number(current.reset_at) > now
        ? current
        : { count: 0, reset_at: now + 60 * 60_000 };
      if (window.count >= PC_SIGNAL_CREATE_PER_IP_PER_HOUR) {
        return { allowed: false, resetAt: window.reset_at };
      }
      window.count += 1;
      await transaction.put("create-limit", window);
      return { allowed: true, resetAt: window.reset_at };
    });
    await this.state.storage.setAlarm(outcome.resetAt);
    if (!outcome.allowed) {
      return durableObjectJson({ status: "rate_limited", retry_after_ms: Math.max(1, outcome.resetAt - now) }, 429, request.method);
    }
    return durableObjectJson({ status: "ok" }, 200, request.method);
  }

  async createRoom() {
    const now = Date.now();
    const expiresAt = now + PC_SIGNAL_WAIT_TTL_MS;
    const hostToken = randomBase64Url(PC_SIGNAL_TOKEN_BYTES);
    const joinCode = randomBase64Url(PC_SIGNAL_JOIN_CODE_BYTES);
    const [hostHash, joinHash] = await Promise.all([
      pcSignalSecretHash(hostToken),
      pcSignalSecretHash(joinCode),
    ]);
    const created = await this.state.storage.transaction(async (transaction) => {
      const oldMeta = await transaction.get("room:meta");
      if (oldMeta && Number(oldMeta.expires_at) > now) return false;
      for (const key of ["room:viewer-token", "room:join-code", "room:events", "room:next-seq", "room:join-failures"]) {
        await transaction.delete(key);
      }
      await transaction.put("room:meta", { created_at: now, expires_at: expiresAt });
      await transaction.put("room:host-token", hostHash);
      await transaction.put("room:join-code", joinHash);
      await transaction.put("room:next-seq", 0);
      await transaction.put("room:events", []);
      return true;
    });
    if (!created) return durableObjectJson({ status: "room_exists" }, 409);
    await this.state.storage.setAlarm(expiresAt);
    return durableObjectJson({ status: "ok", host_token: hostToken, pairing_code: joinCode, expires_at: expiresAt }, 201);
  }

  async joinRoom(request) {
    const parsed = await readPcSignalJson(request, 512);
    if (parsed.error) return parsed.error;
    const pairingCode = parsed.body?.pairing_code;
    if (typeof pairingCode !== "string" || !/^[A-Za-z0-9_-]{32}$/.test(pairingCode)) {
      return durableObjectJson({ status: "invalid_pairing_code" }, 400, request.method);
    }
    const viewerToken = randomBase64Url(PC_SIGNAL_TOKEN_BYTES);
    const suppliedHash = await pcSignalSecretHash(pairingCode);
    const viewerHash = await pcSignalSecretHash(viewerToken);
    const now = Date.now();
    const outcome = await this.state.storage.transaction(async (transaction) => {
      const meta = await transaction.get("room:meta");
      if (!meta || Number(meta.expires_at) <= now) return { status: "room_expired" };
      const failures = await transaction.get("room:join-failures") || { count: 0, since: now };
      if (now - Number(failures.since) >= PC_SIGNAL_WAIT_TTL_MS) {
        failures.count = 0;
        failures.since = now;
      }
      if (Number(failures.count) >= PC_SIGNAL_JOIN_FAILURE_LIMIT) return { status: "rate_limited" };
      const expectedHash = await transaction.get("room:join-code");
      if (!constantTimeStringEqual(expectedHash || "", suppliedHash)) {
        failures.count = Number(failures.count) + 1;
        await transaction.put("room:join-failures", failures);
        return { status: "invalid_pairing_code" };
      }
      if (await transaction.get("room:viewer-token")) return { status: "already_joined" };
      const nextExpiry = now + PC_SIGNAL_ACTIVE_TTL_MS;
      meta.expires_at = nextExpiry;
      await transaction.put("room:meta", meta);
      await transaction.put("room:viewer-token", viewerHash);
      await transaction.delete("room:join-code");
      await transaction.delete("room:join-failures");
      return { status: "ok", expiresAt: nextExpiry };
    });
    if (outcome.status !== "ok") {
      const statusCode = outcome.status === "room_expired" ? 410 :
        outcome.status === "rate_limited" ? 429 :
        outcome.status === "already_joined" ? 409 : 401;
      return durableObjectJson({ status: outcome.status }, statusCode, request.method);
    }
    await this.state.storage.setAlarm(outcome.expiresAt);
    return durableObjectJson({ status: "ok", viewer_token: viewerToken, expires_at: outcome.expiresAt }, 200, request.method);
  }

  async authorize(request) {
    const match = String(request.headers.get("authorization") || "").match(/^Bearer ([A-Za-z0-9_-]{43})$/);
    if (!match) return null;
    const suppliedHash = await pcSignalSecretHash(match[1]);
    const [hostHash, viewerHash] = await Promise.all([
      this.state.storage.get("room:host-token"),
      this.state.storage.get("room:viewer-token"),
    ]);
    if (constantTimeStringEqual(hostHash || "", suppliedHash)) return "host";
    if (constantTimeStringEqual(viewerHash || "", suppliedHash)) return "viewer";
    return null;
  }

  async activeMeta() {
    const meta = await this.state.storage.get("room:meta");
    if (!meta || Number(meta.expires_at) <= Date.now()) return null;
    return meta;
  }

  async readSignals(request) {
    const meta = await this.activeMeta();
    if (!meta) return durableObjectJson({ status: "room_expired" }, 410, request.method);
    const role = await this.authorize(request);
    if (!role) return durableObjectJson({ status: "unauthorized" }, 401, request.method);
    const url = new URL(request.url);
    const afterText = url.searchParams.get("after") || "0";
    if (!/^(0|[1-9][0-9]{0,15})$/.test(afterText)) {
      return durableObjectJson({ status: "invalid_cursor" }, 400, request.method);
    }
    const after = Number(afterText);
    if (!Number.isSafeInteger(after)) return durableObjectJson({ status: "invalid_cursor" }, 400, request.method);
    const now = Date.now();
    const [storedEvents, nextSeq] = await Promise.all([
      this.state.storage.get("room:events"),
      this.state.storage.get("room:next-seq"),
    ]);
    const events = (Array.isArray(storedEvents) ? storedEvents : [])
      .filter((event) => now - Number(event.created_at) <= PC_SIGNAL_EVENT_TTL_MS);
    if (events.length !== (Array.isArray(storedEvents) ? storedEvents.length : 0)) {
      await this.state.storage.put("room:events", events);
    }
    const visible = events.filter((event) => event.seq > after && event.from !== role);
    const cursor = events.reduce((current, event) => Math.max(current, Number(event.seq) || 0), after);
    const renewedExpiry = now + PC_SIGNAL_ACTIVE_TTL_MS;
    meta.expires_at = renewedExpiry;
    await this.state.storage.put("room:meta", meta);
    await this.state.storage.setAlarm(renewedExpiry);
    return durableObjectJson({ status: "ok", role, events: visible, cursor, latest_seq: Number(nextSeq) || 0 }, 200, request.method);
  }

  async appendSignal(request) {
    const meta = await this.activeMeta();
    if (!meta) return durableObjectJson({ status: "room_expired" }, 410, request.method);
    const role = await this.authorize(request);
    if (!role) return durableObjectJson({ status: "unauthorized" }, 401, request.method);
    const parsed = await readPcSignalJson(request, PC_SIGNAL_MAX_BODY_BYTES);
    if (parsed.error) return parsed.error;
    const type = parsed.body?.type;
    const payload = parsed.body?.payload ?? null;
    if (!PC_SIGNAL_TYPES.has(type) || !validatePcSignalPayload(type, payload)) {
      return durableObjectJson({ status: "invalid_signal" }, 400, request.method);
    }
    const now = Date.now();
    const outcome = await this.state.storage.transaction(async (transaction) => {
      const currentMeta = await transaction.get("room:meta");
      if (!currentMeta || Number(currentMeta.expires_at) <= now) return { status: "room_expired" };
      const events = (await transaction.get("room:events") || [])
        .filter((event) => now - Number(event.created_at) <= PC_SIGNAL_EVENT_TTL_MS);
      if (events.length >= PC_SIGNAL_MAX_EVENTS) return { status: "queue_full" };
      const nextSeq = (Number(await transaction.get("room:next-seq")) || 0) + 1;
      const expiresAt = now + PC_SIGNAL_ACTIVE_TTL_MS;
      events.push({ seq: nextSeq, from: role, type, payload, created_at: now });
      currentMeta.expires_at = expiresAt;
      await transaction.put("room:events", events);
      await transaction.put("room:next-seq", nextSeq);
      await transaction.put("room:meta", currentMeta);
      return { status: "ok", seq: nextSeq, expiresAt };
    });
    if (outcome.status !== "ok") {
      const statusCode = outcome.status === "queue_full" ? 429 : 410;
      return durableObjectJson({ status: outcome.status }, statusCode, request.method);
    }
    await this.state.storage.setAlarm(outcome.expiresAt);
    return durableObjectJson({ status: "ok", seq: outcome.seq }, 201, request.method);
  }

  async alarm() {
    const now = Date.now();
    const meta = await this.state.storage.get("room:meta");
    if (meta) {
      if (Number(meta.expires_at) > now) {
        await this.state.storage.setAlarm(Number(meta.expires_at));
        return;
      }
      for (const key of ["room:meta", "room:host-token", "room:viewer-token", "room:join-code", "room:join-failures", "room:events", "room:next-seq"]) {
        await this.state.storage.delete(key);
      }
    }
    const createLimit = await this.state.storage.get("create-limit");
    if (createLimit && Number(createLimit.reset_at) > now) {
      await this.state.storage.setAlarm(Number(createLimit.reset_at));
    } else if (createLimit) {
      await this.state.storage.delete("create-limit");
      await this.state.storage.deleteAlarm();
    }
  }
}

export class ActiveSessions {
  constructor(state) {
    this.state = state;
  }

  async fetch(request) {
    const path = new URL(request.url).pathname;
    if (path === "/count" && request.method === "GET") return this.readCount();
    if (path === "/update" && request.method === "POST") return this.updateLease(request);
    return durableObjectJson({ status: "not_found" }, 404);
  }

  async readCount() {
    try {
      const countValue = await this.state.storage.get("meta:active-count");
      const active = Math.max(0, Math.min(ACTIVE_SESSION_MAX, Number(countValue) || 0));
      await this.ensureCleanupAlarm(active);
      return durableObjectJson({ status: "ok", active_session_count: active });
    } catch {
      return durableObjectJson({ status: "unavailable" }, 503);
    }
  }

  async updateLease(request) {
    const contentType = request.headers.get("content-type") || "";
    if (!isJsonContentType(contentType)) {
      return durableObjectJson({ status: "invalid_content_type" }, 415);
    }
    const contentLength = Number(request.headers.get("content-length") || 0);
    if (contentLength > 512) return durableObjectJson({ status: "payload_too_large" }, 413);

    let raw;
    try {
      raw = await request.text();
    } catch {
      return durableObjectJson({ status: "invalid_request" }, 400);
    }
    if (raw.length > 512) return durableObjectJson({ status: "payload_too_large" }, 413);

    let body;
    try {
      body = JSON.parse(raw);
    } catch {
      return durableObjectJson({ status: "invalid_json" }, 400);
    }
    const action = body?.action;
    const sessionId = body?.session_id;
    if (!ACTIVE_SESSION_ID_RE.test(String(sessionId || "")) || !["heartbeat", "end"].includes(action)) {
      return durableObjectJson({ status: "invalid_session_report" }, 400);
    }

    try {
      const key = await hashedSessionKey(sessionId);
      const now = Date.now();
      const result = await this.state.storage.transaction(async (transaction) => {
        const lastSeen = await transaction.get(key);
        let active = Math.max(0, Number(await transaction.get("meta:active-count")) || 0);

        if (action === "end") {
          if (lastSeen !== undefined) {
            await transaction.delete(key);
            active = Math.max(0, active - 1);
            await transaction.put("meta:active-count", active);
          }
          return { active, full: false };
        }

        if (lastSeen === undefined && active >= ACTIVE_SESSION_MAX) {
          // Only scan the lease set at capacity; ordinary heartbeats remain O(1).
          const entries = await transaction.list({ prefix: ACTIVE_SESSION_PREFIX, limit: ACTIVE_SESSION_MAX + 1 });
          active = 0;
          for (const [entryKey, seenAt] of entries) {
            const timestamp = Number(seenAt);
            if (!Number.isFinite(timestamp) || now - timestamp > ACTIVE_SESSION_TTL_MS) {
              await transaction.delete(entryKey);
            } else {
              active += 1;
            }
          }
          await transaction.put("meta:active-count", active);
          if (active >= ACTIVE_SESSION_MAX) return { active, full: true };
        }

        if (lastSeen === undefined) active += 1;
        await transaction.put(key, now);
        await transaction.put("meta:active-count", active);
        return { active, full: false };
      });
      await this.ensureCleanupAlarm(result.active);
      if (result.full) return durableObjectJson({ status: "capacity_reached" }, 429);
      return durableObjectJson({ status: "ok", active_session_count: result.active });
    } catch {
      return durableObjectJson({ status: "unavailable" }, 503);
    }
  }

  async ensureCleanupAlarm(activeCount) {
    const existing = await this.state.storage.getAlarm();
    if (activeCount > 0 && (existing === null || existing === undefined)) {
      await this.state.storage.setAlarm(Date.now() + 10_000);
    } else if (activeCount === 0 && existing !== null && existing !== undefined) {
      await this.state.storage.deleteAlarm();
    }
  }

  async alarm() {
    const now = Date.now();
    const active = await this.state.storage.transaction(async (transaction) => {
      const entries = await transaction.list({ prefix: ACTIVE_SESSION_PREFIX, limit: ACTIVE_SESSION_MAX + 1 });
      let count = 0;
      for (const [key, lastSeen] of entries) {
        const timestamp = Number(lastSeen);
        if (!Number.isFinite(timestamp) || now - timestamp > ACTIVE_SESSION_TTL_MS) {
          await transaction.delete(key);
        } else {
          count += 1;
        }
      }
      count = Math.min(count, ACTIVE_SESSION_MAX);
      await transaction.put("meta:active-count", count);
      return count;
    });
    await this.ensureCleanupAlarm(active);
  }
}

export class CommunityJokes {
  constructor(state, env) {
    this.state = state;
    this.env = env;
  }

  get kv() {
    return this.env?.UNIQUE_DOWNLOADS || null;
  }

  async fetch(request) {
    const url = new URL(request.url);
    const path = url.pathname;
    if (path === "/feed" && (request.method === "GET" || request.method === "HEAD")) {
      return this.readFeed(request);
    }
    if (path === "/submit" && request.method === "POST") return this.submit(request);
    const reaction = path.match(/^\/reaction\/([0-9a-f-]{36})$/i);
    if (reaction && request.method === "POST") return this.react(request, reaction[1]);
    const report = path.match(/^\/report\/([0-9a-f-]{36})$/i);
    if (report && request.method === "POST") return this.report(request, report[1]);
    const comment = path.match(/^\/comment\/([0-9a-f-]{36})$/i);
    if (comment && request.method === "POST") return this.comment(request, comment[1]);
    const commentDel = path.match(/^\/comment-delete\/([0-9a-f-]{36})$/i);
    if (commentDel && request.method === "POST") return this.deleteComment(request, commentDel[1]);
    const edit = path.match(/^\/edit\/([0-9a-f-]{36})$/i);
    if (edit && request.method === "POST") return this.edit(request, edit[1]);
    const del = path.match(/^\/delete\/([0-9a-f-]{36})$/i);
    if (del && request.method === "POST") return this.deletePost(request, del[1]);
    if (path === "/me" && request.method === "GET") return this.me(request);
    if (path === "/me" && request.method === "PUT") return this.setMe(request);
    return durableObjectJson({ status: "not_found" }, 404, request.method);
  }

  async readFeed(request) {
    const clientId = request.headers.get("x-xydesk-client-id") || "";
    if (clientId && !COMMUNITY_CLIENT_ID_RE.test(clientId)) {
      return durableObjectJson({ status: "invalid_client_id" }, 400, request.method);
    }
    try {
      const clientHash = clientId ? await hashedCommunityClientId(clientId) : null;
      const entries = await this.state.storage.list({ prefix: "joke:", reverse: true, limit: COMMUNITY_JOKE_MAX_ITEMS });
      const items = [];
      for (const [, joke] of entries) {
        if (!joke || joke.hidden) continue;
        const viewerReaction = clientHash
          ? await this.state.storage.get(`joke-reaction:${joke.id}:${clientHash}`) || null
          : null;
        const commentEntries = await this.state.storage.list({
          prefix: `joke-comments:${joke.id}:`,
          reverse: true,
          limit: COMMUNITY_COMMENT_FEED_SIZE,
        });
        const comments = Array.from(commentEntries.values()).map((c) => communityCommentPublicView(c, clientHash));
        const isOwn = Boolean(clientHash && joke.author_hash === clientHash);
        let avatarUrl = null;
        if (joke.author_hash && this.kv) {
          const head = await this.kv.getWithMetadata(`feed-avatar:${joke.author_hash}`).catch(() => null);
          if (head?.value !== null && head?.value !== undefined) {
            avatarUrl = `/api/jokes/avatar/${joke.author_hash}`;
          }
        }
        items.push(communityJokePublicView(joke, viewerReaction, comments, { isOwn, avatarUrl }));
        if (items.length >= COMMUNITY_JOKE_FEED_SIZE) break;
      }
      return durableObjectJson({ status: "ok", items }, 200, request.method);
    } catch {
      return durableObjectJson({ status: "unavailable" }, 503, request.method);
    }
  }

  async submit(request) {
    // Lampiran base64 bisa ~2MB; dengan batas default 2KB postingan bergambar
    // selalu ditolak 413 (akar bug "foto/stiker/GIF belum bisa").
    const parsed = await readCommunityJson(request, 2_400_000);
    if (parsed.error) return parsed.error;
    const clientId = parsed.body?.client_id;
    if (!COMMUNITY_CLIENT_ID_RE.test(String(clientId || ""))) {
      return durableObjectJson({ status: "invalid_client_id" }, 400, request.method);
    }
    if (typeof parsed.body?.text !== "string") {
      return durableObjectJson({ status: "invalid_text" }, 400, request.method);
    }
    const text = parsed.body.text.replace(/\r\n?/g, "\n").replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g, "").trim();
    const chars = [...text].length;
    const hasAttachmentBody = Boolean(parsed.body?.attachment && typeof parsed.body.attachment === "object");
    if (chars > COMMUNITY_JOKE_MAX_CHARS || /(?:https?:\/\/|www\.)/i.test(text) || (chars < 3 && !hasAttachmentBody)) {
      return durableObjectJson({ status: "invalid_text", max_chars: COMMUNITY_JOKE_MAX_CHARS }, 400, request.method);
    }

    try {
      const clientHash = await hashedCommunityClientId(clientId);
      const now = Date.now();
      const outcome = await this.state.storage.transaction(async (transaction) => {
        const rateKey = `joke-rate:${clientHash}`;
        const lastPostAt = Number(await transaction.get(rateKey)) || 0;
        if (now - lastPostAt < COMMUNITY_JOKE_POST_INTERVAL_MS) {
          return { status: "rate_limited", retry_after_seconds: Math.ceil((COMMUNITY_JOKE_POST_INTERVAL_MS - (now - lastPostAt)) / 1_000) };
        }

        const id = crypto.randomUUID();
        const displayName = sanitizeCommunityDisplayName(parsed.body?.display_name);
        const storedName = displayName || await transaction.get(`account-name:${clientHash}`) || null;
        if (displayName) await transaction.put(`account-name:${clientHash}`, displayName);

        // Lampiran foto/stiker/gif opsional (disimpan di KV, bukan storage DO).
        let attachment = null;
        const rawAtt = parsed.body?.attachment;
        if (rawAtt && typeof rawAtt === "object") {
          const mime = COMMUNITY_ATTACHMENT_MIMES.includes(rawAtt.mime) ? rawAtt.mime : null;
          const bytes = decodeBase64Limited(String(rawAtt.data || ""), COMMUNITY_ATTACHMENT_MAX_BYTES);
          if (!mime || !bytes) return { status: "invalid_attachment" };
          attachment = { mime, bytes };
        }

        const hasText = chars >= 3;
        if (!hasText && !attachment) {
          return { status: "invalid_text", max_chars: COMMUNITY_JOKE_MAX_CHARS };
        }
        const joke = {
          id,
          text: hasText ? text : "",
          created_at: now,
          author_name: storedName,
          author_hash: clientHash,
          attachment: attachment ? { mime: attachment.mime, url: `/api/jokes/attachment/${id}` } : null,
          reactions: Object.fromEntries(COMMUNITY_JOKE_REACTIONS.map((emoji) => [emoji, 0])),
          comment_count: 0,
          report_count: 0,
          hidden: false,
        };
        const key = communityJokeListKey(now, id);
        await transaction.put(key, joke);
        await transaction.put(`joke-id:${id}`, key);
        await transaction.put(rateKey, now);

        const posts = await transaction.list({ prefix: "joke:", limit: COMMUNITY_JOKE_MAX_ITEMS + 1 });
        if (posts.size > COMMUNITY_JOKE_MAX_ITEMS) {
          const oldestKey = posts.keys().next().value;
          const oldest = posts.get(oldestKey);
          if (oldest?.id) {
            await transaction.delete(oldestKey);
            await transaction.delete(`joke-id:${oldest.id}`);
            const oldReactions = await transaction.list({ prefix: `joke-reaction:${oldest.id}:`, limit: 1_000 });
            const oldReports = await transaction.list({ prefix: `joke-report:${oldest.id}:`, limit: 1_000 });
            const oldComments = await transaction.list({ prefix: `joke-comments:${oldest.id}:`, limit: 1_000 });
            for (const reactionKey of oldReactions.keys()) await transaction.delete(reactionKey);
            for (const commentKey of oldComments.keys()) await transaction.delete(commentKey);
            for (const reportKey of oldReports.keys()) await transaction.delete(reportKey);
          }
        }
        return { status: "created", joke: communityJokePublicView(joke, null, null, { isOwn: true }), pendingAttachment: attachment };
      });
      if (outcome.status === "rate_limited") return durableObjectJson(outcome, 429, request.method);
      if (outcome.status === "created" && outcome.pendingAttachment && this.kv) {
        await this.kv.put(`feed-att:${outcome.joke.id}`, outcome.pendingAttachment.bytes, {
          metadata: { mime: outcome.pendingAttachment.mime },
        });
      }
      return durableObjectJson(outcome, 201, request.method);
    } catch {
      return durableObjectJson({ status: "unavailable" }, 503, request.method);
    }
  }

  async edit(request, jokeId) {
    const parsed = await readCommunityJson(request, 2_400_000);
    if (parsed.error) return parsed.error;
    const clientId = parsed.body?.client_id;
    if (!COMMUNITY_CLIENT_ID_RE.test(String(clientId || ""))) {
      return durableObjectJson({ status: "invalid_client_id" }, 400, request.method);
    }
    try {
      const clientHash = await hashedCommunityClientId(clientId);
      const outcome = await this.state.storage.transaction(async (transaction) => {
        const listKey = await transaction.get(`joke-id:${jokeId}`);
        if (!listKey) return { status: "not_found" };
        const joke = await transaction.get(listKey);
        if (!joke || joke.hidden) return { status: "not_found" };
        if (joke.author_hash !== clientHash) return { status: "forbidden" };

        let text = joke.text;
        if (typeof parsed.body?.text === "string") {
          text = parsed.body.text.replace(/\r\n?/g, "\n")
            .replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g, "").trim();
          const chars = [...text].length;
          if (chars > COMMUNITY_JOKE_MAX_CHARS || /(?:https?:\/\/|www\.)/i.test(text)) {
            return { status: "invalid_text", max_chars: COMMUNITY_JOKE_MAX_CHARS };
          }
        }
        let attachment = joke.attachment || null;
        let pending = null;
        const rawAtt = parsed.body?.attachment;
        if (rawAtt === null) {
          attachment = null; // hapus lampiran
        } else if (rawAtt && typeof rawAtt === "object") {
          const mime = COMMUNITY_ATTACHMENT_MIMES.includes(rawAtt.mime) ? rawAtt.mime : null;
          const bytes = decodeBase64Limited(String(rawAtt.data || ""), COMMUNITY_ATTACHMENT_MAX_BYTES);
          if (!mime || !bytes) return { status: "invalid_attachment" };
          attachment = { mime, url: `/api/jokes/attachment/${jokeId}` };
          pending = { mime, bytes };
        }
        const hasText = [...text].length >= 3;
        if (!hasText && !attachment) return { status: "invalid_text", max_chars: COMMUNITY_JOKE_MAX_CHARS };

        joke.text = text;
        joke.attachment = attachment;
        joke.edited_at = Date.now();
        await transaction.put(listKey, joke);
        return { status: "edited", joke: communityJokePublicView(joke, null, null, { isOwn: true }), pendingAttachment: pending, jokeId };
      });
      if (["not_found", "forbidden", "invalid_text", "invalid_attachment"].includes(outcome.status)) {
        const code = outcome.status === "forbidden" ? 403 : outcome.status === "not_found" ? 404 : 400;
        return durableObjectJson(outcome, code, request.method);
      }
      if (outcome.pendingAttachment && this.kv) {
        await this.kv.put(`feed-att:${outcome.jokeId}`, outcome.pendingAttachment.bytes, {
          metadata: { mime: outcome.pendingAttachment.mime },
        });
      }
      if (outcome.pendingAttachment === null || (outcome.joke && !outcome.joke.attachment)) {
        // lampiran dihapus
        if (this.kv) await this.kv.delete(`feed-att:${jokeId}`).catch(() => {});
      }
      return durableObjectJson(outcome, 200, request.method);
    } catch {
      return durableObjectJson({ status: "unavailable" }, 503, request.method);
    }
  }

  async deletePost(request, jokeId) {
    const parsed = await readCommunityJson(request, 1_024);
    if (parsed.error) return parsed.error;
    const clientId = parsed.body?.client_id;
    if (!COMMUNITY_CLIENT_ID_RE.test(String(clientId || ""))) {
      return durableObjectJson({ status: "invalid_client_id" }, 400, request.method);
    }
    try {
      const clientHash = await hashedCommunityClientId(clientId);
      const outcome = await this.state.storage.transaction(async (transaction) => {
        const listKey = await transaction.get(`joke-id:${jokeId}`);
        if (!listKey) return { status: "not_found" };
        const joke = await transaction.get(listKey);
        if (!joke) return { status: "not_found" };
        if (joke.author_hash !== clientHash) return { status: "forbidden" };
        await transaction.delete(listKey);
        await transaction.delete(`joke-id:${jokeId}`);
        const reactions = await transaction.list({ prefix: `joke-reaction:${jokeId}:`, limit: 1_000 });
        const comments = await transaction.list({ prefix: `joke-comments:${jokeId}:`, limit: 1_000 });
        const reports = await transaction.list({ prefix: `joke-report:${jokeId}:`, limit: 1_000 });
        for (const k of reactions.keys()) await transaction.delete(k);
        for (const k of comments.keys()) await transaction.delete(k);
        for (const k of reports.keys()) await transaction.delete(k);
        return { status: "deleted", hadAttachment: Boolean(joke.attachment) };
      });
      if (outcome.status === "not_found") return durableObjectJson(outcome, 404, request.method);
      if (outcome.status === "forbidden") return durableObjectJson(outcome, 403, request.method);
      if (outcome.hadAttachment && this.kv) await this.kv.delete(`feed-att:${jokeId}`).catch(() => {});
      return durableObjectJson(outcome, 200, request.method);
    } catch {
      return durableObjectJson({ status: "unavailable" }, 503, request.method);
    }
  }

  async react(request, jokeId) {
    if (!ACTIVE_SESSION_ID_RE.test(jokeId)) {
      return durableObjectJson({ status: "invalid_joke_id" }, 400, request.method);
    }
    const parsed = await readCommunityJson(request, 1_024);
    if (parsed.error) return parsed.error;
    const clientId = parsed.body?.client_id;
    const emoji = parsed.body?.emoji;
    if (!COMMUNITY_CLIENT_ID_RE.test(String(clientId || "")) || !COMMUNITY_JOKE_REACTIONS.includes(emoji)) {
      return durableObjectJson({ status: "invalid_reaction" }, 400, request.method);
    }
    try {
      const clientHash = await hashedCommunityClientId(clientId);
      const outcome = await this.state.storage.transaction(async (transaction) => {
        const listKey = await transaction.get(`joke-id:${jokeId}`);
        if (!listKey) return { status: "not_found" };
        const joke = await transaction.get(listKey);
        if (!joke || joke.hidden) return { status: "not_found" };

        const reactionKey = `joke-reaction:${jokeId}:${clientHash}`;
        const previous = await transaction.get(reactionKey);
        const reactions = { ...joke.reactions };
        if (previous === emoji) {
          reactions[emoji] = Math.max(0, (Number(reactions[emoji]) || 0) - 1);
          await transaction.delete(reactionKey);
          joke.reactions = reactions;
          await transaction.put(listKey, joke);
          return { status: "ok", joke: communityJokePublicView(joke, null) };
        }
        if (previous && COMMUNITY_JOKE_REACTIONS.includes(previous)) {
          reactions[previous] = Math.max(0, (Number(reactions[previous]) || 0) - 1);
        }
        reactions[emoji] = (Number(reactions[emoji]) || 0) + 1;
        joke.reactions = reactions;
        await transaction.put(reactionKey, emoji);
        await transaction.put(listKey, joke);
        return { status: "ok", joke: communityJokePublicView(joke, emoji) };
      });
      return durableObjectJson(outcome, outcome.status === "not_found" ? 404 : 200, request.method);
    } catch {
      return durableObjectJson({ status: "unavailable" }, 503, request.method);
    }
  }

  // Hapus komentar milik sendiri (permintaan pemilik 2026-10-15: hapus
  // komentar via bottom sheet ala TikTok). Komentar lama tanpa author_hash
  // tidak bisa dihapus.
  async deleteComment(request, jokeId) {
    if (!ACTIVE_SESSION_ID_RE.test(jokeId)) {
      return durableObjectJson({ status: "invalid_joke_id" }, 400, request.method);
    }
    const parsed = await readCommunityJson(request, 1_024);
    if (parsed.error) return parsed.error;
    const clientId = parsed.body?.client_id;
    if (!COMMUNITY_CLIENT_ID_RE.test(String(clientId || ""))) {
      return durableObjectJson({ status: "invalid_client_id" }, 400, request.method);
    }
    const commentId = String(parsed.body?.comment_id || "");
    if (!ACTIVE_SESSION_ID_RE.test(commentId)) {
      return durableObjectJson({ status: "invalid_comment_id" }, 400, request.method);
    }
    try {
      const clientHash = await hashedCommunityClientId(clientId);
      const outcome = await this.state.storage.transaction(async (transaction) => {
        const listKey = await transaction.get(`joke-id:${jokeId}`);
        if (!listKey) return { status: "not_found" };
        const joke = await transaction.get(listKey);
        if (!joke || joke.hidden) return { status: "not_found" };
        const entries = await transaction.list({ prefix: `joke-comments:${jokeId}:`, limit: 1_000 });
        let targetKey = null;
        for (const [key, value] of entries) {
          if (value?.id === commentId) { targetKey = key; break; }
        }
        if (!targetKey) return { status: "not_found" };
        const stored = entries.get(targetKey);
        if (!stored?.author_hash || stored.author_hash !== clientHash) return { status: "forbidden" };
        await transaction.delete(targetKey);
        joke.comment_count = Math.max(0, (Number(joke.comment_count) || 0) - 1);
        await transaction.put(listKey, joke);
        return { status: "deleted", comments_total: joke.comment_count };
      });
      if (outcome.status === "not_found") return durableObjectJson(outcome, 404, request.method);
      if (outcome.status === "forbidden") return durableObjectJson(outcome, 403, request.method);
      return durableObjectJson(outcome, 200, request.method);
    } catch {
      return durableObjectJson({ status: "unavailable" }, 503, request.method);
    }
  }

  async comment(request, jokeId) {
    if (!ACTIVE_SESSION_ID_RE.test(jokeId)) {
      return durableObjectJson({ status: "invalid_joke_id" }, 400, request.method);
    }
    const parsed = await readCommunityJson(request);
    if (parsed.error) return parsed.error;
    const clientId = parsed.body?.client_id;
    if (!COMMUNITY_CLIENT_ID_RE.test(String(clientId || ""))) {
      return durableObjectJson({ status: "invalid_client_id" }, 400, request.method);
    }
    if (typeof parsed.body?.text !== "string") {
      return durableObjectJson({ status: "invalid_text" }, 400, request.method);
    }
    const text = parsed.body.text.replace(/\r\n?/g, "\n").replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g, "").trim();
    const chars = [...text].length;
    if (chars < 1 || chars > COMMUNITY_COMMENT_MAX_CHARS || /(?:https?:\/\/|www\.)/i.test(text)) {
      return durableObjectJson({ status: "invalid_text", max_chars: COMMUNITY_COMMENT_MAX_CHARS }, 400, request.method);
    }
    try {
      const clientHash = await hashedCommunityClientId(clientId);
      const now = Date.now();
      const outcome = await this.state.storage.transaction(async (transaction) => {
        const rateKey = `comment-rate:${clientHash}`;
        const lastAt = Number(await transaction.get(rateKey)) || 0;
        if (now - lastAt < COMMUNITY_COMMENT_INTERVAL_MS) {
          return { status: "rate_limited", retry_after_seconds: Math.ceil((COMMUNITY_COMMENT_INTERVAL_MS - (now - lastAt)) / 1_000) };
        }
        const listKey = await transaction.get(`joke-id:${jokeId}`);
        if (!listKey) return { status: "not_found" };
        const joke = await transaction.get(listKey);
        if (!joke || joke.hidden) return { status: "not_found" };

        const requestedName = sanitizeCommunityDisplayName(parsed.body?.display_name);
        const name = requestedName || await transaction.get(`account-name:${clientHash}`) || null;
        if (requestedName) await transaction.put(`account-name:${clientHash}`, requestedName);

        const id = crypto.randomUUID();
        const comment = { id, joke_id: jokeId, name, text, created_at: now, author_hash: clientHash };
        await transaction.put(`joke-comments:${jokeId}:${String(now).padStart(13, "0")}:${id}`, comment);
        await transaction.put(rateKey, now);

        // Batasi komentar per post: buang yang paling tua bila melewati batas.
        const all = await transaction.list({ prefix: `joke-comments:${jokeId}:` });
        const names = Array.from(all.keys());
        if (names.length > COMMUNITY_COMMENT_MAX_PER_JOKE) {
          for (const name of names.slice(0, names.length - COMMUNITY_COMMENT_MAX_PER_JOKE)) {
            await transaction.delete(name);
          }
        }
        joke.comment_count = Math.min(names.length, COMMUNITY_COMMENT_MAX_PER_JOKE);
        await transaction.put(listKey, joke);
        return { status: "ok", comment: communityCommentPublicView(comment, clientHash), comments_total: joke.comment_count };
      });
      return durableObjectJson(outcome, outcome.status === "not_found" ? 404 : outcome.status === "rate_limited" ? 429 : 201, request.method);
    } catch {
      return durableObjectJson({ status: "unavailable" }, 503, request.method);
    }
  }

  async me(request) {
    const clientId = request.headers.get("x-xydesk-client-id") || "";
    if (!COMMUNITY_CLIENT_ID_RE.test(clientId)) {
      return durableObjectJson({ status: "invalid_client_id" }, 400, request.method);
    }
    try {
      const clientHash = await hashedCommunityClientId(clientId);
      const displayName = await this.state.storage.get(`account-name:${clientHash}`) || null;
      let avatarUrl = null;
      if (this.kv) {
        const head = await this.kv.getWithMetadata(`feed-avatar:${clientHash}`).catch(() => null);
        if (head?.value !== null && head?.value !== undefined) avatarUrl = `/api/jokes/avatar/${clientHash}`;
      }
      return durableObjectJson({ status: "ok", display_name: displayName, avatar_url: avatarUrl }, 200, request.method);
    } catch {
      return durableObjectJson({ status: "unavailable" }, 503, request.method);
    }
  }

  async setMe(request) {
    const parsed = await readCommunityJson(request, 2_400_000);
    if (parsed.error) return parsed.error;
    const clientId = parsed.body?.client_id;
    if (!COMMUNITY_CLIENT_ID_RE.test(String(clientId || ""))) {
      return durableObjectJson({ status: "invalid_client_id" }, 400, request.method);
    }
    const displayName = sanitizeCommunityDisplayName(parsed.body?.display_name);
    const wantsAvatar = parsed.body?.avatar !== undefined && parsed.body?.avatar !== null;
    const avatarMime = COMMUNITY_ATTACHMENT_MIMES.includes(parsed.body?.avatar_mime)
      ? parsed.body.avatar_mime : null;
    const avatarBytes = wantsAvatar && parsed.body.avatar !== ""
      ? decodeBase64Limited(String(parsed.body.avatar || ""), COMMUNITY_AVATAR_MAX_BYTES)
      : null;
    if (wantsAvatar && parsed.body.avatar !== "" && (!avatarMime || !avatarBytes)) {
      return durableObjectJson({ status: "invalid_avatar" }, 400, request.method);
    }
    if (!displayName && !wantsAvatar) {
      return durableObjectJson({ status: "invalid_display_name", max_chars: COMMUNITY_DISPLAY_NAME_MAX_CHARS }, 400, request.method);
    }
    try {
      const clientHash = await hashedCommunityClientId(clientId);
      if (displayName) await this.state.storage.put(`account-name:${clientHash}`, displayName);
      if (wantsAvatar) {
        if (parsed.body.avatar === "") {
          if (this.kv) await this.kv.delete(`feed-avatar:${clientHash}`).catch(() => {});
        } else if (this.kv) {
          await this.kv.put(`feed-avatar:${clientHash}`, avatarBytes, { metadata: { mime: avatarMime } });
        }
      }
      const savedName = displayName || await this.state.storage.get(`account-name:${clientHash}`) || null;
      return durableObjectJson({
        status: "ok", display_name: savedName,
        avatar_url: wantsAvatar && parsed.body.avatar !== "" ? `/api/jokes/avatar/${clientHash}` : null,
      }, 200, request.method);
    } catch {
      return durableObjectJson({ status: "unavailable" }, 503, request.method);
    }
  }

  async report(request, jokeId) {
    if (!ACTIVE_SESSION_ID_RE.test(jokeId)) {
      return durableObjectJson({ status: "invalid_joke_id" }, 400, request.method);
    }
    const parsed = await readCommunityJson(request, 1_024);
    if (parsed.error) return parsed.error;
    const clientId = parsed.body?.client_id;
    if (!COMMUNITY_CLIENT_ID_RE.test(String(clientId || ""))) {
      return durableObjectJson({ status: "invalid_client_id" }, 400, request.method);
    }
    try {
      const clientHash = await hashedCommunityClientId(clientId);
      const outcome = await this.state.storage.transaction(async (transaction) => {
        const listKey = await transaction.get(`joke-id:${jokeId}`);
        if (!listKey) return { status: "not_found", hidden: false };
        const joke = await transaction.get(listKey);
        if (!joke || joke.hidden) return { status: "not_found", hidden: true };

        const reportKey = `joke-report:${jokeId}:${clientHash}`;
        const alreadyReported = await transaction.get(reportKey);
        if (!alreadyReported) {
          joke.report_count = (Number(joke.report_count) || 0) + 1;
          if (joke.report_count >= COMMUNITY_JOKE_REPORT_THRESHOLD) joke.hidden = true;
          await transaction.put(reportKey, Date.now());
          await transaction.put(listKey, joke);
        }
        return { status: alreadyReported ? "already_reported" : "reported", hidden: joke.hidden };
      });
      return durableObjectJson(outcome, outcome.status === "not_found" ? 404 : 200, request.method);
    } catch {
      return durableObjectJson({ status: "unavailable" }, 503, request.method);
    }
  }
}

async function pcSignalResponse(request, env) {
  if (!env.PC_SIGNAL_ROOMS) {
    return jsonResponse({ status: "unavailable" }, 503, request.method);
  }
  const url = new URL(request.url);
  if (url.pathname === "/api/pc/rooms") {
    if (request.method !== "POST") return methodNotAllowed("POST");
    const parsed = await readPcSignalJson(request, 512);
    if (parsed.error) return parsed.error;
    if (!parsed.body || typeof parsed.body !== "object" || Array.isArray(parsed.body) || Object.keys(parsed.body).length !== 0) {
      return jsonResponse({ status: "invalid_request" }, 400, request.method);
    }
    const ip = String(request.headers.get("cf-connecting-ip") || "unknown");
    const ipHash = await pcSignalSecretHash(`xydesk-pc-signal-ip:${ip}`);
    const limiter = env.PC_SIGNAL_ROOMS.get(env.PC_SIGNAL_ROOMS.idFromName(`quota:${ipHash}`));
    const limitResponse = await limiter.fetch("https://pc-signal/limit/create", {
      method: "POST",
      headers: { "x-xydesk-ip-hash": ipHash },
    });
    if (!limitResponse.ok) return jsonResponse(await limitResponse.json(), limitResponse.status, request.method);

    const roomId = crypto.randomUUID();
    const room = env.PC_SIGNAL_ROOMS.get(env.PC_SIGNAL_ROOMS.idFromName(roomId));
    const created = await room.fetch("https://pc-signal/create", { method: "POST" });
    const payload = await created.json();
    if (!created.ok) return jsonResponse(payload, created.status, request.method);
    return jsonResponse({ ...payload, room_id: roomId }, created.status, request.method);
  }

  const match = url.pathname.match(/^\/api\/pc\/rooms\/([0-9a-f-]{36})\/(join|signal)$/i);
  if (!match || !PC_SIGNAL_ROOM_ID_RE.test(match[1])) {
    return jsonResponse({ status: "not_found" }, 404, request.method);
  }
  const room = env.PC_SIGNAL_ROOMS.get(env.PC_SIGNAL_ROOMS.idFromName(match[1]));
  if (match[2] === "join") {
    if (request.method !== "POST") return methodNotAllowed("POST");
    const bounded = await readRequestTextLimited(request, 512);
    if (bounded.tooLarge) return jsonResponse({ status: "payload_too_large" }, 413, request.method);
    if (bounded.invalid) return jsonResponse({ status: "invalid_request" }, 400, request.method);
    return room.fetch("https://pc-signal/join", {
      method: "POST",
      headers: { "content-type": request.headers.get("content-type") || "" },
      body: bounded.text,
    });
  }

  if (request.method === "GET") {
    const after = url.searchParams.get("after");
    const internalUrl = new URL("https://pc-signal/signal");
    if (after !== null) internalUrl.searchParams.set("after", after);
    return room.fetch(internalUrl, {
      method: "GET",
      headers: { authorization: request.headers.get("authorization") || "" },
    });
  }
  if (request.method === "POST") {
    const bounded = await readRequestTextLimited(request, PC_SIGNAL_MAX_BODY_BYTES);
    if (bounded.tooLarge) return jsonResponse({ status: "payload_too_large" }, 413, request.method);
    if (bounded.invalid) return jsonResponse({ status: "invalid_request" }, 400, request.method);
    return room.fetch("https://pc-signal/signal", {
      method: "POST",
      headers: {
        "authorization": request.headers.get("authorization") || "",
        "content-type": request.headers.get("content-type") || "",
      },
      body: bounded.text,
    });
  }
  return methodNotAllowed("GET, POST");
}

async function activeSessionsResponse(request, env) {
  if (!env.ACTIVE_SESSIONS) {
    return jsonResponse({ status: "unavailable", active_session_count: null }, 503, request.method);
  }
  try {
    const id = env.ACTIVE_SESSIONS.idFromName("xydesk-remote-global");
    const stub = env.ACTIVE_SESSIONS.get(id);
    const response = await stub.fetch("https://active-sessions/count");
    const data = await response.json();
    return jsonResponse({
      ...data,
      scope: "client-reported-anonymous-rdp-sessions",
      note: "Perkiraan sesi aplikasi yang mengirim heartbeat anonim (aktif secara default, dapat dimatikan di aplikasi); bukan jumlah pengguna unik atau semua koneksi RDP.",
      checked_at: new Date().toISOString(),
    }, response.status, request.method, { "cache-control": "no-store" });
  } catch {
    return jsonResponse({ status: "unavailable", active_session_count: null }, 503, request.method);
  }
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
    const rdpPort = Number(body?.rdp_port);
    if (!Number.isInteger(rdpPort) || rdpPort < 1 || rdpPort > 65535) {
      return jsonResponse({ status: "invalid_port" }, 400, request.method);
    }
    const tailnet = typeof body?.tailnet === "string" ? body.tailnet.trim().toLowerCase() : "";
    if (tailnet.length > 253 || !/^[a-z0-9.-]*$/.test(tailnet)) {
      return jsonResponse({ status: "invalid_tailnet" }, 400, request.method);
    }
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

async function sessionReportResponse(request, env) {
  if (!env.ACTIVE_SESSIONS) {
    return jsonResponse({ status: "unavailable" }, 503, request.method);
  }
  const contentType = request.headers.get("content-type") || "";
  if (!isJsonContentType(contentType)) {
    return jsonResponse({ status: "invalid_content_type" }, 415, request.method);
  }
  const contentLength = Number(request.headers.get("content-length") || 0);
  if (contentLength > 512) return jsonResponse({ status: "payload_too_large" }, 413, request.method);

  let raw;
  try {
    raw = await request.text();
  } catch {
    return jsonResponse({ status: "invalid_request" }, 400, request.method);
  }
  if (raw.length > 512) return jsonResponse({ status: "payload_too_large" }, 413, request.method);

  let body;
  try {
    body = JSON.parse(raw);
  } catch {
    return jsonResponse({ status: "invalid_json" }, 400, request.method);
  }
  if (!ACTIVE_SESSION_ID_RE.test(String(body?.session_id || "")) || !["heartbeat", "end"].includes(body?.action)) {
    return jsonResponse({ status: "invalid_session_report" }, 400, request.method);
  }

  try {
    const id = env.ACTIVE_SESSIONS.idFromName("xydesk-remote-global");
    const stub = env.ACTIVE_SESSIONS.get(id);
    const response = await stub.fetch("https://active-sessions/update", {
      method: "POST",
      headers: { "content-type": "application/json; charset=utf-8" },
      body: JSON.stringify({ action: body.action, session_id: body.session_id }),
    });
    const data = await response.json();
    return jsonResponse(data, response.status, request.method, { "cache-control": "no-store" });
  } catch {
    return jsonResponse({ status: "unavailable" }, 503, request.method);
  }
}

async function serveCommunityBinary(request, env, url) {
  if (request.method !== "GET" && request.method !== "HEAD") return methodNotAllowed("GET, HEAD");
  if (!env.UNIQUE_DOWNLOADS) return jsonResponse({ status: "unavailable" }, 503, request.method);
  const m = url.pathname.match(/^\/api\/jokes\/(attachment|avatar)\/([^/]+)$/i);
  if (!m) return jsonResponse({ status: "not_found" }, 404, request.method);
  const key = m[1] === "attachment" ? `feed-att:${m[2]}` : null;
  const avatarKey = m[1] === "avatar" && /^[0-9a-f]{64}$/i.test(m[2]) ? `feed-avatar:${m[2].toLowerCase()}` : null;
  const kvKey = key || avatarKey;
  if (!kvKey) return jsonResponse({ status: "not_found" }, 404, request.method);
  try {
    const value = await env.UNIQUE_DOWNLOADS.getWithMetadata(kvKey, { type: "arrayBuffer" });
    if (!value || value.value === null || value.value === undefined) {
      return jsonResponse({ status: "not_found" }, 404, request.method);
    }
    const mime = COMMUNITY_ATTACHMENT_MIMES.includes(value.metadata?.mime) ? value.metadata.mime : "application/octet-stream";
    return new Response(request.method === "HEAD" ? null : value.value, {
      status: 200,
      headers: {
        "content-type": mime,
        "cache-control": "public, max-age=86400",
        "access-control-allow-origin": "*",
      },
    });
  } catch {
    return jsonResponse({ status: "unavailable" }, 503, request.method);
  }
}

// Pemeriksaan pembaruan untuk app: tag rilis publik terbaru + ringkasan
// changelog. App membandingkan versinya sendiri dengan `version` di sini.
async function updateCheckResponse(request, env) {
  try {
    const release = await getLatestPublishedRelease(env);
    if (!release) return jsonResponse({ status: "unavailable" }, 503, request.method);
    const tag = String(release.tag_name || "");
    if (!RELEASE_TAG_PATTERN.test(tag)) {
      return jsonResponse({ status: "unavailable" }, 503, request.method);
    }
    const body = String(release.body || "");
    // Body rilis = seluruh CHANGELOG: header "# ..." (opsional) lalu blok
    // "## vX.Y.Z" per versi. Ambil blok pertama yang diawali pola versi.
    const sections = body.split(/\n## |^## /);
    const latest = sections.find((part) => /^v?\d+\.\d+/.test(part.trim())) || sections[0] || "";
    const excerpt = latest.trim().slice(0, 2000);
    return jsonResponse(
      {
        status: "ok",
        tag,
        version: tag.replace(/^v/i, ""),
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

// ============================================================
// Feedback board: laporan bug / saran / pertanyaan dari user,
// dibalas oleh founder/dev (permintaan pemilik 2026-10-15).
// Domain: report.xydeskremote.biz.id / ask.xydeskremote.biz.id.
// Identitas user = client ID anonim (seperti feed komunitas);
// balasan staff ditandai lewat header internal yang hanya bisa
// disuntikkan oleh worker luar setelah verifikasi token rahasia.
// ============================================================
const FEEDBACK_TYPES = new Set(["bug", "idea", "question", "other"]);
const FEEDBACK_STATUSES = new Set(["open", "progress", "done"]);
const FEEDBACK_TEXT_MAX_CHARS = 2000;
const FEEDBACK_REPLY_MAX_CHARS = 1000;
const FEEDBACK_NAME_MAX_CHARS = 30;
const FEEDBACK_MAX_ITEMS = 400;
const FEEDBACK_MAX_REPLIES = 60;
const FEEDBACK_SUBMIT_INTERVAL_MS = 30_000;
const FEEDBACK_REPLY_INTERVAL_MS = 10_000;

function sanitizeFeedbackText(value, maxChars) {
  if (typeof value !== "string") return null;
  const clean = value.replace(/\r\n?/g, "\n").replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g, "").trim();
  const chars = [...clean];
  if (chars.length < 1 || chars.length > maxChars) return null;
  return chars.join("");
}

function sanitizeFeedbackName(value) {
  if (typeof value !== "string") return null;
  const clean = value.replace(/[\u0000-\u001F\u007F]/g, "").replace(/\s+/g, " ").trim();
  const chars = [...clean];
  if (chars.length < 1 || chars.length > FEEDBACK_NAME_MAX_CHARS) return null;
  return chars.join("");
}

function feedbackPublicView(entry, replies) {
  return {
    id: entry.id,
    created_at: entry.created_at,
    name: entry.name || null,
    type: entry.type,
    text: entry.text,
    status: entry.status,
    reply_count: replies.length,
    last_activity: entry.last_activity || entry.created_at,
    replies,
  };
}

function feedbackReplyPublicView(reply) {
  return {
    id: reply.id,
    name: reply.staff ? "XyDesk Team" : (reply.name || null),
    text: reply.text,
    staff: Boolean(reply.staff),
    created_at: reply.created_at,
  };
}

export class FeedbackBoard {
  constructor(state, env) {
    this.state = state;
    this.env = env;
  }

  async fetch(request) {
    const url = new URL(request.url);
    const path = url.pathname;
    const staff = request.headers.get("x-xydesk-verified-staff") === "1";
    if (path === "/list" && (request.method === "GET" || request.method === "HEAD")) {
      return this.list(request);
    }
    if (path === "/submit" && request.method === "POST") return this.submit(request);
    const reply = path.match(/^\/reply\/([0-9a-f-]{36})$/i);
    if (reply && request.method === "POST") return this.reply(request, reply[1], staff);
    const status = path.match(/^\/status\/([0-9a-f-]{36})$/i);
    if (status && request.method === "POST") return this.setStatus(request, status[1], staff);
    const hide = path.match(/^\/hide\/([0-9a-f-]{36})$/i);
    if (hide && request.method === "POST") return this.hide(request, hide[1], staff);
    return durableObjectJson({ status: "not_found" }, 404, request.method);
  }

  async list(request) {
    try {
      const entries = await this.state.storage.list({ prefix: "fb:", limit: FEEDBACK_MAX_ITEMS, reverse: true });
      const items = [];
      for (const [key, entry] of entries) {
        if (!entry || entry.hidden) continue;
        const replyEntries = await this.state.storage.list({ prefix: `fb-reply:${entry.id}:`, limit: FEEDBACK_MAX_REPLIES });
        const replies = Array.from(replyEntries.values())
          .sort((a, b) => (a.created_at || 0) - (b.created_at || 0))
          .map(feedbackReplyPublicView);
        items.push(feedbackPublicView(entry, replies));
        if (items.length >= 50) break;
      }
      return durableObjectJson({ status: "ok", items }, 200, request.method);
    } catch {
      return durableObjectJson({ status: "unavailable" }, 503, request.method);
    }
  }

  async submit(request) {
    const parsed = await readCommunityJson(request, 8_192);
    if (parsed.error) return parsed.error;
    const clientId = parsed.body?.client_id;
    if (!COMMUNITY_CLIENT_ID_RE.test(String(clientId || ""))) {
      return durableObjectJson({ status: "invalid_client_id" }, 400, request.method);
    }
    const type = String(parsed.body?.type || "");
    if (!FEEDBACK_TYPES.has(type)) {
      return durableObjectJson({ status: "invalid_type" }, 400, request.method);
    }
    const text = sanitizeFeedbackText(parsed.body?.text, FEEDBACK_TEXT_MAX_CHARS);
    if (!text) {
      return durableObjectJson({ status: "invalid_text", max_chars: FEEDBACK_TEXT_MAX_CHARS }, 400, request.method);
    }
    const name = sanitizeFeedbackName(parsed.body?.name);
    try {
      const clientHash = await hashedCommunityClientId(clientId);
      const now = Date.now();
      const outcome = await this.state.storage.transaction(async (transaction) => {
        const rateKey = `fb-rate:${clientHash}`;
        const lastAt = Number(await transaction.get(rateKey)) || 0;
        if (now - lastAt < FEEDBACK_SUBMIT_INTERVAL_MS) {
          return { status: "rate_limited", retry_after_seconds: Math.ceil((FEEDBACK_SUBMIT_INTERVAL_MS - (now - lastAt)) / 1_000) };
        }
        const id = crypto.randomUUID();
        const entry = { id, created_at: now, last_activity: now, name, type, text, status: "open", hidden: false };
        await transaction.put(`fb:${String(now).padStart(13, "0")}:${id}`, entry);
        await transaction.put(`fb-id:${id}`, `fb:${String(now).padStart(13, "0")}:${id}`);
        await transaction.put(rateKey, now);
        // Batasi jumlah thread: buang yang paling tua beserta balasannya.
        const all = await transaction.list({ prefix: "fb:", limit: FEEDBACK_MAX_ITEMS + 50 });
        const keys = Array.from(all.keys()).sort();
        if (keys.length > FEEDBACK_MAX_ITEMS) {
          for (const oldKey of keys.slice(0, keys.length - FEEDBACK_MAX_ITEMS)) {
            const old = all.get(oldKey);
            await transaction.delete(oldKey);
            if (old?.id) {
              await transaction.delete(`fb-id:${old.id}`);
              const replies = await transaction.list({ prefix: `fb-reply:${old.id}:`, limit: 1_000 });
              for (const k of replies.keys()) await transaction.delete(k);
            }
          }
        }
        return { status: "created", item: feedbackPublicView(entry, []) };
      });
      if (outcome.status === "rate_limited") return durableObjectJson(outcome, 429, request.method);
      return durableObjectJson(outcome, 201, request.method);
    } catch {
      return durableObjectJson({ status: "unavailable" }, 503, request.method);
    }
  }

  async reply(request, feedbackId, staff) {
    if (!ACTIVE_SESSION_ID_RE.test(feedbackId)) {
      return durableObjectJson({ status: "invalid_feedback_id" }, 400, request.method);
    }
    const parsed = await readCommunityJson(request, 4_096);
    if (parsed.error) return parsed.error;
    const clientId = parsed.body?.client_id;
    if (!COMMUNITY_CLIENT_ID_RE.test(String(clientId || ""))) {
      return durableObjectJson({ status: "invalid_client_id" }, 400, request.method);
    }
    const text = sanitizeFeedbackText(parsed.body?.text, FEEDBACK_REPLY_MAX_CHARS);
    if (!text) {
      return durableObjectJson({ status: "invalid_text", max_chars: FEEDBACK_REPLY_MAX_CHARS }, 400, request.method);
    }
    const name = sanitizeFeedbackName(parsed.body?.name);
    try {
      const clientHash = await hashedCommunityClientId(clientId);
      const now = Date.now();
      const outcome = await this.state.storage.transaction(async (transaction) => {
        const rateKey = `fb-reply-rate:${clientHash}`;
        const lastAt = Number(await transaction.get(rateKey)) || 0;
        if (!staff && now - lastAt < FEEDBACK_REPLY_INTERVAL_MS) {
          return { status: "rate_limited", retry_after_seconds: Math.ceil((FEEDBACK_REPLY_INTERVAL_MS - (now - lastAt)) / 1_000) };
        }
        const key = await transaction.get(`fb-id:${feedbackId}`);
        if (!key) return { status: "not_found" };
        const entry = await transaction.get(key);
        if (!entry || entry.hidden) return { status: "not_found" };
        const id = crypto.randomUUID();
        const reply = { id, feedback_id: feedbackId, name, text, staff, created_at: now };
        await transaction.put(`fb-reply:${feedbackId}:${String(now).padStart(13, "0")}:${id}`, reply);
        await transaction.put(rateKey, now);
        entry.last_activity = now;
        await transaction.put(key, entry);
        return { status: "created", reply: feedbackReplyPublicView(reply) };
      });
      if (outcome.status === "not_found") return durableObjectJson(outcome, 404, request.method);
      if (outcome.status === "rate_limited") return durableObjectJson(outcome, 429, request.method);
      return durableObjectJson(outcome, 201, request.method);
    } catch {
      return durableObjectJson({ status: "unavailable" }, 503, request.method);
    }
  }

  async setStatus(request, feedbackId, staff) {
    if (!staff) return durableObjectJson({ status: "forbidden" }, 403, request.method);
    if (!ACTIVE_SESSION_ID_RE.test(feedbackId)) {
      return durableObjectJson({ status: "invalid_feedback_id" }, 400, request.method);
    }
    const parsed = await readCommunityJson(request, 512);
    if (parsed.error) return parsed.error;
    const status = String(parsed.body?.status || "");
    if (!FEEDBACK_STATUSES.has(status)) {
      return durableObjectJson({ status: "invalid_status" }, 400, request.method);
    }
    try {
      const outcome = await this.state.storage.transaction(async (transaction) => {
        const key = await transaction.get(`fb-id:${feedbackId}`);
        if (!key) return { status: "not_found" };
        const entry = await transaction.get(key);
        if (!entry || entry.hidden) return { status: "not_found" };
        entry.status = status;
        await transaction.put(key, entry);
        return { status: "ok", item_status: status };
      });
      if (outcome.status === "not_found") return durableObjectJson(outcome, 404, request.method);
      return durableObjectJson(outcome, 200, request.method);
    } catch {
      return durableObjectJson({ status: "unavailable" }, 503, request.method);
    }
  }

  async hide(request, feedbackId, staff) {
    if (!staff) return durableObjectJson({ status: "forbidden" }, 403, request.method);
    if (!ACTIVE_SESSION_ID_RE.test(feedbackId)) {
      return durableObjectJson({ status: "invalid_feedback_id" }, 400, request.method);
    }
    try {
      const outcome = await this.state.storage.transaction(async (transaction) => {
        const key = await transaction.get(`fb-id:${feedbackId}`);
        if (!key) return { status: "not_found" };
        const entry = await transaction.get(key);
        if (!entry) return { status: "not_found" };
        entry.hidden = true;
        await transaction.put(key, entry);
        return { status: "hidden" };
      });
      if (outcome.status === "not_found") return durableObjectJson(outcome, 404, request.method);
      return durableObjectJson(outcome, 200, request.method);
    } catch {
      return durableObjectJson({ status: "unavailable" }, 503, request.method);
    }
  }
}

async function communityJokesResponse(request, env) {
  if (!env.COMMUNITY_JOKES) {
    return jsonResponse({ status: "unavailable" }, 503, request.method);
  }
  const url = new URL(request.url);
  let internal;
  if (url.pathname === "/api/jokes") {
    if (request.method === "GET" || request.method === "HEAD") {
      const clientId = request.headers.get("x-xydesk-client-id") || "";
      if (clientId && !COMMUNITY_CLIENT_ID_RE.test(clientId)) {
        return jsonResponse({ status: "invalid_client_id" }, 400, request.method);
      }
      internal = new Request("https://community-jokes/feed", {
        method: request.method,
        headers: clientId ? { "x-xydesk-client-id": clientId } : {},
      });
    } else if (request.method === "POST") {
      const length = Number(request.headers.get("content-length") || 0);
      if (length > 1_600_000) return jsonResponse({ status: "payload_too_large" }, 413, request.method);
      const bounded = await readRequestTextLimited(request, 1_600_000);
      if (bounded.tooLarge) return jsonResponse({ status: "payload_too_large" }, 413, request.method);
      if (bounded.invalid) return jsonResponse({ status: "invalid_request" }, 400, request.method);
      internal = new Request("https://community-jokes/submit", {
        method: "POST",
        headers: { "content-type": request.headers.get("content-type") || "" },
        body: bounded.text,
      });
    } else {
      return methodNotAllowed("GET, HEAD, POST");
    }
  } else if (url.pathname === "/api/jokes/me") {
    // Profil komunitas ringan: nama tampilan per pemasangan anonim.
    if (request.method === "GET" || request.method === "HEAD") {
      const clientId = request.headers.get("x-xydesk-client-id") || "";
      internal = new Request("https://community-jokes/me", {
        method: request.method,
        headers: clientId ? { "x-xydesk-client-id": clientId } : {},
      });
    } else if (request.method === "PUT") {
      const length = Number(request.headers.get("content-length") || 0);
      if (length > 1_600_000) return jsonResponse({ status: "payload_too_large" }, 413, request.method);
      const bounded = await readRequestTextLimited(request, 1_600_000);
      if (bounded.tooLarge) return jsonResponse({ status: "payload_too_large" }, 413, request.method);
      if (bounded.invalid) return jsonResponse({ status: "invalid_request" }, 400, request.method);
      internal = new Request("https://community-jokes/me", {
        method: "PUT",
        headers: { "content-type": request.headers.get("content-type") || "" },
        body: bounded.text,
      });
    } else {
      return methodNotAllowed("GET, HEAD, PUT");
    }
  } else if (url.pathname.match(/^\/api\/jokes\/(attachment|avatar)\//i)) {
    return serveCommunityBinary(request, env, url);
  } else {
    const match = url.pathname.match(/^\/api\/jokes\/([0-9a-f-]{36})\/(reaction|report|comment-delete|comment|edit|delete)$/i);
    if (!match || request.method !== "POST") {
      return match ? methodNotAllowed("POST") : jsonResponse({ status: "not_found" }, 404, request.method);
    }
    const cap = match[2] === "edit" ? 1_600_000 : 1_024;
    const length = Number(request.headers.get("content-length") || 0);
    if (length > cap) return jsonResponse({ status: "payload_too_large" }, 413, request.method);
    const bounded = await readRequestTextLimited(request, cap);
    if (bounded.tooLarge) return jsonResponse({ status: "payload_too_large" }, 413, request.method);
    if (bounded.invalid) return jsonResponse({ status: "invalid_request" }, 400, request.method);
    internal = new Request(`https://community-jokes/${match[2]}/${match[1]}`, {
      method: "POST",
      headers: { "content-type": request.headers.get("content-type") || "" },
      body: bounded.text,
    });
  }

  try {
    const id = env.COMMUNITY_JOKES.idFromName("xydesk-community-jokes-v1");
    return await env.COMMUNITY_JOKES.get(id).fetch(internal);
  } catch {
    return jsonResponse({ status: "unavailable" }, 503, request.method);
  }
}

async function feedbackResponse(request, env) {
  if (!env.FEEDBACK_BOARD) {
    return jsonResponse({ status: "unavailable" }, 503, request.method);
  }
  const url = new URL(request.url);
  // Token staff diverifikasi di tepi ini; DO hanya menerima flag internal
  // yang tidak bisa dipalsukan dari luar (DO tidak terekspos publik).
  const staffToken = request.headers.get("x-xydesk-staff-token") || "";
  const staffVerified = Boolean(env.FEEDBACK_STAFF_TOKEN && staffToken && staffToken === env.FEEDBACK_STAFF_TOKEN);
  const staffHeader = staffVerified ? { "x-xydesk-verified-staff": "1" } : {};

  let internal;
  if (url.pathname === "/api/feedback" && (request.method === "GET" || request.method === "HEAD")) {
    internal = new Request("https://feedback-board/list", { method: request.method, headers: { ...staffHeader } });
  } else if (url.pathname === "/api/feedback" && request.method === "POST") {
    const length = Number(request.headers.get("content-length") || 0);
    if (length > 9_216) return jsonResponse({ status: "payload_too_large" }, 413, request.method);
    const bounded = await readRequestTextLimited(request, 9_216);
    if (bounded.tooLarge) return jsonResponse({ status: "payload_too_large" }, 413, request.method);
    if (bounded.invalid) return jsonResponse({ status: "invalid_request" }, 400, request.method);
    internal = new Request("https://feedback-board/submit", {
      method: "POST",
      headers: { "content-type": request.headers.get("content-type") || "", ...staffHeader },
      body: bounded.text,
    });
  } else {
    const match = url.pathname.match(/^\/api\/feedback\/([0-9a-f-]{36})\/(reply|status|hide)$/i);
    if (!match || request.method !== "POST") {
      return match ? methodNotAllowed("POST") : jsonResponse({ status: "not_found" }, 404, request.method);
    }
    const cap = match[2] === "reply" ? 5_120 : 1_024;
    const length = Number(request.headers.get("content-length") || 0);
    if (length > cap) return jsonResponse({ status: "payload_too_large" }, 413, request.method);
    const bounded = await readRequestTextLimited(request, cap);
    if (bounded.tooLarge) return jsonResponse({ status: "payload_too_large" }, 413, request.method);
    if (bounded.invalid) return jsonResponse({ status: "invalid_request" }, 400, request.method);
    internal = new Request(`https://feedback-board/${match[2]}/${match[1]}`, {
      method: "POST",
      headers: { "content-type": request.headers.get("content-type") || "", ...staffHeader },
      body: bounded.text,
    });
  }

  try {
    const id = env.FEEDBACK_BOARD.idFromName("xydesk-feedback-board-v1");
    return await env.FEEDBACK_BOARD.get(id).fetch(internal);
  } catch {
    return jsonResponse({ status: "unavailable" }, 503, request.method);
  }
}


const CHAT_HISTORY_LIMIT = 100;
const CHAT_TEXT_MAX = 500;
const CHAT_NAME_MAX = 24;
const CHAT_ATTACHMENT_MAX = 240_000;
const CHAT_MSG_PER_WINDOW = 10;
const CHAT_WINDOW_MS = 20_000;
const DEFAULT_ONESIGNAL_APP_ID = "ffaa87d6-8e1d-42b6-bb4f-ebbbfc0bccbb";
const LEGACY_TEST_NAMES = new Set(["tesbot", "budi", "rizky"]);

function isFounderChatName(name, githubLogin = "") {
  const checkOne = (val) => {
    if (typeof val !== "string") return false;
    const normalized = val.trim().replace(/^@/, "").toLowerCase();
    return normalized === "xykal" || normalized === "xykal (founder)" || normalized.startsWith("xykal ");
  };
  return checkOne(name) || checkOne(githubLogin);
}

function normalizeCountryCode(raw, fallback = "ID") {
  const code = String(raw || "").trim().toUpperCase();
  if (/^[A-Z]{2}$/.test(code) && code !== "XX" && code !== "T1") return code;
  const fb = String(fallback || "ID").trim().toUpperCase();
  return /^[A-Z]{2}$/.test(fb) ? fb : "ID";
}

const LANG_TO_COUNTRY = {
  id: "ID", in: "ID", jv: "ID", su: "ID",
  ms: "MY",
  th: "TH",
  ja: "JP",
  ko: "KR",
  vi: "VN",
  tl: "PH", fil: "PH",
  zh: "CN", "zh-cn": "CN", "zh-tw": "TW",
  ru: "RU",
  uk: "UA",
  ar: "SA",
  hi: "IN", bn: "BD", ta: "IN",
  tr: "TR",
  de: "DE",
  fr: "FR",
  es: "ES",
  pt: "BR",
  it: "IT",
  nl: "NL",
  pl: "PL",
};

const ID_WORD_HINT = /\b(halo|hai|apa|gimana|bagaimana|bisa|bang|bre|bro|kak|gan|aku|kamu|saya|kita|yang|ini|itu|dari|buat|untuk|udah|sudah|belum|tidak|tak|gak|nggak|konek|koneksi|langsung|terima|kasih|makasih|mantap|keren|tolong|cara|pakai|pake|kenapa|dimana|kapan|siapa|boleh|dong|aja|saja|kok|nih|tuh|lagi|sama|kalau|kalo|bukan|dengan|di|ke|jir|anjir|wkwk|wkwkwk|gasken|gas|sip|oke|mantul)\b/i;

function resolveSmartCountry(text, detectedLang, clientCountry, cfCountry) {
  const clean = String(text || "").trim();
  const det = String(detectedLang || "").trim().toLowerCase();
  if (/[\u0e00-\u0e7f]/.test(clean) || det === "th") return "TH";
  if (/[\u3040-\u30ff]/.test(clean) || det === "ja") return "JP";
  if (/[\uac00-\ud7af]/.test(clean) || det === "ko") return "KR";
  if (/[\u0400-\u04ff]/.test(clean) || det === "ru") return "RU";
  if (/[\u0600-\u06ff]/.test(clean) || det === "ar") return "SA";
  if (/[\u4e00-\u9fff]/.test(clean) || det.startsWith("zh")) {
    return det === "zh-tw" || det === "zh-hk" ? "TW" : "CN";
  }
  if (ID_WORD_HINT.test(clean) || det === "id" || det === "in" || det === "jv") return "ID";

  const langCountry = LANG_TO_COUNTRY[det];
  const ccClient = typeof clientCountry === "string" && /^[A-Za-z]{2}$/.test(clientCountry.trim())
    ? clientCountry.trim().toUpperCase()
    : "";
  const ccCf = typeof cfCountry === "string" && /^[A-Za-z]{2}$/.test(cfCountry.trim())
    ? cfCountry.trim().toUpperCase()
    : "";

  // Jika bahasa terdeteksi spesifik (mis. vi, tl, ms, tr, de, fr, es, pt, hi)
  // sedangkan negara dari locale/VPN adalah US/GB/SG umum, utamakan negara bahasa tersebut.
  if (langCountry && (!ccClient || ccClient === "US" || ccClient === "GB")) {
    return langCountry;
  }
  if (ccClient && ccClient !== "XX" && ccClient !== "T1") return ccClient;
  if (langCountry) return langCountry;
  if (ccCf && ccCf !== "XX" && ccCf !== "T1") return ccCf;
  return "ID";
}

function detectCountryFromRequest(request, bodyCountry, text = "", detectedLang = "") {
  const cfCountry = request?.cf?.country
    || request?.headers?.get("cf-ipcountry")
    || request?.headers?.get("CF-Connecting-IP-Country")
    || request?.headers?.get("x-chat-country");
  return resolveSmartCountry(text, detectedLang, bodyCountry, cfCountry);
}

function countryToFlag(countryCode) {
  const cc = normalizeCountryCode(countryCode, "ID");
  return String.fromCodePoint(
    0x1F1E6 + (cc.charCodeAt(0) - 65),
    0x1F1E6 + (cc.charCodeAt(1) - 65),
  );
}

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

async function fetchGoogleClients5Translate(text, sourceLang, targetLang) {
  const q = encodeURIComponent(text);
  const sl = sourceLang || "auto";
  const url = `https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=${sl}&tl=${targetLang}&q=${q}`;
  const res = await fetch(url, {
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

async function fetchGoogleTranslate(text, sourceLang, targetLang) {
  const q = encodeURIComponent(text);
  const sl = sourceLang || "auto";
  const url = `https://translate.googleapis.com/translate_a/single?client=dict-chrome-ex&sl=${sl}&tl=${targetLang}&dt=t&q=${q}`;
  const res = await fetch(url, {
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

async function fetchMyMemoryTranslate(text, sourceLang, targetLang) {
  const q = encodeURIComponent(text);
  let sl = !sourceLang || sourceLang === "auto" ? guessSourceLang(text, "") : sourceLang;
  if (sl.toLowerCase() === String(targetLang || "").toLowerCase()) {
    sl = String(targetLang).toLowerCase() === "id" ? "en" : "id";
  }
  const url = `https://api.mymemory.translated.net/get?q=${q}&langpair=${encodeURIComponent(sl)}%7C${encodeURIComponent(targetLang)}`;
  const res = await fetch(url, {
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

async function translateChatText(text, country = "ID") {
  const clean = String(text || "").trim();
  if (!clean) return { translated: "", targetLang: "EN", sourceLang: "id" };
  const guessedSl = guessSourceLang(clean, country);
  const isSourceId = guessedSl === "id" || guessedSl === "ms";
  const primaryTarget = isSourceId ? "en" : "id";
  const primaryLabel = isSourceId ? "EN" : "ID";

  // Jalur 1: Google clients5 (client=dict-chrome-ex, bebas rate-limit 429 & deteksi semua bahasa)
  try {
    const c5 = await fetchGoogleClients5Translate(clean, "auto", primaryTarget);
    const det = (c5.detected || guessedSl).toLowerCase();
    if (det === "id" && primaryTarget === "id") {
      const c5En = await fetchGoogleClients5Translate(clean, "id", "en");
      if (c5En.translated) {
        return { translated: c5En.translated, targetLang: "EN", sourceLang: "id" };
      }
    }
    if (c5.translated && c5.translated.toLowerCase() !== clean.toLowerCase()) {
      return {
        translated: c5.translated,
        targetLang: primaryLabel,
        sourceLang: det,
      };
    }
  } catch {
    // Lanjut ke jalur 2
  }

  // Jalur 2: Google Translate dict-chrome-ex single
  try {
    const gtx = await fetchGoogleTranslate(clean, "auto", primaryTarget);
    const det = (gtx.detected || guessedSl).toLowerCase();
    if (det === "id" && primaryTarget === "id") {
      const gtxEn = await fetchGoogleTranslate(clean, "id", "en");
      if (gtxEn.translated) {
        return { translated: gtxEn.translated, targetLang: "EN", sourceLang: "id" };
      }
    }
    if (gtx.translated && gtx.translated.toLowerCase() !== clean.toLowerCase()) {
      return {
        translated: gtx.translated,
        targetLang: primaryLabel,
        sourceLang: det,
      };
    }
  } catch {
    // Lanjut ke jalur 3
  }

  // Jalur 3: MyMemory API
  try {
    const mm = await fetchMyMemoryTranslate(clean, guessedSl, primaryTarget);
    if (mm && mm.toLowerCase() !== clean.toLowerCase()) {
      return {
        translated: mm,
        targetLang: primaryLabel,
        sourceLang: guessedSl,
      };
    }
  } catch {
    // Lanjut ke kamus offline
  }

  return offlineTranslateFallback(clean, country);
}

function sanitizeChatExtras(raw) {
  const githubLogin = typeof raw?.github_login === "string"
    ? raw.github_login.trim().replace(/^@/, "").slice(0, 39)
    : "";
  const replyToId = typeof raw?.reply_to_id === "string" ? raw.reply_to_id.trim().slice(0, 64) : "";
  const replyToName = typeof raw?.reply_to_name === "string" ? raw.reply_to_name.trim().slice(0, CHAT_NAME_MAX) : "";
  const replyToText = typeof raw?.reply_to_text === "string" ? raw.reply_to_text.trim().slice(0, 180) : "";
  const rawKind = typeof raw?.attachment_kind === "string" && raw.attachment_kind.trim()
    ? raw.attachment_kind.trim().toLowerCase()
    : typeof raw?.attachment_type === "string"
      ? raw.attachment_type.trim().toLowerCase()
      : "";
  const attachmentKind = rawKind === "image" || rawKind === "photo" ? "photo"
    : rawKind === "file" ? "file"
    : rawKind === "sticker" ? "sticker"
    : "";
  const attachmentType = attachmentKind === "photo" ? "image" : attachmentKind;
  const attachmentName = typeof raw?.attachment_name === "string" ? raw.attachment_name.trim().slice(0, 120) : "";
  const attachmentMime = typeof raw?.attachment_mime === "string" ? raw.attachment_mime.trim().slice(0, 80) : "";
  const attachmentSize = Number.isFinite(Number(raw?.attachment_size)) ? Math.max(0, Math.min(10_000_000, Number(raw.attachment_size))) : 0;
  const attachmentData = typeof raw?.attachment_data === "string" && raw.attachment_data.length <= CHAT_ATTACHMENT_MAX
    ? raw.attachment_data
    : "";
  const avatarUrl = typeof raw?.avatar_url === "string" && raw.avatar_url.startsWith("https://")
    ? raw.avatar_url.trim().slice(0, 300)
    : "";
  const hasAttachment = Boolean(attachmentData || attachmentKind === "sticker" || attachmentKind === "file");
  return {
    github_login: githubLogin,
    reply_to_id: replyToId,
    reply_to_name: replyToName,
    reply_to_text: replyToText,
    attachment_kind: hasAttachment ? attachmentKind : "",
    attachment_type: hasAttachment ? attachmentType : "",
    attachment_name: attachmentName,
    attachment_mime: attachmentMime,
    attachment_size: attachmentSize,
    attachment_data: attachmentData,
    avatar_url: avatarUrl,
  };
}

export class GlobalChat {
  constructor(state, env) {
    this.state = state;
    this.env = env || {};
    this.sessions = new Set();
    this.httpRate = new Map();
  }

  async fetch(request) {
    const path = new URL(request.url).pathname;
    if ((path === "/connect" || path === "/ws/chat") && request.headers.get("Upgrade") === "websocket") {
      return this.handleConnect(request);
    }
    if (path === "/history" && request.method === "GET") {
      const messages = await this.loadHistory();
      return durableObjectJson({ status: "ok", messages }, 200, request.method);
    }
    if (path === "/send" && request.method === "POST") {
      return this.handleHttpSend(request);
    }
    return durableObjectJson({ status: "not_found" }, 404, request.method);
  }

  async handleHttpSend(request) {
    let body = null;
    try {
      body = await request.json();
    } catch {
      return durableObjectJson({ status: "error", code: "invalid_json" }, 400, request.method);
    }
    const name = typeof body?.name === "string"
      ? body.name.trim().slice(0, CHAT_NAME_MAX) || "Tamu"
      : "Tamu";
    const extras = sanitizeChatExtras(body);
    const rawText = typeof body?.text === "string" ? body.text.trim().slice(0, CHAT_TEXT_MAX) : "";
    const text = rawText || (
      extras.attachment_type === "sticker" ? `🎨 Stiker: ${extras.attachment_name || "XyDesk"}`
      : extras.attachment_type === "image" ? `📷 Foto${extras.attachment_name ? `: ${extras.attachment_name}` : ""}`
      : extras.attachment_type === "file" ? `📎 File: ${extras.attachment_name || "Dokumen"}`
      : ""
    );
    if (!text) {
      return durableObjectJson({ status: "error", code: "empty_text" }, 400, request.method);
    }
    const prelimCountry = detectCountryFromRequest(request, body?.country, text);
    const now = Date.now();
    const rateKey = name.toLowerCase();
    const bucket = this.httpRate.get(rateKey) || { sent: 0, windowAt: now };
    if (now - bucket.windowAt > CHAT_WINDOW_MS) {
      bucket.windowAt = now;
      bucket.sent = 0;
    }
    bucket.sent += 1;
    this.httpRate.set(rateKey, bucket);
    if (bucket.sent > CHAT_MSG_PER_WINDOW) {
      return durableObjectJson({ status: "error", code: "rate_limited" }, 429, request.method);
    }
    const clientTrText = typeof body?.translated === "string" ? body.translated.trim().slice(0, CHAT_TEXT_MAX) : "";
    const clientTargetLang = typeof body?.target_lang === "string" ? body.target_lang.trim().slice(0, 12).toUpperCase() : "";
    const clientSourceLang = typeof body?.source_lang === "string" ? body.source_lang.trim().slice(0, 12).toLowerCase() : "";
    const tr = (clientTrText && clientTrText.toLowerCase() !== text.toLowerCase())
      ? {
          translated: clientTrText,
          targetLang: clientTargetLang || (prelimCountry === "ID" ? "EN" : "ID"),
          sourceLang: clientSourceLang || guessSourceLang(text, prelimCountry),
        }
      : await translateChatText(text, prelimCountry);
    const country = detectCountryFromRequest(request, body?.country, text, tr.sourceLang);
    const flag = countryToFlag(country);
    const id = `${now.toString(36).padStart(9, "0")}-${Math.random().toString(36).slice(2, 8)}`;
    const msg = {
      id,
      name,
      is_founder: isFounderChatName(name, extras.github_login),
      country,
      flag,
      text,
      translated: tr.translated,
      target_lang: tr.targetLang,
      source_lang: tr.sourceLang,
      ...extras,
      at: now,
    };
    await this.state.storage.put(`msg:${id}`, msg);
    this.trimHistory().catch(() => {});
    this.broadcast(null, JSON.stringify({ type: "msg", ...msg }));
    this.pushChatNotification(msg).catch(() => {});
    return durableObjectJson({ status: "ok", message: msg }, 200, request.method);
  }

  async loadHistory() {
    const entries = await this.state.storage.list({ prefix: "msg:" });
    const msgs = [];
    for (const [k, v] of entries.entries()) {
      const senderLower = String(v?.name || "").trim().toLowerCase();
      if (LEGACY_TEST_NAMES.has(senderLower)) {
        this.state.storage.delete(k).catch(() => {});
        continue;
      }
      const text = String(v?.text || "");
      let sourceLang = typeof v?.source_lang === "string" ? v.source_lang : "";
      const country = resolveSmartCountry(text, sourceLang, v?.country, v?.country);
      const flag = countryToFlag(country);
      let translated = typeof v?.translated === "string" ? v.translated : "";
      let targetLang = typeof v?.target_lang === "string" ? v.target_lang : "";
      if (!translated && text) {
        const fb = offlineTranslateFallback(text, country);
        translated = fb.translated;
        targetLang = fb.targetLang;
        sourceLang = fb.sourceLang;
      }
      msgs.push({
        id: k.slice(4),
        ...v,
        is_founder: Boolean(v?.is_founder || isFounderChatName(v?.name, v?.github_login)),
        country,
        flag,
        translated,
        target_lang: targetLang || "EN",
        source_lang: sourceLang || "id",
      });
    }
    msgs.sort((a, b) => a.at - b.at);
    return msgs.slice(-CHAT_HISTORY_LIMIT);
  }

  async trimHistory() {
    const entries = await this.state.storage.list({ prefix: "msg:" });
    const keys = [...entries.keys()].sort();
    let excess = keys.length - CHAT_HISTORY_LIMIT;
    for (let i = 0; excess > 0 && i < keys.length; i += 1, excess -= 1) {
      await this.state.storage.delete(keys[i]);
    }
  }

  handleConnect(request) {
    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    server.accept();
    const cfCountry = request?.cf?.country
      || request?.headers?.get("cf-ipcountry")
      || request?.headers?.get("CF-Connecting-IP-Country");
    const initialCountry = normalizeCountryCode(cfCountry, "ID");
    const session = {
      ws: server,
      name: "Tamu",
      githubLogin: "",
      country: initialCountry,
      cfCountry: initialCountry,
      sent: 0,
      windowAt: Date.now(),
    };
    this.sessions.add(session);
    server.addEventListener("message", (event) => {
      this.onMessage(session, event).catch(() => {});
    });
    const close = () => this.onClose(session);
    server.addEventListener("close", close);
    server.addEventListener("error", close);
    this.loadHistory()
      .then((messages) => {
        try { server.send(JSON.stringify({ type: "history", messages })); } catch {}
      })
      .catch(() => {});
    this.broadcast(null, JSON.stringify({ type: "presence", count: this.sessions.size }));
    try { server.send(JSON.stringify({ type: "presence", count: this.sessions.size })); } catch {}
    return new Response(null, { status: 101, webSocket: client });
  }

  onClose(session) {
    this.sessions.delete(session);
    try { session.ws.close(); } catch {}
    this.broadcast(null, JSON.stringify({ type: "presence", count: this.sessions.size }));
  }

  async onMessage(session, event) {
    let data = null;
    try { data = JSON.parse(String(event.data)); } catch { return; }
    if (!data || typeof data !== "object") return;
    if (data.type === "hello") {
      if (typeof data.name === "string") {
        session.name = String(data.name).trim().slice(0, CHAT_NAME_MAX) || "Tamu";
      }
      if (typeof data.github_login === "string") {
        session.githubLogin = String(data.github_login).trim().replace(/^@/, "").slice(0, 39);
      }
      if (typeof data.country === "string") {
        session.country = normalizeCountryCode(data.country, session.country);
      }
      return;
    }
    if (data.type === "typing") {
      this.broadcast(session, JSON.stringify({
        type: "typing",
        name: session.name,
        country: session.country,
        flag: countryToFlag(session.country),
        on: !!data.on,
      }));
      return;
    }
    if (data.type !== "msg") return;
    const extras = sanitizeChatExtras(data);
    if (!extras.github_login && session.githubLogin) {
      extras.github_login = session.githubLogin;
    }
    const rawText = typeof data.text === "string" ? data.text.trim().slice(0, CHAT_TEXT_MAX) : "";
    const text = rawText || (
      extras.attachment_type === "sticker" ? `🎨 Stiker: ${extras.attachment_name || "XyDesk"}`
      : extras.attachment_type === "image" ? `📷 Foto${extras.attachment_name ? `: ${extras.attachment_name}` : ""}`
      : extras.attachment_type === "file" ? `📎 File: ${extras.attachment_name || "Dokumen"}`
      : ""
    );
    if (!text) return;
    if (typeof data.country === "string") {
      session.country = normalizeCountryCode(data.country, session.country);
    }
    const now = Date.now();
    if (now - session.windowAt > CHAT_WINDOW_MS) { session.windowAt = now; session.sent = 0; }
    session.sent += 1;
    if (session.sent > CHAT_MSG_PER_WINDOW) {
      try { session.ws.send(JSON.stringify({ type: "error", code: "rate_limited" })); } catch {}
      return;
    }
    const clientTrText = typeof data?.translated === "string" ? data.translated.trim().slice(0, CHAT_TEXT_MAX) : "";
    const clientTargetLang = typeof data?.target_lang === "string" ? data.target_lang.trim().slice(0, 12).toUpperCase() : "";
    const clientSourceLang = typeof data?.source_lang === "string" ? data.source_lang.trim().slice(0, 12).toLowerCase() : "";
    const tr = (clientTrText && clientTrText.toLowerCase() !== text.toLowerCase())
      ? {
          translated: clientTrText,
          targetLang: clientTargetLang || (session.country === "ID" ? "EN" : "ID"),
          sourceLang: clientSourceLang || guessSourceLang(text, session.country),
        }
      : await translateChatText(text, session.country);
    const resolvedCountry = resolveSmartCountry(text, tr.sourceLang, data.country || session.country, session.cfCountry);
    session.country = resolvedCountry;
    const id = `${now.toString(36).padStart(9, "0")}-${Math.random().toString(36).slice(2, 8)}`;
    const flag = countryToFlag(resolvedCountry);
    const msg = {
      id,
      name: session.name,
      is_founder: isFounderChatName(session.name, extras.github_login),
      country: resolvedCountry,
      flag,
      text,
      translated: tr.translated,
      target_lang: tr.targetLang,
      source_lang: tr.sourceLang,
      ...extras,
      at: now,
    };
    this.state.storage.put(`msg:${id}`, msg).then(() => this.trimHistory()).catch(() => {});
    this.broadcast(null, JSON.stringify({ type: "msg", ...msg }));
    this.pushChatNotification(msg).catch(() => {});
  }

  async pushChatNotification(msg) {
    const apiKey = String(this.env?.ONESIGNAL_REST_API_KEY || "").trim();
    const appId = String(this.env?.ONESIGNAL_APP_ID || DEFAULT_ONESIGNAL_APP_ID).trim();
    if (!apiKey || !appId) return;
    const isFounder = Boolean(msg.is_founder || isFounderChatName(msg.name));
    const senderLabel = isFounder
      ? `👑 ${msg.flag || "🇮🇩"} ${msg.name} [FOUNDER] (${msg.country || "ID"})`
      : `${msg.flag || "🌐"} ${msg.name} (${msg.country || "ID"})`;
    const bodyText = msg.translated && msg.translated.toLowerCase() !== msg.text.toLowerCase()
      ? `${msg.text}\n🌐 ${msg.target_lang || "TR"}: ${msg.translated}`
      : msg.text;
    try {
      await fetch("https://api.onesignal.com/notifications", {
        method: "POST",
        headers: {
          Authorization: `Key ${apiKey}`,
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: JSON.stringify({
          app_id: appId,
          target_channel: "push",
          filters: [
            { field: "tag", key: "chat_open", relation: "!=", value: "true" },
            { operator: "AND" },
            { field: "tag", key: "nickname", relation: "!=", value: msg.name },
          ],
          headings: {
            en: `${senderLabel} · Chat Global`,
            id: `${senderLabel} · Chat Global`,
          },
          contents: {
            en: bodyText,
            id: bodyText,
          },
          data: {
            type: "global_chat",
            msg_id: msg.id,
            sender_name: senderLabel,
            country: msg.country || "ID",
            flag: msg.flag || "🇮🇩",
            text: msg.text,
            translated: msg.translated || "",
            target_lang: msg.target_lang || "EN",
            at: msg.at,
          },
          android_group: "xydesk_global_chat",
          priority: 10,
        }),
      });
    } catch {
      // Non-fatal: kegagalan push tidak boleh mengganggu aliran chat.
    }
  }

  broadcast(fromSession, payload) {
    for (const s of this.sessions) {
      if (s === fromSession) continue;
      try { s.ws.send(payload); } catch {}
    }
  }
}


async function releasesListResponse(request, env) {
  const cacheKey = new Request(new URL("/api/releases", request.url), { method: "GET" });
  const cache = caches.default;
  const cached = await cache.match(cacheKey);
  if (cached) return cached;
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
  await cache.put(cacheKey, out.clone());
  return out;
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname;

    if (path === "/ws/chat") {
      if (!env.GLOBAL_CHAT) return notFound(request.method);
      const chatId = env.GLOBAL_CHAT.idFromName("xydesk-global-chat-v1");
      return env.GLOBAL_CHAT.get(chatId).fetch(request);
    }
    if (path === "/api/chat/history") {
      if (!env.GLOBAL_CHAT) return notFound(request.method);
      const chatId2 = env.GLOBAL_CHAT.idFromName("xydesk-global-chat-v1");
      const res = await env.GLOBAL_CHAT.get(chatId2).fetch(new Request(new URL("/history", request.url), { method: "GET" }));
      if (!res.ok) return res;
      const payload = await res.json().catch(() => null);
      if (!payload || !Array.isArray(payload.messages)) {
        return durableObjectJson({ status: "ok", messages: [] }, 200, request.method);
      }
      const cleaned = [];
      for (const m of payload.messages) {
        const senderLower = String(m?.name || "").trim().toLowerCase();
        if (LEGACY_TEST_NAMES.has(senderLower)) continue;
        const text = String(m?.text || "");
        const srcLang = typeof m?.source_lang === "string" ? m.source_lang : "";
        const cc = resolveSmartCountry(text, srcLang, m?.country, m?.country);
        cleaned.push({
          ...m,
          is_founder: Boolean(m?.is_founder || isFounderChatName(m?.name, m?.github_login)),
          country: cc,
          flag: countryToFlag(cc),
        });
      }
      return durableObjectJson({ status: "ok", messages: cleaned }, 200, request.method);
    }
    if (path === "/api/chat/send") {
      if (request.method === "OPTIONS") return corsPreflight("POST, OPTIONS");
      if (request.method !== "POST") return methodNotAllowed("POST, OPTIONS");
      if (!env.GLOBAL_CHAT) return notFound(request.method);
      const chatId3 = env.GLOBAL_CHAT.idFromName("xydesk-global-chat-v1");
      const cfCountry = request.cf?.country || request.headers.get("cf-ipcountry") || "";
      return env.GLOBAL_CHAT.get(chatId3).fetch(
        new Request(new URL("/send", request.url), {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "x-chat-country": cfCountry,
          },
          body: await request.text(),
        })
      );
    }
    if (path === "/api/chat/translate") {
      if (request.method === "OPTIONS") return corsPreflight("GET, OPTIONS");
      if (request.method !== "GET") return methodNotAllowed("GET, OPTIONS");
      const q = (url.searchParams.get("q") || "").trim().slice(0, CHAT_TEXT_MAX);
      const tl = (url.searchParams.get("tl") || "en").trim().slice(0, 12);
      if (!q) return jsonResponse({ status: "error", code: "empty_text" }, 400, request.method);
      const guessedSl = guessSourceLang(q, "");
      const c5 = await fetchGoogleClients5Translate(q, "auto", tl).catch(() => null);
      if (c5 && c5.translated) {
        return jsonResponse({
          status: "ok",
          translated: c5.translated,
          source_lang: c5.detected || guessedSl,
          target_lang: tl.toUpperCase(),
        }, 200, request.method);
      }
      const gtx = await fetchGoogleTranslate(q, "auto", tl).catch(() => null);
      if (gtx && gtx.translated) {
        return jsonResponse({
          status: "ok",
          translated: gtx.translated,
          source_lang: gtx.detected || guessedSl,
          target_lang: tl.toUpperCase(),
        }, 200, request.method);
      }
      const mm = await fetchMyMemoryTranslate(q, guessedSl, tl).catch(() => null);
      if (mm) {
        return jsonResponse({
          status: "ok",
          translated: mm,
          source_lang: guessedSl,
          target_lang: tl.toUpperCase(),
        }, 200, request.method);
      }
      const fb = offlineTranslateFallback(q, tl.toUpperCase() === "ID" ? "US" : "ID");
      return jsonResponse({
        status: "ok",
        translated: fb.translated,
        source_lang: guessedSl || fb.sourceLang,
        target_lang: tl.toUpperCase(),
      }, 200, request.method);
    }
    if (path === "/api/pc/rooms" || path.startsWith("/api/pc/rooms/")) {
      return pcSignalResponse(request, env);
    }
    if (path === "/api/pc/relay") {
      return pcRelayResponse(request, env);
    }
    if (path === "/api/session") {
      if (request.method !== "POST") return methodNotAllowed("POST");
      return sessionReportResponse(request, env);
    }
    if (path === "/api/jokes" || path.startsWith("/api/jokes/")) {
      return communityJokesResponse(request, env);
    }
    if (path === "/api/feedback" || path.startsWith("/api/feedback/")) {
      return feedbackResponse(request, env);
    }
    if (path === "/api/account/settings") {
      return accountSettingsResponse(request, env);
    }
    if (request.method !== "GET" && request.method !== "HEAD") return methodNotAllowed();

    if (path === "/api/health") return healthResponse(request, env);
    if (path === "/api/status") return statusResponse(request, env);
    if (path === "/api/stats") return downloadStatsResponse(request, env);
    if (path === "/api/releases") return releasesListResponse(request, env);
    if (path === "/api/update-check") return updateCheckResponse(request, env);
    if (path === "/api/active-sessions") return activeSessionsResponse(request, env);
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
    const host = url.hostname;
    if ((host === "report.xydeskremote.biz.id" || host === "ask.xydeskremote.biz.id") &&
        (path === "/" || path === "/index.html")) {
      return env.ASSETS.fetch(new Request(new URL("/feedback.html", request.url), request));
    }

    return env.ASSETS.fetch(request);
  },
};
