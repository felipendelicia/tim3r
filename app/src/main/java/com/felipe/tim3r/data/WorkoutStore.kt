package com.felipe.tim3r.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class WorkoutStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun loadWorkouts(): List<WorkoutDefinition> {
        val raw = preferences.getString(KEY_WORKOUTS, null)
        if (raw.isNullOrBlank()) {
            val defaults = builtInWorkouts()
            saveWorkouts(defaults)
            return defaults
        }
        return runCatching { JSONArray(raw).toWorkoutList() }.getOrElse { builtInWorkouts() }
    }

    fun saveWorkouts(workouts: List<WorkoutDefinition>) {
        preferences.edit { putString(KEY_WORKOUTS, workouts.toJson().toString()) }
    }

    fun loadHistory(): List<WorkoutHistoryEntry> {
        val raw = preferences.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching { JSONArray(raw).toHistoryList() }.getOrDefault(emptyList())
    }

    fun saveHistory(history: List<WorkoutHistoryEntry>) {
        preferences.edit { putString(KEY_HISTORY, history.toHistoryJson().toString()) }
    }

    companion object {
        private const val PREFERENCES_NAME = "tim3r_workout_store"
        private const val KEY_WORKOUTS = "workouts_v1"
        private const val KEY_HISTORY = "history_v1"

        fun dashboard(
            workouts: List<WorkoutDefinition>,
            history: List<WorkoutHistoryEntry>,
            now: LocalDate = LocalDate.now(),
        ): DashboardSummary {
            val zone = ZoneId.systemDefault()
            val completed = history.filter { it.completed }
            val sessionDays = completed.map {
                Instant.ofEpochMilli(it.startedAtEpochMillis).atZone(zone).toLocalDate()
            }.toSet()
            var streak = 0
            var day = now
            while (day in sessionDays) {
                streak += 1
                day = day.minusDays(1)
            }
            val byType = completed
                .flatMap { entry -> entry.sectionResults.filter { it.completed }.map { it.type } }
                .groupingBy { it }
                .eachCount()
            val sevenDays = (6 downTo 0).map { offset ->
                val target = now.minusDays(offset.toLong())
                completed.count {
                    val date = Instant.ofEpochMilli(it.startedAtEpochMillis).atZone(zone).toLocalDate()
                    ChronoUnit.DAYS.between(date, target) == 0L
                }
            }
            return DashboardSummary(
                completedSessions = completed.size,
                totalMinutes = completed.sumOf { it.durationSeconds } / 60,
                currentStreakDays = streak,
                favoriteCount = workouts.count { it.favorite },
                sessionsByType = byType,
                lastSevenDays = sevenDays,
            )
        }
    }
}

private fun List<WorkoutDefinition>.toJson(): JSONArray = JSONArray().also { array ->
    forEach { workout -> array.put(workout.toJson()) }
}

private fun WorkoutDefinition.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("description", description)
    .put("favorite", favorite)
    .put("origin", origin.name)
    .put("scheduledLabel", scheduledLabel)
    .put("createdAt", createdAtEpochMillis)
    .put("sections", JSONArray().also { array -> sections.forEach { array.put(it.toJson()) } })

private fun WorkoutSection.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("title", title)
    .put("type", type.name)
    .put("instructions", instructions)
    .put("exercises", JSONArray(exercises))
    .put("duration", durationSeconds)
    .put("work", workSeconds)
    .put("rest", restSeconds)
    .put("rounds", rounds)
    .put("timeCap", timeCapSeconds)

private fun JSONArray.toWorkoutList(): List<WorkoutDefinition> = buildList {
    for (index in 0 until length()) {
        val json = getJSONObject(index)
        add(
            WorkoutDefinition(
                id = json.getString("id"),
                name = json.getString("name"),
                description = json.optString("description"),
                favorite = json.optBoolean("favorite"),
                origin = runCatching { WorkoutOrigin.valueOf(json.optString("origin")) }
                    .getOrDefault(WorkoutOrigin.USER),
                scheduledLabel = json.optString("scheduledLabel"),
                createdAtEpochMillis = json.optLong("createdAt"),
                sections = json.getJSONArray("sections").toSections(),
            ),
        )
    }
}

