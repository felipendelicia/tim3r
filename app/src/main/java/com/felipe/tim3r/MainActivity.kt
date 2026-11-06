package com.felipe.tim3r

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import com.felipe.tim3r.data.AppSettings
import com.felipe.tim3r.domain.IntervalPhase
import com.felipe.tim3r.domain.MixBlock
import com.felipe.tim3r.domain.SessionStatus
import com.felipe.tim3r.domain.TimerSnapshot
import com.felipe.tim3r.domain.WorkoutMode
import com.felipe.tim3r.ui.theme.Tim3rTheme
import com.felipe.tim3r.ui.theme.TimerBlue
import com.felipe.tim3r.ui.theme.TimerGreen
import com.felipe.tim3r.ui.theme.TimerMuted
import com.felipe.tim3r.ui.theme.TimerOrange
import com.felipe.tim3r.ui.theme.TimerSurfaceHigh

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            Tim3rTheme {
                Tim3rApp()
            }
        }
    }
}

@Composable
private fun Tim3rApp(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current
    val isActiveTraining = (state.screen == AppScreen.TIMER && state.snapshot?.status != SessionStatus.FINISHED) ||
        (state.screen == AppScreen.WOD_RUNNER && state.runner?.completed == false)
    val keepScreenOn = isActiveTraining && state.settings.keepScreenOn
    var confirmExit by remember { mutableStateOf(false) }

    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    BackHandler(enabled = state.screen != AppScreen.HOME) {
        if (isActiveTraining) confirmExit = true else viewModel.back()
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("¿Salir del entrenamiento?") },
            text = { Text("El progreso parcial del WOD se guardará si ya completaste una sección.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmExit = false
                    if (state.screen == AppScreen.WOD_RUNNER) viewModel.abortWod() else viewModel.exitTimer()
                }) { Text("SALIR", color = Color(0xFFFF746C)) }
            },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("SEGUIR ENTRENANDO") } },
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = state.screen,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "screen",
        ) { screen ->
            when (screen) {
                AppScreen.HOME -> HomeScreen(
                    state = state,
                    onSelect = viewModel::chooseMode,
                    onWorkouts = viewModel::showWorkouts,
                    onProgress = viewModel::showProgress,
                    onSettings = viewModel::showSettings,
                )
                AppScreen.CONFIG -> ConfigScreen(
                    state = state,
                    onBack = viewModel::back,
                    onAdjust = viewModel::adjust,
                    onToggleCap = viewModel::toggleTimeCap,
                    onAddMix = viewModel::addMixBlock,
                    onRemoveMix = viewModel::removeMixBlock,
                    onCycleMixMode = viewModel::cycleMixMode,
                    onAdjustMixDuration = viewModel::adjustMixDuration,
                    onAdjustMixRest = viewModel::adjustMixRest,
                    onStart = viewModel::startWorkout,
                )
                AppScreen.TIMER -> state.snapshot?.let { snapshot ->
                    TimerScreen(
                        mode = state.mode,
                        snapshot = snapshot,
                        onPause = viewModel::togglePause,
                        onComplete = viewModel::completeForTime,
                        onRestart = viewModel::restart,
                        onExit = viewModel::exitTimer,
                        settings = state.settings,
                        controlsLocked = state.controlsLocked,
                        onToggleLock = viewModel::toggleControlsLock,
                    )
                }
                AppScreen.WORKOUTS -> WorkoutLibraryScreen(state, viewModel)
                AppScreen.WORKOUT_DETAIL -> WorkoutDetailScreen(state, viewModel)
                AppScreen.WORKOUT_EDITOR -> WorkoutEditorScreen(state, viewModel)
                AppScreen.WOD_RUNNER -> WodRunnerScreen(state, viewModel)
                AppScreen.PROGRESS -> ProgressScreen(state, viewModel)
                AppScreen.SETTINGS -> SettingsScreen(state, viewModel)
            }
        }
    }
}

