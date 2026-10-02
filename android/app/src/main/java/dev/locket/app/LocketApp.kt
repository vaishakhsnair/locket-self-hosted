package dev.locket.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun LocketApp(vm: LocketViewModel = viewModel()) {
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val photos by vm.photos.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<PhotoItem?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        if (uri != null && selected != null) vm.export(context, selected!!, uri)
    }

    LocketTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (destination != 1) LocketNavigation(destination) { destination = it }
            },
        ) { padding ->
            when (destination) {
                0 -> MomentsScreen(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    photos = photos,
                    localDeviceId = vm.localDeviceId,
                    onOpen = { selected = it },
                    onRefresh = vm::refreshFromServer,
                )
                1 -> CameraScreen(
                    recent = photos.firstOrNull(),
                    vm = vm,
                    onClose = { destination = 0 },
                )
                else -> SettingsScreen(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    photos = photos,
                    localDeviceId = vm.localDeviceId,
                    vm = vm,
                )
            }
        }
        selected?.let { photo ->
            PhotoViewer(photo = photo, onClose = { selected = null }, onSave = { saveLauncher.launch(photo.filename) })
        }
    }
}

@Composable
private fun LocketNavigation(selected: Int, onSelect: (Int) -> Unit) {
    NavigationBar(windowInsets = WindowInsets.navigationBars) {
        NavigationBarItem(
            selected = selected == 0,
            onClick = { onSelect(0) },
            icon = { Icon(Icons.Outlined.Image, contentDescription = "Moments") },
            label = { Text("Moments") },
        )
        Box(Modifier.weight(1f).padding(top = 2.dp), contentAlignment = Alignment.TopCenter) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FloatingActionButton(
                    onClick = { onSelect(1) },
                    modifier = Modifier.size(58.dp),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) { Icon(Icons.Outlined.CameraAlt, contentDescription = "Camera") }
                Text("Camera", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 2.dp))
            }
        }
        NavigationBarItem(
            selected = selected == 2,
            onClick = { onSelect(2) },
            icon = { Icon(Icons.Outlined.Settings, contentDescription = "Settings") },
            label = { Text("Settings") },
        )
    }
}

@Composable
private fun MomentsScreen(
    modifier: Modifier,
    photos: List<PhotoItem>,
    localDeviceId: String,
    onOpen: (PhotoItem) -> Unit,
    onRefresh: () -> Unit,
) {
    val partner = partnerName(photos, localDeviceId)
    val grouped = photos.groupBy { it.dayLabel }
    LazyColumn(
        modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = 22.dp, top = 16.dp, end = 22.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Locket", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
                    Text("Moments with you", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                }
                IconButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, contentDescription = "Refresh moments", tint = MaterialTheme.colorScheme.primary) }
            }
        }
        item { ConnectedPersonCard(partner) }
        if (photos.isEmpty()) {
            item { EmptyMoments() }
        } else {
            grouped.forEach { (day, dayPhotos) ->
                item { Text(day, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp)) }
                items(dayPhotos, key = { it.id }) { photo ->
                    MomentCard(photo = photo, localDeviceId = localDeviceId, onOpen = { onOpen(photo) })
                }
            }
        }
    }
}

