package com.anvith.expensetracker

import android.content.Context
import androidx.work.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class BackupWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    override fun doWork(): Result = if (runNow(applicationContext)) Result.success() else Result.retry()

    companion object {
        fun runNow(context: Context): Boolean {
            val prefs = context.getSharedPreferences("expense_tracker", 0)
            val urlText = prefs.getString("backup_url", "")?.trim().orEmpty()
            val token = prefs.getString("backup_token", "")?.trim().orEmpty()
            if (urlText.isBlank() || token.isBlank()) return false
            return runCatching {
                val conn = (URL(urlText).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 5000
                    readTimeout = 10000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("X-Expense-Token", token)
                }
                conn.outputStream.use { it.write(payload(context).toByteArray(Charsets.UTF_8)) }
                val ok = conn.responseCode in 200..299
                conn.disconnect()
                ok
            }.getOrDefault(false)
        }

        private fun payload(context: Context): String {
            val prefs = context.getSharedPreferences("expense_tracker", 0)
            return JSONObject().apply {
                put("version", 1)
                put("device", android.os.Build.MODEL)
                put("createdAt", System.currentTimeMillis())
                put("budget", prefs.getFloat("budget", 15000f).toDouble())
                put("expenses", JSONArray(prefs.getString("expenses", "[]") ?: "[]"))
            }.toString()
        }
    }
}

object BackupScheduler {
    fun schedule(context: Context) {
        val prefs = context.getSharedPreferences("expense_tracker", 0)
        if (!prefs.getBoolean("auto_backup", false)) {
            WorkManager.getInstance(context).cancelUniqueWork("expense-auto-backup")
            return
        }
        val request = PeriodicWorkRequestBuilder<BackupWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "expense-auto-backup",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