@Composable
private fun HomeScreen(
    state: Tim3rUiState,
    onSelect: (WorkoutMode) -> Unit,
    onWorkouts: () -> Unit,
    onProgress: () -> Unit,
    onSettings: () -> Unit,
) {
    val modes = listOf(
        ModeInfo(WorkoutMode.AMRAP, "A", "AMRAP", "Tantas rondas como puedas", TimerOrange),
        ModeInfo(WorkoutMode.FOR_TIME, "FT", "FOR TIME", "Completa el trabajo contra reloj", TimerGreen),
        ModeInfo(WorkoutMode.EMOM, "E", "EMOM", "Un bloque cada minuto", TimerBlue),
        ModeInfo(WorkoutMode.TABATA, "T", "TABATA", "Intervalos intensos de trabajo y pausa", Color(0xFFFFC247)),
        ModeInfo(WorkoutMode.MIX, "M", "MIX", "Combina varios formatos en una sesión", Color(0xFFC7A4FF)),
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TIM", fontSize = 32.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text("3", color = TimerOrange, fontSize = 32.sp, fontWeight = FontWeight.Black)
                Text("R", fontSize = 32.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Spacer(Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(50), color = TimerSurfaceHigh) {
                    Text("READY TO TRAIN", modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TimerGreen)
                }
                Spacer(Modifier.width(8.dp))
                Surface(modifier = Modifier.size(38.dp).clickable(onClick = onSettings), shape = CircleShape, color = TimerSurfaceHigh) {
                    Box(contentAlignment = Alignment.Center) { Text("⚙", color = TimerMuted, fontSize = 18.sp) }
                }
            }
            Spacer(Modifier.height(34.dp))
            Text("¿QUÉ VAS A", color = TimerMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text("ENTRENAR HOY?", fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeShortcut(
                    title = "MIS WODS",
                    value = state.workouts.size.toString(),
                    subtitle = "Planes y favoritos",
                    color = Color(0xFFC7A4FF),
                    modifier = Modifier.weight(1f),
                    onClick = onWorkouts,
                )
                HomeShortcut(
                    title = "PROGRESO",
                    value = state.dashboard.completedSessions.toString(),
                    subtitle = "Sesiones guardadas",
                    color = TimerGreen,
                    modifier = Modifier.weight(1f),
                    onClick = onProgress,
                )
            }
            Spacer(Modifier.height(24.dp))
            Text("TEMPORIZADORES RÁPIDOS", color = TimerMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(2.dp))
        }
        itemsIndexed(modes) { _, info -> ModeCard(info = info, onClick = { onSelect(info.mode) }) }
        item {
            Spacer(Modifier.height(16.dp))
            Text("Sin distracciones. Solo tú contra el reloj.", modifier = Modifier.fillMaxWidth(), color = TimerMuted, textAlign = TextAlign.Center, fontSize = 12.sp)
        }
    }
}

