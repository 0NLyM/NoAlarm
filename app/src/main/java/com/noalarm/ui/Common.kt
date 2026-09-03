package com.noalarm.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Orologio di sistema come stato Compose, aggiornato ogni [periodMs]. */
@Composable
fun rememberNow(periodMs: Long = 1000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(periodMs - System.currentTimeMillis() % periodMs)
        }
    }
    return now
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) = Text(
    text.uppercase(),
    modifier = modifier.padding(horizontal = 24.dp, vertical = 12.dp),
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

/** Riquadro squadrato, bordo sottile: la grammatica visiva di Nothing. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) = Surface(
    modifier = modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it },
    shape = RoundedCornerShape(4.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    content = { Box(Modifier.padding(20.dp)) { content() } },
)

/** Pulsante circolare "punto": l'elemento base della UI di NoAlarm. */
@Composable
fun DotButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Int = 72,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
) = Box(
    modifier
        .size(size.dp)
        .clip(CircleShape)
        .background(if (enabled) color else color.copy(alpha = 0.4f))
        .clickable(enabled = enabled, onClick = onClick),
    contentAlignment = Alignment.Center,
) {
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = if (enabled) contentColor else contentColor.copy(alpha = 0.4f),
        textAlign = TextAlign.Center,
        fontSize = if (label.length > 6) 10.sp else 12.sp,
    )
}

/**
 * Il fratello di [DotButton] con un'icona al posto del testo: nell'app le
 * etichette di una sola parola sono sostituite dal simbolo corrispondente.
 */
@Composable
fun DotIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Int = 72,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
) = Box(
    modifier
        .size(size.dp)
        .clip(CircleShape)
        .background(if (enabled) color else color.copy(alpha = 0.4f))
        .clickable(enabled = enabled, onClick = onClick),
    contentAlignment = Alignment.Center,
) {
    Icon(
        icon,
        description,
        Modifier.size((size * 0.42f).dp),
        tint = if (enabled) contentColor else contentColor.copy(alpha = 0.4f),
    )
}

/**
 * Pulsante largo a forma di pillola, con il testo per intero: per le due azioni
 * della sveglia che suona, dove un'icona sola rischia l'ambiguo appena svegli.
 */
@Composable
fun DotPillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Int = 72,
    width: Int = 156,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
) = Box(
    modifier
        .size(width.dp, height.dp)
        .clip(RoundedCornerShape(50))
        .background(if (enabled) color else color.copy(alpha = 0.4f))
        .clickable(enabled = enabled, onClick = onClick),
    contentAlignment = Alignment.Center,
) {
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = if (enabled) contentColor else contentColor.copy(alpha = 0.4f),
        textAlign = TextAlign.Center,
    )
}

@Composable
fun Rows(content: @Composable () -> Unit) = Column(
    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    content = { content() },
)

@Composable
fun RowItem(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) = Panel(onClick = onClick) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing?.invoke()
    }
}
