package it.gaspa.surplus.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** I tre promemoria consentiti: SOLO spuntini, mai colazione/pranzo/cena. */
enum class ReminderKey(val idCode: Int, val label: String, val defaultHour: Int, val defaultMinute: Int) {
    META_MATTINA(1, "Metà mattina", 10, 30),
    POST_ALLENAMENTO(2, "Dopo allenamento", 17, 0),
    PRE_NANNA(3, "Prima di dormire", 22, 30);
}

/** daysMask: bit 0 = lunedì … bit 6 = domenica. */
data class ReminderCfg(
    val key: ReminderKey,
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
    val daysMask: Int,
)

data class Settings(
    val kcalMin: Int = 3300,
    val kcalMax: Int = 3700,
    val protMin: Int = 130,
    val protMax: Int = 150,
    val waterTargetMl: Int = 3250,
    val reminders: List<ReminderCfg> = defaultReminders(),
    // Promemoria post-allenamento dinamico: invece dell'orario fisso,
    // suona N minuti dopo l'ultima serie registrata (solo sessioni di oggi).
    val postWorkoutDynamic: Boolean = false,
    val postWorkoutDelayMin: Int = 30,
    // Scheda del prodotto dopo l'inserimento (stile Yuka), con il suo tutorial.
    val mostraScheda: Boolean = true,
    val schedaTutorialVisto: Boolean = false,
    // Riconoscimento foto: la chiave è dell'utente, l'app non ne contiene nessuna.
    val aiProvider: AiProvider = AiProvider.GEMINI,
    val aiApiKey: String = "",
    val aiModel: String = "",
    // Il NAS di casa: l'indirizzo e il permesso dato a questo telefono.
    val nasIndirizzo: String = "",
    val nasToken: String = "",
    val nasUltima: Long = 0L,
    // La revisione dell'ultimo diario unito che il telefono ha davvero tenuto (-1: non si sa).
    val nasBase: Long = -1L,
) {
    /** Il diario sta anche sul NAS solo se questo telefono è stato abbinato. */
    val nasAbbinato: Boolean get() = nasIndirizzo.isNotBlank() && nasToken.isNotBlank()

    /** La funzione foto esiste solo se l'utente ha messo la sua chiave. */
    val aiAttiva: Boolean get() = aiApiKey.isNotBlank()

    /** Modello scelto, o quello predefinito del fornitore se il campo è vuoto. */
    val aiModelEffettivo: String get() = aiModel.ifBlank { aiProvider.modelDefault }

    companion object {
        const val ALL_DAYS = 0b1111111
        fun defaultReminders(): List<ReminderCfg> = ReminderKey.entries.map {
            // Spenti di default: gli orari li decide l'utente dalle impostazioni.
            ReminderCfg(it, enabled = false, hour = it.defaultHour, minute = it.defaultMinute, daysMask = ALL_DAYS)
        }
    }
}

class SettingsRepo(context: Context) {

    private val appContext = context.applicationContext

    private val kKcalMin = intPreferencesKey("kcal_min")
    private val kKcalMax = intPreferencesKey("kcal_max")
    private val kProtMin = intPreferencesKey("prot_min")
    private val kProtMax = intPreferencesKey("prot_max")
    private val kWater = intPreferencesKey("water_target_ml")
    private val kPostDynamic = booleanPreferencesKey("post_workout_dynamic")
    private val kPostDelay = intPreferencesKey("post_workout_delay_min")
    private val kScheda = booleanPreferencesKey("mostra_scheda")
    private val kSchedaTut = booleanPreferencesKey("scheda_tutorial_visto")
    private val kAiProvider = stringPreferencesKey("ai_provider")
    private val kAiKey = stringPreferencesKey("ai_api_key")
    private val kAiModel = stringPreferencesKey("ai_model")
    private val kNasIndirizzo = stringPreferencesKey("nas_indirizzo")
    private val kNasToken = stringPreferencesKey("nas_token")
    private val kNasUltima = androidx.datastore.preferences.core.longPreferencesKey("nas_ultima")
    private val kNasBase = androidx.datastore.preferences.core.longPreferencesKey("nas_base")

