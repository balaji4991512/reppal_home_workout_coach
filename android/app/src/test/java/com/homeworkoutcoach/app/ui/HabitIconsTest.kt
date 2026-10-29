package com.homeworkoutcoach.app.ui

import com.homeworkoutcoach.app.ui.habits.HabitDraft
import com.homeworkoutcoach.app.ui.habits.HabitIcons
import com.homeworkoutcoach.app.ui.habits.HabitLook
import com.homeworkoutcoach.app.ui.habits.HabitStyles
import com.homeworkoutcoach.engine.Habits
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitIconsTest {
    @Test
    fun thereAreAtLeastAHundredDistinctIconsInKnownGroups() {
        val all = HabitIcons.all
        assertTrue(all.size >= 100)
        assertEquals(all.size, all.map { it.key }.toSet().size)
        assertTrue(all.all { it.emoji.isNotBlank() && it.color in HabitStyles.colors && it.group in HabitIcons.groups })
        // every group has something in it, and the groups together are every icon
        assertTrue(HabitIcons.groups.all { HabitIcons.inGroup(it).isNotEmpty() })
        assertEquals(all.size, HabitIcons.groups.sumOf { HabitIcons.inGroup(it).size })
    }

    @Test
    fun theIconsOfTheFirstVersionKeepTheirLook() {
        val old = mapOf("check" to "✅", "drop" to "💧", "moon" to "🌙", "sun" to "☀️", "book" to "📖", "pen" to "✍️", "dumbbell" to "🏋️", "walk" to "🚶", "lotus" to "🧘", "heart" to "❤️", "star" to "⭐", "list" to "📋", "bolt" to "⚡", "shower" to "🚿", "run" to "🏃", "apple" to "🍎", "sleep" to "😴", "tooth" to "🪥", "pill" to "💊", "broom" to "🧹", "brain" to "🧠", "money" to "💰", "music" to "🎵", "plant" to "🌱", "phone" to "📵")
        old.forEach { (key, emoji) -> assertEquals(key, emoji, HabitLook.emoji(key)) }
        assertEquals("✅", HabitLook.emoji("gone")) // an unknown key still draws something
    }

    @Test
    fun everySuggestionUsesAnIconThatExists() {
        Habits.suggestions.forEach { assertTrue("${it.name}: ${it.icon}", HabitIcons.find(it.icon) != null) }
    }

    @Test
    fun theQuickIconsAreFiveFollowingWhatIsTyped() {
        assertEquals(HabitIcons.starters, HabitIcons.quickFor("", HabitIcons.starters).map { it.key })
        val walk = HabitIcons.quickFor("Walk", HabitIcons.starters, 5)
        assertEquals(5, walk.size)
        assertEquals("walk", walk.first().key)
        assertEquals(5, walk.map { it.key }.toSet().size)
        // a word that has an icon of its own gets it ("morning swim")
        assertTrue(HabitIcons.quickFor("morning swim", HabitIcons.starters).any { it.key == "swim" })
        assertEquals(5, HabitIcons.quickFor("zzzz", HabitIcons.starters).size)
    }

    @Test
    fun aPickedSuggestionBringsItsLookAndKeepsTheRemindersAlreadySet() {
        val water = Habits.suggestions.first { it.name == "Water" }
        val d = HabitDraft.from(water)
        assertEquals("drop", d.icon)
        assertEquals("blue", d.color)
        assertTrue(Habits.suggestions.all { HabitDraft.from(it).color in HabitStyles.colors })
        val times = listOf(LocalTime.of(9, 0), LocalTime.of(15, 0))
        assertEquals(times, d.copy(name = "Water", reminders = times).toStored("id", java.time.LocalDate.of(2026, 10, 29), 0).habit.reminders)
        // the order and repeats are tidied when it is saved
        assertEquals(times, d.copy(reminders = listOf(times[1], times[0], times[1])).toStored("id", java.time.LocalDate.of(2026, 10, 29), 0).habit.reminders)
    }
}
