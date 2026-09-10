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
