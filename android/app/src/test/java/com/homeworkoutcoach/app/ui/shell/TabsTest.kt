package com.homeworkoutcoach.app.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bottom tabs and the pages each hub lists. */
class TabsTest {
    private val pages = Tab.entries.flatMap { t -> t.pages.map { t to it } }

    @Test
    fun thereAreFiveTabsWithTodayFirst() {
        assertEquals(listOf("Today", "Train", "Plan", "Reflect", "Me"), Tab.entries.map { it.label })
        assertTrue(Tab.Today.pages.isEmpty()) // Today is home, not a hub
    }

    @Test
    fun everyMenuPageIsOnExactlyOneHub() {
        val routes = pages.map { it.second.route }
        assertEquals(routes.size, routes.toSet().size) // no page twice
        Dest.entries.filter { it != Dest.Today }.forEach { d ->
            assertTrue("${d.route} is on no hub", d.route in routes)
        }
        assertTrue("privacy" in routes)
    }

    @Test
    fun theGroupsAreTheAgreedOnes() {
        assertEquals(listOf("Week", "Workout", "Body"), Tab.Train.pages.map { it.label })
        assertEquals(listOf("Habits", "Routines", "Planner", "Focus"), Tab.Plan.pages.map { it.label })
        assertEquals(listOf("Progress", "Goals", "Journal", "Character"), Tab.Reflect.pages.map { it.label })
        assertEquals(listOf("Profile", "Plan settings", "How it works", "Privacy"), Tab.Me.pages.map { it.label })
    }

    @Test
    fun aPageHighlightsItsOwnTab() {
        pages.forEach { (tab, page) -> assertEquals("${page.route}", tab, Tab.of(page.route)) }
        Tab.entries.forEach { assertEquals(it, Tab.of(it.root)) }
    }

    @Test
    fun detailScreensBelongToTheTabTheyWereOpenedFrom() {
        listOf("habit/{id}", "habit/{id}/edit", "habit-new", "routine/{id}/run", "routine/{id}/edit", "routine-new", "task/{id}")
            .forEach { assertEquals(it, Tab.Plan, Tab.of(it)) }
        listOf("goal/{id}", "goal-new", "exercise/{id}").forEach { assertEquals(it, Tab.Reflect, Tab.of(it)) }
        assertEquals(Tab.Train, Tab.of("arrange"))
        assertNull(Tab.of(null))
        assertNull(Tab.of("somewhere/else"))
    }

    @Test
    fun onlyHubPagesGetTheTopBarBackArrow() {
        assertTrue(Tab.isHubPage("habits"))
        assertTrue(Tab.isHubPage("progress"))
        assertFalse(Tab.isHubPage("habit/{id}")) // has its own back arrow
        assertFalse(Tab.isHubPage("today"))
        assertFalse(Tab.isHubPage("hub/plan"))
        assertFalse(Tab.isHubPage(null))
    }

    @Test
    fun theWorkoutPagesWaitForThePlan() {
        assertEquals(setOf("week", "workout", "body", "profile"), pages.filter { it.second.needsPlan }.map { it.second.route }.toSet())
    }
}
