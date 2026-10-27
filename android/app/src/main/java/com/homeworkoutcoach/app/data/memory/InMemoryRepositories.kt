package com.homeworkoutcoach.app.data.memory

import android.app.Activity
import com.homeworkoutcoach.app.data.Account
import com.homeworkoutcoach.app.data.AuthRepository
import com.homeworkoutcoach.app.data.SettingsRepository
import com.homeworkoutcoach.app.data.SignInResult
import com.homeworkoutcoach.app.data.TrainingRepository
import com.homeworkoutcoach.app.data.UserSettings
import com.homeworkoutcoach.app.data.Workout
import com.homeworkoutcoach.engine.ExerciseState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

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
}
