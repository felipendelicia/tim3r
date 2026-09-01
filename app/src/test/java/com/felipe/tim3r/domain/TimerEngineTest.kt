package com.felipe.tim3r.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerEngineTest {
    @Test
    fun amrapHonorsPreparationAndExactBoundaries() {
        val engine = TimerEngine(
            config = TimerConfig(mode = WorkoutMode.AMRAP, totalSeconds = 5),
            prepSeconds = 2,
        )

        assertSnapshot(
            snapshot = engine.start(1_000L),
            status = SessionStatus.COUNTDOWN,
            phase = IntervalPhase.PREPARE,
            displaySeconds = 2,
            elapsedSeconds = 0,
        )
        assertEquals(1, engine.tick(2_999L).displaySeconds)

        assertSnapshot(
            snapshot = engine.tick(3_000L),
            status = SessionStatus.RUNNING,
            phase = IntervalPhase.WORK,
            displaySeconds = 5,
            elapsedSeconds = 0,
        )
        assertEquals(1, engine.tick(7_999L).displaySeconds)

        val finished = engine.tick(8_000L)
        assertSnapshot(
            snapshot = finished,
            status = SessionStatus.FINISHED,
            phase = IntervalPhase.COMPLETE,
            displaySeconds = 0,
            elapsedSeconds = 5,
        )
        assertEquals(1f, finished.progress, 0f)
    }

    @Test
    fun amrapRunsMultipleSeriesWithRestOnlyBetweenThem() {
        val engine = TimerEngine(
            config = TimerConfig(
                mode = WorkoutMode.AMRAP,
                totalSeconds = 5,
                restSeconds = 3,
                rounds = 2,
            ),
            prepSeconds = 0,
        )

        val firstWork = engine.start(0L)
        assertEquals(IntervalPhase.WORK, firstWork.phase)
        assertEquals(1, firstWork.currentRound)
        assertEquals(2, firstWork.totalRounds)
        assertEquals(5, firstWork.displaySeconds)

        val rest = engine.tick(5_000L)
        assertEquals(SessionStatus.RUNNING, rest.status)
        assertEquals(IntervalPhase.REST, rest.phase)
        assertEquals(1, rest.currentRound)
        assertEquals(3, rest.displaySeconds)

        val secondWork = engine.tick(8_000L)
        assertEquals(SessionStatus.RUNNING, secondWork.status)
        assertEquals(IntervalPhase.WORK, secondWork.phase)
        assertEquals(2, secondWork.currentRound)
        assertEquals(5, secondWork.displaySeconds)

        val finished = engine.tick(13_000L)
        assertEquals(SessionStatus.FINISHED, finished.status)
        assertEquals(IntervalPhase.COMPLETE, finished.phase)
        assertEquals(2, finished.currentRound)
        assertEquals(13, finished.elapsedSeconds)
        assertEquals(1f, finished.progress, 0f)

        val lateEngine = TimerEngine(
            config = TimerConfig(
                mode = WorkoutMode.AMRAP,
                totalSeconds = 5,
                restSeconds = 3,
                rounds = 2,
            ),
            prepSeconds = 0,
        )
        lateEngine.start(0L)
        val lateSecondWork = lateEngine.tick(9_000L)
        assertEquals(IntervalPhase.WORK, lateSecondWork.phase)
        assertEquals(2, lateSecondWork.currentRound)
        assertEquals(4, lateSecondWork.displaySeconds)
    }

    @Test
    fun emomMovesToTheNextRoundAtTheExactBoundary() {
        val engine = TimerEngine(
            config = TimerConfig(
                mode = WorkoutMode.EMOM,
                workSeconds = 60,
                rounds = 3,
            ),
            prepSeconds = 0,
        )

        val first = engine.start(0L)
        assertEquals(1, first.currentRound)
        assertEquals(60, first.displaySeconds)

        val second = engine.tick(60_000L)
        assertEquals(SessionStatus.RUNNING, second.status)
        assertEquals(2, second.currentRound)
        assertEquals(60, second.displaySeconds)

        val finished = engine.tick(180_000L)
        assertEquals(SessionStatus.FINISHED, finished.status)
        assertEquals(3, finished.currentRound)
        assertEquals(180, finished.elapsedSeconds)
    }

    @Test
    fun pauseFreezesTimeAndResumePreservesTheAnchor() {
        val engine = TimerEngine(
            config = TimerConfig(mode = WorkoutMode.AMRAP, totalSeconds = 10),
            prepSeconds = 0,
        )

        engine.start(1_000L)
        val paused = engine.pause(4_000L)
        assertEquals(SessionStatus.PAUSED, paused.status)
        assertEquals(3, paused.elapsedSeconds)
        assertEquals(7, paused.displaySeconds)

        val stillPaused = engine.tick(14_000L)
        assertEquals(3, stillPaused.elapsedSeconds)
        assertEquals(7, stillPaused.displaySeconds)

        val resumed = engine.resume(14_000L)
        assertEquals(SessionStatus.RUNNING, resumed.status)
        assertEquals(3, resumed.elapsedSeconds)

        val later = engine.tick(16_000L)
        assertEquals(5, later.elapsedSeconds)
        assertEquals(5, later.displaySeconds)
    }

    @Test
    fun pauseAlsoFreezesRestCountdown() {
        val engine = TimerEngine(
            config = TimerConfig(
                mode = WorkoutMode.AMRAP,
                totalSeconds = 5,
                restSeconds = 3,
                rounds = 2,
            ),
            prepSeconds = 0,
        )

        engine.start(0L)
        val paused = engine.pause(6_000L)
        assertEquals(SessionStatus.PAUSED, paused.status)
        assertEquals(IntervalPhase.REST, paused.phase)
        assertEquals(2, paused.displaySeconds)
        assertEquals(6, paused.elapsedSeconds)

        val stillPaused = engine.tick(16_000L)
        assertEquals(IntervalPhase.REST, stillPaused.phase)
        assertEquals(2, stillPaused.displaySeconds)
        assertEquals(6, stillPaused.elapsedSeconds)

        engine.resume(16_000L)
        val secondWork = engine.tick(18_000L)
        assertEquals(IntervalPhase.WORK, secondWork.phase)
        assertEquals(2, secondWork.currentRound)
        assertEquals(5, secondWork.displaySeconds)
    }

    @Test
    fun unlimitedForTimeCanBeCompletedManually() {
        val engine = TimerEngine(
            config = TimerConfig(mode = WorkoutMode.FOR_TIME),
            prepSeconds = 0,
        )

        assertTrue(engine.start(0L).canComplete)
        val running = engine.tick(3_500L)
        assertEquals(3, running.displaySeconds)
        assertEquals(0f, running.progress, 0f)

        val finished = engine.complete(4_500L)
        assertEquals(SessionStatus.FINISHED, finished.status)
        assertEquals(IntervalPhase.COMPLETE, finished.phase)
        assertEquals(4, finished.displaySeconds)
        assertEquals(4, finished.elapsedSeconds)
        assertEquals(1f, finished.progress, 0f)
        assertFalse(finished.canComplete)

        assertEquals(finished, engine.tick(50_000L))
    }

    @Test
    fun manualForTimeRunsMultipleSeriesAndResetsAfterRest() {
        val engine = TimerEngine(
            config = TimerConfig(
                mode = WorkoutMode.FOR_TIME,
                restSeconds = 3,
                rounds = 2,
            ),
            prepSeconds = 0,
        )

        engine.start(0L)
        val firstRest = engine.complete(2_500L)
        assertEquals(SessionStatus.RUNNING, firstRest.status)
        assertEquals(IntervalPhase.REST, firstRest.phase)
        assertEquals(1, firstRest.currentRound)
        assertEquals(3, firstRest.displaySeconds)
        assertFalse(firstRest.canComplete)

        val secondWork = engine.tick(5_500L)
        assertEquals(SessionStatus.RUNNING, secondWork.status)
        assertEquals(IntervalPhase.WORK, secondWork.phase)
        assertEquals(2, secondWork.currentRound)
        assertEquals(2, secondWork.totalRounds)
        assertEquals(0, secondWork.displaySeconds)
        assertTrue(secondWork.canComplete)

        val finished = engine.complete(8_000L)
        assertEquals(SessionStatus.FINISHED, finished.status)
        assertEquals(IntervalPhase.COMPLETE, finished.phase)
        assertEquals(2, finished.currentRound)
        assertEquals(2, finished.displaySeconds)
        assertEquals(8, finished.elapsedSeconds)
        assertFalse(finished.canComplete)
    }

    @Test
    fun cappedForTimeFinishesAutomatically() {
        val engine = TimerEngine(
            config = TimerConfig(mode = WorkoutMode.FOR_TIME, totalSeconds = 10),
            prepSeconds = 0,
        )

        engine.start(0L)
        assertEquals(SessionStatus.RUNNING, engine.tick(9_999L).status)

        val finished = engine.tick(10_000L)
        assertEquals(SessionStatus.FINISHED, finished.status)
        assertEquals(10, finished.displaySeconds)
        assertEquals(10, finished.elapsedSeconds)
    }

    @Test
    fun cappedForTimeLateTickCrossesRestIntoTheSecondSeries() {
        val engine = TimerEngine(
            config = TimerConfig(
                mode = WorkoutMode.FOR_TIME,
                totalSeconds = 5,
                restSeconds = 2,
                rounds = 2,
            ),
            prepSeconds = 0,
        )

        engine.start(0L)
        val firstRest = engine.tick(5_000L)
        assertEquals(IntervalPhase.REST, firstRest.phase)
        assertEquals(1, firstRest.currentRound)
        assertEquals(2, firstRest.displaySeconds)
        assertFalse(firstRest.canComplete)

        val secondWork = engine.tick(7_000L)
        assertEquals(SessionStatus.RUNNING, secondWork.status)
        assertEquals(IntervalPhase.WORK, secondWork.phase)
        assertEquals(2, secondWork.currentRound)
        assertEquals(0, secondWork.displaySeconds)
        assertTrue(secondWork.canComplete)

        val finished = engine.tick(12_000L)
        assertEquals(SessionStatus.FINISHED, finished.status)
        assertEquals(IntervalPhase.COMPLETE, finished.phase)
        assertEquals(5, finished.displaySeconds)
        assertEquals(12, finished.elapsedSeconds)
        assertEquals(1f, finished.progress, 0f)

        val lateEngine = TimerEngine(
            config = TimerConfig(
                mode = WorkoutMode.FOR_TIME,
                totalSeconds = 5,
                restSeconds = 2,
                rounds = 2,
            ),
            prepSeconds = 0,
        )
        lateEngine.start(0L)
        val lateSecondWork = lateEngine.tick(8_000L)
        assertEquals(SessionStatus.RUNNING, lateSecondWork.status)
        assertEquals(IntervalPhase.WORK, lateSecondWork.phase)
        assertEquals(2, lateSecondWork.currentRound)
        assertEquals(1, lateSecondWork.displaySeconds)
    }

    @Test
    fun tabataIncludesTheRestAfterTheLastRound() {
        val engine = TimerEngine(
            config = TimerConfig(
                mode = WorkoutMode.TABATA,
                workSeconds = 20,
                restSeconds = 10,
                rounds = 2,
            ),
            prepSeconds = 0,
        )

        engine.start(0L)
        val firstRest = engine.tick(20_000L)
        assertEquals(IntervalPhase.REST, firstRest.phase)
        assertEquals(1, firstRest.currentRound)
        assertEquals(10, firstRest.displaySeconds)

        val secondWork = engine.tick(30_000L)
        assertEquals(IntervalPhase.WORK, secondWork.phase)
        assertEquals(2, secondWork.currentRound)
        assertEquals(20, secondWork.displaySeconds)

        val finalRest = engine.tick(50_000L)
        assertEquals(SessionStatus.RUNNING, finalRest.status)
        assertEquals(IntervalPhase.REST, finalRest.phase)
        assertEquals(2, finalRest.currentRound)
        assertEquals(10, finalRest.displaySeconds)
        assertEquals(1, engine.tick(59_999L).displaySeconds)

        val finished = engine.tick(60_000L)
        assertEquals(SessionStatus.FINISHED, finished.status)
        assertEquals(IntervalPhase.COMPLETE, finished.phase)
        assertEquals(60, finished.elapsedSeconds)
    }

    @Test
    fun lateMixTickCrossesBlocksAndTransitionRests() {
        val engine = TimerEngine(
            config = TimerConfig(
                mode = WorkoutMode.MIX,
                mixBlocks = listOf(
                    MixBlock(
                        mode = WorkoutMode.AMRAP,
                        durationSeconds = 10,
                        restSeconds = 5,
                    ),
                    MixBlock(
                        mode = WorkoutMode.FOR_TIME,
                        durationSeconds = 8,
                        restSeconds = 99,
                    ),
                ),
            ),
            prepSeconds = 0,
        )

        engine.start(0L)
        val transition = engine.tick(10_000L)
        assertEquals(IntervalPhase.REST, transition.phase)
        assertEquals(1, transition.currentBlock)
        assertEquals(5, transition.displaySeconds)

        val late = engine.tick(16_000L)
        assertEquals(SessionStatus.RUNNING, late.status)
        assertEquals(IntervalPhase.WORK, late.phase)
        assertEquals(2, late.currentBlock)
        assertEquals(2, late.totalBlocks)
        assertEquals(1, late.displaySeconds)
        assertEquals("For Time", late.title)

        val finished = engine.tick(23_000L)
        assertEquals(SessionStatus.FINISHED, finished.status)
        assertEquals(2, finished.currentBlock)
        assertEquals(23, finished.elapsedSeconds)
    }

    @Test
    fun invalidConfigurationsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            TimerEngine(TimerConfig(mode = WorkoutMode.AMRAP, totalSeconds = 0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TimerEngine(
                TimerConfig(
                    mode = WorkoutMode.TABATA,
                    workSeconds = 20,
                    restSeconds = 0,
                    rounds = 8,
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TimerEngine(
                TimerConfig(
                    mode = WorkoutMode.MIX,
                    mixBlocks = listOf(MixBlock(WorkoutMode.MIX, durationSeconds = 10)),
                ),
            )
        }
    }

    @Test
    fun monotonicTimeCannotMoveBackwards() {
        val engine = TimerEngine(
            config = TimerConfig(mode = WorkoutMode.FOR_TIME),
            prepSeconds = 0,
        )
        engine.start(100L)

        assertThrows(IllegalArgumentException::class.java) {
            engine.tick(99L)
        }
    }

    private fun assertSnapshot(
        snapshot: TimerSnapshot,
        status: SessionStatus,
        phase: IntervalPhase,
        displaySeconds: Int,
        elapsedSeconds: Int,
    ) {
        assertEquals(status, snapshot.status)
        assertEquals(phase, snapshot.phase)
        assertEquals(displaySeconds, snapshot.displaySeconds)
        assertEquals(elapsedSeconds, snapshot.elapsedSeconds)
    }
}
