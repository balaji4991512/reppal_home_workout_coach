package com.homeworkoutcoach.app.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** [UserSettings] as one JSON file. Unknown fields are ignored and missing ones take defaults, so adding fields is safe. */
object UserSettingsSerializer : Serializer<UserSettings> {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val defaultValue = UserSettings()

    override suspend fun readFrom(input: InputStream): UserSettings = try {
        json.decodeFromString(UserSettings.serializer(), input.readBytes().decodeToString()).withDumbbellsOnTheSet()
    } catch (e: SerializationException) {
        throw CorruptionException("Unreadable settings", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Unreadable settings", e)
    }

    override suspend fun writeTo(t: UserSettings, output: OutputStream) {
        output.write(json.encodeToString(UserSettings.serializer(), t).encodeToByteArray())
    }
}

/**
 * Dumbbells are the settings of an adjustable set (2.5 to 24 kg). Weights saved before that, such as 5 or 12 kg, move to
 * the nearest setting, so nothing owned is lost and every chip on the Setup screen can show it.
 */
internal fun UserSettings.withDumbbellsOnTheSet(): UserSettings =
    copy(dumbbellsKg = dumbbellsKg.map(com.homeworkoutcoach.engine.Library::snapToDumbbellSet).distinct().sorted())

class DataStoreSettingsRepository(private val store: DataStore<UserSettings>) : SettingsRepository {

    override val settings: Flow<UserSettings> = store.data

    override suspend fun update(transform: (UserSettings) -> UserSettings) {
        store.updateData { transform(it).withDumbbellsOnTheSet() }
    }

    override suspend fun reset() {
        store.updateData { UserSettings() }
    }

    companion object {
        const val FILE_NAME = "settings.json"

        /** A corrupt file falls back to defaults instead of crashing. */
        fun createStore(file: () -> File): DataStore<UserSettings> = DataStoreFactory.create(
            serializer = UserSettingsSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { UserSettings() },
            produceFile = file,
        )

        fun create(context: Context) = DataStoreSettingsRepository(createStore { context.dataStoreFile(FILE_NAME) })
    }
}
