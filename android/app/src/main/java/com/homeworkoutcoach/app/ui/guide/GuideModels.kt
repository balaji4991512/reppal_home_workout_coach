package com.homeworkoutcoach.app.ui.guide

import androidx.lifecycle.ViewModel
import com.homeworkoutcoach.engine.Guides
import com.homeworkoutcoach.engine.Library
import com.homeworkoutcoach.engine.Motion
import com.homeworkoutcoach.engine.Motions
import com.homeworkoutcoach.engine.Muscle
import com.homeworkoutcoach.engine.Need

/** How to do one exercise: the drawn motion, the muscles it works, the steps and what to watch out for. */
data class GuideUi(
    val id: String,
    val name: String,
    val motion: Motion?,
    val primary: Set<Muscle>,
    val secondary: Set<Muscle>,
    /** "Chest". */
    val main: String,
    /** "Triceps, shoulders", or null when nothing else helps. */
    val also: String?,
    /** "Dumbbells and a flat bench", or "Just your body". */
    val needs: String,
    val steps: List<String>,
    val tips: List<String>,
)

class GuidePresenter(private val lib: Library) {
    /** Null for an exercise the library doesn't have. */
    fun present(id: String): GuideUi? {
        val e = lib.exercises[id] ?: return null
        val guide = Guides.of(id)
        fun label(m: Muscle) = lib.muscleLabels[m] ?: m.short
        val also = e.secondary.map(::label)
        return GuideUi(
            id = id,
            name = e.name,
            motion = Motions.of(id),
            primary = e.primary.toSet(),
            secondary = e.secondary.toSet(),
            main = e.primary.joinToString(", ", transform = ::label),
            also = also.takeIf { it.isNotEmpty() }?.mapIndexed { i, s -> if (i == 0) s else s.lowercase() }?.joinToString(", "),
            needs = needs(e.needs),
            steps = guide?.steps.orEmpty(),
            tips = guide?.tips.orEmpty(),
        )
    }

    private fun needs(n: Set<Need>): String {
        val bench = when {
            Need.ADJUSTABLE_BENCH in n -> "an adjustable bench"
            Need.BENCH in n -> "a bench"
            else -> null
        }
        return when {
            Need.DUMBBELL in n && bench != null -> "Dumbbells and $bench"
            Need.DUMBBELL in n -> "Dumbbells"
            bench != null -> bench.replaceFirstChar { it.uppercase() }
            else -> "Just your body"
        }
    }
}

class GuideViewModel(lib: Library, id: String) : ViewModel() {
    val ui: GuideUi? = GuidePresenter(lib).present(id)
}
