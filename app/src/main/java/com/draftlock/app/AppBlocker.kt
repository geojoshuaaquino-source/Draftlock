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
        return when {
            isDO -> "Device Owner ✓ — strong blocking active"
            isPO -> "Profile Owner ✓ — strong blocking active"
            adminActive -> "Device Admin active but NOT owner — run: adb shell dpm set-device-owner com.draftlock.app/.admin.DraftLockDeviceAdminReceiver (needs fresh device/no accounts)"
            else -> "Not device owner — blocking unavailable. Set up via adb on a test device you control; otherwise apps show as LOCKED but won't auto-suspend."
        }
    }

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
