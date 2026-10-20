package com.proyecto.android

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class SaveFileBridge(
    private val activity: ComponentActivity,
    private val onSave: (String, ByteArray) -> Unit
) {
    @JavascriptInterface
    fun saveFile(name: String, base64: String) {
        try {
            val data = Base64.decode(base64, Base64.DEFAULT)
            activity.runOnUiThread { onSave(name, data) }
        } catch (_: Exception) { /* puente: entrada invalida */ }
    }
}

class MainActivity : ComponentActivity() {

    private var pendingFile: Pair<String, ByteArray>? = null
    private val createDoc =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            val uri = if (res.resultCode == RESULT_OK) res.data?.data else null
            val pending = pendingFile
            pendingFile = null
            if (uri != null && pending != null) {
                try {
                    contentResolver.openOutputStream(uri)?.use { it.write(pending.second) }
                    Toast.makeText(this, "Guardado ${pending.first}", Toast.LENGTH_LONG).show()
                } catch (_: Exception) {
                    Toast.makeText(this, "No se pudo guardar.", Toast.LENGTH_SHORT).show()
                }
            }
        }

    private fun saveFileViaSAF(name: String, data: ByteArray) {
        pendingFile = name to data
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/markdown"
            putExtra(Intent.EXTRA_TITLE, name)
        }
        createDoc.launch(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inicializar auto-updater (no bloquea la UI)
        lifecycleScope.launch {
            try {
                val updater = AppUpdater(this@MainActivity)
                val latest = updater.checkLatest()
                if (latest != null && latest.versionCode > updater.installedVersionCode()) {
                    showUpdateDialog(latest)
                }
            } catch (_: Exception) { /* Silenciar errores de red */ }
        }

        setContent {
            MaterialTheme {
                ProyectoContent(onSaveFile = ::saveFileViaSAF)
            }
        }
    }

    private fun showUpdateDialog(info: ReleaseInfo) {
        runOnUiThread {
            android.app.AlertDialog.Builder(this)
                .setTitle("Actualización disponible")
                .setMessage("Versión ${info.versionName} (${info.versionCode}) está disponible. ¿Actualizar ahora?")
                .setPositiveButton("Actualizar") { _, _ ->
                    lifecycleScope.launch {
                        val updater = AppUpdater(this@MainActivity)
                        val ok = updater.downloadAndInstall(info)
                        if (!ok) {
                            Toast.makeText(this@MainActivity, "Error al descargar.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Más tarde", null)
                .setCancelable(false)
                .show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProyectoContent(onSaveFile: (String, ByteArray) -> Unit) {
    var showError by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {},
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        if (showError) {
            ErrorScreen(
                message = "No se pudo cargar la aplicación.",
                onRetry = { showError = false }
            )
        } else {
            WebViewHost(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onLoadError = { showError = true },
                onSaveFile = onSaveFile
            )
        }
    }
}

@Composable
fun WebViewHost(
    modifier: Modifier = Modifier,
    onLoadError: () -> Unit,
    onSaveFile: (String, ByteArray) -> Unit
) {
    var canGoBack by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    BackHandler(enabled = canGoBack) {
        webViewRef?.goBack()
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                addJavascriptInterface(
                    SaveFileBridge(context as ComponentActivity, onSaveFile),
                    "AndroidSaveFile"
                )
                setWebViewClient(object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        canGoBack = view?.canGoBack() ?: false
                    }
                    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                        canGoBack = view?.canGoBack() ?: false
                    }
                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        onLoadError()
                    }
                })
                loadUrl("file:///android_asset/launcher.html")
            }
        },
        update = { webView -> webViewRef = webView }
    )
}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Reintentar")
        }
    }
}
