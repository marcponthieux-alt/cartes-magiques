package fr.cartes.multiplications

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * Une photo de terrain associée à un symbole.
 * user = true : photo ajoutée par la famille (banque de photos) ; sinon photo libre de droits fournie avec l'appli.
 */
class PhotoItem(val symbol: String, val user: Boolean, val path: String, val credit: String?, val page: String? = null)

/**
 * Banque de photos : les photos libres (Wikimedia Commons, dans assets/photos)
 * et les photos ajoutées depuis la galerie du téléphone (stockées dans l'application).
 */
class PhotoBank(ctx: Context) {
    private val app = ctx.applicationContext
    private val dir = File(app.filesDir, "photos").apply { mkdirs() }

    /** Change à chaque ajout ou suppression, pour rafraîchir l'affichage. */
    var version by mutableIntStateOf(0)
        private set

    private val bundled: List<PhotoItem> by lazy {
        try {
            val arr = JSONArray(app.assets.open("photos/credits.json").bufferedReader().use { it.readText() })
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PhotoItem(
                    o.getString("symbol"), false, "photos/" + o.getString("file"),
                    "Photo : ${o.optString("artist")} · ${o.optString("license")} · Wikimedia Commons",
                    o.optString("page")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun userPhotos(): List<PhotoItem> =
        (dir.listFiles() ?: emptyArray()).filter { it.name.endsWith(".jpg") }.sortedByDescending { it.lastModified() }
            .map { PhotoItem(it.name.substringBefore("__"), true, it.absolutePath, null) }

    /** Les photos de la famille d'abord : elles parlent davantage à l'enfant. */
    fun all(): List<PhotoItem> = userPhotos() + bundled
    fun forSymbol(id: String): List<PhotoItem> = all().filter { it.symbol == id }

    fun load(item: PhotoItem, maxPx: Int = 900): ImageBitmap? = try {
        val bmp: Bitmap? = if (item.user) {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(item.path, o)
            val s = sample(o.outWidth, o.outHeight, maxPx)
            BitmapFactory.decodeFile(item.path, BitmapFactory.Options().apply { inSampleSize = s })
        } else {
            app.assets.open(item.path).use { BitmapFactory.decodeStream(it) }
        }
        bmp?.asImageBitmap()
    } catch (e: Exception) {
        null
    }

    private fun sample(w: Int, h: Int, maxPx: Int): Int {
        var s = 1
        while (max(w, h) / (s * 2) >= maxPx) s *= 2
        return s
    }

    /** Copie une photo de la galerie dans l'application (réduite à 1280 px, orientation corrigée). */
    fun add(uri: Uri, symbol: String): Boolean = try {
        val bmp: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
            val src = ImageDecoder.createSource(app.contentResolver, uri)
            ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                val w = info.size.width; val h = info.size.height
                val k = 1280f / max(w, h)
                if (k < 1f) decoder.setTargetSize((w * k).toInt(), (h * k).toInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            val s = sample(o.outWidth, o.outHeight, 1280)
            app.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s })
            } ?: throw IllegalStateException("photo illisible")
        }
        val f = File(dir, "${symbol}__${System.currentTimeMillis()}.jpg")
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        version++
        true
    } catch (e: Exception) {
        false
    }

    fun delete(item: PhotoItem) {
        if (!item.user) return
        File(item.path).delete()
        version++
    }
}
