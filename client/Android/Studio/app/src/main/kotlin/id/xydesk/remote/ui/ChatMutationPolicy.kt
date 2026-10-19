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
    if (current.replyToId == incoming.replyToId && current.replyToText == "Pesan ini dihapus")
        merged = merged.copy(replyToText = "Pesan ini dihapus")
    return if (!merged.deleted && merged.attachmentData.isBlank() && current.attachmentData.isNotBlank())
        merged.copy(attachmentData = current.attachmentData) else merged
}