    private fun kOn(r: ReminderKey) = booleanPreferencesKey("rem_${r.name}_on")
    private fun kHour(r: ReminderKey) = intPreferencesKey("rem_${r.name}_hour")
    private fun kMin(r: ReminderKey) = intPreferencesKey("rem_${r.name}_min")
    private fun kDays(r: ReminderKey) = intPreferencesKey("rem_${r.name}_days")

    val flow: Flow<Settings> = appContext.settingsDataStore.data.map { p ->
        Settings(
            kcalMin = p[kKcalMin] ?: 3300,
            kcalMax = p[kKcalMax] ?: 3700,
            protMin = p[kProtMin] ?: 130,
            protMax = p[kProtMax] ?: 150,
            waterTargetMl = p[kWater] ?: 3250,
            postWorkoutDynamic = p[kPostDynamic] ?: false,
            postWorkoutDelayMin = p[kPostDelay] ?: 30,
            mostraScheda = p[kScheda] ?: true,
            schedaTutorialVisto = p[kSchedaTut] ?: false,
            aiProvider = runCatching { AiProvider.valueOf(p[kAiProvider].orEmpty()) }
                .getOrDefault(AiProvider.GEMINI),
            aiApiKey = p[kAiKey].orEmpty(),
            aiModel = p[kAiModel].orEmpty(),
            nasIndirizzo = p[kNasIndirizzo].orEmpty(),
            nasToken = p[kNasToken].orEmpty(),
            nasUltima = p[kNasUltima] ?: 0L,
            nasBase = p[kNasBase] ?: -1L,
            reminders = ReminderKey.entries.map { r ->
                ReminderCfg(
                    key = r,
                    enabled = p[kOn(r)] ?: false,
                    hour = p[kHour(r)] ?: r.defaultHour,
                    minute = p[kMin(r)] ?: r.defaultMinute,
                    daysMask = p[kDays(r)] ?: Settings.ALL_DAYS,
                )
            },
        )
    }

    suspend fun current(): Settings = flow.first()

    /**
     * Solo le voci del NAS. La sincronizzazione gira in sottofondo e salva
     * l'ora dell'ultima volta: se passasse da [save] riscriverebbe anche gli
     * obiettivi e la chiave, cancellando quello che stai scrivendo adesso.
     */
    suspend fun salvaNas(
        indirizzo: String? = null,
        token: String? = null,
        ultima: Long? = null,
        base: Long? = null,
    ) {
        appContext.settingsDataStore.edit { p ->
            if (indirizzo != null) p[kNasIndirizzo] = indirizzo
            if (token != null) p[kNasToken] = token
            if (ultima != null) p[kNasUltima] = ultima
            if (base != null) p[kNasBase] = base
        }
    }

    suspend fun save(s: Settings) {
        appContext.settingsDataStore.edit { p ->
            p[kKcalMin] = s.kcalMin
            p[kKcalMax] = s.kcalMax
            p[kProtMin] = s.protMin
            p[kProtMax] = s.protMax
            p[kWater] = s.waterTargetMl
            p[kPostDynamic] = s.postWorkoutDynamic
            p[kPostDelay] = s.postWorkoutDelayMin
            p[kScheda] = s.mostraScheda
            p[kSchedaTut] = s.schedaTutorialVisto
            p[kAiProvider] = s.aiProvider.name
            p[kAiKey] = s.aiApiKey
            p[kAiModel] = s.aiModel
            p[kNasIndirizzo] = s.nasIndirizzo
            p[kNasToken] = s.nasToken
            p[kNasUltima] = s.nasUltima
            s.reminders.forEach { r ->
                p[kOn(r.key)] = r.enabled
                p[kHour(r.key)] = r.hour
                p[kMin(r.key)] = r.minute
                p[kDays(r.key)] = r.daysMask
            }
        }
    }
}
