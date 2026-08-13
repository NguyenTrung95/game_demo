package com.example.myapplication.server.core

import android.content.Context
import android.content.pm.ApplicationInfo
import java.io.File

/**
 * Ktor's staticFiles() needs a real java.io.File tree — AssetManager isn't a filesystem — so the
 * bundled web/ assets are copied to internal storage once and served from there afterwards.
 */
object AssetSync {

    fun ensureWebRootCopied(context: Context): File {
        val destRoot = File(context.filesDir, "web")
        val markerFile = File(context.filesDir, "web_synced.marker")
        // Keyed by versionCode, not a one-time flag — otherwise a web/ asset update shipped in a
        // new APK build (new HTML/CSS/JS) would never reach devices that already ran an older build.
        // In a debug build versionCode is typically left untouched across every local rebuild, so
        // that check alone would keep re-serving whatever was copied on the very first install —
        // debug builds always re-copy instead of trusting the marker.
        val currentVersion = appVersionCode(context)
        if (!isDebuggable(context) && destRoot.exists() && markerFile.exists() && markerFile.readText() == currentVersion) {
            return destRoot
        }

        destRoot.deleteRecursively()
        copyAssetDir(context, "web", destRoot)
        markerFile.writeText(currentVersion)
        return destRoot
    }

    private fun isDebuggable(context: Context): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private fun appVersionCode(context: Context): String {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        return if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode.toString() else info.versionCode.toString()
    }

    private fun copyAssetDir(context: Context, assetPath: String, destDir: File) {
        // AssetManager.list() is unreliable about signaling "this is a file, not a directory" —
        // depending on Android version/APK compression it can return null OR an empty array for
        // a leaf file, so both are treated the same way: fall through and try to open it as a file.
        val children = context.assets.list(assetPath) ?: emptyArray()
        if (children.isEmpty()) {
            // Leaf file, not a directory.
            destDir.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                destDir.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }
        destDir.mkdirs()
        for (child in children) {
            copyAssetDir(context, "$assetPath/$child", File(destDir, child))
        }
    }
}
