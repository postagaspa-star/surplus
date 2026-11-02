package it.gaspa.surplus.aggiornamento

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat
import it.gaspa.surplus.R
import it.gaspa.surplus.SurplusApp
import it.gaspa.surplus.data.Nas
import it.gaspa.surplus.data.SettingsRepo
import it.gaspa.surplus.data.VersioneDaScaricare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

sealed interface EsitoAggiornamento {
    data object GiaAggiornato : EsitoAggiornamento
    data object SenzaNas : EsitoAggiornamento
    data class Avviato(val versione: String) : EsitoAggiornamento
    data class Rimandato(val versione: String) : EsitoAggiornamento
    data class NonRiuscito(val messaggio: String) : EsitoAggiornamento
}

fun EsitoAggiornamento.testo(): String = when (this) {
    EsitoAggiornamento.GiaAggiornato -> "Hai già l'ultima versione."
    EsitoAggiornamento.SenzaNas -> "Per aggiornarsi da sola l'app deve essere abbinata al NAS."
    is EsitoAggiornamento.Avviato -> "Sto installando la versione $versione. Se Android lo chiede, conferma."
    is EsitoAggiornamento.Rimandato -> "La versione $versione è pronta: si installa quando lasci l'app."
    is EsitoAggiornamento.NonRiuscito -> messaggio
}

