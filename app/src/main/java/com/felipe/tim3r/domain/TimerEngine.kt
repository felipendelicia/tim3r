package com.felipe.tim3r.domain

enum class WorkoutMode {
    AMRAP,
    FOR_TIME,
    EMOM,
    TABATA,
    MIX,
}

data class TimerConfig(
    val mode: WorkoutMode,
    val workSeconds: Int = 0,
    val restSeconds: Int = 0,
    val rounds: Int = 1,
    val totalSeconds: Int = 0,
    val mixBlocks: List<MixBlock> = emptyList(),
)

data class MixBlock(
    val mode: WorkoutMode,
    val durationSeconds: Int,
    val restSeconds: Int = 0,
)

enum class SessionStatus {
    READY,
    COUNTDOWN,
    RUNNING,
    PAUSED,
    FINISHED,
}

enum class IntervalPhase {
    PREPARE,
    WORK,
    REST,
    COMPLETE,
}

data class TimerSnapshot(
    val status: SessionStatus,
    val phase: IntervalPhase,
    val displaySeconds: Int,
    val elapsedSeconds: Int,
    val currentRound: Int,
    val totalRounds: Int,
    val currentBlock: Int,
    val totalBlocks: Int,
    val progress: Float,
    val title: String,
    val canComplete: Boolean,
)

/**
 * Pure timer state machine. All time values supplied to this class must come
 * from the same monotonic clock.
 */
