/** One deadline for all configured candidates, fetch AND response-body parsing.
 * This exchanges a code only; callers still must verify identity and bind the session.
 */
export async function exchangeGithubCode(code, candidates, { timeoutMs = 4_000 } = {}) {
  const failed = () => ({ ok: false, status: 400, code: "token_exchange_failed" });
  const unavailable = () => ({ ok: false, status: 503, code: "oauth_provider_unavailable" });
  if (typeof code !== "string" || !code.trim() || code.length > 4096 || !Array.isArray(candidates)) return failed();
  const controller = new AbortController();
  let timer;
  const deadline = new Promise(resolve => {
    timer = setTimeout(() => { controller.abort(); resolve(unavailable()); }, timeoutMs);
  });
  const exchange = async () => {
    let providerFailure = false;
    for (const candidate of candidates) {
      if (controller.signal.aborted) return unavailable();
      if (typeof candidate?.client_id !== "string" || !candidate.client_id
          || typeof candidate?.client_secret !== "string" || !candidate.client_secret) continue;
      const body = { client_id: candidate.client_id, client_secret: candidate.client_secret, code };
      if (candidate.redirect_uri) body.redirect_uri = candidate.redirect_uri;
      try {
        const response = await fetch("https://github.com/login/oauth/access_token", {
          method: "POST", redirect: "error", signal: controller.signal,
          headers: { "Content-Type": "application/json", "Accept": "application/json", "User-Agent": "XyDeskRemote-OAuth/1.0" },
          body: JSON.stringify(body),
        });
        if (controller.signal.aborted) return unavailable();
        if (!response.ok) {
          if (response.status >= 500 || response.status === 429 || response.status >= 300 && response.status < 400) providerFailure = true;
          continue;
        }
        const data = await response.json();
        if (controller.signal.aborted) return unavailable();
        if (data?.error) continue;
        const token = typeof data?.access_token === "string" ? data.access_token.trim() : "";
        if (token && token.length <= 4096 && !/[\s\x00-\x1f\x7f]/.test(token)) {
          return { ok: true, accessToken: token };
        }
      } catch {
        providerFailure = true;
      }
    }
    return providerFailure ? unavailable() : failed();
  };
  try {
    return await Promise.race([Promise.resolve().then(exchange).catch(unavailable), deadline]);
  } finally {
    clearTimeout(timer);
    controller.abort();
  }
}
