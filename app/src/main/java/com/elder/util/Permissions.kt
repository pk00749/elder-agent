// 权限 helper（§4.6 §G.1：麦克风 / 相机 / 通知按需申请，不二次引导）
package com.elder.android.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

object Permissions {
    fun check(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED

    fun hasMic(context: Context): Boolean =
        check(context, Manifest.permission.RECORD_AUDIO)

    fun hasCamera(context: Context): Boolean =
        check(context, Manifest.permission.CAMERA)

    fun hasNotification(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return check(context, Manifest.permission.POST_NOTIFICATIONS)
    }
}

data class PermissionState(val granted: Boolean, val request: () -> Unit)

@Composable
fun rememberPermissionState(permission: String): PermissionState {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(Permissions.check(context, permission)) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { ok -> granted = ok }
    return PermissionState(
        granted = granted,
        request = { launcher.launch(permission) },
    )
}

@Composable
fun RequestPermissionEffect(permission: String) {
    val state = rememberPermissionState(permission)
    LaunchedEffect(Unit) { if (!state.granted) state.request() }
}
