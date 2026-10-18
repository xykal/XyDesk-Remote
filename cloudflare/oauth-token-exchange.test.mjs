import test, { beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import { exchangeGithubCode } from "./oauth-token-exchange.mjs";
const originalFetch = globalThis.fetch;
const candidates = [
  { client_id: "test-a", client_secret: "test-secret-a", redirect_uri: "https://example.test/callback" },
  { client_id: "test-b", client_secret: "test-secret-b" },
];
beforeEach(() => { globalThis.fetch = async () => Response.json({ error: "bad_verification_code" }); });
afterEach(() => { globalThis.fetch = originalFetch; });

test("successful first candidate uses fixed POST endpoint and refuses redirects", async () => {
  let calls = 0;
  globalThis.fetch = async (url, options) => {
    calls++;
    assert.equal(url, "https://github.com/login/oauth/access_token");
    assert.equal(options.method, "POST");
    assert.equal(options.redirect, "error");
    assert.deepEqual(JSON.parse(options.body), { ...candidates[0], code: "test-code" });
    return Response.json({ access_token: "test-token" });
  };
  assert.deepEqual(await exchangeGithubCode("test-code", candidates), { ok: true, accessToken: "test-token" });
  assert.equal(calls, 1);
});
test("rejected first candidate can fall back within same budget", async () => {
  const seen = [];
  globalThis.fetch = async (_, options) => {
    seen.push(JSON.parse(options.body).client_id);
    return Response.json(seen.length === 1 ? { error: "bad_verification_code" } : { access_token: "fallback-test-token" });
  };
  assert.equal((await exchangeGithubCode("test-code", candidates)).accessToken, "fallback-test-token");
  assert.deepEqual(seen, ["test-a", "test-b"]);
});
test("empty configuration and malformed code never fetch", async () => {
  globalThis.fetch = async () => { throw new Error("unexpected request"); };
  assert.equal((await exchangeGithubCode("", candidates)).status, 400);
  assert.equal((await exchangeGithubCode("x".repeat(4097), candidates)).status, 400);
  assert.equal((await exchangeGithubCode("test-code", [{ client_id: "missing-secret" }])).status, 400);
});
test("rejected codes return generic error without reflecting provider body", async () => {
  globalThis.fetch = async () => Response.json({ error: "bad_verification_code", error_description: "private-provider-details" });
  assert.deepEqual(await exchangeGithubCode("test-code", candidates), { ok: false, status: 400, code: "token_exchange_failed" });
});
test("token on failed HTTP response or OAuth error is never accepted", async () => {
  for (const response of [() => Response.json({ access_token: "must-not-use" }, { status: 500 }), () => Response.json({ access_token: "must-not-use", error: "denied" })]) {
    globalThis.fetch = async () => response();
    assert.equal((await exchangeGithubCode("test-code", candidates)).ok, false);
  }
});
test("invalid token values never succeed", async () => {
  for (const access_token of [null, {}, "", "bad\nheader", "x".repeat(4097)]) {
    globalThis.fetch = async () => Response.json({ access_token });
    assert.equal((await exchangeGithubCode("test-code", candidates)).ok, false);
  }
});
test("network, redirect, malformed body, server failure and rate limit are unavailable", async () => {
  for (const implementation of [
    async () => { throw new Error("network/redirect failure"); },
    async () => new Response("not json"),
    async () => new Response(null, { status: 503 }),
    async () => new Response(null, { status: 429 }),
  ]) {
    globalThis.fetch = implementation;
    assert.equal((await exchangeGithubCode("test-code", candidates)).status, 503);
  }
});
test("deadline bounds ignored fetch abort and prevents late fallback request", async () => {
  let finishFetch;
  let signal;
  let calls = 0;
  globalThis.fetch = (_, options) => {
    calls++; signal = options.signal;
    return new Promise(resolve => { finishFetch = resolve; });
  };
  assert.equal((await exchangeGithubCode("test-code", candidates, { timeoutMs: 15 })).status, 503);
  assert.equal(signal.aborted, true);
  finishFetch(Response.json({ error: "bad_verification_code" }));
  await new Promise(resolve => setTimeout(resolve, 0));
  assert.equal(calls, 1);
});
test("deadline bounds stalled JSON read and does not reset for next client", async () => {
  let finishBody;
  let calls = 0;
  globalThis.fetch = async () => {
    calls++;
    return { ok: true, json: () => new Promise(resolve => { finishBody = resolve; }) };
  };
  assert.equal((await exchangeGithubCode("test-code", candidates, { timeoutMs: 15 })).status, 503);
  finishBody({ error: "bad_verification_code" });
  await new Promise(resolve => setTimeout(resolve, 0));
  assert.equal(calls, 1);
});