/** L'impronta SHA-256 di un file, in esadecimale minuscolo. */
fun impronta(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { ingresso ->
        val pezzo = ByteArray(64 * 1024)
        while (true) {
            val n = ingresso.read(pezzo)
            if (n < 0) break
            digest.update(pezzo, 0, n)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/** Il file è proprio quello pubblicato dal NAS: stessa impronta e stessa dimensione. */
fun coincide(file: File, versione: VersioneDaScaricare): Boolean {
    val sha = versione.sha256?.trim()?.lowercase()?.takeIf { it.length == 64 } ?: return false
    if (!file.isFile) return false
    if (versione.byte != null && file.length() != versione.byte) return false
    return impronta(file) == sha
}

/**
 * La parte che non ha bisogno di Android: chiede la versione al NAS, scarica
 * l'app e la controlla. Un file che non coincide non arriva mai all'installazione.
 */
class Preparatore(
    private val indirizzo: String,
    private val cartella: File,
    private val codiceMio: Int,
) {
    sealed interface Preparazione {
        data class Pronto(val file: File, val versione: VersioneDaScaricare) : Preparazione
        data class NonRiuscito(val messaggio: String) : Preparazione
    }

    suspend fun versioneNuova(): Pair<VersioneDaScaricare?, String?> =
        Nas.versioneAndroid(indirizzo, codiceMio).fold(
            onSuccess = { it to null },
            onFailure = { null to Nas.messaggio(it) },
        )

    suspend fun prepara(versione: VersioneDaScaricare): Preparazione {
        if (versione.sha256.isNullOrBlank()) {
            return Preparazione.NonRiuscito("Il NAS non dice l'impronta dell'aggiornamento: per sicurezza non lo installo.")
        }
        return withContext(Dispatchers.IO) {
            cartella.mkdirs()
            val file = File(cartella, "surplus-${versione.codice}.apk")
            if (coincide(file, versione)) return@withContext Preparazione.Pronto(file, versione)
            cartella.listFiles()?.filter { it.name.endsWith(".apk") || it.name.endsWith(".tmp") }?.forEach { it.delete() }
            val temporaneo = File(cartella, "scaricamento.tmp")
            if (!Nas.scaricaApk(indirizzo, temporaneo)) {
                temporaneo.delete()
                return@withContext Preparazione.NonRiuscito("Non sono riuscito a scaricare l'aggiornamento. Riprovo più tardi.")
            }
            if (!coincide(temporaneo, versione)) {
                temporaneo.delete()
                return@withContext Preparazione.NonRiuscito("Il file scaricato non coincide con quello del NAS: l'ho scartato.")
            }
            if (!temporaneo.renameTo(file)) {
                temporaneo.delete()
                return@withContext Preparazione.NonRiuscito("Non c'è spazio per l'aggiornamento.")
            }
            Preparazione.Pronto(file, versione)
        }
    }
}

/**
 * L'app si aggiorna da sé, come Lectio: se sul NAS c'è un codice più alto del
 * nostro scarica <c>/scarica/surplus.apk</c>, ne controlla l'impronta e lo passa
 * a PackageInstaller. Si installa quando lasci l'app, mai mentre la usi:
 * l'installazione la chiuderebbe mentre stai scrivendo un pasto.
 *
 * L'app è firmata con la chiave di chi la pubblica: Android rifiuta da solo un
 * aggiornamento firmato da qualcun altro, quindi nemmeno chi entrasse nella rete
 * di casa potrebbe far installare un'app sua al posto di questa.
 */
class Aggiornatore(private val context: Context) {

    private val cartella get() = File(context.cacheDir, "aggiornamenti")
    private val segnoRimandato get() = File(cartella, "rimandato.txt")

    suspend fun controlla(forza: Boolean = false, inSottofondo: Boolean = false): EsitoAggiornamento {
        val impostazioni = SettingsRepo(context).current()
        val indirizzo = Nas.normalizza(impostazioni.nasIndirizzo) ?: return EsitoAggiornamento.SenzaNas
        val preparatore = Preparatore(indirizzo, cartella, codiceMio(context))
        val (nuova, problema) = preparatore.versioneNuova()
        if (problema != null) return EsitoAggiornamento.NonRiuscito(problema)
        if (nuova == null) return EsitoAggiornamento.GiaAggiornato

        if (inSottofondo && !forza) {
            if (rimandataDiRecente(nuova.codice)) return EsitoAggiornamento.Rimandato(nuova.nome)
            if (AppInPrimoPiano.visibile) {
                // Mentre la usi si scarica soltanto: si installa quando la lasci.
                preparatore.prepara(nuova)
                return EsitoAggiornamento.Rimandato(nuova.nome)
            }
        }
        return when (val pronto = preparatore.prepara(nuova)) {
            is Preparatore.Preparazione.Pronto -> {
                if (inSottofondo) segna(nuova.codice)
                if (installa(pronto.file)) EsitoAggiornamento.Avviato(nuova.nome)
                else EsitoAggiornamento.NonRiuscito("Android non ha accettato l'aggiornamento. Riprova più tardi.")
            }
            is Preparatore.Preparazione.NonRiuscito -> EsitoAggiornamento.NonRiuscito(pronto.messaggio)
        }
    }

    /** Solo per le impostazioni: c'è una versione nuova? (senza scaricarla). */
    suspend fun versioneDisponibile(): VersioneDaScaricare? {
        val indirizzo = Nas.normalizza(SettingsRepo(context).current().nasIndirizzo) ?: return null
        return Preparatore(indirizzo, cartella, codiceMio(context)).versioneNuova().first
    }

    private fun rimandataDiRecente(codice: Int): Boolean {
        val riga = runCatching { segnoRimandato.readText().trim() }.getOrNull() ?: return false
        val pezzi = riga.split(":")
        val suo = pezzi.getOrNull(0)?.toIntOrNull() ?: return false
        val quando = pezzi.getOrNull(1)?.toLongOrNull() ?: return false
        return suo == codice && System.currentTimeMillis() - quando < TimeUnit.HOURS.toMillis(24)
    }

    private fun segna(codice: Int) {
        runCatching {
            cartella.mkdirs()
            segnoRimandato.writeText("$codice:${System.currentTimeMillis()}")
        }
    }

    private suspend fun installa(apk: File): Boolean = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        installer.mySessions.forEach { runCatching { installer.abandonSession(it.sessionId) } }
        val parametri = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        var sessione = -1
        try {
            sessione = installer.createSession(parametri)
            installer.openSession(sessione).use { s ->
                apk.inputStream().use { ingresso ->
                    s.openWrite("surplus.apk", 0, apk.length()).use { uscita ->
                        ingresso.copyTo(uscita)
                        s.fsync(uscita)
                    }
                }
                s.commit(intentEsito(sessione).intentSender)
            }
            true
        } catch (_: IOException) {
            if (sessione >= 0) runCatching { installer.abandonSession(sessione) }
            false
        } catch (_: SecurityException) {
            if (sessione >= 0) runCatching { installer.abandonSession(sessione) }
            false
        }
    }

    private fun intentEsito(sessione: Int): PendingIntent {
        val intent = Intent(context, AggiornamentoReceiver::class.java)
            .setAction(AggiornamentoReceiver.AZIONE)
            .setPackage(context.packageName)
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, sessione, intent, flag)
    }
}

/** Il numero di versione di questa app, chiesto ad Android (nessun BuildConfig). */
fun codiceMio(context: Context): Int = runCatching {
    @Suppress("DEPRECATION")
    context.packageManager.getPackageInfo(context.packageName, 0).versionCode
}.getOrDefault(0)

/** Surplus è sullo schermo? Da dietro le quinte Android non lascia aprire finestre. */
object AppInPrimoPiano {
    @Volatile
    var visibile: Boolean = false
}

/** L'esito dell'installazione: se Android vuole la conferma, finestra o notifica. */
class AggiornamentoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val stato = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (stato != PackageInstaller.STATUS_PENDING_USER_ACTION) return
        val conferma = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java) ?: return
        if (AppInPrimoPiano.visibile) {
            conferma.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(conferma)
            return
        }
        val tocca = PendingIntent.getActivity(
            context, 0, conferma.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val avviso = NotificationCompat.Builder(context, SurplusApp.CANALE_AGGIORNAMENTI)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Surplus si è aggiornato")
            .setContentText("Tocca per finire l'installazione.")
            .setContentIntent(tocca)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)?.notify(NOTIFICA, avviso)
    }

    companion object {
        const val AZIONE = "it.gaspa.surplus.ESITO_INSTALLAZIONE"
        private const val NOTIFICA = 4201
    }
}
