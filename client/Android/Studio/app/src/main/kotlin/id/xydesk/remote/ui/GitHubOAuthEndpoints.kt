package id.xydesk.remote.ui

/** Explicit canonical client selector also works against the previous deployed login router. */
internal object GitHubOAuthEndpoints {
    const val CALLBACK = "https://api.xydeskremote.biz.id/api/github/oauth/callback"
    fun login(sessionId: String): String {
        require(sessionId.matches(Regex("[A-Za-z0-9_-]{1,64}")))
        return "https://api.xydeskremote.biz.id/api/github/oauth/login?apk=1&client=xydesk&session_id=$sessionId"
    }
}
