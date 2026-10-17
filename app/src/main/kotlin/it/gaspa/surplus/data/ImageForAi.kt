package it.gaspa.surplus.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Prepara una foto per l'invio: la rimpicciolisce, la raddrizza e la codifica.
 *
 * Il ridimensionamento non è un dettaglio: il costo di una chiamata cresce con
 * i pixel dell'immagine, quindi una foto da 12 megapixel spedita intera
 * costerebbe diverse volte tanto senza leggersi meglio.
 */
object ImageForAi {

    /** Lato lungo in pixel: abbastanza per leggere una tabella nutrizionale, non di più. */
    const val LATO_MAX = 1536

    data class Preparata(val base64: String, val mediaType: String, val larghezza: Int, val altezza: Int)

    suspend fun prepara(context: Context, uri: Uri, latoMax: Int = LATO_MAX): Preparata? =
        withContext(Dispatchers.IO) {
            runCatching {
                val originale = decodificaRidotta(context, uri, latoMax) ?: return@runCatching null
                val dritta = raddrizza(context, uri, originale)
                val scalata = scala(dritta, latoMax)
                val out = ByteArrayOutputStream()
                scalata.compress(Bitmap.CompressFormat.JPEG, 85, out)
                if (scalata !== dritta) scalata.recycle()
                if (dritta !== originale) dritta.recycle()
                originale.recycle()
                Preparata(
                    base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP),
                    mediaType = "image/jpeg",
                    larghezza = scalata.width,
                    altezza = scalata.height,
                )
            }.getOrNull()
        }

    /** Primo passaggio: legge già ridotto, così una foto enorme non satura la memoria. */
    private fun decodificaRidotta(context: Context, uri: Uri, latoMax: Int): Bitmap? {
        val misura = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, misura) }
        var campione = 1
        while (misura.outWidth / campione > latoMax * 2 && misura.outHeight / campione > latoMax * 2) {
            campione *= 2
        }
        val opzioni = BitmapFactory.Options().apply { inSampleSize = campione }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opzioni)
        }
    }

    /** Le foto scattate col telefono girato arrivano ruotate: senza questo, il testo è illeggibile. */
    private fun raddrizza(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val gradi = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        if (gradi == 0f) return bitmap
        val m = Matrix().apply { postRotate(gradi) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }

    private fun scala(bitmap: Bitmap, latoMax: Int): Bitmap {
        val lungo = maxOf(bitmap.width, bitmap.height)
        if (lungo <= latoMax) return bitmap
        val f = latoMax.toFloat() / lungo
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * f).toInt().coerceAtLeast(1),
            (bitmap.height * f).toInt().coerceAtLeast(1),
            true,
        )
    }

    /** Costo indicativo in token immagine: larghezza × altezza / 750, come da documentazione. */
    fun tokenStimati(larghezza: Int, altezza: Int): Int = (larghezza * altezza) / 750

    /** Anteprima piccola per mostrare gli scatti raccolti, senza tenere in memoria le foto intere. */
    suspend fun miniatura(context: Context, uri: Uri, lato: Int = 240): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val grezza = decodificaRidotta(context, uri, lato) ?: return@runCatching null
                val dritta = raddrizza(context, uri, grezza)
                val piccola = scala(dritta, lato)
                if (dritta !== grezza && dritta !== piccola) dritta.recycle()
                if (grezza !== piccola && grezza !== dritta) grezza.recycle()
                piccola
            }.getOrNull()
        }
}
