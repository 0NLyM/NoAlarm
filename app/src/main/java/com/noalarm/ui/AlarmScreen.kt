package com.noalarm.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noalarm.Format
import com.noalarm.alarm.AlarmScheduler
import com.noalarm.data.Alarm
import com.noalarm.data.GlyphStyle
import com.noalarm.data.Store
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

@Composable
fun AlarmScreen() {
    val context = LocalContext.current
    // Esclude la sveglia sintetica della routine del sonno: si modifica dal
    // foglio Riposo, non come una sveglia qualunque in questo elenco.
    val alarms by Store.alarms.collectAsStateWithLifecycle()
    val userAlarms = alarms.filter { it.id != AlarmScheduler.BEDTIME_WAKE_ID }
    val settings by Store.settings.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Alarm?>(null) }
    var bedtime by remember { mutableStateOf(false) }
    var recentlyDeleted by remember { mutableStateOf<Alarm?>(null) }
    val tick = rememberNow(30_000L)

    // Una sveglia a data singola gia' passata non ha piu' senso: si spegne da sola.
    LaunchedEffect(tick) { AlarmScheduler.pruneExpired(context) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                val next = AlarmScheduler.next()
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    if (next == null) {
                        Text(
                            "NESSUNA SVEGLIA ATTIVA",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "${Format.dayOf(next.second)} · ${Format.until(next.second)}".uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Spacer(Modifier.height(12.dp))
                        DotText(
                            Format.hhmm(next.first.hour, next.first.minute, settings.use24h),
                            Modifier.fillMaxWidth().height(162.dp),
                            cell = 11.dp,
                            color = MaterialTheme.colorScheme.onBackground,
                            accentChars = setOf(':'),
                            accentColor = MaterialTheme.colorScheme.secondary,
                            animateChanges = true,
                            // In 12h le ore vanno da 1 a 12 e non sono un modulo
                            // pulito: si lascia il conteggio libero a due cifre.
                            groupMods = listOf(if (settings.use24h) 24 else 100, 60),
                            // Qui l'ora non ticchetta, cambia solo quando cambia
                            // la sveglia in programma: qualunque salto va scorso
                            // per intero invece di comparire di colpo.
                            forceRoll = true,
                        )
                    }
                }
            }

            // Sveglia fissa della routine del sonno, come in Google Clock:
            // sta in cima all'elenco e apre la finestra Riposo.
            item {
                Column {
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        BedtimeRow(onClick = { bedtime = true })
                    }
                    HorizontalDivider(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            // Gruppo vuoto = nessun'intestazione, in cima; gli altri gruppi
            // in ordine alfabetico, ciascuno con la sua etichetta.
            userAlarms.groupBy { it.group }.toSortedMap().forEach { (group, inGroup) ->
                if (group.isNotBlank()) item(key = "group_$group") {
                    Box(Modifier.padding(horizontal = 16.dp)) { SectionLabel(group.uppercase()) }
                }
                items(inGroup, key = { it.id }) { alarm ->
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        AlarmRow(
                            alarm = alarm,
                            use24h = settings.use24h,
                            order = settings.dayOrder(),
                            onToggle = {
                                AlarmScheduler.save(
                                    context,
                                    alarm.copy(enabled = it, snoozedUntil = 0L, skipNext = false),
                                )
                            },
                            onClick = { editing = alarm },
                        )
                    }
                }
            }

            if (userAlarms.isEmpty()) item {
                Text(
                    "Tocca + per creare la prima sveglia.",
                    Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        NothingFab(
            Icons.Outlined.Add, "Nuova sveglia",
            onClick = {
                val now = LocalTime.now()
                editing = Alarm(
                    id = System.currentTimeMillis(),
                    hour = now.hour,
                    minute = now.minute,
                    snoozeMinutes = settings.defaultSnoozeMinutes,
                    autoSilenceMinutes = settings.defaultAutoSilenceMinutes,
                )
            },
            modifier = Modifier.align(Alignment.BottomEnd),
        )

        // Fuori dal foglio di modifica, che nel frattempo si e' gia' chiuso:
        // si puo' continuare a usare il resto della schermata mentre e' visibile.
        UndoBarHost(
            alarm = recentlyDeleted,
            onUndo = { alarm ->
                AlarmScheduler.save(context, alarm.copy(enabled = true, snoozedUntil = 0L))
                recentlyDeleted = null
            },
            onExpire = { recentlyDeleted = null },
        )
    }

    editing?.let {
        AlarmEditSheet(
            alarm = it,
            onDismiss = { editing = null },
            onDeleted = { deleted -> recentlyDeleted = deleted },
        )
    }
    if (bedtime) BedtimeSheet { bedtime = false }
}

/**
 * Ospita la UndoBar con la sua animazione di ingresso/uscita: sale da dietro
 * il menu inferiore (disegnato sopra di lei, dopo, in MainActivity) invece
 * di apparire di scatto, e vi si nasconde di nuovo alla chiusura. "shown"
 * tiene l'ultima sveglia non nulla per tutta la durata dell'uscita, dato che
 * "alarm" torna null nello stesso istante in cui l'animazione parte.
 */
@Composable
fun BoxScope.UndoBarHost(alarm: Alarm?, onUndo: (Alarm) -> Unit, onExpire: () -> Unit) {
    var shown by remember { mutableStateOf(alarm) }
    LaunchedEffect(alarm) { if (alarm != null) shown = alarm }
    AnimatedVisibility(
        visible = alarm != null,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 16.dp)
            .padding(bottom = NothingBarMargin + NothingBarHeight + 8.dp),
        enter = slideInVertically(tween(220)) { it } + fadeIn(tween(220)),
        exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(160)),
    ) {
        shown?.let { a -> UndoBar(alarm = a, onUndo = { onUndo(a) }, onExpire = onExpire) }
    }
}