@Composable
private fun ConnectedPersonCard(name: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(name)
            Column(Modifier.weight(1f).padding(start = 13.dp)) {
                Text(name, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                Text("Connected", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 3.dp))
            }
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun Avatar(name: String) {
    val initials = name.trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "L" }
    Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
        Text(initials, color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MomentCard(photo: PhotoItem, localDeviceId: String, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(photo.uri).crossfade(true).build(),
            contentDescription = photo.filename,
            modifier = Modifier.fillMaxWidth().aspectRatio(1.38f).clip(RoundedCornerShape(24.dp)),
            contentScale = ContentScale.Crop,
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(relativeMoment(photo.capturedAt), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text(
                if (photo.senderDeviceId == localDeviceId) "Sent by ${photo.senderName.ifBlank { "You" }}" else "Sent by ${photo.senderName.ifBlank { "Partner" }}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun EmptyMoments() {
    Column(Modifier.fillMaxWidth().padding(vertical = 74.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Your first moment is waiting", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
        Text("Open the camera when you want to send something small.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun CameraScreen(
    recent: PhotoItem?,
    vm: LocketViewModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var captured by remember { mutableStateOf<Pair<File, Long>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var pendingAt by remember { mutableStateOf(0L) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingFile
        if (success && file != null && file.exists()) {
            captured = file to pendingAt
            error = null
        } else {
            file?.delete()
            error = "Camera capture was cancelled"
            onClose()
        }
        pendingFile = null
    }

    fun launchSystemCamera() {
        val now = System.currentTimeMillis()
        val file = File(context.filesDir, "photos/$now.jpg").apply { parentFile?.mkdirs() }
        pendingFile = file
        pendingAt = now
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            takePicture.launch(uri)
        }.onFailure {
            file.delete()
            pendingFile = null
            error = "No camera app is available"
        }
    }

    LaunchedEffect(Unit) { launchSystemCamera() }
    BackHandler {
        captured?.first?.let(vm::discardCapture)
        onClose()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (captured == null) {
            Column(Modifier.fillMaxSize().padding(horizontal = 28.dp).safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Start) {
                    IconButton(onClick = onClose, modifier = Modifier.background(Color.White.copy(alpha = .12f), CircleShape)) { Icon(Icons.Outlined.Close, contentDescription = "Close camera", tint = Color.White) }
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                Text("Use your camera", color = Color.White, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 18.dp))
                Text("Locket will open the phone’s camera, then let you review the photo before sending.", color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    if (recent != null) AsyncImage(model = recent.uri, contentDescription = "Recent moment", modifier = Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop) else Box(Modifier.size(54.dp).border(1.dp, Color.White.copy(alpha = .45f), RoundedCornerShape(16.dp)))
                    Box(Modifier.size(88.dp).border(4.dp, Color.White, CircleShape).clickable(onClick = ::launchSystemCamera).padding(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                    Spacer(Modifier.size(54.dp))
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp)) }
            }
        } else {
            val (file, capturedAt) = captured!!
            AsyncImage(Uri.fromFile(file), contentDescription = "Photo preview", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).safeDrawingPadding().padding(12.dp), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onClose, modifier = Modifier.background(Color.Black.copy(alpha = .45f), CircleShape)) { Icon(Icons.Outlined.Close, contentDescription = "Close preview", tint = Color.White) }
            }
            Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).safeDrawingPadding().background(Color.Black.copy(alpha = .82f)).padding(20.dp)) {
                Text("Keep this moment?", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text("It stays private until you send it.", color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { vm.discardCapture(file); captured = null; launchSystemCamera() }, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Color.White.copy(alpha = .7f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Icon(Icons.Outlined.Close, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Retake") }
                    Button(onClick = { vm.confirmCapture(file, capturedAt); captured = null; onClose() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) { Icon(Icons.Outlined.Check, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Send") }
                }
            }
        }
    }
}

@Composable
private fun CameraTopBar(onClose: () -> Unit, flashEnabled: Boolean, hasFlash: Boolean, onFlash: () -> Unit, modifier: Modifier) {
    Row(modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose, modifier = Modifier.background(Color.Black.copy(alpha = .42f), CircleShape)) { Icon(Icons.Outlined.Close, contentDescription = "Close camera", tint = Color.White) }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onFlash, enabled = hasFlash, modifier = Modifier.background(Color.Black.copy(alpha = .42f), CircleShape)) { Icon(if (flashEnabled) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff, contentDescription = "Flash", tint = if (hasFlash) Color.White else Color.White.copy(alpha = .35f)) }
    }
}

@Composable
private fun CameraBottomControls(
    recent: PhotoItem?,
    zoom: Float,
    onZoom: (Float) -> Unit,
    onSwitch: () -> Unit,
    onCapture: () -> Unit,
    error: String?,
    modifier: Modifier,
) {
    Column(modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(color = Color.Black.copy(alpha = .42f), shape = RoundedCornerShape(20.dp)) {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("1×", color = Color.White, style = MaterialTheme.typography.labelMedium)
                Slider(value = zoom, onValueChange = onZoom, valueRange = 0f..1f, modifier = Modifier.width(124.dp), colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = Color.White.copy(alpha = .38f)))
                Text("4×", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            if (recent != null) {
                AsyncImage(model = recent.uri, contentDescription = "Recent moment", modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.size(48.dp).border(1.dp, Color.White.copy(alpha = .45f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Image, contentDescription = null, tint = Color.White.copy(alpha = .7f)) }
            }
            Box(Modifier.size(82.dp).border(4.dp, Color.White, CircleShape).clickable(onClick = onCapture).padding(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            IconButton(onClick = onSwitch, modifier = Modifier.background(Color.Black.copy(alpha = .42f), CircleShape)) { Icon(Icons.Outlined.Cameraswitch, contentDescription = "Switch camera", tint = Color.White) }
        }
    }
}

@Composable
private fun SettingsScreen(modifier: Modifier, photos: List<PhotoItem>, localDeviceId: String, vm: LocketViewModel) {
    val context = LocalContext.current
    val partner = partnerName(photos, localDeviceId)
    var showConnection by rememberSaveable { mutableStateOf(false) }
    var url by remember { mutableStateOf(vm.serverUrl()) }
    var token by remember { mutableStateOf(vm.accessToken()) }
    var mediaKey by remember { mutableStateOf(vm.mediaKey()) }
    var displayName by remember { mutableStateOf(vm.displayName()) }
    var revealEnabled by remember { mutableStateOf(vm.doubleTapRevealEnabled()) }
    LazyColumn(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars), contentPadding = PaddingValues(start = 22.dp, top = 16.dp, end = 22.dp, bottom = 112.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground) }
        item { SettingsPersonCard(partner) }
        item { Text("App", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 18.dp, bottom = 3.dp)) }
        item { SettingRow(Icons.Outlined.NotificationsNone, "Notifications", "New moments, silently") }
        item { SettingRow(Icons.Outlined.Settings, "Appearance", "Follow system theme") }
        item { Text("Widget", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 18.dp, bottom = 3.dp)) }
        item {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Widgets, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) { Text("Stealth widget", style = MaterialTheme.typography.titleMedium); Text("Clock cover with received-moment reveal", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 3.dp)) }
                    Switch(checked = revealEnabled, onCheckedChange = { revealEnabled = it; vm.setDoubleTapRevealEnabled(it) })
                }
            }
        }
        item { Text("Connection", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 18.dp, bottom = 3.dp)) }
        item { SettingRow(Icons.Outlined.Link, "Server and encryption", if (url.isBlank()) "Not connected" else "Connected", onClick = { showConnection = !showConnection }) }
        if (showConnection) {
            item { ConnectionEditor(url, token, mediaKey, displayName, { url = it }, { token = it }, { mediaKey = it }, { displayName = it }) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { vm.saveServerUrl(url); vm.saveAccessToken(token); vm.saveMediaKey(mediaKey); vm.saveDisplayName(displayName) }, modifier = Modifier.weight(1f)) { Text("Save") }
                    OutlinedButton(onClick = {
                        vm.connectionInvite(url, token, mediaKey, displayName)?.let { invite ->
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, invite) }, "Share Locket setup"))
                        }
                    }, enabled = url.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Share link") }
                }
            }
        }
        item { Text("About", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 18.dp, bottom = 3.dp)) }
        item { SettingRow(Icons.Outlined.Lock, "Locket", "Private photo moments") }
    }
}

