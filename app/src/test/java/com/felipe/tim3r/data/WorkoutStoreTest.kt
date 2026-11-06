package com.felipe.tim3r.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class WorkoutStoreTest {
    @Test
    fun dashboardAggregatesCompletedSessionsFavoritesAndTypes() {
        val now = LocalDate.of(2026, 9, 1)
        val zone = ZoneId.systemDefault()
        val workouts = listOf(
            WorkoutDefinition(name = "Favorito", favorite = true),
            WorkoutDefinition(name = "Normal"),
        )
        val history = listOf(
            historyEntry(
                date = now,
                durationSeconds = 30 * 60,
                type = WorkoutSectionType.AMRAP,
                zone = zone,
            ),
            historyEntry(
                date = now.minusDays(1),
                durationSeconds = 45 * 60,
                type = WorkoutSectionType.STRENGTH,
                zone = zone,
            ),
            historyEntry(
                date = now.minusDays(3),
                durationSeconds = 20 * 60,
                type = WorkoutSectionType.AMRAP,
                zone = zone,
                completed = false,
            ),
        )

        val summary = WorkoutStore.dashboard(workouts, history, now)

        assertEquals(2, summary.completedSessions)
        assertEquals(75, summary.totalMinutes)
        assertEquals(2, summary.currentStreakDays)
        assertEquals(1, summary.favoriteCount)
        assertEquals(1, summary.sessionsByType[WorkoutSectionType.AMRAP])
        assertEquals(1, summary.sessionsByType[WorkoutSectionType.STRENGTH])
        assertEquals(listOf(0, 0, 0, 0, 0, 1, 1), summary.lastSevenDays)
    }

    private fun historyEntry(
        date: LocalDate,
        durationSeconds: Int,
        type: WorkoutSectionType,
        zone: ZoneId,
        completed: Boolean = true,
    ): WorkoutHistoryEntry = WorkoutHistoryEntry(
        workoutName = "Test",
        startedAtEpochMillis = date.atStartOfDay(zone).toInstant().toEpochMilli(),
        completedAtEpochMillis = date.atTime(1, 0).atZone(zone).toInstant().toEpochMilli(),
        durationSeconds = durationSeconds,
        completed = completed,
        sectionResults = listOf(
            SectionResult("section", type.displayName, type, durationSeconds, completed),
        ),
    )
}
