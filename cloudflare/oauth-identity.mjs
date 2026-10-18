/** Provider identity only. Does NOT bind an OAuth state/session or authorize rewards. */
export async function verifyGithubIdentity(token, claimedLogin, { timeoutMs = 2_000 } = {}) {
  const denied = () => ({ ok: false, status: 401, code: "invalid_identity" });
  const unavailable = () => ({ ok: false, status: 503, code: "identity_provider_unavailable" });
  if (typeof token !== "string" || !token || token.length > 4096 || /[\s\x00-\x1f\x7f]/.test(token)
      || typeof claimedLogin !== "string" || !/^[a-z\d](?:[a-z\d-]{0,37}[a-z\d])?$/i.test(claimedLogin)) {
    return denied();
  }
  const controller = new AbortController();
  let timer;
  const deadline = new Promise(resolve => {
    timer = setTimeout(() => { controller.abort(); resolve(unavailable()); }, timeoutMs);
  });
  const lookup = async () => {
    const response = await fetch("https://api.github.com/user", {
      redirect: "error",
      headers: {
        "Authorization": `Bearer ${token}`,
        "Accept": "application/vnd.github+json",
        "User-Agent": "XyDeskRemote-OAuth/1.0",
        "X-GitHub-Api-Version": "2022-11-28",
      },
      signal: controller.signal,
    });
    if (response.status === 401) return denied();
    if (!response.ok) return unavailable();
    const user = await response.json();
    if (!Number.isSafeInteger(user?.id) || user.id <= 0
        || typeof user?.login !== "string"
        || !/^[a-z\d](?:[a-z\d-]{0,37}[a-z\d])?$/i.test(user.login)) return unavailable();
    if (user.login.toLowerCase() !== claimedLogin.toLowerCase()) return denied();
    // Trust provider metadata, never body-supplied identity/profile claims.
    const name = typeof user.name === "string" ? user.name.trim().slice(0, 80) : "";
    const avatar = typeof user.avatar_url === "string" ? user.avatar_url.trim() : "";
    let avatarUrl = "";
    try {
      const url = new URL(avatar);
      if (url.protocol === "https:" && !url.username && !url.password && avatar.length <= 300) avatarUrl = avatar;
    } catch {}
    return { ok: true, principal: { id: user.id, login: user.login, name: name || user.login, avatar_url: avatarUrl } };
  };
  try {
    // A single bound covers connection AND body parsing, even if a mock/provider ignores abort.
    return await Promise.race([Promise.resolve().then(lookup).catch(unavailable), deadline]);
  } finally {
    clearTimeout(timer);
    controller.abort();
  }
}
