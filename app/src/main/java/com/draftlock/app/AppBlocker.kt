package com.draftlock.app

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import com.draftlock.app.admin.DraftLockDeviceAdminReceiver

data class SuspensionResult(
    val attempted: Boolean,
    val failedPackages: List<String> = emptyList(),
    val error: Exception? = null
)

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
        val enabled = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains("${context.packageName}/${context.packageName}.DraftLockAccessibilityService")
    }

    fun isPopupBlockingAvailable(): Boolean = isAccessibilityEnabled()

    fun suspendChecked(packages: List<String>): SuspensionResult {
        val manager = dpm ?: return SuspensionResult(attempted = false)
        if (!canSuspendApps()) return SuspensionResult(attempted = false)

        val targets = packages.filter { it != context.packageName }.distinct()
        if (targets.isEmpty()) return SuspensionResult(attempted = false)

        return try {
            val failed = manager
                .setPackagesSuspended(admin, targets.toTypedArray(), true)
                ?.toList()
                .orEmpty()

            if (failed.isNotEmpty()) {
                android.util.Log.w("DraftLock", "Could not suspend packages: $failed")
            }
            SuspensionResult(attempted = true, failedPackages = failed)
        } catch (e: SecurityException) {
            android.util.Log.e("DraftLock", "Package suspension rejected", e)
            SuspensionResult(attempted = true, error = e)
        } catch (e: Exception) {
            android.util.Log.e("DraftLock", "Package suspension failed", e)
            SuspensionResult(attempted = true, error = e)
        }
    }

    fun unsuspendChecked(packages: List<String>): SuspensionResult {
        val manager = dpm ?: return SuspensionResult(attempted = false)
        if (!canSuspendApps()) return SuspensionResult(attempted = false)

        val targets = packages.filter { it != context.packageName }.distinct()
        if (targets.isEmpty()) return SuspensionResult(attempted = false)

        return try {
            val failed = manager
                .setPackagesSuspended(admin, targets.toTypedArray(), false)
                ?.toList()
                .orEmpty()

            if (failed.isNotEmpty()) {
                android.util.Log.w("DraftLock", "Could not unsuspend packages: $failed")
            }
            SuspensionResult(attempted = true, failedPackages = failed)
        } catch (e: SecurityException) {
            android.util.Log.e("DraftLock", "Package unsuspension rejected", e)
            SuspensionResult(attempted = true, error = e)
        } catch (e: Exception) {
            android.util.Log.e("DraftLock", "Package unsuspension failed", e)
            SuspensionResult(attempted = true, error = e)
        }
    }

    fun suspend(packages: List<String>): List<String> =
        suspendChecked(packages).failedPackages

    fun unsuspend(packages: List<String>): List<String> =
        unsuspendChecked(packages).failedPackages
}
