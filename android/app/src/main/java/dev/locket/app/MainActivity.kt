package dev.locket.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.Camera
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File

private val Ink = Color(0xFF111311)
private val Panel = Color(0xFF1A201C)
private val Soft = Color(0xFF29322D)
private val Mint = Color(0xFF9BD7C6)
private val TextMain = Color(0xFFE8EEE9)
private val TextMuted = Color(0xFF9BAAA1)

class MainActivity : ComponentActivity() {
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_ON || intent.action == Intent.ACTION_USER_PRESENT) {
                context.getSharedPreferences("widget_state", Context.MODE_PRIVATE).edit().putLong("revealed_until", 0L).apply()
                kotlinx.coroutines.MainScope().launch { StealthWidget().updateAll(context) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppContextHolder.context = applicationContext
        applyConnectionInvite(intent)
        PhotoSyncScheduler.schedule(applicationContext)
        if (ServerClient(applicationContext).baseUrl.isNotBlank()) DeliveryService.start(applicationContext)
        setContent { LocketApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (applyConnectionInvite(intent)) recreate()
    }

    private fun applyConnectionInvite(intent: Intent): Boolean {
        val data = intent.data ?: return false
        if (intent.action != Intent.ACTION_VIEW || data.scheme != "locket" || data.host != "connect") return false
        val serverUrl = data.getQueryParameter("server")?.trim()?.trimEnd('/') ?: return false
        if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) return false
        getSharedPreferences("connection", MODE_PRIVATE).edit()
            .putString("server_url", serverUrl)
            .putString("access_token", data.getQueryParameter("token").orEmpty().trim())
            .putString("media_key", data.getQueryParameter("key").orEmpty().trim())
            .putString("display_name", data.getQueryParameter("name").orEmpty().trim())
            .apply()
        DeliveryService.start(applicationContext)
        Toast.makeText(this, "Locket connection imported", Toast.LENGTH_SHORT).show()
        return true
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        unregisterReceiver(screenReceiver)
        super.onStop()
    }
}

@Composable
private fun LocketApp(vm: LocketViewModel = viewModel()) {
    val context = LocalContext.current
    val photos by vm.photos.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<PhotoItem?>(null) }
    var cameraPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val requestCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraPermission = it }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { destination ->
        if (destination != null && selected != null) vm.export(context, selected!!, destination)
    }

    MaterialTheme(colorScheme = LocketThemeColors, typography = LocketTypography) {
        LaunchedEffect(tab) {
            if (tab == 1) vm.refreshFromServer()
        }
        Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
            Scaffold(containerColor = Ink, bottomBar = {
                NavigationBar(containerColor = Panel, contentColor = TextMuted) {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Outlined.CameraAlt, contentDescription = null) }, label = { Text("Send") })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Outlined.GridView, contentDescription = null) }, label = { Text("Archive") })
                    NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Outlined.Widgets, contentDescription = null) }, label = { Text("Widget") })
                }
            }) { padding ->
                when (tab) {
                    0 -> CameraScreen(Modifier.padding(padding), cameraPermission, { requestCamera.launch(Manifest.permission.CAMERA) }, vm)
                    1 -> ArchiveScreen(Modifier.padding(padding), photos, vm.localDeviceId, { selected = it }, vm::refreshFromServer)
                    else -> WidgetScreen(Modifier.padding(padding), vm)
                }
            }
        }
    }

    selected?.let { photo ->
        PhotoViewer(photo, onClose = { selected = null }, onSave = { saveLauncher.launch(photo.filename) })
    }
}

