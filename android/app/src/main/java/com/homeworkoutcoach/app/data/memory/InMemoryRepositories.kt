package com.homeworkoutcoach.app.data.memory

import android.app.Activity
import com.homeworkoutcoach.app.data.Account
import com.homeworkoutcoach.app.data.AuthRepository
import com.homeworkoutcoach.app.data.BodyRepository
import com.homeworkoutcoach.app.data.BodyWeight
import com.homeworkoutcoach.app.data.DeleteResult
import com.homeworkoutcoach.app.data.HabitRepository
import com.homeworkoutcoach.app.data.SettingsRepository
import com.homeworkoutcoach.app.data.SignInResult
import com.homeworkoutcoach.app.data.StoredHabit
import com.homeworkoutcoach.app.data.TrainingRepository
import com.homeworkoutcoach.app.data.UserSettings
import com.homeworkoutcoach.app.data.Workout
import com.homeworkoutcoach.engine.ExerciseState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/** In-memory fakes for tests and previews. They pass the same contract tests as the real stores. */
class InMemorySettingsRepository(initial: UserSettings = UserSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    override val settings: StateFlow<UserSettings> = state

    override suspend fun update(transform: (UserSettings) -> UserSettings) = state.update(transform)

    override suspend fun reset() {
        state.value = UserSettings()
    }
}

class InMemoryTrainingRepository : TrainingRepository {
    private val states = MutableStateFlow<Map<String, ExerciseState>>(emptyMap())
    private val swaps = MutableStateFlow<Map<String, String>>(emptyMap())
    private val workouts = MutableStateFlow<List<Workout>>(emptyList())
    private var nextId = 1L

    override fun exerciseStates(): Flow<Map<String, ExerciseState>> = states

    override suspend fun putExerciseStates(states: Map<String, ExerciseState>) = this.states.update { it + states }

    override suspend fun clearExerciseStates() {
        states.value = emptyMap()
    }

    override fun swaps(): Flow<Map<String, String>> = swaps

    override suspend fun setSwap(baseId: String, chosenId: String?) = swaps.update {
        if (chosenId == null || chosenId == baseId) it - baseId else it + (baseId to chosenId)
    }

    override fun workouts(): Flow<List<Workout>> = workouts

    override suspend fun saveWorkout(workout: Workout, nextStates: Map<String, ExerciseState>): Long {
        val saved = workout.copy(id = nextId++)
        workouts.update { list -> (list + saved).sortedWith(compareBy({ it.dateMillis }, { it.id })) }
        putExerciseStates(nextStates)
        return saved.id
    }

    override suspend fun wipe() {
        states.value = emptyMap()
        swaps.value = emptyMap()
        workouts.value = emptyList()
    }
}

/** Fake sign-in for tests and previews: [nextResult] is what the next sign-in returns. */
class InMemoryAuthRepository(initial: Account? = null) : AuthRepository {
    private val state = MutableStateFlow(initial)
    override val account: StateFlow<Account?> = state
    var nextResult: SignInResult = SignInResult.SignedIn(Account("asha@gmail.com", "Asha Rao"))

    override suspend fun signInWithGoogle(activity: Activity): SignInResult {
        val r = nextResult
        if (r is SignInResult.SignedIn) state.value = r.account
        return r
    }

    override suspend fun signOut() {
        state.value = null
    }

    /** What the next delete returns. */
    var nextDelete: DeleteResult = DeleteResult.Deleted

    override suspend fun deleteAccount(activity: Activity): DeleteResult {
        if (state.value == null) return DeleteResult.NotSignedIn
        val r = nextDelete
        if (r == DeleteResult.Deleted) state.value = null
        return r
    }
}

class InMemoryBodyRepository : BodyRepository {
    private val state = MutableStateFlow<List<BodyWeight>>(emptyList())
    private var nextId = 1L

    override fun weights(): Flow<List<BodyWeight>> = state

    override suspend fun add(entry: BodyWeight): Long {
        val saved = entry.copy(id = nextId++)
        state.update { list -> (list + saved).sortedWith(compareBy({ it.dateMillis }, { it.id })) }
        return saved.id
    }

    override suspend fun delete(id: Long) = state.update { list -> list.filterNot { it.id == id } }

    override suspend fun wipe() {
        state.value = emptyList()
    }
}

class InMemoryHabitRepository : HabitRepository {
    private val habits = MutableStateFlow<List<StoredHabit>>(emptyList())
    private val entries = MutableStateFlow<Map<String, Map<LocalDate, Int>>>(emptyMap())

    override fun habits(): Flow<List<StoredHabit>> = habits

    override fun entries(): Flow<Map<String, Map<LocalDate, Int>>> = entries

    override suspend fun save(habit: StoredHabit) = habits.update { list ->
        (list.filterNot { it.habit.id == habit.habit.id } + habit).sortedWith(compareBy({ it.position }, { it.habit.name }, { it.habit.id }))
    }

    override suspend fun delete(id: String) {
        habits.update { list -> list.filterNot { it.habit.id == id } }
        entries.update { it - id }
    }

    override suspend fun setAmount(habitId: String, date: LocalDate, amount: Int) = entries.update {
        it + (habitId to ((it[habitId] ?: emptyMap()) + (date to amount.coerceAtLeast(0))))
    }

    override suspend fun clearAmount(habitId: String, date: LocalDate) = entries.update {
        val left = (it[habitId] ?: emptyMap()) - date
        if (left.isEmpty()) it - habitId else it + (habitId to left)
    }

    override suspend fun wipe() {
        habits.value = emptyList()
        entries.value = emptyMap()
    }
}
