/** One deployment-configured OAuth app; never choose a legacy app by query/fallback. */
export const GITHUB_CALLBACK = 'https://api.xydeskremote.biz.id/api/github/oauth/callback';
export function githubOAuthConfig(env) {
  const clientId = typeof env?.GITHUB_OAUTH_CLIENT_ID === 'string' ? env.GITHUB_OAUTH_CLIENT_ID.trim() : '';
  const clientSecret = typeof env?.GITHUB_OAUTH_CLIENT_SECRET === 'string' ? env.GITHUB_OAUTH_CLIENT_SECRET.trim() : '';
  if (!/^[A-Za-z0-9_-]{8,100}$/.test(clientId) || !clientSecret || /\s/.test(clientSecret)) return null;
  return { client_id:clientId, client_secret:clientSecret, redirect_uri:GITHUB_CALLBACK };
}
