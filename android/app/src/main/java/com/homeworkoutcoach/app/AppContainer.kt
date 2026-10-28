package com.homeworkoutcoach.app

import android.app.Application
import android.content.Context
import com.homeworkoutcoach.app.data.AuthRepository
import com.homeworkoutcoach.app.data.BodyRepository
import com.homeworkoutcoach.app.data.FocusRepository
import com.homeworkoutcoach.app.data.GoalRepository
import com.homeworkoutcoach.app.data.ReflectionRepository
import com.homeworkoutcoach.app.data.HabitRepository
import com.homeworkoutcoach.app.data.JournalRepository
import com.homeworkoutcoach.app.data.RoutineRepository
import com.homeworkoutcoach.app.data.TaskRepository
import com.homeworkoutcoach.app.focus.AndroidFocusAlarms
import com.homeworkoutcoach.app.focus.FocusSessions
import com.homeworkoutcoach.app.data.DataExporter
import com.homeworkoutcoach.app.data.DataImporter
import com.homeworkoutcoach.app.data.DataStoreSettingsRepository
import com.homeworkoutcoach.app.data.FirebaseAuthRepository
import com.homeworkoutcoach.app.data.SettingsRepository
import com.homeworkoutcoach.app.data.TrainingRepository
import com.homeworkoutcoach.app.data.room.RoomBodyRepository
import com.homeworkoutcoach.app.data.room.RoomFocusRepository
import com.homeworkoutcoach.app.data.room.RoomGoalRepository
import com.homeworkoutcoach.app.data.room.RoomReflectionRepository
import com.homeworkoutcoach.app.data.room.RoomHabitRepository
import com.homeworkoutcoach.app.data.room.RoomJournalRepository
import com.homeworkoutcoach.app.data.room.RoomRoutineRepository
import com.homeworkoutcoach.app.data.room.RoomTaskRepository
import com.homeworkoutcoach.app.data.room.RoomTrainingRepository
import com.homeworkoutcoach.app.data.room.TrainingDatabase
import com.homeworkoutcoach.app.plan.PlanActions
import com.homeworkoutcoach.engine.ConcernMapper
import com.homeworkoutcoach.engine.KeywordConcernMapper
import com.homeworkoutcoach.engine.Library
import com.homeworkoutcoach.engine.Planner

/** App-wide singletons, built once. Plain constructor wiring; no DI framework needed at this size. */
class AppContainer(private val context: Context) {
    val library: Library by lazy { Library.default }
    val planner: Planner by lazy { Planner(library) }
    val settings: SettingsRepository by lazy { DataStoreSettingsRepository.create(context) }
    private val database: TrainingDatabase by lazy { TrainingDatabase.create(context) }
    val training: TrainingRepository by lazy { RoomTrainingRepository(database) }
    val body: BodyRepository by lazy { RoomBodyRepository(database) }
    val habits: HabitRepository by lazy { RoomHabitRepository(database) }
    val tasks: TaskRepository by lazy { RoomTaskRepository(database) }
    val routines: RoutineRepository by lazy { RoomRoutineRepository(database) }
    val journal: JournalRepository by lazy { RoomJournalRepository(database) }
    val goals: GoalRepository by lazy { RoomGoalRepository(database) }
    val focus: FocusRepository by lazy { RoomFocusRepository(database) }
    val reflections: ReflectionRepository by lazy { RoomReflectionRepository(database) }
    val focusSessions: FocusSessions by lazy { FocusSessions(settings, focus, AndroidFocusAlarms(context)) }
    val concerns: ConcernMapper by lazy { KeywordConcernMapper(library) }
    val actions: PlanActions by lazy { PlanActions(settings, training, concerns, body, habits, tasks, routines, journal, goals, focus, reflections) }
    val auth: AuthRepository by lazy { FirebaseAuthRepository(context) }
    val exporter: DataExporter by lazy { DataExporter(library, settings, training, body, auth, habits = habits, tasks = tasks, routines = routines, journal = journal, goals = goals, focus = focus, reflections = reflections) }
    val importer: DataImporter by lazy { DataImporter(settings, training, body, habits, tasks, routines, journal, goals, focus, reflections) }
}

class HomeWorkoutCoachApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