@Composable
private fun HomeShortcut(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(15.dp)) {
            Text(value, color = color, fontSize = 27.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = TimerMuted, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun ModeCard(info: ModeInfo, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(58.dp).background(info.color.copy(alpha = .14f), RoundedCornerShape(16.dp)).border(1.dp, info.color.copy(alpha = .45f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(info.mark, color = info.color, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(info.name, fontWeight = FontWeight.Black, fontSize = 17.sp, letterSpacing = .5.sp)
                Spacer(Modifier.height(3.dp))
                Text(info.description, color = TimerMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("›", color = info.color, fontSize = 32.sp, fontWeight = FontWeight.Light)
        }
    }
}

@Composable
private fun ConfigScreen(
    state: Tim3rUiState,
    onBack: () -> Unit,
    onAdjust: (ConfigField, Int) -> Unit,
    onToggleCap: () -> Unit,
    onAddMix: () -> Unit,
    onRemoveMix: (Int) -> Unit,
    onCycleMixMode: (Int) -> Unit,
    onAdjustMixDuration: (Int, Int) -> Unit,
    onAdjustMixRest: (Int, Int) -> Unit,
    onStart: () -> Unit,
) {
    val accent = modeColor(state.mode)
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            TopBar(title = modeLabel(state.mode), onBack = onBack)
            Spacer(Modifier.height(22.dp))
            Text("CONFIGURA TU WOD", color = TimerMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(6.dp))
            Text(modeDescription(state.mode), fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
        }

        when (state.mode) {
            WorkoutMode.AMRAP -> {
                item {
                    StepperCard("DURACIÓN", formatDuration(state.totalSeconds), "Tiempo de cada AMRAP", accent, { onAdjust(ConfigField.TOTAL, -60) }, { onAdjust(ConfigField.TOTAL, 60) })
                }
                item {
                    StepperCard("SERIES", state.rounds.toString(), amrapTotalHint(state), accent, { onAdjust(ConfigField.ROUNDS, -1) }, { onAdjust(ConfigField.ROUNDS, 1) })
                }
                if (state.rounds > 1) item {
                    StepperCard("DESCANSO", formatDuration(state.restSeconds), "Entre cada AMRAP", accent, { onAdjust(ConfigField.REST, -15) }, { onAdjust(ConfigField.REST, 15) })
                }
            }
            WorkoutMode.FOR_TIME -> {
                item {
                    SettingToggle("TIME CAP", "Límite automático para cada serie", state.hasTimeCap, onToggleCap)
                }
                if (state.hasTimeCap) item {
                    StepperCard("LÍMITE", formatDuration(state.totalSeconds), "Tiempo máximo por serie", accent, { onAdjust(ConfigField.TOTAL, -60) }, { onAdjust(ConfigField.TOTAL, 60) })
                }
                item {
                    StepperCard("SERIES", state.rounds.toString(), if (state.rounds == 1) "Una pasada" else "Pulsa Terminé al completar cada una", accent, { onAdjust(ConfigField.ROUNDS, -1) }, { onAdjust(ConfigField.ROUNDS, 1) })
                }
                if (state.rounds > 1) item {
                    StepperCard("DESCANSO", formatDuration(state.restSeconds), "Entre cada serie", accent, { onAdjust(ConfigField.REST, -15) }, { onAdjust(ConfigField.REST, 15) })
                }
            }
            WorkoutMode.EMOM -> {
                item { StepperCard("INTERVALO", formatDuration(state.workSeconds), "Duración de cada ronda", accent, { onAdjust(ConfigField.WORK, -5) }, { onAdjust(ConfigField.WORK, 5) }) }
                item { StepperCard("RONDAS", state.rounds.toString(), "${formatDuration(state.workSeconds * state.rounds)} en total", accent, { onAdjust(ConfigField.ROUNDS, -1) }, { onAdjust(ConfigField.ROUNDS, 1) }) }
            }
            WorkoutMode.TABATA -> {
                item { StepperCard("TRABAJO", "${state.workSeconds}s", "Esfuerzo", accent, { onAdjust(ConfigField.WORK, -5) }, { onAdjust(ConfigField.WORK, 5) }) }
                item { StepperCard("DESCANSO", "${state.restSeconds}s", "Recuperación", accent, { onAdjust(ConfigField.REST, -5) }, { onAdjust(ConfigField.REST, 5) }) }
                item { StepperCard("RONDAS", state.rounds.toString(), "${formatDuration((state.workSeconds + state.restSeconds) * state.rounds)} en total", accent, { onAdjust(ConfigField.ROUNDS, -1) }, { onAdjust(ConfigField.ROUNDS, 1) }) }
            }
            WorkoutMode.MIX -> {
                itemsIndexed(state.mixBlocks) { index, block ->
                    MixBlockCard(
                        index = index,
                        block = block,
                        isLast = index == state.mixBlocks.lastIndex,
                        canRemove = state.mixBlocks.size > 1,
                        onRemove = { onRemoveMix(index) },
                        onCycleMode = { onCycleMixMode(index) },
                        onDuration = { onAdjustMixDuration(index, it) },
                        onRest = { onAdjustMixRest(index, it) },
                    )
                }
                item {
                    OutlinedButton(
                        onClick = onAddMix,
                        enabled = state.mixBlocks.size < 8,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text("+  AGREGAR BLOQUE", fontWeight = FontWeight.Bold) }
                }
            }
        }

        item {
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(62.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                shape = RoundedCornerShape(18.dp),
            ) { Text("INICIAR  10", fontSize = 17.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp) }
            Text("Cuenta regresiva de 10 segundos", modifier = Modifier.fillMaxWidth().padding(top = 10.dp), textAlign = TextAlign.Center, color = TimerMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(modifier = Modifier.size(44.dp).clickable(onClick = onBack), shape = CircleShape, color = TimerSurfaceHigh) {
            Box(contentAlignment = Alignment.Center) { Text("‹", fontSize = 32.sp, fontWeight = FontWeight.Light) }
        }
        Spacer(Modifier.width(14.dp))
        Text(title.uppercase(), fontSize = 22.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun StepperCard(label: String, value: String, hint: String, color: Color, onMinus: () -> Unit, onPlus: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 11.sp, color = TimerMuted, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text(value, fontSize = 30.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
                Text(hint, fontSize = 12.sp, color = TimerMuted)
            }
            RoundAction("−", onMinus, TimerSurfaceHigh, MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(10.dp))
            RoundAction("+", onPlus, color, Color.Black)
        }
    }
}

@Composable
private fun RoundAction(label: String, onClick: () -> Unit, background: Color, foreground: Color) {
    Surface(modifier = Modifier.size(48.dp).clickable(onClick = onClick), color = background, shape = CircleShape) {
        Box(contentAlignment = Alignment.Center) { Text(label, color = foreground, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun SettingToggle(label: String, description: String, checked: Boolean, onToggle: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp)) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.Black)
                Text(description, color = TimerMuted, fontSize = 12.sp)
            }
            Switch(checked = checked, onCheckedChange = { onToggle() }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = TimerGreen))
        }
    }
}

@Composable
private fun MixBlockCard(
    index: Int,
    block: MixBlock,
    isLast: Boolean,
    canRemove: Boolean,
    onRemove: () -> Unit,
    onCycleMode: () -> Unit,
    onDuration: (Int) -> Unit,
    onRest: (Int) -> Unit,
) {
    val accent = modeColor(block.mode)
    Column {
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("BLOQUE ${index + 1}", color = TimerMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.weight(1f))
                    if (canRemove) Text("ELIMINAR", modifier = Modifier.clickable(onClick = onRemove).padding(6.dp), color = Color(0xFFFF746C), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onCycleMode), color = accent.copy(alpha = .14f), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(modeLabel(block.mode).uppercase(), color = accent, fontWeight = FontWeight.Black)
                        Spacer(Modifier.weight(1f))
                        Text("CAMBIAR  ↻", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(14.dp))
                CompactStepper("DURACIÓN", formatDuration(block.durationSeconds), { onDuration(-30) }, { onDuration(30) })
                if (!isLast) {
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)
                    CompactStepper("TRANSICIÓN", formatDuration(block.restSeconds), { onRest(-15) }, { onRest(15) })
                }
            }
        }
        if (!isLast) {
            Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) { drawLine(TimerSurfaceHigh, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = 3f) }
            }
        }
    }
}

