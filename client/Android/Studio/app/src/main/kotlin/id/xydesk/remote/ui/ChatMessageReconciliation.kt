package id.xydesk.remote.ui

/** Use the existing server-echoed client_msg_id, never just equal text.
 * A missing legacy echo stays unconfirmed rather than acknowledging the wrong send.
 * This is local correlation, not server-side authentication or idempotency.
 */
internal fun findPendingChatIndex(messages: List<ChatMessage>, incoming: ChatMessage): Int {
    if (!incoming.mine || !incoming.clientMessageId.startsWith("local-")) return -1
    return messages.indexOfFirst { pending ->
        pending.mine && pending.id == incoming.clientMessageId &&
            if (pending.githubLogin.isNotBlank()) {
                pending.githubLogin.equals(incoming.githubLogin, ignoreCase = true)
            } else {
                pending.name.equals(incoming.name, ignoreCase = true)
            }
    }
}
