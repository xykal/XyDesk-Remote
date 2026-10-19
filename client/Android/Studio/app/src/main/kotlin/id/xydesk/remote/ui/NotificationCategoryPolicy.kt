package id.xydesk.remote.ui

/** Replies and mentions (and likes/reactions) are independent filters, not mislabeled unions. */
internal fun notificationCategoryMatches(selected: String, actual: String): Boolean = when (selected) {
    "all" -> true
    "inbox" -> actual == "inbox" || actual == "comment"
    else -> selected == actual
}

internal fun notificationCategoryLabel(category: String): Pair<String, String> = when (category) {
    "announcement" -> "Pengumuman" to "Announcement"
    "reply" -> "Balasan" to "Reply"
    "mention" -> "Sebutan @" to "Mention @"
    "like" -> "Suka" to "Like"
    "reaction" -> "Reaksi" to "Reaction"
    "inbox", "comment" -> "Komentar" to "Comment"
    else -> "Info" to "Info"
}
