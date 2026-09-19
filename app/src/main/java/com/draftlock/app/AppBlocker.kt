package com.draftlock.app

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import com.draftlock.app.admin.DraftLockDeviceAdminReceiver

class AppBlocker(private val context: Context) {
    private val dpm: DevicePolicyManager? = try {
        context.getSystemService(DevicePolicyManager::class.java)
    } catch (_: Exception) { null }
    private val admin = ComponentName(context, DraftLockDeviceAdminReceiver::class.java)

    fun canSuspendApps(): Boolean {
        val manager = dpm ?: return false
        return try {
            manager.isDeviceOwnerApp(context.packageName) || manager.isProfileOwnerApp(context.packageName)
        } catch (_: Exception) { false }
    }

    fun diagnostics(): String {
        val manager = dpm
        val isDO = try { manager?.isDeviceOwnerApp(context.packageName) == true } catch (_: Exception) { false }
        val isPO = try { manager?.isProfileOwnerApp(context.packageName) == true } catch (_: Exception) { false }
        val adminActive = try { manager?.isAdminActive(admin) == true } catch (_: Exception) { false }
        val accessibilityOn = isAccessibilityEnabled()
        return when {
            isDO -> "Device Owner ✓ — strong suspend + popup active"
            isPO -> "Profile Owner ✓ — strong suspend + popup active"
            accessibilityOn -> "Popup blocking ✓ — accessibility service is ON (no device-owner needed). Enable Usage Access too for timers."
            adminActive -> "Device Admin but NOT owner — enable Accessibility (Settings → Accessibility → DraftLock) for popup blocking, or: adb shell dpm set-device-owner com.draftlock.app/.admin.DraftLockDeviceAdminReceiver"
            else -> "Enable Accessibility → DraftLock for popup blocking (needs no device-owner). Strong suspend needs device-owner via adb on a test device."
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.contains("${context.packageName}/${context.packageName}.DraftLockAccessibilityService")
    }

    fun isPopupBlockingAvailable(): Boolean = isAccessibilityEnabled()

    fun suspend(packages: List<String>): List<String> {
        val manager = dpm ?: return emptyList()
        if (!canSuspendApps() || packages.isEmpty()) return emptyList()
        return try {
            manager.setPackagesSuspended(admin, packages.filter { it != context.packageName }.toTypedArray(), true)?.toList() ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: Exception) { emptyList() }
    }

    fun unsuspend(packages: List<String>): List<String> {
        val manager = dpm ?: return emptyList()
        if (!canSuspendApps() || packages.isEmpty()) return emptyList()
        return try {
            manager.setPackagesSuspended(admin, packages.filter { it != context.packageName }.toTypedArray(), false)?.toList() ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: Exception) { emptyList() }
    }
}
