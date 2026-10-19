package id.xydesk.remote.core

/** Session-only native AUDIN intent. All access is serialized by SessionManager.lifecycleLock. */
internal class SessionMicrophoneControl {
    data class Restart(val profileId: String, val instance: Long, val enabled: Boolean, val generation: Long)
    private var profileId: String? = null
    private var micOverride: Boolean? = null
    private var generation = 0L
    private var pending: Restart? = null
    private var activationPending = false

    fun apply(profile: String, saved: RdpOptions): RdpOptions {
        if (profileId != profile) {
            cancel(suppressActivation = true)
            profileId = profile
            micOverride = null
        }
        activationPending = false
        return micOverride?.let { saved.copy(microphone = it) } ?: saved
    }

    fun plan(profile: String, instance: Long, enabled: Boolean): Restart {
        require(instance != 0L)
        profileId = profile
        micOverride = enabled
        activationPending = enabled
        return Restart(profile, instance, enabled, ++generation).also { pending = it }
    }

    fun cancel(suppressActivation: Boolean = false) {
        ++generation
        pending = null
        if (suppressActivation && activationPending) {
            micOverride = false
            activationPending = false
        }
    }

    fun take(instance: Long): Restart? = pending?.takeIf { it.instance == instance }?.also { pending = null }
    fun isCurrent(restart: Restart): Boolean = restart.generation == generation && restart.profileId == profileId
    fun isPending(): Boolean = pending != null
}
