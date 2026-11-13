package com.homeworkoutcoach.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTest {
    private val tags = Focus.starterTags + "Deep Work"

    private fun parse(s: String) = VoiceCommands.parse(s, tags)
    private fun start(minutes: Int, tag: String? = null) = VoiceCommand.StartFocus(minutes, tag)

    @Test
    fun theAskedForSentenceStartsTwentyFiveMinutesOfStudy() {
        assertEquals(start(25, "Study"), parse("Wick, set a timer for 25 minutes for study"))
        assertEquals(start(25, "Study"), parse("wick set a timer for twenty five minutes for study"))
    }

    @Test
    fun nothingWithoutTheWakeWord() {
        assertNull(parse("set a timer for 25 minutes"))
        assertNull(parse(""))
    }

    @Test
    fun onlyWhatFollowsTheWakeWordCounts() {
        assertEquals(start(45), parse("stop it wick focus forty five"))
    }

    @Test
    fun theWakeWordAloneWaitsForTheCommand() {
        assertEquals(VoiceCommand.WakeOnly, parse("wick"))
        assertEquals(VoiceCommand.WakeOnly, parse("  Wick. "))
    }

    @Test
    fun lengthsInMinutesWordsAndBareNumbers() {
        assertEquals(start(45), parse("wick focus 45"))
        assertEquals(start(15), parse("wick start fifteen minutes"))
        assertEquals(start(60), parse("wick focus sixty"))
        assertEquals(start(10), parse("wick timer ten mins"))
    }

    @Test
    fun lengthsInHours() {
        assertEquals(start(60, "Study"), parse("wick study for an hour"))
        assertEquals(start(120), parse("wick focus two hours"))
        assertEquals(start(90), parse("wick focus an hour and a half"))
        assertEquals(start(90), parse("wick focus one and a half hours"))
        assertEquals(start(30), parse("wick focus half an hour"))
        assertEquals(start(90), parse("wick focus one hour thirty minutes"))
        assertEquals(start(90), parse("wick focus 1.5 hours"))
    }

    @Test
    fun noLengthMeansTwentyFive() {
        assertEquals(start(25), parse("wick start focus"))
        assertEquals(start(25, "Reading"), parse("wick reading"))
    }

    @Test
    fun aLengthOutsideOneToOneEightyIsNotUnderstood() {
        assertEquals(VoiceCommand.Unclear, parse("wick focus four hours"))
        assertEquals(VoiceCommand.Unclear, parse("wick focus zero minutes"))
        assertEquals(start(180), parse("wick focus one hundred and eighty minutes"))
    }

    @Test
    fun tagsMatchAnywhereWhateverTheEnding() {
        assertEquals(start(25, "Study"), parse("wick twenty five minutes studying"))
        assertEquals(start(40, "Coding"), parse("wick coding forty minutes"))
        assertEquals(start(25, "Deep Work"), parse("wick deep work"))
        assertEquals(start(25, "Work"), parse("wick work"))
    }

    @Test
    fun pauseResumeAndEnd() {
        assertEquals(VoiceCommand.Pause, parse("wick pause"))
        assertEquals(VoiceCommand.Pause, parse("wick pause the timer"))
        assertEquals(VoiceCommand.Resume, parse("wick resume"))
        assertEquals(VoiceCommand.Resume, parse("wick continue"))
        assertEquals(VoiceCommand.Resume, parse("wick carry on"))
        assertEquals(VoiceCommand.Resume, parse("wick unpause"))
        assertEquals(VoiceCommand.End, parse("wick stop"))
        assertEquals(VoiceCommand.End, parse("wick end the timer"))
        assertEquals(VoiceCommand.End, parse("wick cancel"))
    }

    @Test
    fun anythingElseIsNotUnderstood() {
        assertEquals(VoiceCommand.Unclear, parse("wick hello there"))
        assertEquals(VoiceCommand.Unclear, parse("wick the"))
    }

    @Test
    fun numberWordsBecomeDigits() {
        fun d(s: String) = VoiceCommands.numbersToDigits(s.split(" ")).joinToString(" ")
        assertEquals("25", d("twenty five"))
        assertEquals("120", d("one hundred twenty"))
        assertEquals("125", d("one hundred and twenty five"))
        assertEquals("1 and a half", d("one and a half"))
        assertEquals("15 20", d("fifteen twenty"))
        assertEquals("100", d("a hundred"))
        assertEquals("a", d("a"))
    }

    @Test
    fun theWordListHoldsTheWakeWordAndCommands() {
        val w = VoiceCommands.words
        assertTrue(listOf("wick", "pause", "resume", "minutes", "twenty", "five", "hour").all { it in w })
        assertEquals(w.size, w.distinct().size)
    }
}
