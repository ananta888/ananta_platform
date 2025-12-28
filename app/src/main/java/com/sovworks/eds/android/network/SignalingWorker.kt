package com.sovworks.eds.android.network

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker.Result
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import com.sovworks.eds.android.identity.IdentityManager
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.android.settings.UserSettingsCommon
import java.util.*
import java.util.concurrent.TimeUnit

class SignalingWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val settings = UserSettings.getSettings(applicationContext)
        if (settings.getSignalingMode() != UserSettingsCommon.SIGNALING_MODE_HTTP) {
            return Result.success()
        }

        val urls = settings.getSignalingServerUrls()
        if (urls.isEmpty()) return Result.failure()

        val myId = resolvePeerId(applicationContext, settings)
        val clients = urls.map { HttpSignalingClient(it, myId) }
        val multiClient = MultiSignalingClient(clients)
        
        return try {
            multiClient.pollMessages()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun resolvePeerId(context: Context, settings: UserSettings): String {
        val identity = IdentityManager.loadIdentity(context)
        if (identity != null && identity.id.isNotBlank()) {
            return identity.id
        }
        val prefs = settings.sharedPreferences
        val cached = prefs.getString(UserSettingsCommon.SIGNALING_PEER_ID, null)
        if (!cached.isNullOrBlank()) {
            return cached
        }
        return UUID.randomUUID().toString() // Should not happen often if cached
    }

    companion object {
        private const val WORK_NAME = "SignalingPollingWork"

        fun enqueuePeriodicWork(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequest.Builder(SignalingWorker::class.java, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }

        fun stopWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
