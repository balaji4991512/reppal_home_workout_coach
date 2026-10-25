package com.homeworkoutcoach.app.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homeworkoutcoach.app.plan.SetupDraft
import com.homeworkoutcoach.app.ui.Copy
import com.homeworkoutcoach.app.ui.Factories
import com.homeworkoutcoach.app.ui.SetupViewModel
import com.homeworkoutcoach.app.ui.components.Body
import com.homeworkoutcoach.app.ui.components.Chip
import com.homeworkoutcoach.app.ui.components.Chips
import com.homeworkoutcoach.app.ui.components.Field
import com.homeworkoutcoach.app.ui.components.H1
import com.homeworkoutcoach.app.ui.components.H2
import com.homeworkoutcoach.app.ui.components.Hairline
import com.homeworkoutcoach.app.ui.components.Logo
import com.homeworkoutcoach.app.ui.components.Muted
import com.homeworkoutcoach.app.ui.components.Page
import com.homeworkoutcoach.app.ui.components.PrimaryButton
import com.homeworkoutcoach.app.ui.theme.AppTheme
import com.homeworkoutcoach.engine.Bench
import com.homeworkoutcoach.engine.Library
import com.homeworkoutcoach.engine.Muscle
import com.homeworkoutcoach.engine.Split

/** Setup / "Equipment and plan" [vSetup]. Edits a draft; the button saves it and builds a new week. */
@Composable
fun SetupScreen(appName: String, onBuilt: () -> Unit, vm: SetupViewModel = viewModel(factory = Factories.Setup)) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val d = draft ?: return
    SetupContent(
        appName = appName,
        editing = vm.editing,
        draft = d,
        leftOut = vm.leftOutFor(d.concern),
        onEdit = vm::edit,
        onBuild = { vm.build(onBuilt) },
    )
}

@Composable
fun SetupContent(
    appName: String,
    editing: Boolean,
    draft: SetupDraft,
    leftOut: List<String>,
    onEdit: ((SetupDraft) -> SetupDraft) -> Unit,
    onBuild: () -> Unit,
) {
    val lib = Library.default
    Page {
        if (editing) {
            H1("Edit your plan")
            Muted("Your history and progress are kept. Changing equipment or days builds a new week.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Logo(64.dp)
                H1(appName)
                Body("A workout plan built from your own equipment, adjusted after every set.", color = AppTheme.colors.mut)
            }
        }

        H2("Your name")
        Field(draft.name, { v -> onEdit { it.copy(name = v) } }, placeholder = "Optional", label = "Your name")

        H2("Bench")
        Chips {
            listOf(Bench.NONE to "No bench", Bench.FLAT to "Flat bench", Bench.ADJUSTABLE to "Adjustable bench").forEach { (b, label) ->
                Chip(label, draft.bench == b, { onEdit { it.copy(bench = b) } })
            }
        }

        H2("Dumbbells (kg)")
        Chips {
            lib.dumbbellStepsKg.forEach { w ->
                val owned = w in draft.dumbbellsKg
                Chip(Copy.kg(w), owned, {
                    onEdit { it.copy(dumbbellsKg = if (owned) it.dumbbellsKg - w else it.dumbbellsKg + w) }
                }, Modifier.widthIn(min = 52.dp))
            }
        }
        Chips {
            Chip("Just one 5 kg pair", false, { onEdit { it.copy(dumbbellsKg = setOf(5.0)) } })
            Chip("Full 1 to 24 kg range", false, { onEdit { it.copy(dumbbellsKg = lib.dumbbellStepsKg.toSet()) } })
        }

        H2("Your training so far")
        Chips {
            Copy.EXPERIENCE.forEachIndexed { i, (label, level) ->
                Chip(label, draft.experienceOption == i, { onEdit { it.copy(experienceOption = i, level = level) } })
            }
        }

        H2("Anything to work around?")
        Muted("Tell me about injuries, pain, or exercises you can’t do, in your own words. The plan leaves them out.")
        Field(
            draft.concern,
            { v -> onEdit { it.copy(concern = v) } },
            placeholder = "For example: lower back issues, can’t do deadlifts",
            label = "Anything to work around",
            minLines = 3,
        )
        if (leftOut.isNotEmpty()) Muted("This leaves out: ${leftOut.joinToString(", ")}.")

        H2("Days per week")
        Chips {
            (1..7).forEach { n ->
                Chip(Copy.count(n, "day"), draft.daysPerWeek == n, { onEdit { it.copy(daysPerWeek = n) } })
            }
        }

        H2("Training split")
        Chips {
            Copy.SPLITS.forEach { (split, label) ->
                Chip(label, draft.split == split, { onEdit { it.copy(split = split) } })
            }
        }
        Muted(Copy.splitNote(draft.split, draft.daysPerWeek))
        if (draft.split == Split.CUSTOM) CustomDays(draft, lib, onEdit)

        PrimaryButton(if (editing) "Save and rebuild my week" else "Build my plan", onBuild)
    }
}

/** Per training day, tick muscles; then a coverage hint. */
@Composable
private fun CustomDays(draft: SetupDraft, lib: Library, onEdit: ((SetupDraft) -> SetupDraft) -> Unit) {
    val weekdays = lib.workoutDayIndexes.getValue(draft.daysPerWeek)
    repeat(draft.daysPerWeek) { i ->
        Hairline(Modifier.padding(top = 6.dp))
        Body(lib.dayNames[weekdays[i]], Modifier.padding(top = 10.dp), strong = true)
        Chips {
            Muscle.entries.forEach { m ->
                val on = m in draft.customDays[i]
                Chip(lib.muscleLabels.getValue(m), on, small = true, onClick = {
                    onEdit { d ->
                        d.copy(customDays = d.customDays.mapIndexed { j, day ->
                            if (j != i) day else if (on) day - m else day + m
                        })
                    }
                })
            }
        }
    }
    val counts = Muscle.entries.associateWith { m -> (0 until draft.daysPerWeek).count { m in draft.customDays[it] } }
    val none = counts.filterValues { it == 0 }.keys.map { lib.muscleLabels.getValue(it).lowercase() }
    val once = counts.filterValues { it == 1 }.keys.map { lib.muscleLabels.getValue(it).lowercase() }
    val text = (if (none.isNotEmpty()) "Not trained this week: ${none.joinToString(", ")}. " else "Every major muscle is trained. ") +
        (if (once.isNotEmpty() && draft.daysPerWeek > 1) "Trained once: ${once.joinToString(", ")}. Twice a week usually works better for beginners." else "")
    Muted(text.trim())
}
