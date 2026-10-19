package id.xydesk.remote.ui

/** One dispatch transaction; release attempted keys in reverse even after a failure.
 * Boolean describes callback failures, not server acknowledgement of RDP input.
 */
internal fun dispatchHudKeyCombo(keys: List<Int>, send: (Int, Boolean) -> Unit): Boolean {
    val attempted = mutableListOf<Int>()
    var successful = true
    try {
        for (key in keys.distinct()) {
            attempted.add(key)
            send(key, true)
        }
    } catch (_: Exception) {
        successful = false
    } finally {
        for (key in attempted.asReversed()) {
            try { send(key, false) } catch (_: Exception) { successful = false }
        }
    }
    return successful
}
