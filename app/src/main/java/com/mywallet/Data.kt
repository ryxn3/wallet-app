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
)

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
            )
        }
    } catch (e: Exception) { emptyList() }

    fun save(c: Context, cards: List<WalletCard>) {
        val arr = JSONArray()
        cards.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("number", it.number)
                .put("format", it.format).put("colorA", it.colorA).put("colorB", it.colorB)
                .put("darkText", it.darkText).put("notes", it.notes).put("image", it.image ?: JSONObject.NULL))
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
