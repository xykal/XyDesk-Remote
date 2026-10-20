package id.xydesk.remote.ui

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** System-only entry; process death does not lose encrypted queued payloads. */
class ChatOutboxJobService : JobService() {
    private var work: Job? = null
    private var activeParams: JobParameters? = null
    override fun onStartJob(params: JobParameters): Boolean {
        activeParams = params
        work = GlobalChatOutbox.scope.launch {
            val delay = runCatching { GlobalChatOutbox.flush(applicationContext) }.getOrElse { 300_000L }
            if (kotlin.coroutines.coroutineContext[Job]?.isActive != true) return@launch
            Handler(Looper.getMainLooper()).post {
                if (activeParams !== params) return@post
                activeParams = null
                jobFinished(params, false)
                if (delay != null) GlobalChatOutbox.schedule(applicationContext, delay)
            }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { activeParams = null; work?.cancel(); return true }
}