private fun JSONArray.toSections(): List<WorkoutSection> = buildList {
    for (index in 0 until length()) {
        val json = getJSONObject(index)
        add(
            WorkoutSection(
                id = json.getString("id"),
                title = json.getString("title"),
                type = WorkoutSectionType.valueOf(json.getString("type")),
                instructions = json.optString("instructions"),
                exercises = json.optJSONArray("exercises")?.toStringList().orEmpty(),
                durationSeconds = json.optInt("duration"),
                workSeconds = json.optInt("work"),
                restSeconds = json.optInt("rest"),
                rounds = json.optInt("rounds", 1),
                timeCapSeconds = json.optInt("timeCap"),
            ),
        )
    }
}

private fun List<WorkoutHistoryEntry>.toHistoryJson(): JSONArray = JSONArray().also { array ->
    forEach { entry ->
        array.put(
            JSONObject()
                .put("id", entry.id)
                .put("workoutId", entry.workoutId)
                .put("workoutName", entry.workoutName)
                .put("startedAt", entry.startedAtEpochMillis)
                .put("completedAt", entry.completedAtEpochMillis)
                .put("duration", entry.durationSeconds)
                .put("completed", entry.completed)
                .put("sections", JSONArray().also { results ->
                    entry.sectionResults.forEach { result ->
                        results.put(
                            JSONObject()
                                .put("sectionId", result.sectionId)
                                .put("title", result.title)
                                .put("type", result.type.name)
                                .put("duration", result.durationSeconds)
                                .put("completed", result.completed),
                        )
                    }
                }),
        )
    }
}

private fun JSONArray.toHistoryList(): List<WorkoutHistoryEntry> = buildList {
    for (index in 0 until length()) {
        val json = getJSONObject(index)
        val results = json.getJSONArray("sections")
        add(
            WorkoutHistoryEntry(
                id = json.getString("id"),
                workoutId = json.optString("workoutId").takeIf { it.isNotBlank() && it != "null" },
                workoutName = json.getString("workoutName"),
                startedAtEpochMillis = json.getLong("startedAt"),
                completedAtEpochMillis = json.getLong("completedAt"),
                durationSeconds = json.getInt("duration"),
                completed = json.getBoolean("completed"),
                sectionResults = buildList {
                    for (resultIndex in 0 until results.length()) {
                        val result = results.getJSONObject(resultIndex)
                        add(
                            SectionResult(
                                sectionId = result.getString("sectionId"),
                                title = result.getString("title"),
                                type = WorkoutSectionType.valueOf(result.getString("type")),
                                durationSeconds = result.getInt("duration"),
                                completed = result.getBoolean("completed"),
                            ),
                        )
                    }
                },
            ),
        )
    }
}

private fun JSONArray.toStringList(): List<String> = buildList {
    for (index in 0 until length()) add(getString(index))
}

private fun builtInWorkouts(): List<WorkoutDefinition> = listOf(
    WorkoutDefinition(
        id = "preset-amrap-12",
        name = "AMRAP 12",
        description = "Preset rápido de doce minutos.",
        favorite = true,
        origin = WorkoutOrigin.BUILT_IN,
        sections = listOf(defaultSection(WorkoutSectionType.AMRAP)),
    ),
    WorkoutDefinition(
        id = "preset-emom-10",
        name = "EMOM 10",
        description = "Diez rondas, una por minuto.",
        origin = WorkoutOrigin.BUILT_IN,
        sections = listOf(defaultSection(WorkoutSectionType.EMOM)),
    ),
    WorkoutDefinition(
        id = "preset-tabata-classic",
        name = "Tabata clásico",
        description = "Ocho rondas de 20 segundos de trabajo y 10 de descanso.",
        favorite = true,
        origin = WorkoutOrigin.BUILT_IN,
        sections = listOf(defaultSection(WorkoutSectionType.TABATA)),
    ),
    WorkoutDefinition(
        id = "preset-for-time",
        name = "For Time 20",
        description = "For Time con time cap de veinte minutos.",
        origin = WorkoutOrigin.BUILT_IN,
        sections = listOf(defaultSection(WorkoutSectionType.FOR_TIME)),
    ),
)
