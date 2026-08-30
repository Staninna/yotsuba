package dev.stan.yotsuba.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Settings live in one preference: a JSON blob of [Settings]. Adding a field means adding it
 * to the data class with a default and nothing else.
 *
 * Installs that predate the blob wrote one preference per field. Those are folded into the
 * blob once, the first time anything reads or writes, and then deleted. The store is shared
 * with other owners (the vault migration flag lives here too), so only keys named after a
 * [Settings] field are ever touched.
 */
@Singleton
class SettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private val blobKey = stringPreferencesKey("settings")
    private val migration = Mutex()
    private var migrated = false

    override val settings: Flow<Settings> = flow {
        migrateLegacy()
        emitAll(dataStore.data.map { decode(it[blobKey]) })
    }

    override suspend fun update(transform: (Settings) -> Settings) {
        migrateLegacy()
        dataStore.edit { p -> p[blobKey] = json.encodeToString(transform(decode(p[blobKey]))) }
    }

    private suspend fun migrateLegacy() {
        if (migrated) return
        migration.withLock {
            if (migrated) return
            val prefs = dataStore.data.first()
            val legacyEntries = prefs.asMap().filterKeys { it.name in LEGACY_KEYS }
            if (prefs[blobKey] == null && legacyEntries.isNotEmpty()) {
                val legacy = decode(legacyToJson(legacyEntries))
                dataStore.edit { p ->
                    legacyEntries.keys.forEach { p.remove(it) }
                    p[blobKey] = json.encodeToString(legacy)
                }
            }
            migrated = true
        }
    }

    private fun decode(raw: String?): Settings =
        raw?.let { runCatching { json.decodeFromString<Settings>(it) }.getOrNull() } ?: Settings()

    private companion object {
        /**
         * The only place a missing or unrecognised value becomes a default. `coerceInputValues`
         * covers unknown enum names as well as nulls; `ignoreUnknownKeys` covers a downgrade.
         */
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = true
        }

        /** The per-field preference names the pre-blob layout used: one per [Settings] property. */
        val LEGACY_KEYS: Set<String> = Settings.serializer().descriptor.elementNames.toSet()

        /**
         * One preference per field, keyed by the [Settings] property name, becomes the same
         * JSON the blob holds so the serializer's fallbacks apply to old values too.
         */
        fun legacyToJson(prefs: Map<Preferences.Key<*>, Any>): String {
            val fields = prefs.entries.associate { (key, value) ->
                key.name to when (value) {
                    is Boolean -> JsonPrimitive(value)
                    is Int -> JsonPrimitive(value)
                    is Set<*> -> JsonArray(value.map { JsonPrimitive(it.toString()) })
                    else -> JsonPrimitive(value.toString())
                }
            }
            return JsonObject(fields).toString()
        }
    }
}
