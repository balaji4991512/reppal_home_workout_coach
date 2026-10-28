package com.homeworkoutcoach.app.data

import com.homeworkoutcoach.app.data.memory.InMemorySettingsRepository
import com.homeworkoutcoach.engine.Bench
import com.homeworkoutcoach.engine.Level
import com.homeworkoutcoach.engine.Muscle
import com.homeworkoutcoach.engine.Split
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Behaviour every [SettingsRepository] must have. Runs against DataStore and the in-memory fake. */
abstract class SettingsRepositoryContract {
    protected lateinit var repo: SettingsRepository

    abstract fun create(): SettingsRepository

    @Before
    fun setUp() {
        repo = create()
    }

    @Test
    fun defaultsMatchThePrototypeExceptTheDumbbellIsOnTheAdjustableSet() = runTest {
        val s = repo.settings.first()
        assertEquals(UserSettings(), s)
        assertEquals(Bench.FLAT, s.bench)
        assertEquals(listOf(4.5), s.dumbbellsKg) // the prototype's 5 kg is not a setting of the adjustable set
        assertEquals(3, s.daysPerWeek)
        assertEquals(Split.AUTO, s.split)
        assertEquals(Level.BEGINNER, s.level)
        assertEquals(ThemeMode.LIGHT, s.theme)
    }

    @Test
    fun updateAndReset() = runTest {
        repo.update { it.copy(name = "Asha", dumbbellsKg = listOf(2.5, 4.5), split = Split.CUSTOM) }
        repo.update { it.copy(customDays = listOf(listOf(Muscle.CHEST, Muscle.ABS)), theme = ThemeMode.DARK) }
        val s = repo.settings.first()
        assertEquals("Asha", s.name)
        assertEquals(listOf(2.5, 4.5), s.dumbbellsKg)
        assertEquals(listOf(listOf(Muscle.CHEST, Muscle.ABS)), s.customDays)
        assertEquals(ThemeMode.DARK, s.theme)
        repo.reset()
        assertEquals(UserSettings(), repo.settings.first())
    }

    @Test
    fun planSettingsCarryEverythingThePlannerNeeds() = runTest {
        repo.update {
            it.copy(bench = Bench.NONE, dumbbellsKg = listOf(4.5, 8.0), daysPerWeek = 4, excluded = listOf("rdl"))
        }
        val p = repo.settings.first().planSettings(mapOf("goblet" to "sumo"))
        assertEquals(Bench.NONE, p.equipment.bench)
        assertEquals(setOf(4.5, 8.0), p.equipment.dumbbellsKg)
        assertEquals(4, p.daysPerWeek)
        assertEquals(setOf("rdl"), p.excluded)
        assertEquals(mapOf("goblet" to "sumo"), p.swaps)
    }
}

class DataStoreSettingsRepositoryTest : SettingsRepositoryContract() {
    @get:Rule
    val tmp = TemporaryFolder()

    private val file: File by lazy { File(tmp.root, DataStoreSettingsRepository.FILE_NAME) }

    override fun create(): SettingsRepository =
        DataStoreSettingsRepository(DataStoreSettingsRepository.createStore { file })

    /** A store whose scope can be cancelled, so a second store can open the same file. */
    private fun openStore(scope: CoroutineScope) = DataStoreSettingsRepository(
        DataStoreFactory.create(
            serializer = UserSettingsSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { UserSettings() },
            scope = scope,
            produceFile = { File(tmp.root, "reopen.json") },
        ),
    )

    @Test
    fun survivesReopening() = runTest {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        openStore(scope).update { it.copy(started = true, daysPerWeek = 5, painExcluded = listOf("ohp")) }
        scope.coroutineContext[kotlinx.coroutines.Job]!!.cancelAndJoin()

        val reopened = openStore(CoroutineScope(Dispatchers.IO + SupervisorJob())).settings.first()
        assertEquals(true, reopened.started)
        assertEquals(5, reopened.daysPerWeek)
        assertEquals(listOf("ohp"), reopened.painExcluded)
    }

    @Test
    fun dumbbellsSavedBeforeTheAdjustableSetMoveToTheNearestSetting() = runTest {
        // 5 kg is halfway between 4.5 and 5.5 (the lighter wins); 12 is nearest 11.5; 8 is already a setting
        tmp.newFile("old.json").writeText("""{"started":true,"dumbbellsKg":[5.0,8.0,12.0,1.0]}""")
        val store = DataStoreSettingsRepository(DataStoreSettingsRepository.createStore { File(tmp.root, "old.json") })
        assertEquals(listOf(2.5, 4.5, 8.0, 11.5), store.settings.first().dumbbellsKg)
        // and weights saved later are kept on the set too
        store.update { it.copy(dumbbellsKg = listOf(6.0, 6.5, 24.0)) }
        assertEquals(listOf(5.5, 6.5, 24.0), store.settings.first().dumbbellsKg)
    }

    @Test
    fun corruptFileFallsBackToDefaults() = runTest {
        tmp.newFile("broken.json").writeText("{ not json")
        val store = DataStoreSettingsRepository(DataStoreSettingsRepository.createStore { File(tmp.root, "broken.json") })
        assertEquals(UserSettings(), store.settings.first())
    }

    @Test
    fun oldAndNewerFilesStillRead() = runTest {
        // missing fields take defaults, unknown fields are ignored
        tmp.newFile("partial.json").writeText("""{"name":"Ravi","daysPerWeek":2,"someFutureField":true}""")
        val store = DataStoreSettingsRepository(DataStoreSettingsRepository.createStore { File(tmp.root, "partial.json") })
        assertEquals(UserSettings(name = "Ravi", daysPerWeek = 2), store.settings.first())
    }
}

class InMemorySettingsRepositoryTest : SettingsRepositoryContract() {
    override fun create(): SettingsRepository = InMemorySettingsRepository()
}
