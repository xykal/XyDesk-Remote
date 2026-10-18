import test, { afterEach, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { verifyGithubIdentity } from "./oauth-identity.mjs";
const originalFetch = globalThis.fetch;
const user = { id: 123, login: "Alice", name: "Provider Name", avatar_url: "https://avatars.githubusercontent.com/u/123" };
beforeEach(() => { globalThis.fetch = async () => Response.json(user); });
afterEach(() => { globalThis.fetch = originalFetch; });

test("canonical provider identity, fixed URL and no authorization forwarding redirects", async () => {
  globalThis.fetch = async (url, options) => {
    assert.equal(url, "https://api.github.com/user");
    assert.equal(options.redirect, "error");
    assert.equal(options.headers.Authorization, "Bearer example-test-token");
    return Response.json(user);
  };
  assert.deepEqual(await verifyGithubIdentity("example-test-token", "alice"), { ok: true, principal: user });
});
test("different claimed account fails closed", async () => {
  assert.equal((await verifyGithubIdentity("example-test-token", "bob")).status, 401);
});
test("invalid credentials do not call provider", async () => {
  globalThis.fetch = async () => { throw new Error("must not fetch"); };
  for (const [token, login] of [["", "alice"], ["bad\nheader", "alice"], ["x".repeat(4097), "alice"], ["test", "@alice"], ["test", ""], ["test", "x".repeat(40)]]) {
    assert.equal((await verifyGithubIdentity(token, login)).status, 401);
  }
});
test("provider 401 rejects token; rate limit and failure are unavailable", async () => {
  for (const status of [401, 403, 429, 500]) {
    globalThis.fetch = async () => new Response(null, { status });
    assert.equal((await verifyGithubIdentity("test", "alice")).status, status === 401 ? 401 : 503);
  }
});
test("missing stable provider id, invalid login and broken JSON never verify", async () => {
  for (const payload of [{ ...user, id: null }, { ...user, id: 0 }, { ...user, login: "@alice" }, null]) {
    globalThis.fetch = async () => Response.json(payload);
    assert.equal((await verifyGithubIdentity("test", "alice")).status, 503);
  }
  globalThis.fetch = async () => new Response("not-json");
  assert.equal((await verifyGithubIdentity("test", "alice")).status, 503);
});
test("network and redirect failures never verify", async () => {
  globalThis.fetch = async () => { throw new Error("network/redirect failure"); };
  assert.equal((await verifyGithubIdentity("test", "alice")).status, 503);
});
test("deadline includes response body even when provider ignores abort", async () => {
  let signal;
  globalThis.fetch = async (_, options) => {
    signal = options.signal;
    return { ok: true, status: 200, json: () => new Promise(() => {}) };
  };
  const result = await verifyGithubIdentity("test", "alice", { timeoutMs: 15 });
  assert.equal(result.status, 503);
  assert.equal(signal.aborted, true);
});
test("insecure provider avatar ignored and blank name falls back to canonical login", async () => {
  globalThis.fetch = async () => Response.json({ ...user, name: " ", avatar_url: "http://example.test/avatar" });
  const result = await verifyGithubIdentity("test", "alice");
  assert.equal(result.principal.name, "Alice");
  assert.equal(result.principal.avatar_url, "");
});
