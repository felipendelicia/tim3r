package com.felipe.tim3r

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.felipe.tim3r.data.AppSettings
import com.felipe.tim3r.data.DashboardSummary
import com.felipe.tim3r.data.SettingsStore
import com.felipe.tim3r.data.SectionResult
import com.felipe.tim3r.data.WorkoutDefinition
import com.felipe.tim3r.data.WorkoutHistoryEntry
import com.felipe.tim3r.data.WorkoutOrigin
import com.felipe.tim3r.data.WorkoutSection
import com.felipe.tim3r.data.WorkoutSectionType
import com.felipe.tim3r.data.WorkoutStore
import com.felipe.tim3r.data.defaultSection
import com.felipe.tim3r.domain.MixBlock
import com.felipe.tim3r.domain.SessionStatus
import com.felipe.tim3r.domain.TimerConfig
import com.felipe.tim3r.domain.TimerEngine
import com.felipe.tim3r.domain.TimerSnapshot
import com.felipe.tim3r.domain.WorkoutMode
import com.felipe.tim3r.importer.RoutinePdfImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class AppScreen {
    HOME,
    CONFIG,
    TIMER,
    WORKOUTS,
    WORKOUT_DETAIL,
    WORKOUT_EDITOR,
    WOD_RUNNER,
    PROGRESS,
    SETTINGS,
}

enum class ConfigField { TOTAL, WORK, REST, ROUNDS }

data class WodRunnerState(
    val workoutId: String,
    val currentSectionIndex: Int = 0,
    val sessionElapsedSeconds: Int = 0,
    val sectionElapsedSeconds: Int = 0,
    val paused: Boolean = false,
    val completed: Boolean = false,
    val results: List<SectionResult> = emptyList(),
)

private val defaultMixBlocks = listOf(
    MixBlock(WorkoutMode.AMRAP, durationSeconds = 5 * 60, restSeconds = 60),
    MixBlock(WorkoutMode.EMOM, durationSeconds = 5 * 60, restSeconds = 60),
    MixBlock(WorkoutMode.FOR_TIME, durationSeconds = 5 * 60),
)

private val emptyDashboard = DashboardSummary(0, 0, 0, 0, emptyMap(), List(7) { 0 })