@Composable
private fun CameraScreen(modifier: Modifier, permission: Boolean, request: () -> Unit, vm: LocketViewModel) {
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var zoom by remember { mutableFloatStateOf(0f) }
    var captured by remember { mutableStateOf<Pair<File, Long>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(permission, lensFacing, provider, previewView) {
        val currentProvider = provider ?: return@LaunchedEffect
        val currentPreview = previewView ?: return@LaunchedEffect
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val preview = Preview.Builder().build().also { it.surfaceProvider = currentPreview.surfaceProvider }
        val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
        currentProvider.unbindAll()
        camera = currentProvider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
        imageCapture = capture
        zoom = 0f
    }
    Box(modifier.fillMaxSize().background(Color.Black)) {
        if (permission && captured == null) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                val view = PreviewView(context)
                previewView = view
                val providerFuture = ProcessCameraProvider.getInstance(context)
                providerFuture.addListener({
                    provider = providerFuture.get()
                }, ContextCompat.getMainExecutor(context))
                view
            })
            Column(Modifier.fillMaxWidth().align(Alignment.TopCenter).safeDrawingPadding().padding(horizontal = 18.dp, vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Color.Black.copy(alpha = .42f), shape = RoundedCornerShape(24.dp)) { Text("Send a small moment", color = TextMain, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge) }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK }, modifier = Modifier.background(Color.Black.copy(alpha = .42f), CircleShape)) { Icon(Icons.Outlined.Cameraswitch, contentDescription = "Switch camera", tint = TextMain) }
                }
                Surface(color = Color.Black.copy(alpha = .42f), shape = RoundedCornerShape(18.dp), modifier = Modifier.padding(top = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                        Text("1×", color = TextMain, style = MaterialTheme.typography.labelMedium)
                        Slider(value = zoom, onValueChange = { zoom = it; camera?.cameraControl?.setLinearZoom(it) }, modifier = Modifier.width(160.dp), colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Mint, activeTrackColor = Mint))
                        Text("4×", color = TextMain, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                error?.let { Text(it, color = Color(0xFFFFB4AB), modifier = Modifier.padding(bottom = 10.dp)) }
                FloatingActionButton(onClick = { imageCapture?.let { vm.capture(it, { file, at -> captured = file to at; error = null }, { error = it }) } }, modifier = Modifier.size(78.dp), containerColor = Mint, contentColor = Ink) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = "Capture photo", modifier = Modifier.size(32.dp))
                }
            }
        } else if (captured != null) {
            val (file, capturedAt) = captured!!
            AsyncImage(model = Uri.fromFile(file), contentDescription = "Photo preview", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).safeDrawingPadding().background(Color.Black.copy(alpha = .78f)).padding(20.dp)) {
                Text("Use this photo?", color = TextMain, style = MaterialTheme.typography.titleLarge)
                Text("Review it before it is sent.", color = TextMuted, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.compose.material3.OutlinedButton(onClick = { vm.discardCapture(file); captured = null }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Close, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Retake") }
                    Button(onClick = { vm.confirmCapture(file, capturedAt); captured = null }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink)) { Icon(Icons.Outlined.Check, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Send") }
                }
            }
        } else {
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Camera access is needed to send a photo.", color = TextMain, fontSize = 18.sp)
                Spacer(Modifier.height(18.dp))
                Button(onClick = request, colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink)) { Text("Allow camera") }
            }
        }
    }
}

