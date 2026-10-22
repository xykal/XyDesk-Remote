import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import worker, {
  ActiveSessions,
  CommunityJokes,
  PcSignalRoom,
  FeedbackBoard,
  GlobalChat,
  createKvBackedStorage,
} from "../cloudflare/worker.mjs";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const WEB_DIR = path.resolve(__dirname, "../web");

const MIME_TYPES = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".mjs": "application/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".webp": "image/webp",
  ".svg": "image/svg+xml",
  ".ico": "image/x-icon",
  ".txt": "text/plain; charset=utf-8",
  ".xml": "application/xml; charset=utf-8",
  ".ps1": "text/plain; charset=utf-8",
  ".bat": "text/plain; charset=utf-8",
  ".sh": "text/plain; charset=utf-8",
};

function createRemoteD1Adapter(accountId, databaseId, apiToken) {
  const endpoint = `https://api.cloudflare.com/client/v4/accounts/${accountId}/d1/database/${databaseId}/query`;
  const rowCache = new Map();
  const inFlightQueries = new Map();

  function getCacheTtlMs(key) {
    if (!key) return 0;
    if (key.startsWith("feed-att:")) return 300_000;
    if (key.startsWith("feed-avatar:") || key.startsWith("xy-settings:") || key.startsWith("leaderboard:")) return 15_000;
    if (key === "chat:prod_backup_v1" || key.includes("global_chat")) return 350;
    if (key.startsWith("do_fallback:")) return 800;
    return 1_000;
  }

  async function executeSql(sql, params = []) {
    if (!accountId || !databaseId || !apiToken) return null;
    const normSql = String(sql || "").trim().toUpperCase();
    const isSelectByKey = normSql.startsWith("SELECT ") && normSql.includes("FROM XYDESK_KV_STORE WHERE K = ?1") && params.length === 1;
    const isWriteByKey = (normSql.startsWith("INSERT ") || normSql.startsWith("DELETE ")) && normSql.includes("XYDESK_KV_STORE") && params.length >= 1;
    const targetKey = params.length >= 1 && params[0] !== null && params[0] !== undefined ? String(params[0]) : "";

    if (isWriteByKey && targetKey) {
      rowCache.delete(`v:${targetKey}`);
      rowCache.delete(`vm:${targetKey}`);
    }

    if (isSelectByKey && targetKey) {
      const cacheKey = `${normSql.includes("META") ? "vm" : "v"}:${targetKey}`;
      const ttl = getCacheTtlMs(targetKey);
      const now = Date.now();
      const hit = rowCache.get(cacheKey);
      if (hit && now < hit.expiresAt) {
        return hit.data;
      }
      const pending = inFlightQueries.get(cacheKey);
      if (pending) {
        return await pending;
      }
      const reqPromise = (async () => {
        try {
          const res = await fetch(endpoint, {
            method: "POST",
            headers: {
              Authorization: `Bearer ${apiToken}`,
              "Content-Type": "application/json",
              Connection: "keep-alive",
            },
            body: JSON.stringify({ sql, params }),
          });
          if (!res.ok) return null;
          const data = await res.json().catch(() => null);
          const resultObj = data?.result?.[0] || null;
          if (resultObj && ttl > 0) {
            rowCache.set(cacheKey, { data: resultObj, expiresAt: Date.now() + ttl });
          }
          return resultObj;
        } finally {
          inFlightQueries.delete(cacheKey);
        }
      })();
      inFlightQueries.set(cacheKey, reqPromise);
      return await reqPromise;
    }

    const res = await fetch(endpoint, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${apiToken}`,
        "Content-Type": "application/json",
        Connection: "keep-alive",
      },
      body: JSON.stringify({ sql, params }),
    });
    if (!res.ok) return null;
    const data = await res.json().catch(() => null);
    return data?.result?.[0] || null;
  }

  return {
    prepare(sql) {
      let boundParams = [];
      const stmt = {
        bind(...args) {
          boundParams = args.map((v) => (v === undefined ? null : v));
          return stmt;
        },
        async first() {
          const r = await executeSql(sql, boundParams);
          return Array.isArray(r?.results) && r.results.length > 0 ? r.results[0] : null;
        },
        async all() {
          const r = await executeSql(sql, boundParams);
          return { results: Array.isArray(r?.results) ? r.results : [] };
        },
        async run() {
          const r = await executeSql(sql, boundParams);
          return r || { success: true };
        },
      };
      return stmt;
    },
  };
}

function createD1BackedRawKv(_d1Db) {
  const mem = new Map();
  return {
    async get(key) {
      const k = String(key);
      return mem.has(k) ? mem.get(k) : null;
    },
    async put(key, value) {
      const k = String(key);
      mem.set(k, value);
    },
    async delete(key) {
      const k = String(key);
      mem.delete(k);
    },
  };
}

const DO_INSTANCES = new Map();

function createDoNamespace(ClassRef, prefix, getEnv) {
  return {
    idFromName(name) {
      return `${prefix}:${String(name || "default")}`;
    },
    get(id) {
      const key = String(id);
      return {
        async fetch(input, init) {
          const env = getEnv();
          let inst = DO_INSTANCES.get(key);
          if (!inst) {
            const storage = createKvBackedStorage(env, `do_fallback:${key}`);
            inst = new ClassRef({ storage }, env);
            DO_INSTANCES.set(key, inst);
          } else {
            inst.env = env;
          }
          const req = input instanceof Request ? input : new Request(String(input), init);
          return await inst.fetch(req);
        },
      };
    },
  };
}

const STATIC_ASSETS = {
  async fetch(request) {
    const url = new URL(request.url);
    let relPath = decodeURIComponent(url.pathname || "/");
    if (relPath === "/" || relPath === "") relPath = "/index.html";
    const safePath = path.normalize(relPath).replace(/^(\.\.(\/|\\|$))+/, "");
    let fullPath = path.join(WEB_DIR, safePath);

    try {
      const data = await readFile(fullPath);
      const ext = path.extname(fullPath).toLowerCase();
      return new Response(request.method === "HEAD" ? null : data, {
        status: 200,
        headers: {
          "content-type": MIME_TYPES[ext] || "application/octet-stream",
          "cache-control": "public, max-age=300",
        },
      });
    } catch {
      if (!path.extname(safePath)) {
        try {
          const htmlPath = `${fullPath}.html`;
          const data = await readFile(htmlPath);
          return new Response(request.method === "HEAD" ? null : data, {
            status: 200,
            headers: {
              "content-type": "text/html; charset=utf-8",
              "cache-control": "public, max-age=300",
            },
          });
        } catch {}
      }
      return new Response(request.method === "HEAD" ? null : "Not Found", {
        status: 404,
        headers: { "content-type": "text/plain; charset=utf-8" },
      });
    }
  },
};

let cachedEnv = null;
function buildVercelEnv() {
  if (cachedEnv) return cachedEnv;
  const accountId = process.env.CLOUDFLARE_ACCOUNT_ID || "a678fee6e0a026ccd2fd978cdf07806a";
  const databaseId = process.env.CLOUDFLARE_D1_DB_ID || "7ffccd9e-3432-47bf-b3e7-7492cbe56ea2";
  const apiToken = process.env.CLOUDFLARE_API_TOKEN || "";
  const d1Db = createRemoteD1Adapter(accountId, databaseId, apiToken);
  const rawKv = createD1BackedRawKv(d1Db);

  const env = {
    XYDESK_DB: d1Db,
    UNIQUE_DOWNLOADS: rawKv,
    ASSETS: STATIC_ASSETS,
    ONESIGNAL_APP_ID: process.env.ONESIGNAL_APP_ID || "e3d5adea-1c0f-4986-9ce5-e088d65f0998",
    ONESIGNAL_REST_API_KEY: process.env.ONESIGNAL_REST_API_KEY || process.env.ONESIGNAL_XYDESK_API_KEY || "",
    GITHUB_OAUTH_CLIENT_ID: process.env.GITHUB_OAUTH_CLIENT_ID || "Ov23liJ0QBoRfaMohJp5",
    GITHUB_OAUTH_CLIENT_SECRET: process.env.GITHUB_OAUTH_CLIENT_SECRET || "",
    GH_PAT: process.env.GH_PAT || process.env.GITHUB_TOKEN || "",
    GH_TOKEN: process.env.GH_TOKEN || process.env.GITHUB_TOKEN || "",
    FEEDBACK_STAFF_TOKEN: process.env.FEEDBACK_STAFF_TOKEN || "",
  };

  const getEnv = () => env;
  env.ACTIVE_SESSIONS = createDoNamespace(ActiveSessions, "active_sessions_v1", getEnv);
  env.COMMUNITY_JOKES = {
    idFromName: () => "community_jokes_v1",
    get: () => ({
      async fetch() {
        throw new Error("use_edge_d1_fallback");
      },
    }),
  };
  env.FEEDBACK_BOARD = {
    idFromName: () => "feedback_board_v1",
    get: () => ({
      async fetch() {
        throw new Error("use_edge_d1_fallback");
      },
    }),
  };
  env.GLOBAL_CHAT = {
    idFromName: (name) => String(name || "xydesk-global-chat-v1"),
    get: () => ({
      async fetch() {
        throw new Error("use_edge_d1_fallback");
      },
    }),
  };
  env.PC_SIGNAL_ROOMS = createDoNamespace(PcSignalRoom, "pc_signal_v1", getEnv);

  cachedEnv = env;
  return env;
}

export default async function handler(req, res) {
  try {
    const proto = req.headers["x-forwarded-proto"] || "https";
    const host = req.headers["x-forwarded-host"] || req.headers.host || "api.xydeskremote.biz.id";
    const fullUrl = `${proto}://${host}${req.url || "/"}`;

    const headers = new Headers();
    for (const [k, v] of Object.entries(req.headers)) {
      if (v === undefined) continue;
      if (Array.isArray(v)) {
        for (const item of v) headers.append(k, item);
      } else {
        headers.set(k, String(v));
      }
    }
    const country = req.headers["x-vercel-ip-country"] || req.headers["cf-ipcountry"] || "ID";
    if (!headers.has("cf-ipcountry")) headers.set("cf-ipcountry", String(country));
    const clientIp = String(req.headers["x-forwarded-for"] || req.headers["x-real-ip"] || "").split(",")[0].trim();
    if (clientIp && !headers.has("cf-connecting-ip")) headers.set("cf-connecting-ip", clientIp);

    const method = (req.method || "GET").toUpperCase();
    let body = undefined;
    if (method !== "GET" && method !== "HEAD") {
      const chunks = [];
      for await (const chunk of req) {
        chunks.push(typeof chunk === "string" ? Buffer.from(chunk) : chunk);
      }
      body = Buffer.concat(chunks);
    }

    const webReq = new Request(fullUrl, { method, headers, body });
    Object.defineProperty(webReq, "cf", { value: { country: String(country) }, configurable: true });

    const env = buildVercelEnv();
    const waitUntilPromises = [];
    const ctx = {
      waitUntil(promise) {
        if (promise && typeof promise.then === "function") {
          waitUntilPromises.push(promise.catch(() => null));
        }
      },
    };
    const webRes = await worker.fetch(webReq, env, ctx);
    const arrayBuf = await webRes.arrayBuffer();
    if (waitUntilPromises.length > 0) {
      await Promise.allSettled(waitUntilPromises);
    }

    res.statusCode = webRes.status;
    webRes.headers.forEach((val, key) => {
      res.setHeader(key, val);
    });
    res.end(Buffer.from(arrayBuf));
  } catch (err) {
    res.statusCode = 500;
    res.setHeader("content-type", "application/json; charset=utf-8");
    res.end(JSON.stringify({ status: "error", code: "internal_error" }));
  }
}
