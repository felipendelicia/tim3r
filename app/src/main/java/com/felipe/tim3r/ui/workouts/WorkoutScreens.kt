package com.felipe.tim3r

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felipe.tim3r.data.WorkoutDefinition
import com.felipe.tim3r.data.WorkoutHistoryEntry
import com.felipe.tim3r.data.WorkoutOrigin
import com.felipe.tim3r.data.WorkoutSection
import com.felipe.tim3r.data.WorkoutSectionType
import com.felipe.tim3r.ui.theme.TimerBlue
import com.felipe.tim3r.ui.theme.TimerGreen
import com.felipe.tim3r.ui.theme.TimerMuted
import com.felipe.tim3r.ui.theme.TimerOrange
import com.felipe.tim3r.ui.theme.TimerSurfaceHigh
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun WorkoutLibraryScreen(state: Tim3rUiState, viewModel: MainViewModel) {
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importPdf)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            WodTopBar("MIS WODS", viewModel::showHome)
            Spacer(Modifier.height(22.dp))
            Text("BIBLIOTECA", color = TimerMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text("Entrena con un plan", fontSize = 29.sp, fontWeight = FontWeight.Black)
            Text(
                "Crea rutinas completas, guarda presets o importa la planificación que te envía tu coach.",
                color = TimerMuted,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = viewModel::createWorkout,
                    modifier = Modifier.weight(1f).height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TimerOrange, contentColor = Color.Black),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("+ CREAR WOD", fontWeight = FontWeight.Black) }
                OutlinedButton(
                    onClick = { pdfLauncher.launch(arrayOf("application/pdf")) },
                    modifier = Modifier.weight(1f).height(54.dp),
                    enabled = !state.importInProgress,
                    shape = RoundedCornerShape(16.dp),
                ) { Text(if (state.importInProgress) "LEYENDO…" else "IMPORTAR PDF", fontWeight = FontWeight.Bold) }
            }
            state.message?.let {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clickable(onClick = viewModel::dismissMessage),
                    color = TimerBlue.copy(alpha = .12f),
                    shape = RoundedCornerShape(14.dp),
                ) { Text(it, modifier = Modifier.padding(14.dp), color = TimerBlue, fontSize = 13.sp) }
            }
            Spacer(Modifier.height(10.dp))
        }

        val favorites = state.workouts.filter { it.favorite }
        if (favorites.isNotEmpty()) {
            item { SectionHeader("FAVORITOS", "${favorites.size}") }
            items(favorites, key = { "favorite-${it.id}" }) { workout ->
                WorkoutLibraryCard(workout, viewModel)
            }
        }
        item { SectionHeader("TODOS", "${state.workouts.size}") }
        items(state.workouts, key = { it.id }) { workout ->
            WorkoutLibraryCard(workout, viewModel)
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

@Composable
private fun WorkoutLibraryCard(workout: WorkoutDefinition, viewModel: MainViewModel) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { viewModel.openWorkout(workout.id) },
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(52.dp).background(sectionColor(workout.sections.firstOrNull()?.type).copy(alpha = .15f), RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    workout.sections.size.toString(),
                    color = sectionColor(workout.sections.firstOrNull()?.type),
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(workout.name, fontWeight = FontWeight.Black, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (workout.scheduledLabel.isNotBlank()) {
                        Text(" · ${workout.scheduledLabel}", color = TimerBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    "${workout.sections.size} secciones · ${formatWodDuration(workout.estimatedSeconds)}",
                    color = TimerMuted,
                    fontSize = 12.sp,
                )
                Text(
                    workout.sections.joinToString(" · ") { it.type.displayName },
                    color = sectionColor(workout.sections.firstOrNull()?.type),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                if (workout.favorite) "★" else "☆",
                modifier = Modifier.clickable { viewModel.toggleWorkoutFavorite(workout.id) }.padding(10.dp),
                color = if (workout.favorite) Color(0xFFFFC247) else TimerMuted,
                fontSize = 25.sp,
            )
        }
    }
}

@Composable
fun WorkoutDetailScreen(state: Tim3rUiState, viewModel: MainViewModel) {
    val workout = state.workouts.firstOrNull { it.id == state.selectedWorkoutId }
    if (workout == null) {
        viewModel.showWorkouts()
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            WodTopBar(workout.name, viewModel::showWorkouts)
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = TimerSurfaceHigh, shape = RoundedCornerShape(50)) {
                    Text(
                        when (workout.origin) {
                            WorkoutOrigin.BUILT_IN -> "PRESET"
                            WorkoutOrigin.IMPORTED -> "IMPORTADO"
                            WorkoutOrigin.USER -> "PERSONAL"
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = TimerGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (workout.favorite) "★ FAVORITO" else "☆ FAVORITO",
                    modifier = Modifier.clickable { viewModel.toggleWorkoutFavorite(workout.id) }.padding(8.dp),
                    color = if (workout.favorite) Color(0xFFFFC247) else TimerMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(workout.name, fontSize = 30.sp, fontWeight = FontWeight.Black)
            if (workout.description.isNotBlank()) Text(workout.description, color = TimerMuted, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Text("${workout.sections.size} SECCIONES  ·  ${formatWodDuration(workout.estimatedSeconds)} EST.", color = TimerBlue, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Spacer(Modifier.height(14.dp))
        }
        itemsIndexed(workout.sections, key = { _, section -> section.id }) { index, section ->
            SectionPreviewCard(index, section)
        }
        item {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.startWod(workout.id) },
                modifier = Modifier.fillMaxWidth().height(62.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TimerGreen, contentColor = Color.Black),
                shape = RoundedCornerShape(18.dp),
            ) { Text("SEGUIR WOD PASO A PASO", fontWeight = FontWeight.Black, fontSize = 15.sp) }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.editWorkout(workout.id) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("EDITAR") }
                OutlinedButton(onClick = { viewModel.duplicateWorkout(workout.id) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("DUPLICAR") }
            }
            if (workout.origin != WorkoutOrigin.BUILT_IN) {
                Text(
                    "ELIMINAR WOD",
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.deleteWorkout(workout.id) }.padding(18.dp),
                    textAlign = TextAlign.Center,
                    color = Color(0xFFFF746C),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun SectionPreviewCard(index: Int, section: WorkoutSection) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${('A'.code + index).toChar()}", color = sectionColor(section.type), fontSize = 22.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(section.title.ifBlank { section.type.displayName }, fontWeight = FontWeight.Black)
                    Text(sectionSummary(section), color = TimerMuted, fontSize = 12.sp)
                }
                Text(section.type.displayName.uppercase(), color = sectionColor(section.type), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            if (section.exercises.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)
                section.exercises.take(5).forEach { exercise -> Text("• $exercise", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
                if (section.exercises.size > 5) Text("+ ${section.exercises.size - 5} indicaciones", color = TimerMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun WorkoutEditorScreen(state: Tim3rUiState, viewModel: MainViewModel) {
    val draft = state.workoutDraft ?: return
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            WodTopBar("EDITOR DE WOD", viewModel::showWorkouts)
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = draft.name,
                onValueChange = viewModel::updateDraftName,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nombre del WOD") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = draft.description,
                onValueChange = viewModel::updateDraftDescription,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Descripción o intención") },
                minLines = 2,
                shape = RoundedCornerShape(16.dp),
            )
            state.message?.let { Text(it, color = Color(0xFFFF746C), fontSize = 12.sp) }
            Spacer(Modifier.height(8.dp))
            SectionHeader("SECCIONES", draft.sections.size.toString())
        }
        itemsIndexed(draft.sections, key = { _, section -> section.id }) { index, section ->
            SectionEditorCard(index, section, draft.sections.size > 1, viewModel)
        }
        item {
            Text("AGREGAR SECCIÓN", color = TimerMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(WorkoutSectionType.entries) { type ->
                    OutlinedButton(onClick = { viewModel.addDraftSection(type) }, shape = RoundedCornerShape(50)) {
                        Text("+ ${type.displayName}", color = sectionColor(type), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = viewModel::saveDraft,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TimerOrange, contentColor = Color.Black),
                shape = RoundedCornerShape(18.dp),
            ) { Text("GUARDAR WOD", fontWeight = FontWeight.Black) }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun SectionEditorCard(index: Int, section: WorkoutSection, canRemove: Boolean, viewModel: MainViewModel) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SECCIÓN ${index + 1}", color = TimerMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.weight(1f))
                Text("↑", modifier = Modifier.clickable { viewModel.moveDraftSection(index, -1) }.padding(7.dp), color = TimerMuted, fontWeight = FontWeight.Black)
                Text("↓", modifier = Modifier.clickable { viewModel.moveDraftSection(index, 1) }.padding(7.dp), color = TimerMuted, fontWeight = FontWeight.Black)
                if (canRemove) Text("ELIMINAR", modifier = Modifier.clickable { viewModel.removeDraftSection(index) }.padding(5.dp), color = Color(0xFFFF746C), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { viewModel.cycleDraftSectionType(index) },
                color = sectionColor(section.type).copy(alpha = .14f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(section.type.displayName.uppercase(), color = sectionColor(section.type), fontWeight = FontWeight.Black)
                    Spacer(Modifier.weight(1f))
                    Text("CAMBIAR ↻", color = sectionColor(section.type), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = section.title,
                onValueChange = { viewModel.updateDraftSectionTitle(index, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Título") },
                singleLine = true,
                shape = RoundedCornerShape(13.dp),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = section.instructions,
                onValueChange = { viewModel.updateDraftSectionInstructions(index, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ejercicios e indicaciones (una línea por ejercicio)") },
                minLines = 3,
                shape = RoundedCornerShape(13.dp),
            )
            Spacer(Modifier.height(12.dp))
            when (section.type) {
                WorkoutSectionType.TABATA -> {
                    EditorStepper("TRABAJO", "${section.workSeconds}s", { viewModel.adjustDraftSectionWork(index, -5) }, { viewModel.adjustDraftSectionWork(index, 5) })
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline)
                    EditorStepper("DESCANSO", "${section.restSeconds}s", { viewModel.adjustDraftSectionRest(index, -5) }, { viewModel.adjustDraftSectionRest(index, 5) })
                }
                WorkoutSectionType.EMOM -> EditorStepper("INTERVALO", formatWodDuration(section.workSeconds), { viewModel.adjustDraftSectionWork(index, -5) }, { viewModel.adjustDraftSectionWork(index, 5) })
                WorkoutSectionType.NOTE, WorkoutSectionType.WARM_UP, WorkoutSectionType.STRENGTH -> Unit
                else -> EditorStepper(
                    if (section.type == WorkoutSectionType.FOR_TIME) "TIME CAP" else "DURACIÓN",
                    formatWodDuration(if (section.type == WorkoutSectionType.FOR_TIME) section.timeCapSeconds else section.durationSeconds),
                    { viewModel.adjustDraftSectionDuration(index, -60) },
                    { viewModel.adjustDraftSectionDuration(index, 60) },
                )
            }
            if (section.type in setOf(WorkoutSectionType.WARM_UP, WorkoutSectionType.STRENGTH, WorkoutSectionType.AMRAP, WorkoutSectionType.FOR_TIME, WorkoutSectionType.EMOM, WorkoutSectionType.TABATA)) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline)
                EditorStepper("RONDAS / SERIES", section.rounds.toString(), { viewModel.adjustDraftSectionRounds(index, -1) }, { viewModel.adjustDraftSectionRounds(index, 1) })
            }
            if (section.type in setOf(WorkoutSectionType.AMRAP, WorkoutSectionType.FOR_TIME) && section.rounds > 1) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline)
                EditorStepper("DESCANSO ENTRE SERIES", formatWodDuration(section.restSeconds), { viewModel.adjustDraftSectionRest(index, -15) }, { viewModel.adjustDraftSectionRest(index, 15) })
            }
        }
    }
}

@Composable
private fun EditorStepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = TimerMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("−", modifier = Modifier.size(34.dp).background(TimerSurfaceHigh, CircleShape).clickable(onClick = onMinus).padding(top = 4.dp), textAlign = TextAlign.Center, fontSize = 19.sp)
        Text(value, modifier = Modifier.width(74.dp), textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Text("+", modifier = Modifier.size(34.dp).background(TimerSurfaceHigh, CircleShape).clickable(onClick = onPlus).padding(top = 4.dp), textAlign = TextAlign.Center, fontSize = 19.sp)
    }
}

@Composable
fun WodRunnerScreen(state: Tim3rUiState, viewModel: MainViewModel) {
    val runner = state.runner ?: return
    val workout = state.workouts.firstOrNull { it.id == runner.workoutId } ?: return
    if (runner.completed) {
        WodCompletedScreen(workout, runner, viewModel)
        return
    }
    val section = workout.sections.getOrNull(runner.currentSectionIndex) ?: return
    val target = sectionTargetSeconds(section)
    val countsDown = section.type in setOf(
        WorkoutSectionType.MOBILITY,
        WorkoutSectionType.SKILL,
        WorkoutSectionType.AMRAP,
        WorkoutSectionType.EMOM,
        WorkoutSectionType.TABATA,
        WorkoutSectionType.REST,
    ) && target > 0
    val displaySeconds = if (countsDown) (target - runner.sectionElapsedSeconds).coerceAtLeast(0) else runner.sectionElapsedSeconds
    val progress = if (target > 0) (runner.sectionElapsedSeconds.toFloat() / target).coerceIn(0f, 1f)
    else (runner.currentSectionIndex.toFloat() / workout.sections.size).coerceIn(0f, 1f)

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("SECCIÓN ${runner.currentSectionIndex + 1} / ${workout.sections.size}", color = sectionColor(section.type), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            Text(formatWodDuration(runner.sessionElapsedSeconds), color = TimerMuted, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            color = sectionColor(section.type),
            trackColor = TimerSurfaceHigh,
        )
        Spacer(Modifier.height(38.dp))
        Text(section.type.displayName.uppercase(), color = sectionColor(section.type), fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        Text(section.title, fontSize = 28.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Spacer(Modifier.height(26.dp))
        Text(displayTime(displaySeconds), fontSize = 70.sp, lineHeight = 75.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, letterSpacing = (-4).sp)
        Text(if (countsDown) "TIEMPO RESTANTE" else "TIEMPO DE SECCIÓN", color = TimerMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        if (section.type == WorkoutSectionType.FOR_TIME && section.timeCapSeconds > 0) {
            Text("TIME CAP ${formatWodDuration(section.timeCapSeconds)}", color = TimerGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(28.dp))
        Surface(modifier = Modifier.fillMaxWidth().weight(1f), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp)) {
            LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (section.exercises.isNotEmpty()) {
                    itemsIndexed(section.exercises) { index, exercise ->
                        Row {
                            Text("${index + 1}", modifier = Modifier.width(28.dp), color = sectionColor(section.type), fontWeight = FontWeight.Black)
                            Text(exercise, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                        }
                    }
                } else {
                    item { Text(section.instructions.ifBlank { "Completa esta sección siguiendo las indicaciones de tu coach." }, color = TimerMuted) }
                }
            }
        }
        val next = workout.sections.getOrNull(runner.currentSectionIndex + 1)
        if (next != null) {
            Text("SIGUE: ${next.type.displayName.uppercase()} · ${next.title}", modifier = Modifier.padding(vertical = 10.dp), color = TimerMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        } else Spacer(Modifier.height(10.dp))
        if (state.controlsLocked) {
            Button(
                onClick = viewModel::toggleControlsLock,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TimerSurfaceHigh),
                shape = RoundedCornerShape(18.dp),
            ) { Text("🔒  DESBLOQUEAR CONTROLES", fontWeight = FontWeight.Black) }
        } else {
            Button(
                onClick = viewModel::completeWodSection,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = sectionColor(section.type), contentColor = Color.Black),
                shape = RoundedCornerShape(18.dp),
            ) { Text(if (runner.currentSectionIndex == workout.sections.lastIndex) "COMPLETAR WOD" else "SECCIÓN COMPLETADA", fontWeight = FontWeight.Black) }
            OutlinedButton(onClick = viewModel::toggleWodPause, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(16.dp)) {
                Text(if (runner.paused) "REANUDAR" else "PAUSAR")
            }
            Text("BLOQUEAR CONTROLES", modifier = Modifier.fillMaxWidth().clickable(onClick = viewModel::toggleControlsLock).padding(7.dp), textAlign = TextAlign.Center, color = TimerMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun WodCompletedScreen(workout: WorkoutDefinition, runner: WodRunnerState, viewModel: MainViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.height(30.dp))
            Text("✓", color = TimerGreen, fontSize = 64.sp, fontWeight = FontWeight.Black)
            Text("WOD COMPLETADO", color = TimerGreen, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text(workout.name, fontSize = 29.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Text(displayTime(runner.sessionElapsedSeconds), fontFamily = FontFamily.Monospace, fontSize = 48.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(10.dp))
        }
        items(runner.results) { result ->
            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("✓", color = TimerGreen, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(result.title, fontWeight = FontWeight.Bold)
                        Text(result.type.displayName, color = TimerMuted, fontSize = 11.sp)
                    }
                    Text(formatWodDuration(result.durationSeconds), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Button(
                onClick = viewModel::exitWodRunner,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TimerGreen, contentColor = Color.Black),
                shape = RoundedCornerShape(18.dp),
            ) { Text("VER PROGRESO", fontWeight = FontWeight.Black) }
        }
    }
}

@Composable
fun SettingsScreen(state: Tim3rUiState, viewModel: MainViewModel) {
    val settings = state.settings
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            WodTopBar("AJUSTES", viewModel::showHome)
            Spacer(Modifier.height(24.dp))
            Text("ENTRENAMIENTO", color = TimerMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text("A tu manera", fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text("Estos valores se guardan en el teléfono y se aplican a los próximos timers.", color = TimerMuted, fontSize = 13.sp)
            Spacer(Modifier.height(18.dp))
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(onClick = viewModel::cyclePrepSeconds),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
            ) {
                Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("CUENTA INICIAL", fontWeight = FontWeight.Black)
                        Text("Toca para cambiar: 0, 3, 5, 10 o 15 segundos", color = TimerMuted, fontSize = 11.sp)
                    }
                    Text("${settings.prepSeconds}s", color = TimerOrange, fontFamily = FontFamily.Monospace, fontSize = 24.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        item { SettingsToggle("SONIDO", "Beep en cuenta regresiva e intervalos", settings.soundEnabled, viewModel::toggleSound) }
        item { SettingsToggle("VIBRACIÓN", "Respuesta háptica en los avisos", settings.vibrationEnabled, viewModel::toggleVibration) }
        item { SettingsToggle("PANTALLA ACTIVA", "Evita que se apague únicamente mientras entrenas", settings.keepScreenOn, viewModel::toggleKeepScreenOn) }
        item {
            Spacer(Modifier.height(8.dp))
            Surface(color = TimerBlue.copy(alpha = .1f), shape = RoundedCornerShape(16.dp)) {
                Text("Durante un timer puedes bloquear los controles para evitar pausas accidentales. Salir de una sesión activa siempre requiere confirmación.", modifier = Modifier.padding(16.dp), color = TimerBlue, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SettingsToggle(title: String, description: String, checked: Boolean, onToggle: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Black)
                Text(description, color = TimerMuted, fontSize = 11.sp)
            }
            Switch(
                checked = checked,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(checkedTrackColor = TimerGreen, checkedThumbColor = Color.Black),
            )
        }
    }
}

@Composable
fun ProgressScreen(state: Tim3rUiState, viewModel: MainViewModel) {
    val summary = state.dashboard
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            WodTopBar("PROGRESO", viewModel::showHome)
            Spacer(Modifier.height(22.dp))
            Text("TU ENTRENAMIENTO", color = TimerMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text("Dashboard", fontSize = 30.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DashboardMetric("SESIONES", summary.completedSessions.toString(), TimerOrange, Modifier.weight(1f))
                DashboardMetric("MINUTOS", summary.totalMinutes.toString(), TimerBlue, Modifier.weight(1f))
                DashboardMetric("RACHA", "${summary.currentStreakDays}d", TimerGreen, Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            WeeklyChart(summary.lastSevenDays)
            Spacer(Modifier.height(16.dp))
            SectionHeader("POR TIPO", summary.sessionsByType.values.sum().toString())
        }
        if (summary.sessionsByType.isEmpty()) {
            item { EmptyProgress() }
        } else {
            items(summary.sessionsByType.entries.sortedByDescending { it.value }) { (type, count) ->
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(15.dp)) {
                    Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(sectionColor(type), CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Text(type.displayName, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text(count.toString(), color = sectionColor(type), fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            SectionHeader("HISTORIAL", state.history.size.toString())
        }
        if (state.history.isEmpty()) {
            item { EmptyProgress() }
        } else {
            items(state.history, key = { it.id }) { entry -> HistoryCard(entry) }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

@Composable
private fun DashboardMetric(label: String, value: String, color: Color, modifier: Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(17.dp)) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = color, fontSize = 25.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            Text(label, color = TimerMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun WeeklyChart(values: List<Int>) {
    val max = values.maxOrNull()?.coerceAtLeast(1) ?: 1
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text("ÚLTIMOS 7 DÍAS", color = TimerMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Canvas(Modifier.fillMaxWidth().height(90.dp).padding(top = 14.dp)) {
                val slot = size.width / 7
                values.forEachIndexed { index, value ->
                    val height = if (value == 0) 3.dp.toPx() else size.height * value / max
                    val x = slot * index + slot / 2
                    drawLine(
                        color = if (value > 0) TimerGreen else TimerSurfaceHigh,
                        start = Offset(x, size.height),
                        end = Offset(x, size.height - height),
                        strokeWidth = slot * .45f,
                        cap = StrokeCap.Round,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                val locale = Locale.forLanguageTag("es")
                (6 downTo 0).forEach { offset ->
                    val label = LocalDate.now().minusDays(offset.toLong()).dayOfWeek
                        .getDisplayName(TextStyle.NARROW, locale)
                    Text(label, color = TimerMuted, fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(entry: WorkoutHistoryEntry) {
    val date = Instant.ofEpochMilli(entry.startedAtEpochMillis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd MMM · HH:mm", Locale.forLanguageTag("es")))
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(17.dp)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (entry.completed) "✓" else "!", color = if (entry.completed) TimerGreen else Color(0xFFFFC247), fontSize = 20.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.workoutName, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$date · ${entry.sectionResults.size} secciones", color = TimerMuted, fontSize = 11.sp)
            }
            Text(formatWodDuration(entry.durationSeconds), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyProgress() {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp)) {
        Text("Completa tu primer entrenamiento para empezar a construir estadísticas.", modifier = Modifier.padding(20.dp), color = TimerMuted, textAlign = TextAlign.Center, fontSize = 13.sp)
    }
}

@Composable
private fun WodTopBar(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(modifier = Modifier.size(44.dp).clickable(onClick = onBack), shape = CircleShape, color = TimerSurfaceHigh) {
            Box(contentAlignment = Alignment.Center) { Text("‹", fontSize = 32.sp, fontWeight = FontWeight.Light) }
        }
        Spacer(Modifier.width(14.dp))
        Text(title.uppercase(), fontSize = 21.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SectionHeader(title: String, count: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = TimerMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Spacer(Modifier.weight(1f))
        Text(count, color = TimerMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
    }
}

private fun sectionColor(type: WorkoutSectionType?): Color = when (type) {
    WorkoutSectionType.MOBILITY -> Color(0xFF62E6A5)
    WorkoutSectionType.WARM_UP -> Color(0xFFFFC247)
    WorkoutSectionType.STRENGTH -> Color(0xFFFF746C)
    WorkoutSectionType.SKILL -> Color(0xFFC7A4FF)
    WorkoutSectionType.AMRAP -> TimerOrange
    WorkoutSectionType.FOR_TIME -> TimerGreen
    WorkoutSectionType.EMOM -> TimerBlue
    WorkoutSectionType.TABATA -> Color(0xFFFFC247)
    WorkoutSectionType.REST -> TimerBlue
    WorkoutSectionType.NOTE, null -> TimerMuted
}

private fun sectionTargetSeconds(section: WorkoutSection): Int = when (section.type) {
    WorkoutSectionType.TABATA -> (section.workSeconds + section.restSeconds) * section.rounds
    WorkoutSectionType.EMOM -> section.durationSeconds.takeIf { it > 0 } ?: section.workSeconds * section.rounds
    WorkoutSectionType.FOR_TIME -> section.timeCapSeconds
    else -> section.durationSeconds
}

private fun sectionSummary(section: WorkoutSection): String {
    val time = sectionTargetSeconds(section)
    val parts = buildList {
        if (time > 0) add(formatWodDuration(time))
        if (section.rounds > 1) add("${section.rounds} rondas/series")
        if (section.exercises.isNotEmpty()) add("${section.exercises.size} indicaciones")
    }
    return parts.joinToString(" · ").ifBlank { "Avance manual" }
}

private fun displayTime(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return "%02d:%02d".format(Locale.US, safe / 60, safe % 60)
}

private fun formatWodDuration(seconds: Int): String {
    if (seconds <= 0) return "Manual"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remaining = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(Locale.US, hours, minutes, remaining)
    else "%02d:%02d".format(Locale.US, minutes, remaining)
}
