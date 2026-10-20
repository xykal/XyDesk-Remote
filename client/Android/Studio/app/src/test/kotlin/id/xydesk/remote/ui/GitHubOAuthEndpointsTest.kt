package id.xydesk.remote.ui

import java.net.URI
import org.junit.Assert.*
import org.junit.Test

class GitHubOAuthEndpointsTest {
    @Test fun loginIsAlwaysCanonicalApiAndExplicitClient() {
        val uri = URI(GitHubOAuthEndpoints.login("session-123_ABC"))
        assertEquals("https", uri.scheme)
        assertEquals("api.xydeskremote.biz.id", uri.host)
        assertEquals("/api/github/oauth/login", uri.path)
        assertTrue(uri.query.contains("client=xydesk"))
        assertTrue(uri.query.contains("session_id=session-123_ABC"))
        assertEquals("api.xydeskremote.biz.id", URI(GitHubOAuthEndpoints.CALLBACK).host)
    }
    @Test fun invalidSessionCannotInjectClientOrRedirect() {
        for (sid in listOf("", "a&client=legacy", "a?redirect_uri=https://example.test", "../a", "x".repeat(65))) {
            try { GitHubOAuthEndpoints.login(sid); fail("Expected invalid session rejection") }
            catch (expected: IllegalArgumentException) { }
        }
    }
}
