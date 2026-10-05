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
  "XyDesk-Remote-Host-Agent-win64.zip": { repo: "xykal/XyDesk-Remote", id: 606095797, fileName: "XyDesk-Remote-Host-Agent-win64.zip" },
  "XyDeskRemoteHost.exe": { repo: "xykal/XyDesk-Remote", id: 606095804, fileName: "XyDeskRemoteHost.exe" },
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
const COMMUNITY_JOKE_FEED_SIZE = 20;
const COMMUNITY_JOKE_MAX_ITEMS = 1_000;
const COMMUNITY_JOKE_POST_INTERVAL_MS = 60_000;
const COMMUNITY_JOKE_REPORT_THRESHOLD = 3;
const COMMUNITY_JOKE_REACTIONS = Object.freeze(["😂", "😭", "💀", "🔥", "👍", "❤️", "🎉", "🤔"]);
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

async function getReleaseMetadata(request, env) {
  const stateUrl = new URL("/release-state.json", request.url);
  const response = await env.ASSETS.fetch(new Request(stateUrl, { method: "GET" }));
  if (!response.ok) throw new Error("Release metadata unavailable");
  return response.json();
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
    const entry = asset.abi
      ? entries.find((item) => item?.abi === asset.abi)
      : entries.find((item) => item?.file === asset.fileName);
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
    const body = {
      status: "ok",
      scope: "available-public-releases",
      total_apk_downloads: apkAssets.reduce((sum, asset) => sum + asset.downloads, 0),
      apk_asset_count: apkAssets.length,
      release_count: releaseTags.size,
      source: "GitHub Releases API",
      note: "Jumlah unduhan aset APK pada rilis yang masih tersedia, bukan jumlah pengguna unik. Unduhan ulang dihitung lagi.",
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

function communityJokeListKey(createdAt, id) {
  return `joke:${String(createdAt).padStart(13, "0")}:${id}`;
}

function communityJokePublicView(joke, viewerReaction = null) {
  return {
    id: joke.id,
    text: joke.text,
    created_at: joke.created_at,
    reactions: Object.fromEntries(COMMUNITY_JOKE_REACTIONS.map((emoji) => [emoji, Math.max(0, Number(joke.reactions?.[emoji]) || 0)])),
    viewer_reaction: viewerReaction,
  };
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
  constructor(state) {
    this.state = state;
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
        items.push(communityJokePublicView(joke, viewerReaction));
        if (items.length >= COMMUNITY_JOKE_FEED_SIZE) break;
      }
      return durableObjectJson({ status: "ok", items }, 200, request.method);
    } catch {
      return durableObjectJson({ status: "unavailable" }, 503, request.method);
    }
  }

  async submit(request) {
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
    if (chars < 3 || chars > COMMUNITY_JOKE_MAX_CHARS || /(?:https?:\/\/|www\.)/i.test(text)) {
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
        const joke = {
          id,
          text,
          created_at: now,
          reactions: Object.fromEntries(COMMUNITY_JOKE_REACTIONS.map((emoji) => [emoji, 0])),
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
            for (const reactionKey of oldReactions.keys()) await transaction.delete(reactionKey);
            for (const reportKey of oldReports.keys()) await transaction.delete(reportKey);
          }
        }
        return { status: "created", joke: communityJokePublicView(joke) };
      });
      if (outcome.status === "rate_limited") return durableObjectJson(outcome, 429, request.method);
      return durableObjectJson(outcome, 201, request.method);
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
      if (length > 2_048) return jsonResponse({ status: "payload_too_large" }, 413, request.method);
      const bounded = await readRequestTextLimited(request, 2_048);
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
  } else {
    const match = url.pathname.match(/^\/api\/jokes\/([0-9a-f-]{36})\/(reaction|report)$/i);
    if (!match || request.method !== "POST") {
      return match ? methodNotAllowed("POST") : jsonResponse({ status: "not_found" }, 404, request.method);
    }
    const length = Number(request.headers.get("content-length") || 0);
    if (length > 1_024) return jsonResponse({ status: "payload_too_large" }, 413, request.method);
    const bounded = await readRequestTextLimited(request, 1_024);
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

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname;

    if (path === "/api/pc/rooms" || path.startsWith("/api/pc/rooms/")) {
      return pcSignalResponse(request, env);
    }
    if (path === "/api/session") {
      if (request.method !== "POST") return methodNotAllowed("POST");
      return sessionReportResponse(request, env);
    }
    if (path === "/api/jokes" || path.startsWith("/api/jokes/")) {
      return communityJokesResponse(request, env);
    }
    if (request.method !== "GET" && request.method !== "HEAD") return methodNotAllowed();

    if (path === "/api/health") return healthResponse(request, env);
    if (path === "/api/status") return statusResponse(request, env);
    if (path === "/api/stats") return downloadStatsResponse(request, env);
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

    return env.ASSETS.fetch(request);
  },
};
