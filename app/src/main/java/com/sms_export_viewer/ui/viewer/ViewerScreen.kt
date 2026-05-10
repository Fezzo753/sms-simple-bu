package com.sms_export_viewer.ui.viewer

import android.app.Activity
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sms_export_viewer.SimpleBackupApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val container = (context.applicationContext as SimpleBackupApplication).container
    var htmlFile by remember { mutableStateOf<File?>(null) }
    var resolved by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val s = withContext(Dispatchers.IO) { container.settings.current() }
        val devicePhone = container.resolveDevicePhone(s.phoneOverride)
        val f = withContext(Dispatchers.IO) { container.regenerateHtmlIfStale(devicePhone) }
        htmlFile = f?.takeIf { it.exists() }
        resolved = true
    }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup viewer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val f = htmlFile
            if (f != null) {
                AndroidView(
                    factory = { ctx ->
                        var webViewHolder: WebView? = null
                        WebView(ctx).apply {
                            webViewHolder = this
                            // Explicit MATCH_PARENT so the WebView's container size is
                            // unambiguous — without this, `100%` heights inside the page
                            // can resolve to 0 and collapse the grid layout.
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.javaScriptEnabled = true
                            settings.allowFileAccess = true
                            settings.useWideViewPort = true
                            // Keep `loadWithOverviewMode` off — the page is mobile-aware
                            // via the viewport meta, and overview mode can squash the
                            // page into a small region in some WebView builds.
                            settings.loadWithOverviewMode = false
                            settings.domStorageEnabled = true
                            webChromeClient = object : WebChromeClient() {
                                override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                                    val tag = "SimpleBackupViewer"
                                    val line = "${m.message()} (src=${m.sourceId()}:${m.lineNumber()})"
                                    when (m.messageLevel()) {
                                        ConsoleMessage.MessageLevel.ERROR -> Log.e(tag, line)
                                        ConsoleMessage.MessageLevel.WARNING -> Log.w(tag, line)
                                        else -> Log.d(tag, line)
                                    }
                                    return true
                                }
                            }
                            // Bridge for Print + Export TXT — WebView doesn't implement
                            // window.print() or <a download>, so the page calls these
                            // methods on `window.AndroidBridge` when present.
                            (ctx as? Activity)?.let { activity ->
                                addJavascriptInterface(
                                    WebViewBridge(
                                        activity = activity,
                                        webViewRef = { webViewHolder },
                                        cacheDir = activity.cacheDir,
                                        authority = "${activity.packageName}.fileprovider"
                                    ),
                                    WebViewBridge.NAME
                                )
                            }
                            // Use toURI() so paths with `+` or spaces are URL-encoded.
                            loadUrl(f.toURI().toString())
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (resolved) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "No backup yet",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Run a backup from the home screen and then come back here to view it.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onBack) { Text("Go back") }
                }
            }
        }
    }
}
