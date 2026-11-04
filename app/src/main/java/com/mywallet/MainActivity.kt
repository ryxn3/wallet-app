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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.util.UUID

private val PALETTE = listOf(
    0xFF1A73E8, 0xFF6C3CE0, 0xFFE8365D, 0xFFFF7043, 0xFFFFC107, 0xFF2E9E5B,
    0xFF00ACC1, 0xFF263238, 0xFF000000, 0xFFFFFFFF, 0xFFF48FB1, 0xFF8D6E63,
)
private val PATTERNS = listOf("None", "Circles", "Stripes", "Arcs", "Dots", "Glow")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark = androidx.compose.foundation.isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) darkColorScheme(background = Color(0xFF0E0E11), surface = Color(0xFF0E0E11))
            else lightColorScheme(background = Color(0xFFF4F5F9), surface = Color(0xFFF4F5F9))) {
                Surface(Modifier.fillMaxSize()) { App() }
            }
        }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE) }
    val cards = remember { mutableStateListOf<WalletCard>().apply { addAll(Store.load(ctx)) } }
    var openId by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<WalletCard?>(null) }
    var sort by remember { mutableStateOf(runCatching { SortMode.valueOf(prefs.getString("sort", "FAVORITES")!!) }.getOrDefault(SortMode.FAVORITES)) }
    var stacked by remember { mutableStateOf(prefs.getBoolean("stacked", true)) }

    fun persist() = Store.save(ctx, cards)
    fun update(c: WalletCard) { val i = cards.indexOfFirst { it.id == c.id }; if (i >= 0) cards[i] = c; persist() }

    when {
        editing != null -> {
            BackHandler { editing = null }
            Editor(editing!!, onCancel = { editing = null }, onSave = { c ->
                val i = cards.indexOfFirst { it.id == c.id }
                if (i >= 0) cards[i] = c else cards.add(0, c)
                persist(); editing = null
            })
        }
        openId != null && cards.any { it.id == openId } -> {
            BackHandler { openId = null }
            val card = cards.first { it.id == openId }
            Detail(card, onBack = { openId = null },
                onEdit = { editing = card },
                onFavorite = { update(card.copy(favorite = !card.favorite)) },
                onDelete = { cards.remove(card); persist(); openId = null })
        }
        else -> CardList(cards.sortedFor(sort), sort, stacked,
            onSort = { sort = it; prefs.edit().putString("sort", it.name).apply() },
            onStacked = { stacked = it; prefs.edit().putBoolean("stacked", it).apply() },
            onOpen = { c -> update(c.copy(lastUsed = System.currentTimeMillis())); openId = c.id },
            onAdd = { editing = WalletCard() })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardList(
    cards: List<WalletCard>, sort: SortMode, stacked: Boolean,
    onSort: (SortMode) -> Unit, onStacked: (Boolean) -> Unit,
    onOpen: (WalletCard) -> Unit, onAdd: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Wallet", fontWeight = FontWeight.Bold, fontSize = 28.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    IconButton({ menu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort and view") }
                    DropdownMenu(menu, { menu = false }) {
                        Text("Sort by", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontSize = 12.sp, color = Color.Gray)
                        SortMode.values().forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m.label) },
                                leadingIcon = { if (m == sort) Icon(Icons.Default.Check, null) else Spacer(Modifier.size(24.dp)) },
                                onClick = { onSort(m); menu = false },
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(if (stacked) "Show cards in full" else "Show cards stacked") },
                            onClick = { onStacked(!stacked); menu = false },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Add card") })
        },
    ) { pad ->
        if (cards.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("Your wallet is empty.\nTap \"Add card\" to create your first card.",
                    color = Color.Gray, textAlign = TextAlign.Center)
            }
        } else {
            // Apple-Wallet style: a tight stack that opens when tapped, then scrolls.
            var expanded by remember { mutableStateOf(false) }
            val useStack = stacked && !expanded
            BackHandler(expanded) { expanded = false }
            val frac by androidx.compose.animation.core.animateFloatAsState(
                when { !stacked -> 1f; expanded -> 0.3f; else -> 0.13f }, label = "overlap")
            val shown = if (useStack) cards.take(6) else cards
            LazyColumn(
                Modifier.padding(pad), userScrollEnabled = !useStack,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(if (stacked) 0.dp else 16.dp),
            ) {
                itemsIndexed(shown, key = { _, c -> c.id }) { i, card ->
                    val overlap = stacked && i != shown.lastIndex
                    val m = if (overlap) Modifier.layout { measurable, constraints ->
                        val p = measurable.measure(constraints)
                        layout(p.width, (p.height * frac).toInt()) { p.place(0, 0) }
                    } else Modifier
                    CardView(card, m.clickable { if (useStack) expanded = true else onOpen(card) })
                }
            }
        }
    }
}

