package com.felipe.tim3r.importer

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.felipe.tim3r.data.WorkoutSectionType
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RoutinePdfImporterTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun importsFiveColumnDigitalWeeklyPlan() {
        val file = File(context.cacheDir, "weekly-plan.pdf")
        createWeeklyPlan(file)

        val workouts = RoutinePdfImporter(context).import(Uri.fromFile(file))

        assertEquals(5, workouts.size)
        assertTrue(workouts.all { it.sections.isNotEmpty() })
        assertTrue(workouts.all { workout -> workout.sections.any { it.type == WorkoutSectionType.AMRAP } })
    }

    @Test
    fun importsIonGymSampleWhenAvailableOnDevice() {
        val file = File(context.filesDir, "rutina-ion-gym.pdf")
        assumeTrue("Sample PDF is not present on this device", file.exists())

        val workouts = RoutinePdfImporter(context).import(Uri.fromFile(file))

        assertEquals(5, workouts.size)
        assertTrue(workouts.sumOf { it.sections.size } >= 15)
        assertTrue(workouts.flatMap { it.sections }.any { it.type == WorkoutSectionType.AMRAP })
        assertTrue(workouts.flatMap { it.sections }.any { it.type == WorkoutSectionType.EMOM })
        assertTrue(workouts.flatMap { it.sections }.any { it.type == WorkoutSectionType.FOR_TIME })
    }

    private fun createWeeklyPlan(file: File) {
        PDFBoxResourceLoader.init(context)
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.A4.apply { })
            document.addPage(page)
            val width = page.mediaBox.width / 5f
            PDPageContentStream(document, page).use { stream ->
                val days = listOf("lunes", "martes", "miercoles", "jueves", "viernes")
                days.forEachIndexed { index, day ->
                    val x = index * width + 8f
                    writeLine(stream, x, 560f, day)
                    writeLine(stream, x, 535f, "A) MOVILIDAD 5'")
                    writeLine(stream, x, 510f, "D) Wod: AMRAP 12'")
                    writeLine(stream, x, 495f, "10 burpees")
                }
            }
            document.save(file)
        }
    }

    private fun writeLine(stream: PDPageContentStream, x: Float, y: Float, text: String) {
        stream.beginText()
        stream.setFont(PDType1Font.HELVETICA, 7f)
        stream.newLineAtOffset(x, y)
        stream.showText(text)
        stream.endText()
    }
}