/**
 * Conferma minimale dopo Elimina: qualche secondo per tornare indietro, poi si
 * chiude da sola. Stessa forma e altezza della pillola del menu inferiore
 * (NothingBottomBar), non piu' uno stretto rettangolo scollegato da lei.
 */
@Composable
fun UndoBar(alarm: Alarm, onUndo: () -> Unit, onExpire: () -> Unit) {
    val progress = remember(alarm.id) { Animatable(1f) }
    LaunchedEffect(alarm.id) {
        progress.animateTo(0f, tween(4000, easing = LinearEasing))
        onExpire()
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Black,
        modifier = Modifier.height(NothingBarHeight),
    ) {
        // width(IntrinsicSize.Min): stretta quanto basta per il contenuto -
        // la barra sotto, pur larga quanto tutta la colonna, ne segue la
        // larghezza invece di allungarsi fino al bordo dello schermo.
        Box(
            Modifier.fillMaxHeight().width(IntrinsicSize.Min).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Sveglia eliminata", style = MaterialTheme.typography.bodySmall, color = Color.White)
                    TextButton(onClick = onUndo) {
                        Text("ANNULLA", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f),
                )
            }
        }
    }
}

/**
 * Riga fissa in cima all'elenco, nello stesso stile delle sveglie: mostra l'ora
 * del risveglio in dot-matrix e apre la routine del sonno. La separa dal resto
 * un filetto, perche' non e' una sveglia come le altre.
 */
