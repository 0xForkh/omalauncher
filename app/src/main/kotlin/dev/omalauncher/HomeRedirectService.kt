package dev.omalauncher

import android.accessibilityservice.AccessibilityService
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.os.UserManager
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Keeps Home on Omalauncher on phones whose skin sends the Home button to its own launcher first
 * (ColorOS, and others whose recents and gestures live in the stock launcher). When one of the
 * other home activities comes to the front while Omalauncher holds the home role, it opens
 * Omalauncher instead. The stock launcher stays enabled, so its recents screen and the
 * quick-switch gesture, separate activities, keep working.
 *
 * It listens to window changes of the other home apps only, and never reads window content.
 */
class HomeRedirectService : AccessibilityService() {
    private var homes: Set<String> = emptySet()
    private var lastRedirect = 0L

    override fun onServiceConnected() {
        val others =
            packageManager
                .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DISABLED_COMPONENTS)
                .map { it.activityInfo }
                .filter { it.packageName != packageName }
        homes = others.map { "${it.packageName}/${it.name}" }.toSet()
        serviceInfo = serviceInfo.apply { packageNames = others.map { it.packageName }.distinct().toTypedArray() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val activity = "${event.packageName}/${event.className}"
        if (activity !in homes) return
        // Before the first unlock only the system's direct-boot home (FallbackHome) can run.
        if (!getSystemService(UserManager::class.java).isUserUnlocked) return
        if (!getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastRedirect < 500) return
        lastRedirect = now
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setClass(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
        )
    }

    override fun onInterrupt() {}

    companion object {
        fun isEnabled(context: Context): Boolean {
            val self = ComponentName(context, HomeRedirectService::class.java)
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == self }
        }

        fun openSettings(context: Context) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
