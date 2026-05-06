package com.simplebackup.app.ui.viewer

import android.app.Activity
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
import com.simplebackup.app.SimpleBackupApplication
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
        val f = File(container.filesDir, "Backup_${devicePhone}.html")
        htmlFile = if (f.exists()) f else null
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
                            // Explicit MATCH_PARENT so the WebView's height is unambiguous —
                            // without this, some configurations leave 100vh / 100% resolving
                            // to 0, which collapses the inner grid layout.
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.javaScriptEnabled = true
                            settings.allowFileAccess = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = false
                            settings.domStorageEnabled = true
                            // Pipe JS console + page errors to Logcat so blank-page bugs are diagnosable.
                            webChromeClient = object : WebChromeClient() {
                                override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                                    Log.println(
                                        when (msg.messageLevel()) {
                                            ConsoleMessage.MessageLevel.ERROR -> Log.ERROR
                                            ConsoleMessage.MessageLevel.WARNING -> Log.WARN
                                            else -> Log.DEBUG
                                        },
                                        "ViewerWebView",
                                        "${msg.sourceId()}:${msg.lineNumber()} — ${msg.message()}"
                                    )
                                    return true
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    Log.e(
                                        "ViewerWebView",
                                        "load error: ${error?.description} for ${request?.url}"
                                    )
                                }
                            }
                            // Bridge for Print + Export TXT — WebView doesn't implement
                            // window.print() or <a download>, so the page calls these methods
                            // on `window.AndroidBridge` when it detects the bridge.
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
                            // Use toURI() so paths with `+` or spaces are URL-encoded properly.
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
