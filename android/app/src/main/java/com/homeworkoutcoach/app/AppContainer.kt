package com.homeworkoutcoach.app

import android.app.Application
import android.content.Context
import com.homeworkoutcoach.app.data.AuthRepository
import com.homeworkoutcoach.app.data.DataStoreSettingsRepository
import com.homeworkoutcoach.app.data.FirebaseAuthRepository
import com.homeworkoutcoach.app.data.SettingsRepository
import com.homeworkoutcoach.app.data.TrainingRepository
import com.homeworkoutcoach.app.data.room.RoomTrainingRepository
import com.homeworkoutcoach.app.data.room.TrainingDatabase
import com.homeworkoutcoach.app.plan.PlanActions
import com.homeworkoutcoach.engine.ConcernMapper
import com.homeworkoutcoach.engine.KeywordConcernMapper
import com.homeworkoutcoach.engine.Library
import com.homeworkoutcoach.engine.Planner

/** App-wide singletons, built once. Plain constructor wiring; no DI framework needed at this size. */
class AppContainer(context: Context) {
    val library: Library by lazy { Library.default }
    val planner: Planner by lazy { Planner(library) }
    val settings: SettingsRepository by lazy { DataStoreSettingsRepository.create(context) }
    val training: TrainingRepository by lazy { RoomTrainingRepository(TrainingDatabase.create(context)) }
    val concerns: ConcernMapper by lazy { KeywordConcernMapper(library) }
    val actions: PlanActions by lazy { PlanActions(settings, training, concerns) }
    val auth: AuthRepository by lazy { FirebaseAuthRepository(context) }
}

class HomeWorkoutCoachApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
