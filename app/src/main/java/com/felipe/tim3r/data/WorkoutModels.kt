package com.felipe.tim3r.data

import java.util.UUID

enum class WorkoutSectionType(val displayName: String) {
    MOBILITY("Movilidad"),
    WARM_UP("Warm-up"),
    STRENGTH("Fuerza"),
    SKILL("Técnica"),
    AMRAP("AMRAP"),
    FOR_TIME("For Time"),
    EMOM("EMOM"),
    TABATA("Tabata"),
    REST("Descanso"),
    NOTE("Nota"),
}

enum class WorkoutOrigin { BUILT_IN, USER, IMPORTED }

data class WorkoutSection(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val type: WorkoutSectionType,
    val instructions: String = "",
    val exercises: List<String> = emptyList(),
    val durationSeconds: Int = 0,
    val workSeconds: Int = 0,
    val restSeconds: Int = 0,
    val rounds: Int = 1,
    val timeCapSeconds: Int = 0,
)

data class WorkoutDefinition(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val favorite: Boolean = false,
    val origin: WorkoutOrigin = WorkoutOrigin.USER,
    val scheduledLabel: String = "",
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val sections: List<WorkoutSection> = emptyList(),
) {
    val estimatedSeconds: Int
        get() = sections.sumOf { section ->
            when (section.type) {
                WorkoutSectionType.TABATA ->
                    (section.workSeconds + section.restSeconds) * section.rounds
                WorkoutSectionType.EMOM -> section.durationSeconds.takeIf { it > 0 }
                    ?: (section.workSeconds * section.rounds)
                WorkoutSectionType.FOR_TIME -> section.timeCapSeconds
                else -> section.durationSeconds
            }
        }
}

data class SectionResult(
    val sectionId: String,
    val title: String,
    val type: WorkoutSectionType,
    val durationSeconds: Int,
    val completed: Boolean,
)

data class WorkoutHistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val workoutId: String? = null,
    val workoutName: String,
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long,
    val durationSeconds: Int,
    val completed: Boolean,
    val sectionResults: List<SectionResult>,
)

data class DashboardSummary(
    val completedSessions: Int,
    val totalMinutes: Int,
    val currentStreakDays: Int,
    val favoriteCount: Int,
    val sessionsByType: Map<WorkoutSectionType, Int>,
    val lastSevenDays: List<Int>,
)

fun defaultSection(type: WorkoutSectionType): WorkoutSection = when (type) {
    WorkoutSectionType.MOBILITY -> WorkoutSection(
        title = "Movilidad",
        type = type,
        durationSeconds = 5 * 60,
    )
    WorkoutSectionType.WARM_UP -> WorkoutSection(
        title = "Warm-up",
        type = type,
        rounds = 3,
    )
    WorkoutSectionType.STRENGTH -> WorkoutSection(title = "Fuerza", type = type, rounds = 5)
    WorkoutSectionType.SKILL -> WorkoutSection(title = "Técnica", type = type, durationSeconds = 8 * 60)
    WorkoutSectionType.AMRAP -> WorkoutSection(title = "AMRAP", type = type, durationSeconds = 12 * 60)
    WorkoutSectionType.FOR_TIME -> WorkoutSection(title = "For Time", type = type, timeCapSeconds = 20 * 60)
    WorkoutSectionType.EMOM -> WorkoutSection(
        title = "EMOM",
        type = type,
        durationSeconds = 10 * 60,
        workSeconds = 60,
        rounds = 10,
    )
    WorkoutSectionType.TABATA -> WorkoutSection(
        title = "Tabata",
        type = type,
        workSeconds = 20,
        restSeconds = 10,
        rounds = 8,
    )
    WorkoutSectionType.REST -> WorkoutSection(title = "Descanso", type = type, durationSeconds = 60)
    WorkoutSectionType.NOTE -> WorkoutSection(title = "Indicaciones", type = type)
}
