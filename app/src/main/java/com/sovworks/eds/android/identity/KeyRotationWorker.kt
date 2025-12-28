package com.sovworks.eds.android.identity

import android.content.Context
import androidx.work.*
import com.sovworks.eds.android.network.WebRtcService
import java.util.concurrent.TimeUnit

class KeyRotationWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val identity = IdentityManager.loadIdentity(applicationContext) ?: return Result.success()
        
        if (IdentityManager.shouldRotate(identity)) {
            val newIdentity = IdentityManager.rotateKeys(applicationContext)
            if (newIdentity != null) {
                // Benachrichtige verbundene Peers \u00fcber den neuen Schl\u00fcssel
                WebRtcService.getPeerConnectionManager()?.broadcastTrustPackage()
            }
        }
        
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "KeyRotationWork"

        fun enqueuePeriodicWork(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            // Pr\u00fcfung alle 24 Stunden
            val workRequest = PeriodicWorkRequest.Builder(KeyRotationWorker::class.java, 24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
