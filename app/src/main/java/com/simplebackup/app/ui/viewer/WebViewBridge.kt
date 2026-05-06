package com.simplebackup.app.ui.viewer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * JavaScript ↔ Android bridge exposed to the in-app WebView as `window.AndroidBridge`.
 *
 * Why it exists: Android WebView does not implement `window.print()` or `<a download>`.
 * The shared `viewer_template.html` calls these methods on `window.AndroidBridge` when it
 * detects the bridge, and falls back to the browser-native APIs when the same HTML file
 * is opened in a desktop browser (no bridge).
 *
 * `@JavascriptInterface` methods are invoked from a background thread by the WebView; UI
 * work is hopped to the activity's main thread.
 */
class WebViewBridge(
    private val activity: Activity,
    private val webViewRef: () -> WebView?,
    private val cacheDir: File,
    private val authority: String
) {
    @JavascriptInterface
    fun print() {
        activity.runOnUiThread {
            val wv = webViewRef() ?: run {
                Log.w(TAG, "print() called but WebView is gone")
                return@runOnUiThread
            }
            try {
                val pm = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val jobName = "SimpleBackup ${System.currentTimeMillis()}"
                val adapter = wv.createPrintDocumentAdapter(jobName)
                pm.print(jobName, adapter, PrintAttributes.Builder().build())
            } catch (e: Exception) {
                Log.e(TAG, "print failed", e)
                Toast.makeText(activity, "Print failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    @JavascriptInterface
    fun exportTxt(content: String, filename: String) {
        activity.runOnUiThread {
            try {
                val safe = filename.replace(Regex("[^A-Za-z0-9._\\-]"), "_").ifBlank { "export.txt" }
                val out = File(cacheDir, safe)
                out.writeText(content)
                val uri = FileProvider.getUriForFile(activity, authority, out)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, safe)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                activity.startActivity(Intent.createChooser(intent, "Save export"))
            } catch (e: Exception) {
                Log.e(TAG, "exportTxt failed", e)
                Toast.makeText(activity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        private const val TAG = "WebViewBridge"
        const val NAME = "AndroidBridge"
    }
}
