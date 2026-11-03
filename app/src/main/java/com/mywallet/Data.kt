package com.mywallet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class WalletCard(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val number: String = "",
    val format: String = "QR_CODE", // BarcodeFormat name, or "NONE"
    val colorA: Long = 0xFF1A73E8,
    val colorB: Long = 0xFF6C3CE0,
    val darkText: Boolean = false,
    val notes: String = "",
    val image: String? = null, // file name inside filesDir/images
    val pattern: Int = 0,
    val favorite: Boolean = false,
    val created: Long = System.currentTimeMillis(),
    val lastUsed: Long = 0,
)

data class Preset(val name: String, val a: Long, val b: Long, val dark: Boolean, val pattern: Int)

val PRESETS = listOf(
    Preset("Obsidian", 0xFF0B0B0F, 0xFF2A2A33, false, 5),
    Preset("Graphite", 0xFF37474F, 0xFF1C262B, false, 2),
    Preset("Midnight", 0xFF0F2044, 0xFF1B4F9C, false, 3),
    Preset("Ocean", 0xFF0093E9, 0xFF00D2C6, false, 3),
    Preset("Aurora", 0xFF00C9A7, 0xFF845EC2, false, 5),
    Preset("Emerald", 0xFF0B6E4F, 0xFF3DDC97, false, 1),
    Preset("Forest", 0xFF1B4332, 0xFF52796F, false, 4),
    Preset("Mint", 0xFFB9F6CA, 0xFF69F0AE, true, 1),
    Preset("Lime Pop", 0xFFC6FF00, 0xFF76FF03, true, 2),
    Preset("Sunshine", 0xFFFFD54F, 0xFFFF9800, true, 1),
    Preset("Gold", 0xFFBF953F, 0xFFFCF6BA, true, 2),
    Preset("Rose Gold", 0xFFB76E79, 0xFFF4C2C2, true, 5),
    Preset("Sunset", 0xFFFF512F, 0xFFDD2476, false, 3),
    Preset("Coral", 0xFFFF7E67, 0xFFFFB199, true, 1),
    Preset("Ruby", 0xFF8E0E1C, 0xFFE53935, false, 4),
    Preset("Crimson", 0xFF4A0010, 0xFFB71C1C, false, 5),
    Preset("Berry", 0xFF6A1B9A, 0xFFE91E63, false, 1),
    Preset("Lavender", 0xFFD1C4E9, 0xFF9575CD, true, 3),
    Preset("Royal", 0xFF311B92, 0xFF7C4DFF, false, 2),
    Preset("Neon Pink", 0xFFFF00A8, 0xFFFF6EC7, false, 4),
    Preset("Silver", 0xFFBDC3C7, 0xFF2C3E50, false, 2),
    Preset("Platinum", 0xFFE5E4E2, 0xFFB8B8C0, true, 3),
    Preset("Ice", 0xFFE0F7FA, 0xFF80DEEA, true, 5),
    Preset("Sand", 0xFFF5E6CA, 0xFFD7B98E, true, 4),
    Preset("Chocolate", 0xFF3E2723, 0xFF8D6E63, false, 1),
)

enum class SortMode(val label: String) {
    FAVORITES("Favorites first"), NEWEST("Newest"), OLDEST("Oldest"),
    NAME_AZ("Name A to Z"), NAME_ZA("Name Z to A"), RECENT("Recently used"),
}

fun List<WalletCard>.sortedFor(mode: SortMode): List<WalletCard> = when (mode) {
    SortMode.FAVORITES -> sortedWith(compareByDescending<WalletCard> { it.favorite }.thenByDescending { it.created })
    SortMode.NEWEST -> sortedByDescending { it.created }
    SortMode.OLDEST -> sortedBy { it.created }
    SortMode.NAME_AZ -> sortedBy { it.name.lowercase() }
    SortMode.NAME_ZA -> sortedByDescending { it.name.lowercase() }
    SortMode.RECENT -> sortedByDescending { it.lastUsed }
}

/** A random value that is valid for the chosen barcode type. */
fun randomNumber(format: String): String {
    val r = java.util.Random()
    val digits = StringBuilder().append(1 + r.nextInt(9))
    repeat(11) { digits.append(r.nextInt(10)) }
    if (format == "EAN_13") {
        val sum = digits.mapIndexed { i, ch -> (ch - '0') * if (i % 2 == 0) 1 else 3 }.sum()
        digits.append((10 - sum % 10) % 10)
    }
    return digits.toString()
}

val FORMATS = listOf("QR_CODE", "CODE_128", "EAN_13", "PDF_417", "AZTEC", "NONE")

object Store {
    private fun file(c: Context) = File(c.filesDir, "cards.json")

    fun load(c: Context): List<WalletCard> = try {
        val arr = JSONArray(file(c).readText())
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            WalletCard(
                o.getString("id"), o.getString("name"), o.getString("number"), o.getString("format"),
                o.getLong("colorA"), o.getLong("colorB"), o.getBoolean("darkText"), o.getString("notes"),
                if (o.isNull("image")) null else o.getString("image"),
                o.optInt("pattern", 0), o.optBoolean("favorite", false),
                o.optLong("created", 0L), o.optLong("lastUsed", 0L),
            )
        }
    } catch (e: Exception) { emptyList() }

    fun save(c: Context, cards: List<WalletCard>) {
        val arr = JSONArray()
        cards.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("number", it.number)
                .put("format", it.format).put("colorA", it.colorA).put("colorB", it.colorB)
                .put("darkText", it.darkText).put("notes", it.notes).put("image", it.image ?: JSONObject.NULL)
                .put("pattern", it.pattern).put("favorite", it.favorite)
                .put("created", it.created).put("lastUsed", it.lastUsed))
        }
        file(c).writeText(arr.toString())
    }

    fun imageFile(c: Context, name: String) = File(File(c.filesDir, "images").apply { mkdirs() }, name)
}

/** Returns null when the number is invalid for the chosen format. */
fun makeBarcode(text: String, format: String, size: Int = 720): Bitmap? {
    if (format == "NONE" || text.isBlank()) return null
    return try {
        val f = BarcodeFormat.valueOf(format)
        val square = f == BarcodeFormat.QR_CODE || f == BarcodeFormat.AZTEC
        val h = if (square) size else size / 3
        val m = MultiFormatWriter().encode(text, f, size, h)
        val bmp = Bitmap.createBitmap(m.width, m.height, Bitmap.Config.RGB_565)
        for (x in 0 until m.width) for (y in 0 until m.height)
            bmp.setPixel(x, y, if (m[x, y]) Color.BLACK else Color.WHITE)
        bmp
    } catch (e: Exception) { null }
}
