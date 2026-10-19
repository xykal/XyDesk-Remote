package id.xydesk.remote.ui

import java.security.MessageDigest
import java.util.Locale

internal fun normalizedChatAccount(login: String?): String = login.orEmpty().trim().removePrefix("@").lowercase(Locale.ROOT)

/** Distinct guest namespace; never use a raw login as a preference-key suffix. */
internal fun chatAccountPreferenceKey(base: String, login: String?): String {
    val identity = normalizedChatAccount(login).let { if (it.isEmpty()) "guest" else "github:$it" }
    val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return "$base.account.$digest"
}

/** Reject responses started before an account switch/logout. */
internal fun chatAccountSnapshotIsCurrent(requested: String?, current: String?): Boolean =
    normalizedChatAccount(requested) == normalizedChatAccount(current)
