package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONTokener

/** Visible standard WebView only. No challenge handler, cookie export or HTTP replay. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun InkShelf2EventImportScreen(
    guide: InkShelf2Guide,
    padding: PaddingValues,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onProvider: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val slug = requireNotNull(guide.batcaveSlug)
    val session = remember(slug) { InkShelf2EventImportSession(slug) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var opened by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var generation by remember { mutableIntStateOf(0) }
    var revision by remember { mutableIntStateOf(0) }
    var active by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("Import reading-list metadata only where the website permits it. Complete any website verification yourself. No comics are downloaded.") }
    var complete by remember { mutableStateOf("") }
    var essential by remember { mutableStateOf("") }
    val next = remember(revision) { session.nextPage }
    BackHandler { onBack() }
    DisposableEffect(session) {
        onDispose {
            active = false
            webView?.apply { stopLoading(); loadUrl("about:blank"); clearHistory(); removeAllViews(); destroy() }
            session.clear()
        }
    }
    LaunchedEffect(generation, opened) {
        if (opened) {
            delay(45_000)
            if (!ready && !failed) {
                failed = true
                webView?.stopLoading()
                message = "Page did not finish loading. Use a permitted saved HTML/MHT file or Metron."
            }
        }
    }
    fun imported() {
        revision++
        complete = session.declaredTotal?.toString() ?: complete
        essential = session.essentialCount.toString()
        message = "${session.importedCount} entries extracted. " +
            (session.nextPage?.let { "Open and import page $it next." }
                ?: "Check the Complete and Essential counts against the source, then save.")
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !busy) {
            val page = session.nextPage
            if (page != null) {
                busy = true
                scope.launch {
                    try {
                        val html = withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.use {
                                val output = java.io.ByteArrayOutputStream()
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val count = it.read(buffer)
                                    if (count < 0) break
                                    require(output.size() + count <= InkShelf2EventImportSession.MAX_HTML) { "Saved file exceeds 2 MB." }
                                    output.write(buffer, 0, count)
                                }
                                InkShelf2SavedEventPage.decode(output.toByteArray())
                            } ?: error("Could not open selected file.")
                        }
                        session.add(html, session.url(page))
                        imported()
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (error: Exception) { message = error.message ?: "Saved page could not be imported." }
                    finally { busy = false }
                }
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
        TextButton(onClick = onBack) { Text("Back to guides") }
        Text("Import ${guide.name}", style = MaterialTheme.typography.titleLarge)
        Text(message, style = MaterialTheme.typography.bodySmall)
        if (next != null) {
            Text(session.url(next), style = MaterialTheme.typography.labelSmall)
            Row {
                TextButton(enabled = !busy, onClick = {
                    opened = true; ready = false; failed = false
                    message = "Complete any verification normally. Tap Extract only once the reading list is visible and you have permission to import it."
                    webView?.loadUrl(session.url(next))
                }) { Text(if (opened) "Open page $next" else "Open permitted website") }
                TextButton(enabled = !busy, onClick = {
                    picker.launch(arrayOf("text/html", "application/xhtml+xml", "multipart/related", "message/rfc822", "application/octet-stream"))
                }) { Text("Select HTML/MHT") }
            }
        }
        TextButton(onClick = onProvider, enabled = !busy) { Text("Use authorized Metron metadata") }
        if (opened) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { ctx -> WebView(ctx).apply {
                    webView = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    settings.setSupportMultipleWindows(false)
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    @Suppress("DEPRECATION")
                    settings.saveFormData = false
                    setDownloadListener { _, _, _, _, _ ->
                        message = "Downloads are disabled. Only event reading-list metadata can be imported."
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            if (!request.isForMainFrame) return false
                            val blocked = InkShelf2EventImportSession.pageNumber(request.url.toString(), slug) == null
                            if (blocked) {
                                ready = false; failed = true
                                message = "This destination is outside the event reading list. Use saved HTML/MHT or Metron if access is unavailable."
                            }
                            return blocked
                        }
                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            ready = false; failed = false; generation++
                        }
                        override fun onPageFinished(view: WebView, url: String?) {
                            ready = !failed && InkShelf2EventImportSession.pageNumber(url.orEmpty(), slug) == session.nextPage
                        }
                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                            if (request.isForMainFrame) {
                                ready = false; failed = true
                                message = "Website returned HTTP ${response.statusCode}. No metadata extracted. Complete any offered verification normally, or use saved HTML/MHT or Metron."
                            }
                        }
                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) {
                                ready = false; failed = true
                                message = "Website could not be loaded. Use a permitted saved HTML/MHT file or Metron."
                            }
                        }
                        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                            handler.cancel(); ready = false; failed = true
                            message = "Secure connection failed. Import is unavailable; use saved HTML/MHT or Metron."
                        }
                    }
                    loadUrl(session.url(session.nextPage ?: 1))
                } },
            )
            TextButton(enabled = ready && !busy && next != null, onClick = {
                val view = webView ?: return@TextButton
                val url = view.url ?: return@TextButton
                val ticket = generation
                busy = true
                // Explicit user action; no bridge, forms, cookies, tokens or scripts are collected.
                view.evaluateJavascript("""(function(){if(document.readyState!=='complete')return null;var d=document.documentElement.cloneNode(true);d.querySelectorAll('script,style,iframe,form,input,textarea,select').forEach(function(n){n.remove()});var h=d.outerHTML;return h.length<=2000000?h:null})()""") { result ->
                    if (active) {
                        try {
                            require(ticket == generation && ready && view.url == url) { "Page changed during extraction. Please retry." }
                            val raw = JSONTokener(result).nextValue() as? String ?: error("Page is not ready or exceeds the import limit.")
                            session.add(raw, url)
                            ready = false
                            imported()
                        } catch (error: Exception) { message = error.message ?: "Could not extract this page." }
                        finally { busy = false }
                    }
                }
            }) { Text("Extract visible page — I have permission") }
        } else Spacer(Modifier.weight(1f))
        if (next == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(complete, { complete = it.filter(Char::isDigit).take(5) },
                    label = { Text("Complete count") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(essential, { essential = it.filter(Char::isDigit).take(5) },
                    label = { Text("Essential count") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            InkShelf2EventMetadataStore.save(context, session,
                                complete.toIntOrNull() ?: 0, essential.toIntOrNull() ?: 0)
                        }
                        onSaved()
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (error: Exception) { message = error.message ?: "Could not save metadata." }
                    finally { busy = false }
                }
            }) { Text("Confirm counts and save native guide") }
        }
    }
}
