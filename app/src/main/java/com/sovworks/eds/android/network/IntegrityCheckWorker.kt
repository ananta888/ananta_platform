package com.sovworks.eds.android.network

import android.content.Context
import android.util.Log
import androidx.work.*
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class IntegrityCheckWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        Log.d("IntegrityCheckWorker", "Starting file integrity check...")
        val sharedFileManager = SharedFileManager.getInstance(applicationContext)
        val files = sharedFileManager.getAllSharedFiles()

        for (path in files) {
            try {
                val file = File(path)
                if (file.exists()) {
                    val hash = calculateSHA256(file)
                    sharedFileManager.updateHash(path, hash)
                    Log.d("IntegrityCheckWorker", "Updated hash for $path: $hash")
                } else {
                    Log.w("IntegrityCheckWorker", "File not found: $path")
                }
            } catch (e: Exception) {
                Log.e("IntegrityCheckWorker", "Error checking integrity for $path", e)
            }
        }

        return Result.success()
    }

    private fun calculateSHA256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead = input.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val WORK_NAME = "IntegrityCheckWork"

        fun enqueuePeriodicWork(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequest.Builder(IntegrityCheckWorker::class.java, 24, TimeUnit.HOURS)
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
