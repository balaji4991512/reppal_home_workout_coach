package com.homeworkoutcoach.engine

/** Raw parse of a spoken or typed set note. Reps may be missing; [validate] turns it into a [SetRecord]. */
data class ParsedSet(
    val reps: Int?,
    val weightKg: Double?,
    val effort: Effort,
    val flags: Set<SetFlag>,
    val comment: String,
)

sealed interface SetCheck {
    data class Ok(val record: SetRecord) : SetCheck
    /** No rep count found, or outside 1..30. */
    data object NoReps : SetCheck
    data class WeightNotOwned(val kg: Double) : SetCheck
}

/** Text -> set. Later an on-device LLM implements this too, validated, falling back to [KeywordSetParser]. */
fun interface SetParser {
    fun parse(text: String): ParsedSet
}

/** Text -> excluded exercise ids. Same pattern as [SetParser]. */
fun interface ConcernMapper {
    fun excludedIds(text: String): Set<String>
}

/** Keyword parser [parse]. */
object KeywordSetParser : SetParser {
    private val NUMBER_WORDS = listOf(
        "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen",
    )
    private val NUMBER_WORD = Regex("\\b(" + NUMBER_WORDS.joinToString("|") + ")\\b")
    private val WEIGHT = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:kg|kilo)")
    private val DIGITS = Regex("\\d+")
    private val EASY = Regex("easy|light|breez")
    private val HARD = Regex("hard|tough|struggl|heavy|couldn|failed")
    private val PAIN = Regex("pain|hurt|sharp|pinch|ache|twinge|tweak")
    private val NOT_COMMENT = Regex(
        "\\d|easy|hard|tough|just right|^pain$|reps?|kg|\\b(" + NUMBER_WORDS.joinToString("|") + ")\\b",
        RegexOption.IGNORE_CASE,
    )

    override fun parse(text: String): ParsedSet {
        var x = NUMBER_WORD.replace(text.lowercase()) { (NUMBER_WORDS.indexOf(it.value) + 1).toString() }
        val wm = WEIGHT.find(x)
        val weight = wm?.groupValues?.get(1)?.toDouble()
        if (wm != null) x = x.replaceFirst(wm.value, " ")
        val reps = DIGITS.find(x)?.value?.toIntOrNull()
        val effort = when {
            EASY.containsMatchIn(x) -> Effort.EASY
            HARD.containsMatchIn(x) -> Effort.HARD
            else -> Effort.JUST_RIGHT
        }
        val comment = text.split(',', ';', '.').map { it.trim() }
            .filter { it.isNotEmpty() && !NOT_COMMENT.containsMatchIn(it) }
            .joinToString(", ")
        val flags = if (PAIN.containsMatchIn(x)) setOf(SetFlag.PAIN) else emptySet()
        return ParsedSet(reps, weight, effort, flags, comment)
    }

    /** Check a parse against the current exercise [read]. Weighted sets default to the live weight. */
    fun validate(parsed: ParsedSet, exercise: Exercise, equipment: Equipment, live: LiveTarget): SetCheck {
        val reps = parsed.reps
        if (reps == null || reps < 1 || reps > 30) return SetCheck.NoReps
        val weight = if (exercise.bodyweight) {
            null
        } else {
            if (parsed.weightKg != null && !equipment.owns(parsed.weightKg)) return SetCheck.WeightNotOwned(parsed.weightKg)
            parsed.weightKg ?: live.weightKg
        }
        return SetCheck.Ok(SetRecord(reps, weight, parsed.effort, parsed.flags, parsed.comment))
    }
}

/** Keyword table from plan_tables.json [derive]. Kept as the offline fallback once an LLM mapper exists. */
class KeywordConcernMapper(private val lib: Library = Library.default) : ConcernMapper {
    override fun excludedIds(text: String): Set<String> {
        val t = text.lowercase()
        return lib.concernKeywords.filter { it.value.containsMatchIn(t) }.keys
    }
}

/** Training level from free text about experience [lvl]. */
object LevelFromText {
    private val YEARS = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:years?|yrs?)")
    private val MONTHS = Regex("(\\d+(?:\\.\\d+)?)\\s*months?")
    private val RETURNING = Regex("break|stopped|haven.?t|returning|restart|never|first time|just start|beginner")
    private val NAMED = Regex("advanced|intermediate")

    fun parse(text: String): Level {
        val t = text.lowercase()
        val months = YEARS.find(t)?.let { it.groupValues[1].toDouble() * 12 }
            ?: MONTHS.find(t)?.groupValues?.get(1)?.toDouble()
        if (RETURNING.containsMatchIn(t) || (months == null && !NAMED.containsMatchIn(t))) return Level.BEGINNER
        if ("advanced" in t || (months != null && months >= 24)) return Level.ADVANCED
        return if ("intermediate" in t || (months != null && months >= 6)) Level.INTERMEDIATE else Level.BEGINNER
    }
}
