package com.homeworkoutcoach.app.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeworkoutcoach.app.data.JournalRepository
import com.homeworkoutcoach.app.ui.habits.todayFlow
import com.homeworkoutcoach.engine.Habits
import com.homeworkoutcoach.engine.Journal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

class JournalViewModel(
    repo: JournalRepository,
    private val actions: JournalActions,
    presenter: JournalPresenter = JournalPresenter(),
    private val clock: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    /** The day being written; null means today, so a screen left open past midnight moves on with the date. */
    private val selected = MutableStateFlow<LocalDate?>(null)

    /** Null until loaded. */
    val ui: StateFlow<JournalUi?> = combine(repo.entries(), selected, todayFlow(clock)) { entries, day, today ->
        presenter.present(entries, day ?: today, today)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val shown get() = selected.value ?: clock()

    fun previousDay() = selected.update { (it ?: clock()).minusDays(1) }

    /** The day after, but never past today: nothing can be written ahead. */
    fun nextDay() = selected.update { current -> ((current ?: clock()).plusDays(1)).takeIf { it <= clock() } ?: current }

    fun previousWeek() = selected.update { (it ?: clock()).minusWeeks(1) }

    /** A week on, but not into one that has not started: nothing is written ahead. */
    fun nextWeek() = selected.update { current ->
        val next = (current ?: clock()).plusWeeks(1)
        if (Habits.weekStart(next).isAfter(clock())) current else next.takeIf { it <= clock() } ?: clock()
    }

    /** Shows [date], never one past today. */
    fun select(date: LocalDate) {
        if (date.isAfter(clock())) return
        selected.value = date.takeUnless { it == clock() }
    }

    fun goToToday() {
        selected.value = null
    }

    /** Saves the day shown. Returns what to fix, or null if it was saved. */
    fun save(win: String, improve: String): String? {
        Journal.problem(win, improve)?.let { return it }
        val day = shown
        viewModelScope.launch { actions.save(day, win, improve) }
        return null
    }

    fun delete() {
        val day = shown
        viewModelScope.launch { actions.delete(day) }
    }
}
