package com.homeworkoutcoach.app.ui

import com.homeworkoutcoach.app.data.memory.InMemoryFocusRepository
import com.homeworkoutcoach.app.data.memory.InMemorySettingsRepository
import com.homeworkoutcoach.app.data.memory.InMemoryTaskRepository
import com.homeworkoutcoach.app.data.focusSession
import com.homeworkoutcoach.app.focus.FocusAlarms
import com.homeworkoutcoach.app.focus.FocusSessions
import com.homeworkoutcoach.app.ui.focus.FocusViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class FocusViewModelTest {
    private val settings = InMemorySettingsRepository()
    private val repo = InMemoryFocusRepository()
    private val noAlarms = object : FocusAlarms {
        override fun schedule(startMillis: Long, endMillis: Long) = Unit
        override fun cancel() = Unit
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = FocusViewModel(settings, repo, InMemoryTaskRepository(), FocusSessions(settings, repo, noAlarms), zone = ZoneOffset.UTC)

    private suspend fun tags() = repo.sessions().first().sortedBy { it.id }.map { it.tag }

    @Test
    fun aFinishedSessionCanBeGivenANameThatIsNotInTheListYet() = runTest {
        repo.save(focusSession("a", start = 1_000, tag = null))
        vm().retag("a", "  Piano practice ")
        assertEquals(listOf("Piano practice"), tags())
    }

    @Test
    fun aNameAlreadyInUseTakesItsSpelling() = runTest {
        repo.save(focusSession("a", start = 1_000, tag = "Reading"))
        repo.save(focusSession("b", start = 2_000, tag = null))
        vm().retag("b", "reading")
        assertEquals(listOf("Reading", "Reading"), tags())
    }

    @Test
    fun aStarterNameIsAvailableFromTheStart() = runTest {
        repo.save(focusSession("a", start = 1_000, tag = null))
        vm().retag("a", "study")
        assertEquals(listOf("Study"), tags()) // the starter's spelling
    }

    @Test
    fun aBlankNameTakesTheTagOff() = runTest {
        repo.save(focusSession("a", start = 1_000, tag = "Reading"))
        vm().retag("a", "   ")
        assertEquals(listOf(null), tags())
        assertNull(repo.sessions().first().single().tag)
    }

    @Test
    fun renamingLeavesTheRestOfTheSessionAlone() = runTest {
        repo.save(focusSession("a", start = 1_000, planned = 45, actual = 20, task = "Chapter 2", tag = null))
        vm().retag("a", "Reading")
        val s = repo.sessions().first().single()
        assertEquals(listOf(45, 20, "Chapter 2", "Reading"), listOf(s.plannedMinutes, s.actualMinutes, s.taskTitle, s.tag))
    }

    @Test
    fun anUnknownSessionIsIgnored() = runTest {
        repo.save(focusSession("a", start = 1_000, tag = "Reading"))
        vm().retag("missing", "Piano")
        assertEquals(listOf("Reading"), tags())
    }
}