@Composable
private fun BedtimeRow(onClick: () -> Unit) {
    val context = LocalContext.current
    val s by Store.settings.collectAsStateWithLifecycle()
    val sleep = Duration.between(
        LocalTime.of(s.bedtimeHour, s.bedtimeMinute),
        LocalTime.of(s.wakeHour, s.wakeMinute),
    ).let { if (it.isNegative) it.plusHours(24) else it }

    Panel(onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                // Stessa misura e stesso riquadro di AlarmRow: le due ore si allineano.
                DotText(
                    Format.hhmm(s.wakeHour, s.wakeMinute, s.use24h),
                    Modifier.height(40.dp).fillMaxWidth(0.75f),
                    cell = 5.dp,
                    color = if (s.bedtimeEnabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Bedtime,
                        "Routine del sonno",
                        Modifier.size(14.dp),
                        tint = if (s.bedtimeEnabled) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (s.bedtimeEnabled)
                            "A letto %s · %dh%02d · %s".format(
                                Format.hhmm(s.bedtimeHour, s.bedtimeMinute, s.use24h),
                                sleep.toHours(), sleep.toMinutes() % 60,
                                Format.daysLabel(s.bedtimeDays, s.dayOrder()),
                            )
                        else "Routine del sonno disattivata",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            NothingSwitch(
                checked = s.bedtimeEnabled,
                onCheckedChange = { on ->
                    Store.update { it.copy(bedtimeEnabled = on) }
                    AlarmScheduler.scheduleBedtime(context)
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BedtimeSheet(onDismiss: () -> Unit) = ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = MaterialTheme.colorScheme.background,
) { BedtimeScreen() }

/**
 * Foglio di modifica di una sveglia, condiviso con la schermata Calendario.
 *
 * Una sveglia gia' esistente salva in tempo reale: chiuderlo in un modo
 * qualunque - swipe compreso - non perde mai le modifiche. Una sveglia
 * nuova (aperta con "+", non ancora nello Store) invece non esiste finche'
 * non si preme "Fatto": aprire il foglio e chiuderlo senza confermare non
 * deve creare nulla.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditSheet(alarm: Alarm, onDismiss: () -> Unit, onDeleted: (Alarm) -> Unit) {
    val context = LocalContext.current
    val settings by Store.settings.collectAsStateWithLifecycle()
    var current by remember(alarm.id) { mutableStateOf(alarm) }
    val isNew = remember(alarm.id) { Store.alarm(alarm.id) == null }

    fun commit(a: Alarm) = AlarmScheduler.save(context, a.copy(enabled = true, snoozedUntil = 0L))

    if (!isNew) {
        // Ammortizzato: riprogrammare nel sistema non e' gratuito, non ha
        // senso rifarlo a ogni singolo frame mentre si trascina il
        // carosello dell'ora. Solo per una sveglia gia' salvata: su una
        // nuova, salvare qui la creerebbe ancora prima di "Fatto".
        LaunchedEffect(current) {
            if (current == alarm) return@LaunchedEffect
            delay(400)
            commit(current)
        }
    }

    fun confirm() {
        commit(current)
        onDismiss()
    }

    fun dismiss() {
        // Una nuova non confermata si scarta; una gia' esistente e' gia'
        // salvata (in tempo reale, o lo sta per essere): basta chiudere.
        if (!isNew) commit(current)
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = ::dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        AlarmEditor(
            alarm = alarm,
            use24h = settings.use24h,
            order = settings.dayOrder(),
            onDraftChange = { current = it },
            onDone = ::confirm,
            onDelete = {
                if (!isNew) {
                    AlarmScheduler.delete(context, current.id)
                    onDeleted(current)
                }
                onDismiss()
            },
        )
    }
}

@Composable
private fun AlarmRow(
    alarm: Alarm,
    use24h: Boolean,
    order: List<DayOfWeek>,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) = Panel(onClick = onClick) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            DotText(
                Format.hhmm(alarm.hour, alarm.minute, use24h),
                Modifier.height(40.dp).fillMaxWidth(0.75f),
                cell = 5.dp,
                color = if (alarm.enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                buildString {
                    append(alarm.date?.let(Format::dateLabel) ?: Format.daysLabel(alarm.days, order))
                    if (alarm.label.isNotBlank()) append(" · ${alarm.label}")
                    if (alarm.snoozedUntil > 0) append(" · posticipata")
                    if (alarm.skipNext) append(" · saltata una volta")
                    if (alarm.enabled && alarm.nextTrigger() == null) append(" · scaduta")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        NothingSwitch(checked = alarm.enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun AlarmEditor(
    alarm: Alarm,
    use24h: Boolean,
    order: List<DayOfWeek>,
    onDraftChange: (Alarm) -> Unit,
    onDone: () -> Unit,
    onDelete: () -> Unit,
) {
    var draft by remember(alarm.id) { mutableStateOf(alarm) }
    LaunchedEffect(draft) { onDraftChange(draft) }

    // Gruppi gia' usati da altre sveglie, per scegliere invece di riscrivere
    // lo stesso nome ogni volta. Basta lo scatto di quando si apre il foglio.
    val existingGroups = remember {
        Store.alarms.value.mapNotNull { it.group.ifBlank { null } }.distinct().sorted()
    }

    val ringtone = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = r.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            draft = draft.copy(soundUri = uri?.toString())
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Column(
            // fill = false: la barra dei comandi resta sempre visibile in basso,
            // qualunque sia l'altezza del contenuto.
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Il selettore a griglia sostituisce il rullo verticale solo in 24h: le
            // ore 1..12 + AM/PM non si prestano alla stessa griglia decina/unita'.
            // DotTimePicker resta nel codice apposta per questo caso, non e' stato
            // buttato.
            if (use24h) {
                var pickerOpen by remember { mutableStateOf(false) }
                DotText(
                    Format.hhmm(draft.hour, draft.minute, true),
                    Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .padding(vertical = 8.dp)
                        .clickable { pickerOpen = true },
                    cell = 9.dp,
                    color = MaterialTheme.colorScheme.onBackground,
                    accentChars = setOf(':'),
                    accentColor = MaterialTheme.colorScheme.secondary,
                    blinkAccent = true,
                )
                if (pickerOpen) {
                    GridTimePickerPopup(
                        hour = draft.hour,
                        minute = draft.minute,
                        onChange = { h, m -> draft = draft.copy(hour = h, minute = m) },
                        onDismiss = { pickerOpen = false },
                    )
                }
            } else {
                DotTimePicker(
                    hour = draft.hour,
                    minute = draft.minute,
                    use24h = use24h,
                    onChange = { h, m -> draft = draft.copy(hour = h, minute = m) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
            }

            if (draft.onDate) {
                RowItem(
                    title = "Data singola",
                    subtitle = draft.date?.let(Format::dateLabel),
                    trailing = {
                        DotIconButton(
                            Icons.Outlined.Close,
                            "Torna alla ripetizione settimanale",
                            { draft = draft.copy(dateEpochDay = 0L) },
                            size = 40,
                        )
                    },
                )
            } else {
                SectionLabel("Ripetizione")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    order.forEach { day ->
                        val on = day.value in draft.days
                        Box(Modifier.weight(1f)) {
                            DotButton(
                                label = Format.dayLabel(day).take(1),
                                size = 40,
                                color = if (on) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = if (on) MaterialTheme.colorScheme.onSecondary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                onClick = {
                                    draft = draft.copy(
                                        days = if (on) draft.days - day.value else draft.days + day.value
                                    )
                                },
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = draft.label,
                onValueChange = { draft = draft.copy(label = it.take(24)) },
                label = { Text("Etichetta") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = draft.group,
                onValueChange = { draft = draft.copy(group = it.take(24)) },
                label = { Text("Gruppo (opzionale)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (existingGroups.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    existingGroups.forEach { name ->
                        val selected = name == draft.group
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (selected) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (selected) MaterialTheme.colorScheme.onSecondary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { draft = draft.copy(group = if (selected) "" else name) },
                        ) {
                            Text(
                                name,
                                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }

            RowItem(
                title = "Suoneria",
                subtitle = ringtoneName(draft.soundUri),
                onClick = {
                    ringtone.launch(
                        Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            .putExtra(
                                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                draft.soundUri?.let(Uri::parse)
                                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                            )
                    )
                },
            )

            SwitchRow("Vibrazione", draft.vibrate) { draft = draft.copy(vibrate = it) }
            SwitchRow("Volume crescente", draft.gradualVolume) { draft = draft.copy(gradualVolume = it) }
            SwitchRow("Glyph Matrix mentre suona", draft.glyph) { draft = draft.copy(glyph = it) }
            if (draft.glyph) {
                SectionLabel("Cosa mostra la matrice")
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GlyphStyle.entries.forEach { style ->
                        GlyphStyleCard(
                            style = style,
                            label = draft.label,
                            name = glyphStyleLabel(style),
                            selected = style == draft.glyphStyle,
                            onClick = { draft = draft.copy(glyphStyle = style) },
                        )
                    }
                }
            }
            StepperRow(
                "Notifica di preavviso",
                if (draft.reminderMinutes == 0) "Mai" else "${draft.reminderMinutes} min prima",
                0, 180, draft.reminderMinutes,
            ) { draft = draft.copy(reminderMinutes = it) }
            StepperRow(
                "Silenzia dopo",
                if (draft.autoSilenceMinutes == 0) "Mai" else "${draft.autoSilenceMinutes} min",
                0, 60, draft.autoSilenceMinutes,
            ) { draft = draft.copy(autoSilenceMinutes = it) }

            SectionLabel("Rinvio di questa sveglia")
            StepperRow("Minuti di partenza", "${draft.snoozeMinutes} min", 1, 60, draft.snoozeMinutes) {
                draft = draft.copy(snoozeMinutes = it)
            }
            StepperRow("Passo dei pulsanti", "${draft.snoozeStepMinutes} min", 1, 15, draft.snoozeStepMinutes) {
                draft = draft.copy(snoozeStepMinutes = it)
            }
            StepperRow("Minimo", "${draft.snoozeMinMinutes} min", 1, 30, draft.snoozeMinMinutes) {
                draft = draft.copy(
                    snoozeMinMinutes = it,
                    snoozeMaxMinutes = maxOf(it, draft.snoozeMaxMinutes),
                )
            }
            StepperRow("Massimo", "${draft.snoozeMaxMinutes} min", 1, 120, draft.snoozeMaxMinutes) {
                draft = draft.copy(snoozeMaxMinutes = maxOf(it, draft.snoozeMinMinutes))
            }
            StepperRow(
                "Numero massimo di rinvii",
                if (draft.snoozeLimit == 0) "Illimitati" else "${draft.snoozeLimit}",
                0, 10, draft.snoozeLimit,
            ) { draft = draft.copy(snoozeLimit = it) }

            Spacer(Modifier.height(8.dp))
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DotIconButton(
                Icons.Outlined.Delete,
                "Elimina",
                onDelete,
                size = 56,
                contentColor = MaterialTheme.colorScheme.error,
            )
            DotIconButton(
                Icons.Outlined.Check,
                "Fatto",
                onDone,
                size = 64,
                color = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            )
        }
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) = RowItem(
    title = title,
    onClick = { onChange(!checked) },
    trailing = { NothingSwitch(checked = checked, onCheckedChange = onChange) },
)

@Composable
fun StepperRow(title: String, value: String, min: Int, max: Int, current: Int, onChange: (Int) -> Unit) {
    var typing by remember { mutableStateOf(false) }
    RowItem(
        title = title,
        subtitle = value,
        // Tutta la riga apre la tastiera: i pulsanti sotto hanno il loro
        // click e lo intercettano prima che arrivi qui.
        onClick = { typing = true },
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DotIconButton(
                    Icons.Outlined.Remove, "Diminuisci",
                    { onChange((current - 1).coerceAtLeast(min)) },
                    size = 40, enabled = current > min,
                )
                DotIconButton(
                    Icons.Outlined.Add, "Aumenta",
                    { onChange((current + 1).coerceAtMost(max)) },
                    size = 40, enabled = current < max,
                )
            }
        },
    )
    if (typing) NumberInputDialog(
        title = title,
        value = current,
        min = min,
        max = max,
        onDismiss = { typing = false },
        onConfirm = { onChange(it); typing = false },
    )
}

/** Foglio minimo per battere a tastiera un valore che altrimenti si regola solo coi pulsanti +/-. */
@Composable
private fun NumberInputDialog(
    title: String,
    value: Int,
    min: Int,
    max: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(4) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text("Fra $min e $max") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm((text.toIntOrNull() ?: value).coerceIn(min, max)) }) {
                Text("OK")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

fun glyphStyleLabel(style: GlyphStyle) = when (style) {
    GlyphStyle.CYCLE -> "Ciclo"
    GlyphStyle.CLOCK -> "Ora"
    GlyphStyle.BELL -> "Campanella"
    GlyphStyle.LABEL -> "Etichetta"
    GlyphStyle.COUNTDOWN -> "Timer"
}

@Composable
fun ringtoneName(uri: String?): String {
    val context = LocalContext.current
    return remember(uri) {
        runCatching {
            RingtoneManager.getRingtone(
                context,
                uri?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            ).getTitle(context)
        }.getOrNull() ?: "Suoneria predefinita"
    }
}
