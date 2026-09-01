package com.felipe.tim3r

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.felipe.tim3r.domain.MixBlock
import com.felipe.tim3r.domain.SessionStatus
import com.felipe.tim3r.domain.TimerConfig
import com.felipe.tim3r.domain.TimerEngine
import com.felipe.tim3r.domain.TimerSnapshot
import com.felipe.tim3r.domain.WorkoutMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AppScreen { HOME, CONFIG, TIMER }

enum class ConfigField { TOTAL, WORK, REST, ROUNDS }

private val defaultMixBlocks = listOf(
    MixBlock(WorkoutMode.AMRAP, durationSeconds = 5 * 60, restSeconds = 60),
    MixBlock(WorkoutMode.EMOM, durationSeconds = 5 * 60, restSeconds = 60),
    MixBlock(WorkoutMode.FOR_TIME, durationSeconds = 5 * 60),
)

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
)

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(Tim3rUiState())
    val uiState: StateFlow<Tim3rUiState> = _uiState.asStateFlow()

    private var engine: TimerEngine? = null
    private var ticker: Job? = null

    fun chooseMode(mode: WorkoutMode) {
        _uiState.update { current ->
            val defaults = when (mode) {
                WorkoutMode.AMRAP -> current.copy(
                    totalSeconds = 12 * 60,
                    restSeconds = 60,
                    rounds = 1,
                )
                WorkoutMode.FOR_TIME -> current.copy(
                    totalSeconds = 20 * 60,
                    restSeconds = 60,
                    rounds = 1,
                    hasTimeCap = true,
                )
                WorkoutMode.EMOM -> current.copy(workSeconds = 60, rounds = 10)
                WorkoutMode.TABATA -> current.copy(workSeconds = 20, restSeconds = 10, rounds = 8)
                WorkoutMode.MIX -> current
            }
            defaults.copy(screen = AppScreen.CONFIG, mode = mode, snapshot = null)
        }
    }

    fun back() {
        when (_uiState.value.screen) {
            AppScreen.HOME -> Unit
            AppScreen.CONFIG -> _uiState.update { it.copy(screen = AppScreen.HOME) }
            AppScreen.TIMER -> exitTimer()
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

    fun addMixBlock() {
        _uiState.update { state ->
            if (state.mixBlocks.size >= 8) state else state.copy(
                mixBlocks = state.mixBlocks + MixBlock(
                    mode = WorkoutMode.AMRAP,
                    durationSeconds = 5 * 60,
                    restSeconds = 60,
                ),
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
        _uiState.update { state ->
            state.copy(mixBlocks = state.mixBlocks.mapIndexed { itemIndex, block ->
                if (itemIndex != index) block
                else block.copy(mode = available[(available.indexOf(block.mode) + 1) % available.size])
            })
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
            totalSeconds = when {
                state.mode == WorkoutMode.FOR_TIME && !state.hasTimeCap -> 0
                else -> state.totalSeconds
            },
            mixBlocks = state.mixBlocks,
        )
        val newEngine = TimerEngine(config = config, prepSeconds = 10)
        engine = newEngine
        _uiState.update {
            it.copy(
                screen = AppScreen.TIMER,
                snapshot = newEngine.start(SystemClock.elapsedRealtime()),
            )
        }
        startTicker()
    }

    fun togglePause() {
        val currentEngine = engine ?: return
        val snapshot = _uiState.value.snapshot ?: return
        val now = SystemClock.elapsedRealtime()
        val updated = if (snapshot.status == SessionStatus.PAUSED) {
            currentEngine.resume(now)
        } else {
            currentEngine.pause(now)
        }
        _uiState.update { it.copy(snapshot = updated) }
    }

    fun completeForTime() {
        val currentEngine = engine ?: return
        val updated = currentEngine.complete(SystemClock.elapsedRealtime())
        _uiState.update { it.copy(snapshot = updated) }
        if (updated.status == SessionStatus.FINISHED) ticker?.cancel()
    }

    fun restart() = startWorkout()

    fun exitTimer() {
        ticker?.cancel()
        ticker = null
        engine = null
        _uiState.update { it.copy(screen = AppScreen.HOME, snapshot = null) }
    }

    private fun updateMixBlock(index: Int, transform: (MixBlock) -> MixBlock) {
        _uiState.update { state ->
            state.copy(mixBlocks = state.mixBlocks.mapIndexed { itemIndex, block ->
                if (itemIndex == index) transform(block) else block
            })
        }
    }

    private fun startTicker() {
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(100)
                val currentEngine = engine ?: break
                val updated = currentEngine.tick(SystemClock.elapsedRealtime())
                _uiState.update { it.copy(snapshot = updated) }
                if (updated.status == SessionStatus.FINISHED) break
            }
        }
    }

    override fun onCleared() {
        ticker?.cancel()
        super.onCleared()
    }

}