class TimerEngine(
    private val config: TimerConfig,
    private val prepSeconds: Int = 10,
) {
    private var status = SessionStatus.READY
    private var startedAtMillis: Long? = null
    private var pausedAtMillis: Long? = null
    private var pausedDurationMillis = 0L
    private var lastNowMillis: Long? = null
    private var completedActiveMillis: Long? = null
    private var forTimeRound = 1
    private var forTimePhase = IntervalPhase.WORK
    private var forTimePhaseStartedAtActiveMillis = 0L
    private var forTimeLastWorkMillis = 0L

    private val prepDurationMillis = prepSeconds.toLong() * MILLIS_PER_SECOND
    private val sessionDurationMillis: Long? by lazy { calculateSessionDurationMillis() }

    init {
        validateConfig()
    }

    fun start(nowMillis: Long): TimerSnapshot {
        observe(nowMillis)
        if (status == SessionStatus.READY) {
            startedAtMillis = nowMillis
            status = if (prepSeconds > 0) SessionStatus.COUNTDOWN else SessionStatus.RUNNING
        }
        advance(nowMillis)
        return snapshot(nowMillis)
    }

    fun tick(nowMillis: Long): TimerSnapshot {
        observe(nowMillis)
        advance(nowMillis)
        return snapshot(nowMillis)
    }

    fun pause(nowMillis: Long): TimerSnapshot {
        observe(nowMillis)
        advance(nowMillis)
        if (status == SessionStatus.COUNTDOWN || status == SessionStatus.RUNNING) {
            pausedAtMillis = nowMillis
            status = SessionStatus.PAUSED
        }
        return snapshot(nowMillis)
    }

    fun resume(nowMillis: Long): TimerSnapshot {
        observe(nowMillis)
        if (status == SessionStatus.PAUSED) {
            val pauseStarted = requireNotNull(pausedAtMillis)
            pausedDurationMillis += nowMillis - pauseStarted
            pausedAtMillis = null
            status = SessionStatus.RUNNING
            advance(nowMillis)
        }
        return snapshot(nowMillis)
    }

    fun complete(nowMillis: Long): TimerSnapshot {
        observe(nowMillis)
        advance(nowMillis)
        if (
            config.mode == WorkoutMode.FOR_TIME &&
            forTimePhase == IntervalPhase.WORK &&
            (status == SessionStatus.RUNNING || status == SessionStatus.PAUSED)
        ) {
            val activeMillis = activeMillis(nowMillis)
            forTimeLastWorkMillis =
                (activeMillis - forTimePhaseStartedAtActiveMillis).coerceAtLeast(0L)
            if (forTimeRound == config.rounds) {
                completedActiveMillis = activeMillis
                status = SessionStatus.FINISHED
                pausedAtMillis = null
            } else {
                forTimePhase = IntervalPhase.REST
                forTimePhaseStartedAtActiveMillis = activeMillis
                advanceForTime(activeMillis)
            }
        }
        return snapshot(nowMillis)
    }

    fun reset(): TimerSnapshot {
        status = SessionStatus.READY
        startedAtMillis = null
        pausedAtMillis = null
        pausedDurationMillis = 0L
        lastNowMillis = null
        completedActiveMillis = null
        forTimeRound = 1
        forTimePhase = IntervalPhase.WORK
        forTimePhaseStartedAtActiveMillis = 0L
        forTimeLastWorkMillis = 0L
        return readySnapshot()
    }

    private fun validateConfig() {
        require(prepSeconds >= 0) { "prepSeconds must be non-negative" }
        require(config.workSeconds >= 0) { "workSeconds must be non-negative" }
        require(config.restSeconds >= 0) { "restSeconds must be non-negative" }
        require(config.rounds > 0) { "rounds must be greater than zero" }
        require(config.totalSeconds >= 0) { "totalSeconds must be non-negative" }

        when (config.mode) {
            WorkoutMode.AMRAP ->
                require(config.totalSeconds > 0) { "AMRAP requires totalSeconds greater than zero" }

            WorkoutMode.FOR_TIME -> Unit

            WorkoutMode.EMOM ->
                require(config.workSeconds > 0) { "EMOM requires workSeconds greater than zero" }

            WorkoutMode.TABATA -> {
                require(config.workSeconds > 0) { "TABATA requires workSeconds greater than zero" }
                require(config.restSeconds > 0) { "TABATA requires restSeconds greater than zero" }
            }

            WorkoutMode.MIX -> {
                require(config.mixBlocks.isNotEmpty()) { "MIX requires at least one block" }
                config.mixBlocks.forEachIndexed { index, block ->
                    require(block.mode != WorkoutMode.MIX) { "MIX block $index cannot contain another MIX" }
                    require(block.durationSeconds > 0) {
                        "MIX block $index requires durationSeconds greater than zero"
                    }
                    require(block.restSeconds >= 0) {
                        "MIX block $index restSeconds must be non-negative"
                    }
                }
            }
        }
    }

    private fun observe(nowMillis: Long) {
        require(nowMillis >= 0L) { "nowMillis must be non-negative" }
        val previous = lastNowMillis
        require(previous == null || nowMillis >= previous) {
            "nowMillis must not move backwards"
        }
        lastNowMillis = nowMillis
    }

    private fun advance(nowMillis: Long) {
        if (status != SessionStatus.COUNTDOWN && status != SessionStatus.RUNNING) return

        val timelineMillis = timelineMillis(nowMillis)
        if (timelineMillis < prepDurationMillis) {
            status = SessionStatus.COUNTDOWN
            return
        }

        val activeMillis = timelineMillis - prepDurationMillis
        status = SessionStatus.RUNNING
        if (config.mode == WorkoutMode.FOR_TIME) {
            advanceForTime(activeMillis)
            return
        }

        val durationMillis = sessionDurationMillis
        if (durationMillis != null && activeMillis >= durationMillis) {
            completedActiveMillis = durationMillis
            status = SessionStatus.FINISHED
        }
    }

    private fun advanceForTime(activeMillis: Long) {
        val capMillis = config.totalSeconds.toLong() * MILLIS_PER_SECOND
        val restMillis = config.restSeconds.toLong() * MILLIS_PER_SECOND

        while (status != SessionStatus.FINISHED) {
            when (forTimePhase) {
                IntervalPhase.WORK -> {
                    if (capMillis == 0L) return
                    val workEndsAt = forTimePhaseStartedAtActiveMillis + capMillis
                    if (activeMillis < workEndsAt) return

                    forTimeLastWorkMillis = capMillis
                    if (forTimeRound == config.rounds) {
                        completedActiveMillis = workEndsAt
                        status = SessionStatus.FINISHED
                        pausedAtMillis = null
                        return
                    }
                    forTimePhase = IntervalPhase.REST
                    forTimePhaseStartedAtActiveMillis = workEndsAt
                }

                IntervalPhase.REST -> {
                    val restEndsAt = forTimePhaseStartedAtActiveMillis + restMillis
                    if (activeMillis < restEndsAt) return

                    forTimeRound += 1
                    forTimePhase = IntervalPhase.WORK
                    forTimePhaseStartedAtActiveMillis = restEndsAt
                }

                else -> error("Unexpected For Time phase: $forTimePhase")
            }
        }
    }

    private fun snapshot(nowMillis: Long): TimerSnapshot {
        if (status == SessionStatus.READY) return readySnapshot()

        val timelineMillis = timelineMillis(nowMillis)
        if (status != SessionStatus.FINISHED && timelineMillis < prepDurationMillis) {
            val base = modeFrame(activeMillis = 0L, finished = false)
            return TimerSnapshot(
                status = status,
                phase = IntervalPhase.PREPARE,
                displaySeconds = ceilSeconds(prepDurationMillis - timelineMillis),
                elapsedSeconds = 0,
                currentRound = base.currentRound,
                totalRounds = base.totalRounds,
                currentBlock = base.currentBlock,
                totalBlocks = base.totalBlocks,
                progress = 0f,
                title = TITLE_PREPARE,
                canComplete = false,
            )
        }

        val isFinished = status == SessionStatus.FINISHED
        val activeMillis = if (isFinished) {
            completedActiveMillis ?: activeMillis(nowMillis)
        } else {
            activeMillis(nowMillis)
        }
        val frame = modeFrame(activeMillis, isFinished)
        return TimerSnapshot(
            status = status,
            phase = frame.phase,
            displaySeconds = frame.displaySeconds,
            elapsedSeconds = floorSeconds(activeMillis),
            currentRound = frame.currentRound,
            totalRounds = frame.totalRounds,
            currentBlock = frame.currentBlock,
            totalBlocks = frame.totalBlocks,
            progress = progress(activeMillis, isFinished),
            title = frame.title,
            canComplete = config.mode == WorkoutMode.FOR_TIME &&
                frame.phase == IntervalPhase.WORK &&
                (status == SessionStatus.RUNNING || status == SessionStatus.PAUSED),
        )
    }

    private fun readySnapshot(): TimerSnapshot {
        val frame = modeFrame(activeMillis = 0L, finished = false)
        return TimerSnapshot(
            status = SessionStatus.READY,
            phase = IntervalPhase.PREPARE,
            displaySeconds = frame.displaySeconds,
            elapsedSeconds = 0,
            currentRound = frame.currentRound,
            totalRounds = frame.totalRounds,
            currentBlock = frame.currentBlock,
            totalBlocks = frame.totalBlocks,
            progress = 0f,
            title = modeTitle(config.mode),
            canComplete = false,
        )
    }

    private fun modeFrame(activeMillis: Long, finished: Boolean): ModeFrame = when (config.mode) {
        WorkoutMode.AMRAP -> amrapFrame(activeMillis, finished)

        WorkoutMode.FOR_TIME -> forTimeFrame(activeMillis, finished)

        WorkoutMode.EMOM -> roundFrame(
            activeMillis = activeMillis,
            workMillis = config.workSeconds.toLong() * MILLIS_PER_SECOND,
            restMillis = 0L,
            finished = finished,
            mode = WorkoutMode.EMOM,
        )

        WorkoutMode.TABATA -> roundFrame(
            activeMillis = activeMillis,
            workMillis = config.workSeconds.toLong() * MILLIS_PER_SECOND,
            restMillis = config.restSeconds.toLong() * MILLIS_PER_SECOND,
            finished = finished,
            mode = WorkoutMode.TABATA,
        )

        WorkoutMode.MIX -> mixFrame(activeMillis, finished)
    }

    private fun amrapFrame(activeMillis: Long, finished: Boolean): ModeFrame {
        if (finished) {
            return ModeFrame(
                phase = IntervalPhase.COMPLETE,
                displaySeconds = 0,
                currentRound = config.rounds,
                totalRounds = config.rounds,
                currentBlock = 0,
                totalBlocks = 0,
                title = TITLE_COMPLETE,
            )
        }

        val workMillis = config.totalSeconds.toLong() * MILLIS_PER_SECOND
        val restMillis = config.restSeconds.toLong() * MILLIS_PER_SECOND
        val seriesMillis = workMillis + restMillis
        val roundIndex = (activeMillis / seriesMillis).toInt().coerceAtMost(config.rounds - 1)
        val millisInSeries = activeMillis % seriesMillis
        val inWork = millisInSeries < workMillis
        return ModeFrame(
            phase = if (inWork) IntervalPhase.WORK else IntervalPhase.REST,
            displaySeconds = if (inWork) {
                ceilSeconds(workMillis - millisInSeries)
            } else {
                ceilSeconds(seriesMillis - millisInSeries)
            },
            currentRound = roundIndex + 1,
            totalRounds = config.rounds,
            currentBlock = 0,
            totalBlocks = 0,
            title = if (inWork) modeTitle(WorkoutMode.AMRAP) else TITLE_REST,
        )
    }

    private fun forTimeFrame(activeMillis: Long, finished: Boolean): ModeFrame {
        val phase = if (finished) IntervalPhase.COMPLETE else forTimePhase
        val displayMillis = when (phase) {
            IntervalPhase.COMPLETE -> forTimeLastWorkMillis
            IntervalPhase.WORK ->
                (activeMillis - forTimePhaseStartedAtActiveMillis).coerceAtLeast(0L)

            IntervalPhase.REST -> {
                val restMillis = config.restSeconds.toLong() * MILLIS_PER_SECOND
                (forTimePhaseStartedAtActiveMillis + restMillis - activeMillis).coerceAtLeast(0L)
            }

            IntervalPhase.PREPARE -> 0L
        }
        return ModeFrame(
            phase = phase,
            displaySeconds = if (phase == IntervalPhase.REST) {
                ceilSeconds(displayMillis)
            } else {
                floorSeconds(displayMillis)
            },
            currentRound = forTimeRound,
            totalRounds = config.rounds,
            currentBlock = 0,
            totalBlocks = 0,
            title = when (phase) {
                IntervalPhase.COMPLETE -> TITLE_COMPLETE
                IntervalPhase.REST -> TITLE_REST
                else -> modeTitle(WorkoutMode.FOR_TIME)
            },
        )
    }

    private fun roundFrame(
        activeMillis: Long,
        workMillis: Long,
        restMillis: Long,
        finished: Boolean,
        mode: WorkoutMode,
    ): ModeFrame {
        if (finished) {
            return ModeFrame(
                phase = IntervalPhase.COMPLETE,
                displaySeconds = 0,
                currentRound = config.rounds,
                totalRounds = config.rounds,
                currentBlock = 0,
                totalBlocks = 0,
                title = TITLE_COMPLETE,
            )
        }

        val roundMillis = workMillis + restMillis
        val roundIndex = (activeMillis / roundMillis).toInt().coerceAtMost(config.rounds - 1)
        val millisInRound = activeMillis % roundMillis
        val inWork = millisInRound < workMillis
        return ModeFrame(
            phase = if (inWork) IntervalPhase.WORK else IntervalPhase.REST,
            displaySeconds = if (inWork) {
                ceilSeconds(workMillis - millisInRound)
            } else {
                ceilSeconds(roundMillis - millisInRound)
            },
            currentRound = roundIndex + 1,
            totalRounds = config.rounds,
            currentBlock = 0,
            totalBlocks = 0,
            title = if (inWork) modeTitle(mode) else TITLE_REST,
        )
    }

    private fun mixFrame(activeMillis: Long, finished: Boolean): ModeFrame {
        val blocks = config.mixBlocks
        if (finished) {
            return ModeFrame(
                phase = IntervalPhase.COMPLETE,
                displaySeconds = 0,
                currentRound = 1,
                totalRounds = 1,
                currentBlock = blocks.size,
                totalBlocks = blocks.size,
                title = TITLE_COMPLETE,
            )
        }

        var remainingMillis = activeMillis
        blocks.forEachIndexed { index, block ->
            val blockMillis = block.durationSeconds.toLong() * MILLIS_PER_SECOND
            if (remainingMillis < blockMillis) {
                val displaySeconds = if (block.mode == WorkoutMode.FOR_TIME) {
                    floorSeconds(remainingMillis)
                } else {
                    ceilSeconds(blockMillis - remainingMillis)
                }
                return ModeFrame(
                    phase = IntervalPhase.WORK,
                    displaySeconds = displaySeconds,
                    currentRound = 1,
                    totalRounds = 1,
                    currentBlock = index + 1,
                    totalBlocks = blocks.size,
                    title = modeTitle(block.mode),
                )
            }
            remainingMillis -= blockMillis

            if (index < blocks.lastIndex) {
                val restMillis = block.restSeconds.toLong() * MILLIS_PER_SECOND
                if (remainingMillis < restMillis) {
                    return ModeFrame(
                        phase = IntervalPhase.REST,
                        displaySeconds = ceilSeconds(restMillis - remainingMillis),
                        currentRound = 1,
                        totalRounds = 1,
                        currentBlock = index + 1,
                        totalBlocks = blocks.size,
                        title = TITLE_REST,
                    )
                }
                remainingMillis -= restMillis
            }
        }

        error("Active MIX time exceeded its validated duration")
    }

    private fun calculateSessionDurationMillis(): Long? = when (config.mode) {
        WorkoutMode.AMRAP ->
            (config.totalSeconds.toLong() * config.rounds.toLong() +
                config.restSeconds.toLong() * (config.rounds - 1).toLong()) * MILLIS_PER_SECOND

        WorkoutMode.FOR_TIME -> config.totalSeconds
            .takeIf { it > 0 }
            ?.let { capSeconds ->
                (capSeconds.toLong() * config.rounds.toLong() +
                    config.restSeconds.toLong() * (config.rounds - 1).toLong()) * MILLIS_PER_SECOND
            }

        WorkoutMode.EMOM ->
            config.workSeconds.toLong() * config.rounds.toLong() * MILLIS_PER_SECOND

        WorkoutMode.TABATA ->
            (config.workSeconds.toLong() + config.restSeconds.toLong()) *
                config.rounds.toLong() * MILLIS_PER_SECOND

        WorkoutMode.MIX -> config.mixBlocks.foldIndexed(0L) { index, total, block ->
            val workMillis = block.durationSeconds.toLong() * MILLIS_PER_SECOND
            val transitionMillis = if (index < config.mixBlocks.lastIndex) {
                block.restSeconds.toLong() * MILLIS_PER_SECOND
            } else {
                0L
            }
            total + workMillis + transitionMillis
        }
    }

    private fun timelineMillis(nowMillis: Long): Long {
        val start = startedAtMillis ?: return 0L
        val effectiveNow = if (status == SessionStatus.PAUSED) {
            requireNotNull(pausedAtMillis)
        } else {
            nowMillis
        }
        return (effectiveNow - start - pausedDurationMillis).coerceAtLeast(0L)
    }

    private fun activeMillis(nowMillis: Long): Long =
        (timelineMillis(nowMillis) - prepDurationMillis).coerceAtLeast(0L)

    private fun progress(activeMillis: Long, finished: Boolean): Float {
        if (finished) return 1f
        val duration = sessionDurationMillis ?: return 0f
        if (duration == 0L) return 0f
        return (activeMillis.toDouble() / duration.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    private fun floorSeconds(millis: Long): Int =
        (millis / MILLIS_PER_SECOND).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    private fun ceilSeconds(millis: Long): Int {
        if (millis <= 0L) return 0
        return ((millis - 1L) / MILLIS_PER_SECOND + 1L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    private fun modeTitle(mode: WorkoutMode): String = when (mode) {
        WorkoutMode.AMRAP -> "AMRAP"
        WorkoutMode.FOR_TIME -> "For Time"
        WorkoutMode.EMOM -> "EMOM"
        WorkoutMode.TABATA -> "Tabata"
        WorkoutMode.MIX -> "Mix"
    }

    private data class ModeFrame(
        val phase: IntervalPhase,
        val displaySeconds: Int,
        val currentRound: Int,
        val totalRounds: Int,
        val currentBlock: Int,
        val totalBlocks: Int,
        val title: String,
    )

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        const val TITLE_PREPARE = "Prepare"
        const val TITLE_REST = "Rest"
        const val TITLE_COMPLETE = "Complete"
    }
}