private fun DrawScope.drawPattern(p: Int, c: Color) {
    val w = size.width; val h = size.height
    when (p) {
        1 -> { drawCircle(c, h * 0.8f, Offset(w * 0.95f, h * 0.1f)); drawCircle(c, h * 0.5f, Offset(w * 0.1f, h * 1.1f)) }
        2 -> for (i in -3..14) { val x = i * w / 8f; drawLine(c, Offset(x, 0f), Offset(x - h * 0.6f, h), strokeWidth = w * 0.03f) }
        3 -> for (i in 1..5) drawCircle(c, h * 0.3f * i, Offset(w, h), style = Stroke(width = w * 0.012f))
        4 -> for (x in 0..11) for (y in 0..6) drawCircle(c, w * 0.007f, Offset(w * 0.08f * x + w * 0.05f, h * 0.15f * y + h * 0.08f))
        5 -> drawCircle(Brush.radialGradient(listOf(c.copy(alpha = c.alpha * 3f), Color.Transparent), Offset(w * 0.8f, h * 0.2f), h), h, Offset(w * 0.8f, h * 0.2f))
    }
}

@Composable
fun CardView(card: WalletCard, modifier: Modifier = Modifier, big: Boolean = false) {
    val ctx = LocalContext.current
    val shape = RoundedCornerShape(20.dp)
    val textColor = if (card.darkText) Color(0xFF111111) else Color.White
    val bg = remember(card.image) {
        card.image?.let {
            runCatching { BitmapFactory.decodeFile(Store.imageFile(ctx, it).path)?.asImageBitmap() }.getOrNull()
        }
    }
    Box(
        modifier.fillMaxWidth().aspectRatio(1.586f).shadow(8.dp, shape).clip(shape)
            .background(Brush.linearGradient(listOf(Color(card.colorA), Color(card.colorB))))
    ) {
        if (bg != null) {
            Image(bg, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.28f)))
        } else Canvas(Modifier.matchParentSize()) {
            drawPattern(card.pattern, if (card.darkText) Color.Black.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.13f))
        }
        val tc = if (bg != null) Color.White else textColor
        Column(Modifier.padding(if (big) 24.dp else 18.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(card.name.ifBlank { "Card name" }, color = tc, fontSize = if (big) 28.sp else 22.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
                if (card.favorite) Icon(Icons.Default.Star, null, tint = tc.copy(alpha = 0.9f), modifier = Modifier.size(20.dp))
            }
            Text(card.number.chunked(4).joinToString(" "), color = tc.copy(alpha = 0.85f), fontSize = if (big) 18.sp else 15.sp, letterSpacing = 1.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Detail(card: WalletCard, onBack: () -> Unit, onEdit: () -> Unit, onFavorite: () -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    // Full brightness while the barcode is shown so scanners can read it.
    DisposableEffect(showDetails) {
        val w = (ctx as ComponentActivity).window
        val old = w.attributes.screenBrightness
        if (showDetails) w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
        onDispose { w.attributes = w.attributes.apply { screenBrightness = old } }
    }
    val barcode = remember(card.number, card.format) { makeBarcode(card.number, card.format) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(horizontal = 16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = if (showDetails) Arrangement.Top else Arrangement.Center,
        ) {
            CardView(card, Modifier.clickable { showDetails = !showDetails }, big = true)
            Spacer(Modifier.height(14.dp))
            if (!showDetails) Text("Tap the card to see details", Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center, color = Color.Gray, fontSize = 14.sp)
            AnimatedVisibility(showDetails, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (barcode != null) Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(barcode.asImageBitmap(), "Barcode",
                            Modifier.fillMaxWidth(if (card.format == "QR_CODE" || card.format == "AZTEC") 0.65f else 1f))
                    }
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            InfoRow("Name", card.name)
                            if (card.number.isNotBlank()) InfoRow("Number", card.number)
                            InfoRow("Type", card.format.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() })
                            if (card.notes.isNotBlank()) InfoRow("Notes", card.notes)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onFavorite, Modifier.weight(1f)) {
                            Icon(if (card.favorite) Icons.Default.Star else Icons.Default.StarBorder, null)
                            Spacer(Modifier.width(6.dp)); Text(if (card.favorite) "Favorite" else "Favorite")
                        }
                        OutlinedButton(onEdit, Modifier.weight(1f)) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(6.dp)); Text("Edit") }
                        OutlinedButton({ confirm = true }, Modifier.weight(1f)) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(6.dp)); Text("Delete") }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
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

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Text(value, fontSize = 16.sp)
    }
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
                    Barcode.FORMAT_QR_CODE -> "QR_CODE"
                    Barcode.FORMAT_EAN_13 -> "EAN_13"
                    Barcode.FORMAT_PDF417 -> "PDF_417"
                    Barcode.FORMAT_AZTEC -> "AZTEC"
                    else -> "CODE_128"
                }
                c = c.copy(number = v, format = fmt)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (initial.name.isBlank()) "New card" else "Edit card") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel") } },
                actions = { TextButton({ onSave(c) }, enabled = valid) { Text("Save") } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardView(c)
            OutlinedTextField(c.name, { c = c.copy(name = it) }, Modifier.fillMaxWidth(), label = { Text("Card name") }, singleLine = true)
            OutlinedTextField(c.number, { c = c.copy(number = it) }, Modifier.fillMaxWidth(), label = { Text("Barcode / card number") }, singleLine = true,
                isError = c.format != "NONE" && c.number.isNotBlank() && !valid,
                supportingText = { if (c.format == "EAN_13") Text("EAN-13 needs 12 or 13 digits") })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ c = c.copy(number = randomNumber(c.format)) }, Modifier.weight(1f)) {
                    Icon(Icons.Default.Casino, null); Spacer(Modifier.width(6.dp)); Text("Random number")
                }
                OutlinedButton(::scan, Modifier.weight(1f)) { Text("Scan barcode") }
            }

            Text("Barcode type", fontWeight = FontWeight.Medium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FORMATS.forEach { f ->
                    FilterChip(c.format == f, { c = c.copy(format = f) }, { Text(f.replace('_', ' ')) })
                }
            }

            Text("Design presets", fontWeight = FontWeight.Medium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(PRESETS) { p ->
                    val sel = c.colorA == p.a && c.colorB == p.b && c.pattern == p.pattern
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.width(96.dp).aspectRatio(1.586f).clip(RoundedCornerShape(10.dp))
                                .background(Brush.linearGradient(listOf(Color(p.a), Color(p.b))))
                                .border(if (sel) 3.dp else 0.dp, if (sel) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(10.dp))
                                .clickable { c = c.copy(colorA = p.a, colorB = p.b, darkText = p.dark, pattern = p.pattern, image = null) }
                        ) { Canvas(Modifier.matchParentSize()) { drawPattern(p.pattern, if (p.dark) Color.Black.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.13f)) } }
                        Text(p.name, fontSize = 11.sp)
                    }
                }
            }

            Text("Store names (name and colors only, no logos)", fontWeight = FontWeight.Medium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(STORES) { p ->
                    AssistChip(
                        onClick = { c = c.copy(name = p.name, colorA = p.a, colorB = p.b, darkText = p.dark, pattern = p.pattern, image = null) },
                        label = { Text(p.name) },
                        leadingIcon = { Box(Modifier.size(14.dp).clip(CircleShape).background(Color(p.a))) },
                    )
                }
            }

            Text("Pattern", fontWeight = FontWeight.Medium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PATTERNS.forEachIndexed { i, n -> FilterChip(c.pattern == i, { c = c.copy(pattern = i) }, { Text(n) }) }
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
            Spacer(Modifier.height(24.dp))
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
