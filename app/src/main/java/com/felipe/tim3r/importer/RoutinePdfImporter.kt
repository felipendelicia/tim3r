package com.felipe.tim3r.importer

import android.content.Context
import android.graphics.RectF
import android.net.Uri
import com.felipe.tim3r.data.WorkoutDefinition
import com.felipe.tim3r.data.WorkoutOrigin
import com.felipe.tim3r.data.WorkoutSection
import com.felipe.tim3r.data.WorkoutSectionType
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripperByArea
import java.util.Locale

class RoutinePdfImporter(private val context: Context) {
    fun import(uri: Uri): List<WorkoutDefinition> {
        PDFBoxResourceLoader.init(context.applicationContext)
        val input = requireNotNull(context.contentResolver.openInputStream(uri)) {
            "No se pudo abrir el documento"
        }
        return input.use { stream ->
            PDDocument.load(stream).use { document ->
                require(document.numberOfPages > 0) { "El PDF no contiene páginas" }
                val page = document.getPage(0)
                val box = page.mediaBox
                val dayNames = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes")
                val columnWidth = box.width / dayNames.size
                dayNames.mapIndexedNotNull { index, day ->
                    val stripper = PDFTextStripperByArea().apply {
                        sortByPosition = true
                        addRegion(
                            "day",
                            RectF(
                                box.lowerLeftX + index * columnWidth,
                                box.lowerLeftY,
                                box.lowerLeftX + (index + 1) * columnWidth,
                                box.lowerLeftY + box.height,
                            ),
                        )
                    }
                    stripper.extractRegions(page)
                    val text = stripper.getTextForRegion("day").trim()
                    parseDay(day, text).takeIf { it.sections.isNotEmpty() }
                }
            }
        }
    }

    private fun parseDay(day: String, rawText: String): WorkoutDefinition {
        val normalized = rawText
            .replace('\u0000', ' ')
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals(day, ignoreCase = true) }
        val chunks = mutableListOf<MutableList<String>>()
        normalized.forEach { line ->
            if (SECTION_HEADER.matches(line) || SECTION_HEADER.containsMatchIn(line)) {
                chunks += mutableListOf(line)
            } else if (chunks.isEmpty()) {
                chunks += mutableListOf(line)
            } else {
                chunks.last() += line
            }
        }
        val sections = chunks.mapNotNull(::parseSection)
        return WorkoutDefinition(
            name = "$day · Rutina importada",
            description = "Importado desde PDF. Revisa cargas, tiempos y escalas antes de entrenar.",
            origin = WorkoutOrigin.IMPORTED,
            scheduledLabel = day,
            sections = sections,
        )
    }

    private fun parseSection(lines: List<String>): WorkoutSection? {
        if (lines.isEmpty()) return null
        val fullText = lines.joinToString("\n")
        val lower = fullText.lowercase(Locale.getDefault())
        val type = when {
            "amrap" in lower -> WorkoutSectionType.AMRAP
            "emom" in lower || "otm" in lower -> WorkoutSectionType.EMOM
            "for time" in lower || "a completar" in lower -> WorkoutSectionType.FOR_TIME
            "tabata" in lower -> WorkoutSectionType.TABATA
            "strenght" in lower || "strength" in lower || "weightlifting" in lower -> WorkoutSectionType.STRENGTH
            "skill" in lower -> WorkoutSectionType.SKILL
            "warm up" in lower || "warm-up" in lower -> WorkoutSectionType.WARM_UP
            "movilidad" in lower -> WorkoutSectionType.MOBILITY
            "rest" in lower || "descanso" in lower -> WorkoutSectionType.REST
            else -> WorkoutSectionType.NOTE
        }
        val title = lines.first().replace(Regex("^[A-Da-d]\\)\\s*"), "").trim()
            .ifBlank { type.displayName }
        val durationMinutes = when (type) {
            WorkoutSectionType.FOR_TIME -> TIME_CAP.findAll(lower).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()
            else -> TIMED_MODE.find(lower)?.groupValues?.get(1)?.toIntOrNull()
                ?: MINUTES.find(lower)?.groupValues?.get(1)?.toIntOrNull()
        } ?: 0
        val rounds = ROUNDS.find(lower)?.groupValues?.get(1)?.toIntOrNull()
            ?: PREFIX_ROUNDS.find(lower)?.groupValues?.get(1)?.toIntOrNull()
            ?: 1
        val exercises = lines.drop(1)
            .filterNot { it.startsWith("TC", ignoreCase = true) }
            .take(20)
        return WorkoutSection(
            title = title,
            type = type,
            instructions = fullText,
            exercises = exercises,
            durationSeconds = if (type == WorkoutSectionType.FOR_TIME) 0 else durationMinutes * 60,
            workSeconds = if (type == WorkoutSectionType.EMOM) 60 else 0,
            rounds = rounds.coerceAtLeast(1),
            timeCapSeconds = if (type == WorkoutSectionType.FOR_TIME) durationMinutes * 60 else 0,
        )
    }

    private companion object {
        val SECTION_HEADER = Regex("^[A-Da-d]\\)\\s*.+")
        val TIMED_MODE = Regex("(?:amrap|emom|otm)\\s*(\\d+)[’'′]?")
        val MINUTES = Regex("(\\d+)\\s*[’'′]")
        val TIME_CAP = Regex("(?:tc|time\\s*cap)\\s*:?\\s*(\\d+)")
        val ROUNDS = Regex("(?:x|por)\\s*(\\d+)\\s*(?:rounds?|rondas?)")
        val PREFIX_ROUNDS = Regex("(\\d+)\\s*(?:rondas?|rounds?)")
    }
}
