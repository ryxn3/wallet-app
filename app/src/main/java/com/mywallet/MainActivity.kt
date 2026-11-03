package com.mywallet

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.util.UUID

private val PALETTE = listOf(
    0xFF1A73E8, 0xFF6C3CE0, 0xFFE8365D, 0xFFFF7043, 0xFFFFC107, 0xFF2E9E5B,
    0xFF00ACC1, 0xFF263238, 0xFF000000, 0xFFFFFFFF, 0xFFF48FB1, 0xFF8D6E63,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { App() } } }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val cards = remember { mutableStateListOf<WalletCard>().apply { addAll(Store.load(ctx)) } }
    var openId by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<WalletCard?>(null) }
    var isNew by remember { mutableStateOf(false) }

    fun persist() = Store.save(ctx, cards)

    when {
        editing != null -> {
            BackHandler { editing = null }
            Editor(editing!!, onCancel = { editing = null }, onSave = { c ->
                val i = cards.indexOfFirst { it.id == c.id }
                if (i >= 0) cards[i] = c else cards.add(0, c)
                persist(); editing = null; if (isNew) openId = null
            })
        }
        openId != null && cards.any { it.id == openId } -> {
            BackHandler { openId = null }
            val card = cards.first { it.id == openId }
            Detail(card, onBack = { openId = null },
                onEdit = { isNew = false; editing = card },
                onDelete = { cards.remove(card); persist(); openId = null })
        }
        else -> CardList(cards, onOpen = { openId = it.id },
            onAdd = { isNew = true; editing = WalletCard() })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardList(cards: List<WalletCard>, onOpen: (WalletCard) -> Unit, onAdd: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("My Wallet", fontWeight = FontWeight.Bold) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) { Icon(Icons.Default.Add, "Add card") }
        },
    ) { pad ->
        if (cards.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("No cards yet. Tap + to add one.", color = Color.Gray)
            }
        } else LazyColumn(
            Modifier.padding(pad), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) { items(cards, key = { it.id }) { CardView(it, Modifier.clickable { onOpen(it) }) } }
    }
}

@Composable
fun CardView(card: WalletCard, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val textColor = if (card.darkText) Color.Black else Color.White
    val bg = remember(card.image) {
        card.image?.let {
            runCatching { BitmapFactory.decodeFile(Store.imageFile(ctx, it).path)?.asImageBitmap() }.getOrNull()
        }
    }
    Box(
        modifier.fillMaxWidth().aspectRatio(1.586f).clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Color(card.colorA), Color(card.colorB))))
    ) {
        if (bg != null) {
            Image(bg, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.25f)))
        }
        Column(Modifier.padding(20.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Text(card.name.ifBlank { "Card name" }, color = if (bg != null) Color.White else textColor,
                fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(card.number, color = if (bg != null) Color.White else textColor.copy(alpha = 0.85f), fontSize = 16.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Detail(card: WalletCard, onBack: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    // Max brightness while the barcode is shown so scanners read it.
    DisposableEffect(Unit) {
        val w = (ctx as ComponentActivity).window
        val old = w.attributes.screenBrightness
        w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
        onDispose { w.attributes = w.attributes.apply { screenBrightness = old } }
    }
    val barcode = remember(card.number, card.format) { makeBarcode(card.number, card.format) }
    Scaffold(topBar = {
        TopAppBar(
            title = {},
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                IconButton(onEdit) { Icon(Icons.Default.Edit, "Edit") }
                IconButton({ confirm = true }) { Icon(Icons.Default.Delete, "Delete") }
            },
        )
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CardView(card)
            if (barcode != null) Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).padding(16.dp),
                contentAlignment = Alignment.Center,
            ) { Image(barcode.asImageBitmap(), "Barcode", Modifier.fillMaxWidth(if (card.format == "QR_CODE" || card.format == "AZTEC") 0.7f else 1f)) }
            if (card.notes.isNotBlank()) Text(card.notes)
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Delete card?") },
        text = { Text("This removes \"${card.name}\" from this phone.") },
        confirmButton = { TextButton({ confirm = false; onDelete() }) { Text("Delete") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Editor(initial: WalletCard, onCancel: () -> Unit, onSave: (WalletCard) -> Unit) {
    val ctx = LocalContext.current
    var c by remember { mutableStateOf(initial) }
    val valid = c.name.isNotBlank() && (c.format == "NONE" || makeBarcode(c.number, c.format, 100) != null)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) c = c.copy(image = copyImage(ctx, uri))
    }
    fun scan() {
        GmsBarcodeScanning.getClient(ctx).startScan().addOnSuccessListener { b ->
            b.rawValue?.let { v ->
                val fmt = when (b.format) {
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE -> "QR_CODE"
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_13 -> "EAN_13"
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_PDF417 -> "PDF_417"
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_AZTEC -> "AZTEC"
                    else -> "CODE_128"
                }
                c = c.copy(number = v, format = fmt)
            }
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (initial.name.isBlank()) "New card" else "Edit card") },
            navigationIcon = { IconButton(onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel") } },
            actions = { TextButton({ onSave(c) }, enabled = valid) { Text("Save") } },
        )
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardView(c)
            OutlinedTextField(c.name, { c = c.copy(name = it) }, Modifier.fillMaxWidth(), label = { Text("Card name") }, singleLine = true)
            OutlinedTextField(c.number, { c = c.copy(number = it) }, Modifier.fillMaxWidth(), label = { Text("Barcode / card number") }, singleLine = true,
                isError = c.format != "NONE" && c.number.isNotBlank() && !valid,
                supportingText = { if (c.format == "EAN_13") Text("EAN-13 needs 12 or 13 digits") })
            OutlinedButton(::scan, Modifier.fillMaxWidth()) { Text("Scan barcode with camera") }

            Text("Barcode type", fontWeight = FontWeight.Medium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FORMATS.forEach { f ->
                    FilterChip(c.format == f, { c = c.copy(format = f) }, { Text(f.replace('_', ' ')) })
                }
            }

            Text("Color 1", fontWeight = FontWeight.Medium)
            Swatches(c.colorA) { c = c.copy(colorA = it) }
            Text("Color 2", fontWeight = FontWeight.Medium)
            Swatches(c.colorB) { c = c.copy(colorB = it) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(c.darkText, { c = c.copy(darkText = it) })
                Spacer(Modifier.width(8.dp)); Text("Dark text")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Background picture") }
                if (c.image != null) TextButton({ c = c.copy(image = null) }) { Text("Remove picture") }
            }
            OutlinedTextField(c.notes, { c = c.copy(notes = it) }, Modifier.fillMaxWidth(), label = { Text("Notes") }, minLines = 2)
        }
    }
}

@Composable
private fun Swatches(selected: Long, onPick: (Long) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PALETTE.forEach {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Color(it))
                    .border(if (it == selected) 3.dp else 1.dp, if (it == selected) Color(0xFF1A73E8) else Color.LightGray, CircleShape)
                    .clickable { onPick(it) }
            )
        }
    }
}

private fun copyImage(ctx: Context, uri: Uri): String? = runCatching {
    val name = UUID.randomUUID().toString() + ".jpg"
    // Downscale so cards stay light.
    val src = ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
    val scale = 1200f / maxOf(src.width, src.height).coerceAtLeast(1)
    val bmp = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true) else src
    Store.imageFile(ctx, name).outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
    name
}.getOrNull()
