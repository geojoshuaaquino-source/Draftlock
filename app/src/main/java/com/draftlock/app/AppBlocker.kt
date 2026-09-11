package com.draftlock.app

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import com.draftlock.app.admin.DraftLockDeviceAdminReceiver

class AppBlocker(private val context: Context) {
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(context, DraftLockDeviceAdminReceiver::class.java)

    fun canSuspendApps(): Boolean =
        dpm.isDeviceOwnerApp(context.packageName) || dpm.isProfileOwnerApp(context.packageName)

    fun diagnostics(): String {
        val isDO = dpm.isDeviceOwnerApp(context.packageName)
        val isPO = dpm.isProfileOwnerApp(context.packageName)
        val adminActive = dpm.isAdminActive(admin)
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
        if (!canSuspendApps() || packages.isEmpty()) return emptyList()
        return try {
            dpm.setPackagesSuspended(admin, packages.filter { it != context.packageName }.toTypedArray(), true).toList()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    fun unsuspend(packages: List<String>): List<String> {
        if (!canSuspendApps() || packages.isEmpty()) return emptyList()
        return try {
            dpm.setPackagesSuspended(admin, packages.filter { it != context.packageName }.toTypedArray(), false).toList()
        } catch (_: SecurityException) {
            emptyList()
        }
    }
}