@Composable
private fun ArchiveScreen(modifier: Modifier, photos: List<PhotoItem>, localDeviceId: String, open: (PhotoItem) -> Unit, refresh: () -> Unit) {
    val grouped = photos.groupBy { it.dayLabel }
    LazyColumn(modifier.fillMaxSize().safeDrawingPadding(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Archive", color = TextMain, style = MaterialTheme.typography.headlineLarge); Text("Sent and received, in order.", color = TextMuted, modifier = Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodyMedium) }; IconButton(onClick = refresh) { Icon(Icons.Outlined.Refresh, contentDescription = "Refresh archive", tint = Mint) } } }
        if (photos.isEmpty()) item { EmptyArchive() }
        grouped.forEach { (day, items) ->
            item { Text(day, color = Mint, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
            items(items, key = { it.id }) { photo ->
                Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).clickable { open(photo) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(photo.uri).crossfade(true).build(), contentDescription = null, modifier = Modifier.size(74.dp).aspectRatio(1f), contentScale = ContentScale.Crop)
                    Column(Modifier.padding(start = 14.dp).weight(1f)) { Text(photo.filename, color = TextMain, style = MaterialTheme.typography.titleMedium); Text(photo.timeLabel, color = TextMuted, modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium); Text(if (photo.senderDeviceId == localDeviceId) "Sent by ${photo.senderName.ifBlank { "You" }} · ${statusLabel(photo.status)}" else "Sent by ${photo.senderName.ifBlank { "Partner" }}", color = if (photo.senderDeviceId == localDeviceId) TextMuted else Mint, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp)) }
                    Text("›", color = Mint, style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
    }
}

@Composable private fun EmptyArchive() { Column(Modifier.fillMaxWidth().padding(vertical = 70.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("Nothing here yet", color = TextMain, style = MaterialTheme.typography.titleMedium); Text("Your sent and received photos will land here.", color = TextMuted, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium) } }

@Composable
private fun PhotoViewer(photo: PhotoItem, onClose: () -> Unit, onSave: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = Ink) { Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Text("Archive", color = TextMuted, modifier = Modifier.weight(1f).clickable { onClose() }, style = MaterialTheme.typography.labelLarge); Button(onClick = onSave, colors = ButtonDefaults.buttonColors(containerColor = Soft, contentColor = TextMain)) { Text("Save to Files") } }
        AsyncImage(model = photo.uri, contentDescription = photo.filename, modifier = Modifier.fillMaxWidth().weight(1f), contentScale = ContentScale.Fit)
        Column(Modifier.fillMaxWidth().padding(start = 22.dp, top = 14.dp, end = 22.dp, bottom = 30.dp)) { Text(photo.filename, color = TextMain, style = MaterialTheme.typography.titleMedium); Text("${photo.dayLabel} · ${photo.timeLabel}", color = TextMuted, modifier = Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodyMedium); Text(if (photo.senderName.isBlank()) "Sent by ${if (photo.senderDeviceId == AppContextHolder.context?.let { DeviceIdentity.id(it) }) "You" else "Partner"}" else "Sent by ${photo.senderName}", color = TextMuted, modifier = Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall) }
    } }
}

private fun statusLabel(status: PhotoStatus): String = when (status) { PhotoStatus.PENDING -> "Waiting to send"; PhotoStatus.UPLOADING -> "Sending…"; PhotoStatus.SENT -> "Sent to server"; PhotoStatus.STORED -> "Stored on this device" }

@Composable
private fun WidgetScreen(modifier: Modifier, vm: LocketViewModel) {
    val context = LocalContext.current
    var url by remember { mutableStateOf(vm.serverUrl()) }
    var token by remember { mutableStateOf(vm.accessToken()) }
    var mediaKey by remember { mutableStateOf(vm.mediaKey()) }
    var displayName by remember { mutableStateOf(vm.displayName()) }
    var doubleTapReveal by remember { mutableStateOf(vm.doubleTapRevealEnabled()) }
    Column(
        modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 22.dp, top = 22.dp, end = 22.dp, bottom = 104.dp)
    ) {
        Text("Widget", color = TextMain, style = MaterialTheme.typography.headlineLarge)
        Text("A quiet cover for your home screen.", color = TextMuted, modifier = Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(28.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(1.15f).background(Color(0xFF171B18), RoundedCornerShape(24.dp)).padding(24.dp)) { Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), color = TextMain, style = MaterialTheme.typography.displaySmall, modifier = Modifier.align(Alignment.BottomStart)) }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Double-tap reveal", color = TextMain, style = MaterialTheme.typography.titleMedium); Text("Temporarily show the latest received photo.", color = TextMuted, style = MaterialTheme.typography.bodySmall) }; Switch(checked = doubleTapReveal, onCheckedChange = { doubleTapReveal = it; vm.setDoubleTapRevealEnabled(it) }) }
        Spacer(Modifier.height(22.dp))
        Text("Server connection", color = TextMain, style = MaterialTheme.typography.titleMedium)
        androidx.compose.material3.OutlinedTextField(value = url, onValueChange = { url = it }, singleLine = true, label = { Text("Self-hosted server URL") }, placeholder = { Text("https://photos.example.com") }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
        androidx.compose.material3.OutlinedTextField(value = token, onValueChange = { token = it }, singleLine = true, visualTransformation = PasswordVisualTransformation(), label = { Text("Server access token (optional)") }, placeholder = { Text("Set LOCKET_ACCESS_TOKEN on the server") }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
        androidx.compose.material3.OutlinedTextField(value = mediaKey, onValueChange = { mediaKey = it }, singleLine = true, visualTransformation = PasswordVisualTransformation(), label = { Text("Shared photo key (optional)") }, placeholder = { Text("Use the same key on both phones") }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
        androidx.compose.material3.OutlinedTextField(value = displayName, onValueChange = { displayName = it }, singleLine = true, label = { Text("Your name") }, placeholder = { Text("Shown to your partner") }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
        Button(onClick = { vm.saveServerUrl(url); vm.saveAccessToken(token); vm.saveMediaKey(mediaKey); vm.saveDisplayName(displayName) }, modifier = Modifier.padding(top = 12.dp), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink)) { Text("Save connection") }
        androidx.compose.material3.OutlinedButton(onClick = {
            vm.connectionInvite(url, token, mediaKey, displayName)?.let { invite ->
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, invite)
                }, "Share Locket setup"))
            }
        }, enabled = url.trim().isNotBlank(), modifier = Modifier.padding(top = 10.dp)) { Text("Share setup link") }
        Text("The setup link contains the server token and shared photo key. Share it only with the intended recipient.", color = TextMuted, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
        Text("When a shared key is set, photos are encrypted on-device before upload and decrypted only on devices with the same key.", color = TextMuted, lineHeight = 20.sp, modifier = Modifier.padding(top = 14.dp), style = MaterialTheme.typography.bodySmall)
        Text("Add Locket from your launcher’s widget picker. The widget stays visually quiet until you choose to reveal a received photo.", color = TextMuted, lineHeight = 21.sp, modifier = Modifier.padding(top = 22.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
