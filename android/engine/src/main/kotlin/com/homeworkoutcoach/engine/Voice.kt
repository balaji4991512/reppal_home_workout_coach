package com.homeworkoutcoach.engine

/** What a spoken sentence to Wick asks for. */
sealed interface VoiceCommand {
    data class StartFocus(val minutes: Int, val tag: String?) : VoiceCommand
    data object Pause : VoiceCommand
    data object Resume : VoiceCommand
    data object End : VoiceCommand
    /** "Wick" was heard but nothing after it: listen a little longer for the command. */
    data object WakeOnly : VoiceCommand
    /** Heard, but not something Wick can do (or a length outside 1 to 180 minutes). */
    data object Unclear : VoiceCommand
}

/** Turns what the recogniser heard into a [VoiceCommand]. Pure; the app does the acting. */
object VoiceCommands {
    const val WAKE = "wick"
    const val DEFAULT_MINUTES = 25

    private val UNITS = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
    )
    private val TENS = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
        "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90,
    )

    /** The words besides tags and numbers that commands use; the recogniser listens for these only. */
    val words: List<String> = (
        listOf(WAKE) +
            "set a an the timer for focus start minute minutes min mins hour hours half and pause hold resume continue carry on unpause stop end finish cancel"
                .split(" ") +
            UNITS + TENS.keys + "hundred"
        ).distinct()

    private val RESUME = Regex("\\b(resume|continue|carry on|unpause)\\b")
    private val PAUSE = Regex("\\b(pause|hold)\\b")
    private val END = Regex("\\b(stop|end|finish|cancel)\\b")
    private val START = Regex("\\b(timer|focus|start|minutes?|mins?|hours?)\\b|\\d")

    /**
     * The command in [heard], or null when it holds no "Wick" (not meant for the app). Only what follows the first
     * "Wick" counts. [tags] are the focus tags that can be named.
     */
    fun parse(heard: String, tags: Collection<String>): VoiceCommand? {
        val tokens = clean(heard).split(" ").filter { it.isNotEmpty() }
        val at = tokens.indexOf(WAKE)
        if (at < 0) return null
        val rest = tokens.drop(at + 1)
        if (rest.isEmpty()) return VoiceCommand.WakeOnly
        val text = numbersToDigits(rest).joinToString(" ")
        return when {
            RESUME.containsMatchIn(text) -> VoiceCommand.Resume
            PAUSE.containsMatchIn(text) -> VoiceCommand.Pause
            END.containsMatchIn(text) -> VoiceCommand.End
            else -> {
                val tag = tagIn(text, tags)
                if (tag == null && !START.containsMatchIn(text) && !Regex("\\ban hour\\b").containsMatchIn(text)) {
                    return VoiceCommand.Unclear
                }
                val minutes = minutesIn(text) ?: DEFAULT_MINUTES
                if (Focus.problem(minutes) != null) VoiceCommand.Unclear else VoiceCommand.StartFocus(minutes, tag)
            }
        }
    }

    /** Lower case, punctuation and hyphens to spaces, "wick" spelt the way the recogniser may write it. */
    private fun clean(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9.\\s]"), " ").replace(Regex("(?<!\\d)\\.|\\.(?!\\d)"), " ")
            .replace(Regex("\\s+"), " ").trim()

    /** Runs of number words become digits: "twenty five" -> "25", "one hundred and twenty" -> "120". */
    internal fun numbersToDigits(tokens: List<String>): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            var value: Int? = null
            var j = i
            while (j < tokens.size) {
                val w = tokens[j]
                val v = value
                when {
                    w in UNITS && (v == null || (v % 100 == 0 && v >= 100) || (v % 10 == 0 && v % 100 >= 20 && UNITS.indexOf(w) in 1..9)) ->
                        value = (v ?: 0) + UNITS.indexOf(w)
                    w in TENS && (v == null || (v % 100 == 0 && v >= 100)) -> value = (v ?: 0) + TENS.getValue(w)
                    w == "a" && v == null && tokens.getOrNull(j + 1) == "hundred" -> value = 1
                    w == "hundred" && v != null && v in 1..9 -> value = v * 100
                    w == "and" && v != null && v >= 100 && v % 100 == 0 && j + 1 < tokens.size &&
                        (tokens[j + 1] in UNITS || tokens[j + 1] in TENS) -> Unit
                    else -> break
                }
                j++
            }
            if (value == null) {
                out += tokens[i]
                i++
            } else {
                out += value.toString()
                i = j
            }
        }
        return out
    }

    /** The length said, in minutes, or null when none was said. */
    internal fun minutesIn(text: String): Int? {
        var hours = 0.0
        var any = false
        var rest = text
        Regex("\\bhalf (an )?hour\\b").find(rest)?.let {
            hours += 0.5; any = true; rest = rest.replaceFirst(it.value, " ")
        }
        Regex("\\b(an|a|\\d+(?:\\.\\d+)?)( and a half)? hours?( and a half)?\\b").find(rest)?.let { m ->
            val n = m.groupValues[1].toDoubleOrNull() ?: 1.0
            hours += n + if (m.groupValues[2].isNotEmpty() || m.groupValues[3].isNotEmpty()) 0.5 else 0.0
            any = true
            rest = rest.replaceFirst(m.value, " ")
        }
        var minutes = 0
        val unit = Regex("\\b(\\d+) (minutes?|mins?)\\b").find(rest)
        if (unit != null) {
            minutes = unit.groupValues[1].toInt(); any = true
        } else if (!any) {
            Regex("\\b(\\d+)\\b").find(rest)?.let { minutes = it.groupValues[1].toInt(); any = true }
        }
        return if (any) (hours * 60).toInt() + minutes else null
    }

    /** The longest tag named in [text]; a word that starts with the tag counts ("studying" is Study). */
    internal fun tagIn(text: String, tags: Collection<String>): String? =
        tags.sortedByDescending { it.length }.firstOrNull { tag ->
            val t = clean(tag)
            t.isNotEmpty() && Regex("\\b" + Regex.escape(t) + if (t.length >= 4) "\\w*\\b" else "\\b").containsMatchIn(text)
        }
}