@Composable
private fun CompactStepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = TimerMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text("−", modifier = Modifier.size(36.dp).clip(CircleShape).background(TimerSurfaceHigh).clickable(onClick = onMinus).padding(top = 4.dp), textAlign = TextAlign.Center, fontSize = 20.sp)
        Text(value, modifier = Modifier.width(74.dp), textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Text("+", modifier = Modifier.size(36.dp).clip(CircleShape).background(TimerSurfaceHigh).clickable(onClick = onPlus).padding(top = 4.dp), textAlign = TextAlign.Center, fontSize = 20.sp)
    }
}

@Composable
private fun TimerScreen(
    mode: WorkoutMode,
    snapshot: TimerSnapshot,
    onPause: () -> Unit,
    onComplete: () -> Unit,
    onRestart: () -> Unit,
    onExit: () -> Unit,
    settings: AppSettings,
    controlsLocked: Boolean,
    onToggleLock: () -> Unit,
) {
    TimerCue(snapshot, mode, settings)
    val accent = phaseColor(snapshot, mode)
    val animatedProgress by animateFloatAsState(
        targetValue = if (snapshot.progress > 0f) snapshot.progress else (snapshot.elapsedSeconds % 60) / 60f,
        animationSpec = tween(180),
        label = "timer progress",
    )

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(50), color = accent.copy(alpha = .15f)) {
                Text(statusLabel(snapshot), modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = accent, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
            Spacer(Modifier.weight(1f))
            Text(modeLabel(mode).uppercase(), fontWeight = FontWeight.Black, color = TimerMuted)
        }

        Spacer(Modifier.weight(.7f))
        TimerDial(snapshot = snapshot, progress = animatedProgress, color = accent)
        Spacer(Modifier.height(28.dp))

        AnimatedContent(targetState = snapshot.title, label = "phase title") { title ->
            Text(displayTitle(title), color = accent, fontSize = 21.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        }
        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (snapshot.totalRounds > 1) StatPill("RONDA", "${snapshot.currentRound} / ${snapshot.totalRounds}")
            if (snapshot.totalBlocks > 0) StatPill("BLOQUE", "${snapshot.currentBlock} / ${snapshot.totalBlocks}")
            StatPill("TOTAL", formatDuration(snapshot.elapsedSeconds))
        }

        Spacer(Modifier.weight(1f))

        AnimatedVisibility(snapshot.status != SessionStatus.FINISHED) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (controlsLocked) {
                    Button(
                        onClick = onToggleLock,
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TimerSurfaceHigh),
                        shape = RoundedCornerShape(18.dp),
                    ) { Text("🔒  DESBLOQUEAR CONTROLES", fontWeight = FontWeight.Black) }
                    Text("CONTROLES BLOQUEADOS", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = TimerGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                } else {
                    if (snapshot.canComplete) {
                        Button(
                            onClick = onComplete,
                            modifier = Modifier.fillMaxWidth().height(60.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TimerGreen, contentColor = Color.Black),
                            shape = RoundedCornerShape(18.dp),
                        ) { Text("TERMINÉ", fontSize = 16.sp, fontWeight = FontWeight.Black) }
                    }
                    Button(
                        onClick = onPause,
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (snapshot.status == SessionStatus.PAUSED) accent else TimerSurfaceHigh),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text(if (snapshot.status == SessionStatus.PAUSED) "REANUDAR" else "PAUSAR", color = if (snapshot.status == SessionStatus.PAUSED) Color.Black else Color.White, fontWeight = FontWeight.Black)
                    }
                    Text("BLOQUEAR CONTROLES", modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleLock).padding(7.dp), textAlign = TextAlign.Center, color = TimerMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                }
            }
        }

        AnimatedVisibility(snapshot.status == SessionStatus.FINISHED) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRestart, modifier = Modifier.fillMaxWidth().height(60.dp), colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black), shape = RoundedCornerShape(18.dp)) {
                    Text("REPETIR WOD", fontWeight = FontWeight.Black)
                }
                OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) {
                    Text("VOLVER AL INICIO", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TimerDial(snapshot: TimerSnapshot, progress: Float, color: Color) {
    Box(modifier = Modifier.fillMaxWidth(.82f).aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2
            drawArc(color = TimerSurfaceHigh, startAngle = -90f, sweepAngle = 360f, useCenter = false, topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(color = color, startAngle = -90f, sweepAngle = 360f * progress.coerceIn(0f, 1f), useCenter = false, topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatDuration(snapshot.displaySeconds),
                fontSize = if (snapshot.displaySeconds >= 3600) 58.sp else 72.sp,
                lineHeight = 76.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                letterSpacing = (-4).sp,
            )
            Text(if (snapshot.phase == IntervalPhase.PREPARE) "PREPÁRATE" else "MIN : SEG", color = TimerMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
    }
}

@Composable
private fun StatPill(label: String, value: String) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = TimerMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(value, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun TimerCue(snapshot: TimerSnapshot, mode: WorkoutMode, settings: AppSettings) {
    val context = LocalContext.current
    LaunchedEffect(snapshot.status, snapshot.phase, snapshot.currentRound, snapshot.currentBlock, snapshot.displaySeconds, settings) {
        val countdownCue = snapshot.phase == IntervalPhase.PREPARE && snapshot.displaySeconds in 1..3
        val boundaryCue = snapshot.status == SessionStatus.RUNNING && snapshot.displaySeconds == 1 &&
            (mode != WorkoutMode.FOR_TIME || snapshot.phase == IntervalPhase.REST)
        val finishCue = snapshot.status == SessionStatus.FINISHED
        if (!countdownCue && !boundaryCue && !finishCue) return@LaunchedEffect

        var tone: ToneGenerator? = null
        try {
            if (settings.soundEnabled) {
                tone = ToneGenerator(AudioManager.STREAM_MUSIC, 90).also {
                    it.startTone(
                        if (finishCue) ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD else ToneGenerator.TONE_PROP_BEEP,
                        if (finishCue) 700 else 120,
                    )
                }
            }
            if (settings.vibrationEnabled) {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(VibratorManager::class.java)?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
                }
                vibrator?.vibrate(VibrationEffect.createOneShot(if (finishCue) 400 else 80, VibrationEffect.DEFAULT_AMPLITUDE))
            }
            delay(if (finishCue) 750 else 150)
        } finally {
            tone?.release()
        }
    }
}

private data class ModeInfo(val mode: WorkoutMode, val mark: String, val name: String, val description: String, val color: Color)

private fun modeColor(mode: WorkoutMode): Color = when (mode) {
    WorkoutMode.AMRAP -> TimerOrange
    WorkoutMode.FOR_TIME -> TimerGreen
    WorkoutMode.EMOM -> TimerBlue
    WorkoutMode.TABATA -> Color(0xFFFFC247)
    WorkoutMode.MIX -> Color(0xFFC7A4FF)
}

private fun phaseColor(snapshot: TimerSnapshot, mode: WorkoutMode): Color = when {
    snapshot.status == SessionStatus.FINISHED -> TimerGreen
    snapshot.phase == IntervalPhase.PREPARE -> TimerOrange
    snapshot.phase == IntervalPhase.REST -> TimerBlue
    else -> modeColor(mode)
}

private fun modeLabel(mode: WorkoutMode): String = when (mode) {
    WorkoutMode.AMRAP -> "AMRAP"
    WorkoutMode.FOR_TIME -> "For Time"
    WorkoutMode.EMOM -> "EMOM"
    WorkoutMode.TABATA -> "Tabata"
    WorkoutMode.MIX -> "Mix"
}

private fun modeDescription(mode: WorkoutMode): String = when (mode) {
    WorkoutMode.AMRAP -> "Encadena una o más series AMRAP con descansos configurables."
    WorkoutMode.FOR_TIME -> "Completa una o más series contra reloj, con descanso entre ellas."
    WorkoutMode.EMOM -> "Inicia una nueva ronda en cada intervalo. El tiempo sobrante es descanso."
    WorkoutMode.TABATA -> "Alterna periodos cortos de máximo esfuerzo y recuperación."
    WorkoutMode.MIX -> "Encadena formatos y transiciones para crear tu propio WOD."
}

private fun statusLabel(snapshot: TimerSnapshot): String = when (snapshot.status) {
    SessionStatus.READY -> "LISTO"
    SessionStatus.COUNTDOWN -> "PREPARACIÓN"
    SessionStatus.RUNNING -> if (snapshot.phase == IntervalPhase.REST) "DESCANSO" else "EN CURSO"
    SessionStatus.PAUSED -> "PAUSADO"
    SessionStatus.FINISHED -> "COMPLETADO"
}

private fun displayTitle(title: String): String = when (title) {
    "Prepare" -> "PREPÁRATE"
    "Rest" -> "DESCANSO"
    "Complete" -> "¡WOD COMPLETADO!"
    else -> title.uppercase()
}

private fun amrapTotalHint(state: Tim3rUiState): String {
    if (state.rounds == 1) return "Un AMRAP"
    val total = state.totalSeconds * state.rounds + state.restSeconds * (state.rounds - 1)
    return "${formatDuration(total)} incluyendo descansos"
}

private fun formatDuration(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val seconds = safe % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}
