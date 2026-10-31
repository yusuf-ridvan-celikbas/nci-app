package com.cansin.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun BarChart(title: String, values: List<Double>, description: String, modifier: Modifier = Modifier) {
    val bar = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    Column(modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .semantics { contentDescription = description },
        ) {
            if (values.isEmpty()) return@Canvas
            val slot = size.width / values.size
            val gap = slot * 0.2f
            val radius = CornerRadius(4.dp.toPx())
            values.forEachIndexed { i, value ->
                val left = i * slot + gap / 2
                drawRoundRect(track, Offset(left, 0f), Size(slot - gap, size.height), radius)
                val h = (value / max * size.height).toFloat()
                drawRoundRect(bar, Offset(left, size.height - h), Size(slot - gap, h), radius)
            }
        }
    }
}
