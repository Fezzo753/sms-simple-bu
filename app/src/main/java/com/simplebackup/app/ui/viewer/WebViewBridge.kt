package com.simplebackup.app.ui.viewer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
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
                val inner = wv.createPrintDocumentAdapter(jobName)
                // Wrap the inner adapter so we can notify JS when the print job
                // is complete (or cancelled) — that's the cue to repaginate
                // the DOM. Until then the unpaginated view is what gets
                // captured into the PDF.
                val adapter = NotifyingPrintAdapter(inner) {
                    activity.runOnUiThread {
                        webViewRef()?.evaluateJavascript(
                            "window.__onPrintFinished && window.__onPrintFinished()",
                            null
                        )
                    }
                }
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

/**
 * Forwards every call to the underlying WebView-supplied [PrintDocumentAdapter] and
 * fires [onComplete] from `onFinish()` — which the system invokes once the user has
 * saved/cancelled the print job. Lets the page restore its paginated layout only
 * after PrintManager has finished reading the WebView.
 */
private class NotifyingPrintAdapter(
    private val inner: PrintDocumentAdapter,
    private val onComplete: () -> Unit
) : PrintDocumentAdapter() {
    override fun onStart() = inner.onStart()

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: android.os.Bundle?
    ) {
        inner.onLayout(oldAttributes, newAttributes, cancellationSignal, callback, extras)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        inner.onWrite(pages, destination, cancellationSignal, callback)
    }

    override fun onFinish() {
        try {
            inner.onFinish()
        } finally {
            onComplete()
        }
    }
}
