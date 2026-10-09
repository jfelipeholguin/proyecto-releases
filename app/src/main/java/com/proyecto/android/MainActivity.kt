package com.proyecto.android

import android.os.Bundle
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

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
                ProyectoContent()
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
fun ProyectoContent() {
    var showError by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Proyecto") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
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
                onLoadError = { showError = true }
            )
        }
    }
}

@Composable
fun WebViewHost(
    modifier: Modifier = Modifier,
    onLoadError: () -> Unit
) {
    var canGoBack by remember { mutableStateOf(false) }

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
                setWebViewClient(object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
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
        update = { webView -> }
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
