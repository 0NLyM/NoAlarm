package com.noalarm.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Pulsante a pillola in stile Nothing: stesso linguaggio del telefono (DotPillButton), senza dot-matrix. */
@Composable
fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) = Text(
    label.uppercase(),
    modifier
        .clip(RoundedCornerShape(50))
        .background(color)
        .clickable(onClick = onClick)
        .padding(horizontal = 24.dp, vertical = 10.dp),
    color = contentColor,
    style = MaterialTheme.typography.labelLarge,
    textAlign = TextAlign.Center,
)