@Composable
private fun SettingsPersonCard(name: String) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(name)
            Column(Modifier.weight(1f).padding(start = 13.dp)) { Text(name, style = MaterialTheme.typography.titleMedium); Text("Connected", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 3.dp)) }
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(enabled = onClick != null) { onClick?.invoke() }.padding(vertical = 12.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 10.dp))
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp)) }
        if (onClick != null) Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(end = 8.dp))
    }
}

@Composable
private fun ConnectionEditor(url: String, token: String, mediaKey: String, name: String, onUrl: (String) -> Unit, onToken: (String) -> Unit, onKey: (String) -> Unit, onName: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(value = url, onValueChange = onUrl, singleLine = true, label = { Text("Server URL") }, placeholder = { Text("https://photos.example.com") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = token, onValueChange = onToken, singleLine = true, visualTransformation = PasswordVisualTransformation(), label = { Text("Access token") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = mediaKey, onValueChange = onKey, singleLine = true, visualTransformation = PasswordVisualTransformation(), label = { Text("Shared media key") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = name, onValueChange = onName, singleLine = true, label = { Text("Your name") }, placeholder = { Text("Shown to your partner") }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun PhotoViewer(photo: PhotoItem, onClose: () -> Unit, onSave: () -> Unit) {
    Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back") }
                Text("Moment", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                Button(onClick = onSave, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)) { Text("Save") }
            }
            AsyncImage(model = photo.uri, contentDescription = photo.filename, modifier = Modifier.fillMaxWidth().weight(1f), contentScale = ContentScale.Fit)
            Column(Modifier.fillMaxWidth().padding(start = 22.dp, top = 14.dp, end = 22.dp, bottom = 28.dp)) {
                Text(relativeMoment(photo.capturedAt), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                val sender = photo.senderName.ifBlank { if (photo.senderDeviceId == AppContextHolder.context?.let { DeviceIdentity.id(it) }) "You" else "Partner" }
                Text("Sent by $sender", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 5.dp))
                Text("${photo.dayLabel} at ${photo.timeLabel}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

private fun partnerName(photos: List<PhotoItem>, localDeviceId: String): String = photos.firstOrNull { it.senderDeviceId != localDeviceId && it.senderName.isNotBlank() }?.senderName ?: "Your person"

private fun relativeMoment(timestamp: Long): String {
    val elapsed = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed)
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 24 * 60 -> "${minutes / 60}h ago"
        minutes < 48 * 60 -> "Yesterday"
        else -> "${minutes / (24 * 60)}d ago"
    }
}
