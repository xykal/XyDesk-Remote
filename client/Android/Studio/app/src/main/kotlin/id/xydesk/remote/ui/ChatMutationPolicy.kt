package id.xydesk.remote.ui

internal fun ChatMessage.deletedPlaceholder(atMs: Long = deletedAt): ChatMessage = copy(
    text = "Pesan ini dihapus", translated = "", deleted = true, deletedAt = atMs,
    attachmentType = "", attachmentName = "", attachmentMime = "", attachmentSize = 0,
    attachmentData = "", viewOnce = false, replyToId = "", replyToName = "", replyToText = "",
    e2eeSig = "", e2eeVerified = false, isAnnouncement = false,
)

internal fun canMutateChatMessage(message: ChatMessage, account: String?): Boolean =
    message.authorId > 0 && !message.deleted && !message.mutationPending &&
        !message.id.startsWith("local-") && !account.isNullOrBlank() &&
        message.githubLogin.equals(account.trim().removePrefix("@"), ignoreCase = true)

/** History/WS responses must not overwrite newer revisions or a local in-flight mutation. */
internal fun reconcileChatMessage(current: ChatMessage, incoming: ChatMessage): ChatMessage {
    if (incoming.revision < current.revision) return current
    if (incoming.revision == current.revision && (current.mutationPending || current.deleted && !incoming.deleted)) return current
    var merged = if (incoming.deleted) incoming.deletedPlaceholder() else incoming
    // An unchanged history refresh is not confirmation of a failed local operation.
    if (incoming.revision == current.revision && !incoming.deleted && current.mutationError.isNotBlank())
        merged = merged.copy(mutationError = current.mutationError)
    if (current.replyToId == incoming.replyToId && current.replyToText == "Pesan ini dihapus")
        merged = merged.copy(replyToText = "Pesan ini dihapus")
    if (!merged.deleted) {
        if (merged.avatarUrl.isBlank() && current.avatarUrl.isNotBlank()) {
            merged = merged.copy(avatarUrl = current.avatarUrl)
        }
        if (merged.githubLogin.isBlank() && current.githubLogin.isNotBlank()) {
            merged = merged.copy(githubLogin = current.githubLogin)
        }
        if (merged.translated.isBlank() && current.translated.isNotBlank() && merged.text == current.text) {
            merged = merged.copy(translated = current.translated)
        }
        if (merged.attachmentData.isBlank() && current.attachmentData.isNotBlank()) {
            merged = merged.copy(attachmentData = current.attachmentData)
        }
    }
    return merged
}

/** Never acknowledge an unrelated/unchanged row merely because HTTP returned 200. */
internal fun isConfirmedChatMutation(original: ChatMessage, incoming: ChatMessage, action: String, text: String): Boolean =
    incoming.id == original.id && incoming.revision > original.revision &&
        if (action == "delete") incoming.deleted else !incoming.deleted && incoming.text == text.trim()
