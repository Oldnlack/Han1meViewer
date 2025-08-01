package com.yenaly.han1meviewer.util

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.itxca.spannablex.spannable
import com.yenaly.han1meviewer.BuildConfig
import com.yenaly.han1meviewer.FILE_PROVIDER_AUTHORITY
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.model.github.Latest
import com.yenaly.han1meviewer.worker.HUpdateWorker
import com.yenaly.yenaly_libs.utils.dp
import com.yenaly.yenaly_libs.utils.showShortToast
import java.io.File
import java.util.regex.Pattern

val Context.updateFile: File get() = File(applicationContext.cacheDir, "update.apk")

fun checkNeedUpdate(versionName: String): Boolean {
    val latestVersionCode = versionName.substringAfter("+", "").toIntOrNull() ?: Int.MAX_VALUE
    return BuildConfig.VERSION_CODE < latestVersionCode
}

suspend fun Context.showUpdateDialog(latest: Latest) {
    val spannable = spannable {
//        getString(R.string.new_version_found).span {
//            style(Typeface.BOLD)
//            relativeSize(1.2f)
//        }
//        newline()
        latest.version.span {
            style(Typeface.BOLD)
            relativeSize(1.2f)
        }
        newline()
        newline()
        getString(R.string.update_content).span {
            style(Typeface.BOLD)
            relativeSize(1.2f)
        }
        newline()
        latest.changelog.text()
    }
    val webUrlPattern = Pattern.compile(
        "(https?://[\\w-]+(\\.[\\w-]+)+([\\w.,@?^=%&:/~+#-]*[\\w@?^=%&/~+#-])?)"
    )
    val transformFilter = Linkify.TransformFilter { match, _ ->
        var url = match.group()
        while (url.endsWith(")") || url.endsWith("]") || url.endsWith("}") || url.endsWith(",") || url.endsWith(".")) {
            url = url.dropLast(1)
        }
        url
    }

    val messageView = TextView(this).apply {
        text = spannable
        movementMethod = LinkMovementMethod.getInstance()
        setPadding(32.dp, 16.dp, 32.dp, 0)
        setTextIsSelectable(true)
        Linkify.addLinks(this, webUrlPattern, null, null, transformFilter)
    }

    val dialog = createAlertDialog {
        setTitle(R.string.new_version_found)
        setView(messageView)
        setCancelable(false)
    }
    val res = dialog.await(
        positiveText = getString(R.string.update),
        negativeText = getString(R.string.cancel),
    )
    if (res == AlertDialog.BUTTON_POSITIVE) {
        val update = this.getUpdateIfExists(latest)
        if (update != null) {
            installApkPackage(update)
        } else {
            requestPostNotificationPermission()
            HUpdateWorker.enqueue(this.applicationContext, latest)
            showShortToast(R.string.update_download_background)
        }
    }
    dialog.dismiss()
}

private fun Context.getUpdateIfExists(latest: Latest): File? {
    val nodeId = Preferences.updateNodeId
    return updateFile.takeIf { file ->
        !BuildConfig.DEBUG && file.exists() && nodeId.isNotEmpty() && nodeId == latest.nodeId
    }
}

suspend fun Context.installApkPackage(file: File) {
    val canInstall = requestInstallPermission()
    if (canInstall) {
        val uri = FileProvider.getUriForFile(this.applicationContext, FILE_PROVIDER_AUTHORITY, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setDataAndType(uri, "application/vnd.android.package-archive")
        }
        startActivity(intent)
    }
}
