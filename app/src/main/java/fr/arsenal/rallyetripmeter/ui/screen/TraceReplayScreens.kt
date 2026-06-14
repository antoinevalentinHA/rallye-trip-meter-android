package fr.arsenal.rallyetripmeter.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.arsenal.rallyetripmeter.domain.diag.GpxTrack
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * ARSENAL RALLYE — Écrans de relecture de trace a posteriori
 *
 * Visualiseur LOCAL (pas de fond de carte, pas de tuiles, pas de réseau) :
 * - TraceListScreen : liste des traces .gpx du dossier interne.
 * - TraceDetailScreen : polyligne de la trace sur Canvas + résumé.
 *
 * Garde-fous : ces écrans ne lisent que des fichiers déjà écrits. Ils n'ont
 * AUCUN accès au LocationEngine, au service, ni à la position courante. La
 * navigation vers ces écrans est verrouillée hors session (cf. TripMeterRoute).
 */

data class TraceListItem(
    val id: String,
    val displayName: String,
    val lastModifiedMillis: Long,
)

@Composable
fun TraceListScreen(
    traces: List<TraceListItem>,
    onOpenTrace: (TraceListItem) -> Unit,
    onBack: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.statusBars.asPaddingValues())
                .padding(WindowInsets.navigationBars.asPaddingValues())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ReplayHeader(title = "Traces", onBack = onBack)

            if (traces.isEmpty()) {
                Text(
                    text = "Aucune trace enregistrée pour le moment. " +
                        "Terminez une session pour générer une trace.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(traces, key = { it.id }) { trace ->
                        TraceRow(trace = trace, onClick = { onOpenTrace(trace) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TraceRow(
    trace: TraceListItem,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = trace.displayName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = formatDate(trace.lastModifiedMillis),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TraceDetailScreen(
    title: String,
    track: GpxTrack,
    onBack: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.statusBars.asPaddingValues())
                .padding(WindowInsets.navigationBars.asPaddingValues())
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ReplayHeader(title = title, onBack = onBack)

            if (track.pointCount < 2) {
                Text(
                    text = "Trace inexploitable : pas assez de points pour tracer " +
                        "un parcours.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                TracePolyline(
                    track = track,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(18.dp)
                        )
                        .padding(12.dp)
                )
            }

            TraceSummary(track = track)
        }
    }
}

@Composable
private fun TracePolyline(
    track: GpxTrack,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val startColor = MaterialTheme.colorScheme.tertiary
    val endColor = MaterialTheme.colorScheme.error

    Canvas(modifier = modifier) {
        val points = track.points
        if (points.size < 2) {
            return@Canvas
        }

        var minLat = Double.POSITIVE_INFINITY
        var maxLat = Double.NEGATIVE_INFINITY
        var minLon = Double.POSITIVE_INFINITY
        var maxLon = Double.NEGATIVE_INFINITY
        for (point in points) {
            if (point.latitude < minLat) minLat = point.latitude
            if (point.latitude > maxLat) maxLat = point.latitude
            if (point.longitude < minLon) minLon = point.longitude
            if (point.longitude > maxLon) maxLon = point.longitude
        }

        val margin = size.minDimension * 0.08f
        val drawWidth = size.width - 2 * margin
        val drawHeight = size.height - 2 * margin

        val spanLat = (maxLat - minLat).takeIf { it > 0.0 } ?: 1e-6
        val spanLon = (maxLon - minLon).takeIf { it > 0.0 } ?: 1e-6

        // Respect du ratio : même échelle sur les deux axes, centrée.
        val scale = minOf(drawWidth / spanLon, drawHeight / spanLat)
        val usedWidth = (spanLon * scale).toFloat()
        val usedHeight = (spanLat * scale).toFloat()
        val offsetX = margin + (drawWidth - usedWidth) / 2f
        val offsetY = margin + (drawHeight - usedHeight) / 2f

        fun project(latitude: Double, longitude: Double): Offset {
            val x = offsetX + ((longitude - minLon) * scale).toFloat()
            // y inversé : latitude croissante vers le haut de l'écran.
            val y = offsetY + ((maxLat - latitude) * scale).toFloat()
            return Offset(x, y)
        }

        val path = Path()
        val first = project(points[0].latitude, points[0].longitude)
        path.moveTo(first.x, first.y)
        for (index in 1 until points.size) {
            val projected = project(points[index].latitude, points[index].longitude)
            path.lineTo(projected.x, projected.y)
        }

        drawPath(
            path = path,
            color = lineColor,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
        )

        val last = points.last()
        drawCircle(color = startColor, radius = 8f, center = first)
        drawCircle(
            color = endColor,
            radius = 8f,
            center = project(last.latitude, last.longitude)
        )
    }
}

@Composable
private fun TraceSummary(track: GpxTrack) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SummaryRow(label = "Points", value = track.pointCount.toString())

        val start = track.startTimeMillis
        if (start != null) {
            SummaryRow(label = "Début", value = formatDateTime(start))
        }
        val end = track.endTimeMillis
        if (end != null) {
            SummaryRow(label = "Fin", value = formatDateTime(end))
        }
        val duration = track.durationMillis
        if (duration != null) {
            SummaryRow(label = "Durée", value = formatDuration(duration))
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ReplayHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "‹ Retour",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { onBack() }
        )
        Spacer(modifier = Modifier.height(1.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

private fun formatDate(epochMillis: Long): String {
    return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        .format(Date(epochMillis))
}

private fun formatDateTime(epochMillis: Long): String {
    return SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        .format(Date(epochMillis))
}

private fun formatDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%dh%02dm%02ds", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%dm%02ds", minutes, seconds)
    }
}
