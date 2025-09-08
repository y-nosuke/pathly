package com.pathly.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object PermissionUtils {

    // 権限定数定義
    object Permissions {
        const val FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION
        const val COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION
        const val POST_NOTIFICATIONS = Manifest.permission.POST_NOTIFICATIONS
    }

    // 権限グループ定義
    object PermissionGroups {
        val LOCATION_PERMISSIONS = arrayOf(Permissions.FINE_LOCATION, Permissions.COARSE_LOCATION)

        val ALL_REQUIRED_PERMISSIONS =
                arrayOf(
                        Permissions.FINE_LOCATION,
                        Permissions.COARSE_LOCATION,
                        Permissions.POST_NOTIFICATIONS
                )
    }

    /** 単一権限をチェック */
    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
    }

    /** 複数権限をすべてチェック */
    fun hasAllPermissions(context: Context, permissions: Array<String>): Boolean {
        return permissions.all { permission -> hasPermission(context, permission) }
    }

    /** 位置権限（FINE_LOCATION + COARSE_LOCATION）をチェック */
    fun hasLocationPermissions(context: Context): Boolean {
        return hasAllPermissions(context, PermissionGroups.LOCATION_PERMISSIONS)
    }

    /** アプリで必要なすべての権限をチェック (FINE_LOCATION + COARSE_LOCATION + POST_NOTIFICATIONS) */
    fun hasAllRequiredPermissions(context: Context): Boolean {
        return hasAllPermissions(context, PermissionGroups.ALL_REQUIRED_PERMISSIONS)
    }

    /** 欠けている権限のリストを取得 */
    fun getMissingPermissions(context: Context, permissions: Array<String>): List<String> {
        return permissions.filter { permission -> !hasPermission(context, permission) }
    }

    /** 欠けている権限のリストを取得（すべての必須権限対象） */
    fun getMissingRequiredPermissions(context: Context): List<String> {
        return getMissingPermissions(context, PermissionGroups.ALL_REQUIRED_PERMISSIONS)
    }
}
