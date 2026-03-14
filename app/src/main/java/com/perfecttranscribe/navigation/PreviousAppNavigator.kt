package com.perfecttranscribe.navigation

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process

object PreviousAppNavigator {
    private const val LOOKBACK_WINDOW_MILLIS = 2 * 60 * 1000L

    fun captureReturnPackage(
        context: Context,
        now: Long = System.currentTimeMillis(),
    ): String? {
        if (!hasUsageAccess(context)) return null

        val usageStatsManager = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val ignoredPackages = buildSet {
            add(context.packageName)
            addAll(resolveHomePackages(context.packageManager))
        }

        val events = usageStatsManager.queryEvents(now - LOOKBACK_WINDOW_MILLIS, now)
        val event = UsageEvents.Event()
        var candidate: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val packageName = event.packageName ?: continue
            if (!isForegroundEvent(event.eventType) || packageName in ignoredPackages) continue
            candidate = packageName
        }

        return candidate
    }

    fun relaunchPackage(context: Context, packageName: String?): Boolean {
        if (packageName.isNullOrBlank() || packageName == context.packageName) return false

        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
        )

        return runCatching {
            context.startActivity(launchIntent)
            true
        }.getOrDefault(false)
    }

    private fun hasUsageAccess(context: Context): Boolean {
        val appOpsManager = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun resolveHomePackages(packageManager: PackageManager): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.queryIntentActivities(intent, 0)
            .mapNotNullTo(mutableSetOf()) { it.activityInfo?.packageName }
    }

    private fun isForegroundEvent(eventType: Int): Boolean {
        if (eventType == UsageEvents.Event.ACTIVITY_RESUMED) return true
        @Suppress("DEPRECATION")
        return eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
    }
}
