package com.homeworkoutcoach.app.ui

import androidx.activity.ComponentActivity
import com.homeworkoutcoach.app.data.Account
import com.homeworkoutcoach.app.data.BodyWeight
import com.homeworkoutcoach.app.data.DataExporter
import com.homeworkoutcoach.app.data.DataImporter
import com.homeworkoutcoach.app.data.DeleteResult
import com.homeworkoutcoach.app.data.SignInResult
import com.homeworkoutcoach.app.data.UserSettings
import com.homeworkoutcoach.app.data.WeightSource
import com.homeworkoutcoach.app.data.memory.InMemoryAuthRepository
import com.homeworkoutcoach.app.data.memory.InMemoryBodyRepository
import com.homeworkoutcoach.app.data.memory.InMemorySettingsRepository
import com.homeworkoutcoach.app.data.memory.InMemoryTrainingRepository
import com.homeworkoutcoach.app.plan.PlanActions
import com.homeworkoutcoach.app.ui.profile.ProfilePresenter
import com.homeworkoutcoach.engine.ExerciseState
import com.homeworkoutcoach.engine.KeywordConcernMapper
import com.homeworkoutcoach.engine.Library
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SignInTest {
    private val settings = InMemorySettingsRepository()
    private val auth = InMemoryAuthRepository()
    private val actions = PlanActions(settings, InMemoryTrainingRepository(), KeywordConcernMapper(Library.default))
    private val activity by lazy { Robolectric.buildActivity(ComponentActivity::class.java).get() }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun signInUsesFirstNameUnlessOneWasTyped() = runTest {
        val vm = AppViewModel(settings, actions, auth)
        var done = false
        vm.signInWithGoogle(activity) { done = true }
        assertTrue(done)
        assertEquals(Account("asha@gmail.com", "Asha Rao"), vm.account.value)
        assertEquals("Asha", settings.settings.first().name)
        assertEquals(SignInUi(), vm.signIn.value)

        vm.signOut()
        assertNull(vm.account.value)
        assertFalse(settings.settings.first().guest)

        settings.update { it.copy(name = "Ash") }
        vm.signInWithGoogle(activity)
        assertEquals("Ash", settings.settings.first().name)
    }

    @Test
    fun problemsShowAPlainMessageAndCancelShowsNothing() {
        val vm = AppViewModel(settings, actions, auth)
        auth.nextResult = SignInResult.Cancelled
        vm.signInWithGoogle(activity)
        assertEquals(SignInUi(), vm.signIn.value)

        auth.nextResult = SignInResult.NoAccount
        vm.signInWithGoogle(activity)
        assertEquals("No Google account on this phone. Add one in Settings, or try without an account.", vm.signIn.value.error)

        auth.nextResult = SignInResult.Failed("network")
        vm.signInWithGoogle(activity)
        assertEquals("Couldn’t sign in with Google. Check your connection and try again.", vm.signIn.value.error)
        assertNull(vm.account.value)
    }

    private fun profileVm(training: InMemoryTrainingRepository, body: InMemoryBodyRepository) =
        ProfileViewModel(
            settings, training, auth, ProfilePresenter(Library.default),
            PlanActions(settings, training, KeywordConcernMapper(Library.default), body),
            DataExporter(Library.default, settings, training, body, auth),
            DataImporter(settings, training, body),
            Library.default, body,
        )

    @Test
    fun deletingTheAccountErasesThePhoneAndReturnsToSignIn() = runTest {
        val training = InMemoryTrainingRepository()
        val body = InMemoryBodyRepository()
        val app = AppViewModel(settings, actions, auth)
        app.signInWithGoogle(activity)
        training.putExerciseStates(mapOf("bench" to ExerciseState(8.0)))
        body.add(BodyWeight(dateMillis = 1, weightKg = 84.5, source = WeightSource.SCALE))

        // cancelled at Google's confirm step: nothing changes
        auth.nextDelete = DeleteResult.Cancelled
        val vm = profileVm(training, body)
        vm.deleteAccount(activity)
        assertEquals(DeleteUi(), vm.delete.value)
        assertEquals("asha@gmail.com", auth.account.value?.email)
        assertEquals(1, body.weights().first().size)

        // failed: a message, nothing erased
        auth.nextDelete = DeleteResult.Failed("network")
        vm.deleteAccount(activity)
        assertEquals("Couldn’t delete your account. Check your connection and try again.", vm.delete.value.error)
        assertEquals(1, body.weights().first().size)

        // deleted: signed out, everything on the phone erased, back to the sign-in screen
        auth.nextDelete = DeleteResult.Deleted
        vm.deleteAccount(activity)
        assertNull(auth.account.value)
        assertTrue(training.exerciseStates().first().isEmpty())
        assertTrue(body.weights().first().isEmpty())
        assertEquals(UserSettings(), settings.settings.first())
    }

    @Test
    fun profileShowsTheAccountEmail() {
        val ui = ProfilePresenter(Library.default).present(UserSettings(), emptyMap(), emptyList(), Account("asha@gmail.com", null))
        assertEquals("Account" to "asha@gmail.com", ui.rows.first())
    }
}