data class Tim3rUiState(
    val screen: AppScreen = AppScreen.HOME,
    val mode: WorkoutMode = WorkoutMode.AMRAP,
    val totalSeconds: Int = 12 * 60,
    val workSeconds: Int = 60,
    val restSeconds: Int = 10,
    val rounds: Int = 10,
    val hasTimeCap: Boolean = true,
    val mixBlocks: List<MixBlock> = defaultMixBlocks,
    val snapshot: TimerSnapshot? = null,
    val workouts: List<WorkoutDefinition> = emptyList(),
    val history: List<WorkoutHistoryEntry> = emptyList(),
    val dashboard: DashboardSummary = emptyDashboard,
    val selectedWorkoutId: String? = null,
    val workoutDraft: WorkoutDefinition? = null,
    val runner: WodRunnerState? = null,
    val importInProgress: Boolean = false,
    val message: String? = null,
    val settings: AppSettings = AppSettings(),
    val controlsLocked: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val store = WorkoutStore(application)
    private val settingsStore = SettingsStore(application)
    private val importer = RoutinePdfImporter(application)
    private val initialWorkouts = store.loadWorkouts()
    private val initialHistory = store.loadHistory().sortedByDescending { it.startedAtEpochMillis }

    private val _uiState = MutableStateFlow(
        Tim3rUiState(
            workouts = initialWorkouts,
            history = initialHistory,
            dashboard = WorkoutStore.dashboard(initialWorkouts, initialHistory),
            settings = settingsStore.load(),
        ),
    )
    val uiState: StateFlow<Tim3rUiState> = _uiState.asStateFlow()

    private var engine: TimerEngine? = null
    private var ticker: Job? = null
    private var quickStartedAtEpochMillis = 0L
    private var quickHistoryRecorded = false

    private var runnerTicker: Job? = null
    private var runnerStartedAtEpochMillis = 0L
    private var runnerStartedAtRealtime = 0L
    private var runnerPausedAtRealtime: Long? = null
    private var runnerPausedDurationMillis = 0L
    private var runnerSectionStartedAtActiveMillis = 0L

    fun chooseMode(mode: WorkoutMode) {
        if (mode == WorkoutMode.MIX) {
            _uiState.update {
                it.copy(
                    screen = AppScreen.WORKOUT_EDITOR,
                    workoutDraft = WorkoutDefinition(
                        name = "Mix personalizado",
                        description = "Combina y configura cada bloque de tu sesión.",
                        sections = listOf(
                            defaultSection(WorkoutSectionType.AMRAP),
                            defaultSection(WorkoutSectionType.EMOM),
                            defaultSection(WorkoutSectionType.FOR_TIME),
                        ),
                    ),
                    message = null,
                )
            }
            return
        }
        _uiState.update { current ->
            val defaults = when (mode) {
                WorkoutMode.AMRAP -> current.copy(totalSeconds = 12 * 60, restSeconds = 60, rounds = 1)
                WorkoutMode.FOR_TIME -> current.copy(
                    totalSeconds = 20 * 60,
                    restSeconds = 60,
                    rounds = 1,
                    hasTimeCap = true,
                )
                WorkoutMode.EMOM -> current.copy(workSeconds = 60, rounds = 10)
                WorkoutMode.TABATA -> current.copy(workSeconds = 20, restSeconds = 10, rounds = 8)
                WorkoutMode.MIX -> error("MIX uses the composed WOD editor")
            }
            defaults.copy(screen = AppScreen.CONFIG, mode = mode, snapshot = null, message = null)
        }
    }

    fun showHome() = _uiState.update { it.copy(screen = AppScreen.HOME, message = null) }
    fun showWorkouts() = _uiState.update { it.copy(screen = AppScreen.WORKOUTS, message = null) }
    fun showProgress() = _uiState.update { it.copy(screen = AppScreen.PROGRESS, message = null) }
    fun showSettings() = _uiState.update { it.copy(screen = AppScreen.SETTINGS, message = null) }

    fun back() {
        when (_uiState.value.screen) {
            AppScreen.HOME -> Unit
            AppScreen.CONFIG -> showHome()
            AppScreen.TIMER -> exitTimer()
            AppScreen.WORKOUTS, AppScreen.PROGRESS, AppScreen.SETTINGS -> showHome()
            AppScreen.WORKOUT_DETAIL, AppScreen.WORKOUT_EDITOR -> showWorkouts()
            AppScreen.WOD_RUNNER -> abortWod()
        }
    }

    fun adjust(field: ConfigField, delta: Int) {
        _uiState.update { state ->
            when (field) {
                ConfigField.TOTAL -> state.copy(totalSeconds = (state.totalSeconds + delta).coerceIn(60, 3 * 60 * 60))
                ConfigField.WORK -> state.copy(workSeconds = (state.workSeconds + delta).coerceIn(5, 60 * 60))
                ConfigField.REST -> state.copy(restSeconds = (state.restSeconds + delta).coerceIn(5, 30 * 60))
                ConfigField.ROUNDS -> state.copy(rounds = (state.rounds + delta).coerceIn(1, 99))
            }
        }
    }

    fun toggleTimeCap() = _uiState.update { it.copy(hasTimeCap = !it.hasTimeCap) }

    fun cyclePrepSeconds() = updateSettings { settings ->
        val options = listOf(0, 3, 5, 10, 15)
        settings.copy(prepSeconds = options[(options.indexOf(settings.prepSeconds) + 1).mod(options.size)])
    }

    fun toggleSound() = updateSettings { it.copy(soundEnabled = !it.soundEnabled) }
    fun toggleVibration() = updateSettings { it.copy(vibrationEnabled = !it.vibrationEnabled) }
    fun toggleKeepScreenOn() = updateSettings { it.copy(keepScreenOn = !it.keepScreenOn) }
    fun toggleControlsLock() = _uiState.update { it.copy(controlsLocked = !it.controlsLocked) }

    fun addMixBlock() {
        _uiState.update { state ->
            if (state.mixBlocks.size >= 8) state else state.copy(
                mixBlocks = state.mixBlocks + MixBlock(WorkoutMode.AMRAP, 5 * 60, 60),
            )
        }
    }

    fun removeMixBlock(index: Int) {
        _uiState.update { state ->
            if (state.mixBlocks.size <= 1) state
            else state.copy(mixBlocks = state.mixBlocks.filterIndexed { itemIndex, _ -> itemIndex != index })
        }
    }

    fun cycleMixMode(index: Int) {
        val available = listOf(WorkoutMode.AMRAP, WorkoutMode.FOR_TIME, WorkoutMode.EMOM, WorkoutMode.TABATA)
        updateMixBlock(index) { block ->
            block.copy(mode = available[(available.indexOf(block.mode) + 1) % available.size])
        }
    }

    fun adjustMixDuration(index: Int, deltaSeconds: Int) = updateMixBlock(index) { block ->
        block.copy(durationSeconds = (block.durationSeconds + deltaSeconds).coerceIn(30, 60 * 60))
    }

    fun adjustMixRest(index: Int, deltaSeconds: Int) = updateMixBlock(index) { block ->
        block.copy(restSeconds = (block.restSeconds + deltaSeconds).coerceIn(0, 10 * 60))
    }

    fun startWorkout() {
        ticker?.cancel()
        val state = _uiState.value
        val config = TimerConfig(
            mode = state.mode,
            workSeconds = state.workSeconds,
            restSeconds = state.restSeconds,
            rounds = state.rounds,
            totalSeconds = if (state.mode == WorkoutMode.FOR_TIME && !state.hasTimeCap) 0 else state.totalSeconds,
            mixBlocks = state.mixBlocks,
        )
        val newEngine = TimerEngine(config, prepSeconds = state.settings.prepSeconds)
        engine = newEngine
        quickStartedAtEpochMillis = System.currentTimeMillis()
        quickHistoryRecorded = false
        _uiState.update {
            it.copy(screen = AppScreen.TIMER, snapshot = newEngine.start(SystemClock.elapsedRealtime()), controlsLocked = false)
        }
        startTicker()
    }

    fun togglePause() {
        val currentEngine = engine ?: return
        val snapshot = _uiState.value.snapshot ?: return
        val now = SystemClock.elapsedRealtime()
        val updated = if (snapshot.status == SessionStatus.PAUSED) currentEngine.resume(now)
        else currentEngine.pause(now)
        _uiState.update { it.copy(snapshot = updated) }
    }

    fun completeForTime() {
        val currentEngine = engine ?: return
        val updated = currentEngine.complete(SystemClock.elapsedRealtime())
        _uiState.update { it.copy(snapshot = updated) }
        if (updated.status == SessionStatus.FINISHED) {
            ticker?.cancel()
            recordQuickSession(updated)
        }
    }

    fun restart() = startWorkout()

    fun exitTimer() {
        ticker?.cancel()
        ticker = null
        engine = null
        _uiState.update { it.copy(screen = AppScreen.HOME, snapshot = null) }
    }

    fun openWorkout(id: String) = _uiState.update {
        it.copy(screen = AppScreen.WORKOUT_DETAIL, selectedWorkoutId = id, message = null)
    }

    fun createWorkout() {
        _uiState.update {
            it.copy(
                screen = AppScreen.WORKOUT_EDITOR,
                workoutDraft = WorkoutDefinition(name = "Mi WOD", sections = listOf(defaultSection(WorkoutSectionType.AMRAP))),
                message = null,
            )
        }
    }

    fun editWorkout(id: String) {
        val workout = _uiState.value.workouts.firstOrNull { it.id == id } ?: return
        _uiState.update {
            it.copy(
                screen = AppScreen.WORKOUT_EDITOR,
                workoutDraft = workout.copy(origin = WorkoutOrigin.USER),
                message = null,
            )
        }
    }

    fun duplicateWorkout(id: String) {
        val source = _uiState.value.workouts.firstOrNull { it.id == id } ?: return
        val copy = source.copy(
            id = UUID.randomUUID().toString(),
            name = "${source.name} · copia",
            favorite = false,
            origin = WorkoutOrigin.USER,
            createdAtEpochMillis = System.currentTimeMillis(),
            sections = source.sections.map { it.copy(id = UUID.randomUUID().toString()) },
        )
        saveWorkoutList(_uiState.value.workouts + copy)
        openWorkout(copy.id)
    }

    fun toggleWorkoutFavorite(id: String) {
        saveWorkoutList(_uiState.value.workouts.map { workout ->
            if (workout.id == id) workout.copy(favorite = !workout.favorite) else workout
        })
    }

    fun deleteWorkout(id: String) {
        val workout = _uiState.value.workouts.firstOrNull { it.id == id } ?: return
        if (workout.origin == WorkoutOrigin.BUILT_IN) return
        saveWorkoutList(_uiState.value.workouts.filterNot { it.id == id })
        showWorkouts()
    }

    fun updateDraftName(name: String) = updateDraft { it.copy(name = name) }
    fun updateDraftDescription(description: String) = updateDraft { it.copy(description = description) }

    fun addDraftSection(type: WorkoutSectionType) = updateDraft { draft ->
        draft.copy(sections = draft.sections + defaultSection(type))
    }

    fun removeDraftSection(index: Int) = updateDraft { draft ->
        if (draft.sections.size <= 1) draft
        else draft.copy(sections = draft.sections.filterIndexed { itemIndex, _ -> itemIndex != index })
    }

    fun moveDraftSection(index: Int, delta: Int) = updateDraft { draft ->
        val destination = (index + delta).coerceIn(0, draft.sections.lastIndex)
        if (destination == index) draft else {
            val reordered = draft.sections.toMutableList()
            val section = reordered.removeAt(index)
            reordered.add(destination, section)
            draft.copy(sections = reordered)
        }
    }

    fun cycleDraftSectionType(index: Int) = updateDraftSection(index) { section ->
        val types = WorkoutSectionType.entries
        defaultSection(types[(types.indexOf(section.type) + 1) % types.size]).copy(
            id = section.id,
            instructions = section.instructions,
            exercises = section.exercises,
        )
    }

    fun updateDraftSectionTitle(index: Int, title: String) = updateDraftSection(index) { it.copy(title = title) }
    fun updateDraftSectionInstructions(index: Int, text: String) = updateDraftSection(index) {
        it.copy(instructions = text, exercises = text.lines().map(String::trim).filter(String::isNotBlank))
    }

    fun adjustDraftSectionDuration(index: Int, deltaSeconds: Int) = updateDraftSection(index) { section ->
        if (section.type == WorkoutSectionType.FOR_TIME) {
            section.copy(timeCapSeconds = (section.timeCapSeconds + deltaSeconds).coerceIn(0, 3 * 60 * 60))
        } else {
            section.copy(durationSeconds = (section.durationSeconds + deltaSeconds).coerceIn(0, 3 * 60 * 60))
        }
    }

    fun adjustDraftSectionWork(index: Int, deltaSeconds: Int) = updateDraftSection(index) {
        it.copy(workSeconds = (it.workSeconds + deltaSeconds).coerceIn(5, 60 * 60))
    }

    fun adjustDraftSectionRounds(index: Int, delta: Int) = updateDraftSection(index) {
        it.copy(rounds = (it.rounds + delta).coerceIn(1, 99))
    }

    fun adjustDraftSectionRest(index: Int, deltaSeconds: Int) = updateDraftSection(index) {
        it.copy(restSeconds = (it.restSeconds + deltaSeconds).coerceIn(0, 30 * 60))
    }

    fun saveDraft() {
        val draft = _uiState.value.workoutDraft ?: return
        if (draft.name.isBlank() || draft.sections.isEmpty()) {
            _uiState.update { it.copy(message = "Agrega un nombre y al menos una sección") }
            return
        }
        val workouts = _uiState.value.workouts.toMutableList()
        val index = workouts.indexOfFirst { it.id == draft.id }
        if (index >= 0) workouts[index] = draft else workouts += draft
        saveWorkoutList(workouts)
        _uiState.update {
            it.copy(
                screen = AppScreen.WORKOUT_DETAIL,
                selectedWorkoutId = draft.id,
                workoutDraft = null,
                message = "WOD guardado",
            )
        }
    }

    fun importPdf(uri: Uri) {
        _uiState.update { it.copy(importInProgress = true, message = "Analizando PDF…") }
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { importer.import(uri) } }
            result.onSuccess { imported ->
                if (imported.isEmpty()) {
                    _uiState.update { it.copy(importInProgress = false, message = "No se detectaron días en el PDF") }
                } else {
                    saveWorkoutList(_uiState.value.workouts + imported)
                    _uiState.update {
                        it.copy(
                            importInProgress = false,
                            message = "${imported.size} días importados. Revisa cada WOD antes de usarlo.",
                        )
                    }
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(importInProgress = false, message = error.message ?: "No se pudo importar el PDF")
                }
            }
        }
    }

    fun dismissMessage() = _uiState.update { it.copy(message = null) }

    fun startWod(id: String) {
        val workout = _uiState.value.workouts.firstOrNull { it.id == id } ?: return
        if (workout.sections.isEmpty()) return
        runnerTicker?.cancel()
        val now = SystemClock.elapsedRealtime()
        runnerStartedAtEpochMillis = System.currentTimeMillis()
        runnerStartedAtRealtime = now
        runnerPausedAtRealtime = null
        runnerPausedDurationMillis = 0L
        runnerSectionStartedAtActiveMillis = 0L
        _uiState.update {
            it.copy(
                screen = AppScreen.WOD_RUNNER,
                selectedWorkoutId = id,
                runner = WodRunnerState(workoutId = id),
                message = null,
                controlsLocked = false,
            )
        }
        startRunnerTicker()
    }

    fun toggleWodPause() {
        val runner = _uiState.value.runner ?: return
        if (runner.completed) return
        val now = SystemClock.elapsedRealtime()
        if (runner.paused) {
            val pausedAt = runnerPausedAtRealtime ?: now
            runnerPausedDurationMillis += now - pausedAt
            runnerPausedAtRealtime = null
        } else {
            runnerPausedAtRealtime = now
        }
        updateRunnerSnapshot(now, paused = !runner.paused)
    }

    fun completeWodSection() {
        val state = _uiState.value
        val runner = state.runner ?: return
        val workout = state.workouts.firstOrNull { it.id == runner.workoutId } ?: return
        if (runner.completed) return
        val section = workout.sections.getOrNull(runner.currentSectionIndex) ?: return
        val activeMillis = runnerActiveMillis(SystemClock.elapsedRealtime())
        val sectionDuration = ((activeMillis - runnerSectionStartedAtActiveMillis) / 1_000L).toInt()
        val results = runner.results + SectionResult(
            sectionId = section.id,
            title = section.title,
            type = section.type,
            durationSeconds = sectionDuration,
            completed = true,
        )
        if (runner.currentSectionIndex == workout.sections.lastIndex) {
            finishWod(workout, results, completed = true, activeMillis = activeMillis)
        } else {
            runnerSectionStartedAtActiveMillis = activeMillis
            _uiState.update {
                it.copy(
                    runner = runner.copy(
                        currentSectionIndex = runner.currentSectionIndex + 1,
                        sectionElapsedSeconds = 0,
                        results = results,
                    ),
                )
            }
        }
    }

    fun abortWod() {
        val state = _uiState.value
        val runner = state.runner
        val workout = runner?.let { active -> state.workouts.firstOrNull { it.id == active.workoutId } }
        runnerTicker?.cancel()
        if (runner != null && workout != null && !runner.completed && runner.results.isNotEmpty()) {
            finishWod(workout, runner.results, completed = false, activeMillis = runnerActiveMillis(SystemClock.elapsedRealtime()))
        }
        _uiState.update { it.copy(screen = AppScreen.WORKOUT_DETAIL, runner = null) }
    }

    fun exitWodRunner() {
        runnerTicker?.cancel()
        _uiState.update { it.copy(screen = AppScreen.PROGRESS, runner = null) }
    }

    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_uiState.value.settings)
        settingsStore.save(updated)
        _uiState.update { it.copy(settings = updated) }
    }

    private fun updateMixBlock(index: Int, transform: (MixBlock) -> MixBlock) {
        _uiState.update { state ->
            state.copy(mixBlocks = state.mixBlocks.mapIndexed { itemIndex, block ->
                if (itemIndex == index) transform(block) else block
            })
        }
    }

    private fun updateDraft(transform: (WorkoutDefinition) -> WorkoutDefinition) {
        _uiState.update { state -> state.copy(workoutDraft = state.workoutDraft?.let(transform)) }
    }

    private fun updateDraftSection(index: Int, transform: (WorkoutSection) -> WorkoutSection) {
        updateDraft { draft ->
            draft.copy(sections = draft.sections.mapIndexed { itemIndex, section ->
                if (itemIndex == index) transform(section) else section
            })
        }
    }

    private fun saveWorkoutList(workouts: List<WorkoutDefinition>) {
        val sorted = workouts.sortedWith(
            compareByDescending<WorkoutDefinition> { it.favorite }.thenByDescending { it.createdAtEpochMillis },
        )
        store.saveWorkouts(sorted)
        _uiState.update {
            it.copy(workouts = sorted, dashboard = WorkoutStore.dashboard(sorted, it.history))
        }
    }

    private fun saveHistoryEntry(entry: WorkoutHistoryEntry) {
        val history = (_uiState.value.history + entry).sortedByDescending { it.startedAtEpochMillis }
        store.saveHistory(history)
        _uiState.update {
            it.copy(history = history, dashboard = WorkoutStore.dashboard(it.workouts, history))
        }
    }

    private fun startTicker() {
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(100)
                val currentEngine = engine ?: break
                val updated = currentEngine.tick(SystemClock.elapsedRealtime())
                _uiState.update { it.copy(snapshot = updated) }
                if (updated.status == SessionStatus.FINISHED) {
                    recordQuickSession(updated)
                    break
                }
            }
        }
    }

    private fun recordQuickSession(snapshot: TimerSnapshot) {
        if (quickHistoryRecorded) return
        quickHistoryRecorded = true
        val mode = _uiState.value.mode
        val type = when (mode) {
            WorkoutMode.AMRAP -> WorkoutSectionType.AMRAP
            WorkoutMode.FOR_TIME -> WorkoutSectionType.FOR_TIME
            WorkoutMode.EMOM -> WorkoutSectionType.EMOM
            WorkoutMode.TABATA -> WorkoutSectionType.TABATA
            WorkoutMode.MIX -> WorkoutSectionType.NOTE
        }
        saveHistoryEntry(
            WorkoutHistoryEntry(
                workoutName = mode.name.replace('_', ' '),
                startedAtEpochMillis = quickStartedAtEpochMillis,
                completedAtEpochMillis = System.currentTimeMillis(),
                durationSeconds = snapshot.elapsedSeconds,
                completed = true,
                sectionResults = listOf(SectionResult("quick", type.displayName, type, snapshot.elapsedSeconds, true)),
            ),
        )
    }

    private fun startRunnerTicker() {
        runnerTicker = viewModelScope.launch {
            while (isActive) {
                delay(200)
                val runner = _uiState.value.runner ?: break
                if (runner.completed) break
                updateRunnerSnapshot(SystemClock.elapsedRealtime(), runner.paused)
            }
        }
    }

    private fun updateRunnerSnapshot(now: Long, paused: Boolean) {
        val activeMillis = runnerActiveMillis(now)
        _uiState.update { state ->
            val runner = state.runner ?: return@update state
            state.copy(
                runner = runner.copy(
                    paused = paused,
                    sessionElapsedSeconds = (activeMillis / 1_000L).toInt(),
                    sectionElapsedSeconds = ((activeMillis - runnerSectionStartedAtActiveMillis) / 1_000L).toInt(),
                ),
            )
        }
    }

    private fun runnerActiveMillis(now: Long): Long {
        val effectiveNow = runnerPausedAtRealtime ?: now
        return (effectiveNow - runnerStartedAtRealtime - runnerPausedDurationMillis).coerceAtLeast(0L)
    }

    private fun finishWod(
        workout: WorkoutDefinition,
        results: List<SectionResult>,
        completed: Boolean,
        activeMillis: Long,
    ) {
        runnerTicker?.cancel()
        val elapsedSeconds = (activeMillis / 1_000L).toInt()
        saveHistoryEntry(
            WorkoutHistoryEntry(
                workoutId = workout.id,
                workoutName = workout.name,
                startedAtEpochMillis = runnerStartedAtEpochMillis,
                completedAtEpochMillis = System.currentTimeMillis(),
                durationSeconds = elapsedSeconds,
                completed = completed,
                sectionResults = results,
            ),
        )
        _uiState.update { state ->
            state.copy(
                runner = state.runner?.copy(
                    completed = completed,
                    sessionElapsedSeconds = elapsedSeconds,
                    results = results,
                ),
            )
        }
    }

    override fun onCleared() {
        ticker?.cancel()
        runnerTicker?.cancel()
        super.onCleared()
    }
}
