package fr.arsenal.rallyetripmeter.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.arsenal.rallyetripmeter.domain.model.GpsStatus
import fr.arsenal.rallyetripmeter.domain.model.TripSessionState
import fr.arsenal.rallyetripmeter.domain.model.TripState
import fr.arsenal.rallyetripmeter.ui.mapper.toTripDisplayState
import fr.arsenal.rallyetripmeter.ui.model.TripDisplayState
import fr.arsenal.rallyetripmeter.ui.theme.RallyeTripMeterTheme

@Composable
fun TripMeterScreen(
    state: TripDisplayState
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
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TripValueCard(
                    label = "PARTIEL",
                    value = state.partialDistanceText,
                    emphasis = TripValueEmphasis.Primary
                )

                TripValueCard(
                    label = "TOTAL",
                    value = state.totalDistanceText,
                    emphasis = TripValueEmphasis.Secondary
                )

                TripValueCard(
                    label = "VITESSE",
                    value = state.speedText,
                    emphasis = TripValueEmphasis.Tertiary
                )

                StatusBar(
                    gpsStatus = state.gpsStatusText,
                    sessionStatus = state.sessionStatusText
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PartialCorrectionControls()
                SessionControls()
            }
        }
    }
}

@Composable
private fun TripValueCard(
    label: String,
    value: String,
    emphasis: TripValueEmphasis
) {
    val valueStyle = when (emphasis) {
        TripValueEmphasis.Primary -> MaterialTheme.typography.displayLarge
        TripValueEmphasis.Secondary -> MaterialTheme.typography.displayMedium
        TripValueEmphasis.Tertiary -> MaterialTheme.typography.headlineLarge
    }

    val cardHeight = when (emphasis) {
        TripValueEmphasis.Primary -> 150.dp
        TripValueEmphasis.Secondary -> 120.dp
        TripValueEmphasis.Tertiary -> 96.dp
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(18.dp)
            )
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.TopStart)
        )

        Text(
            text = value,
            style = valueStyle,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun StatusBar(
    gpsStatus: String,
    sessionStatus: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = gpsStatus,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = sessionStatus,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PartialCorrectionControls() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TripButton(
            label = "-100 m",
            modifier = Modifier.weight(1f)
        )

        TripButton(
            label = "-10 m",
            modifier = Modifier.weight(1f)
        )

        TripButton(
            label = "RESET\nPARTIEL",
            modifier = Modifier.weight(1.6f)
        )

        TripButton(
            label = "+10 m",
            modifier = Modifier.weight(1f)
        )

        TripButton(
            label = "+100 m",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SessionControls() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TripButton(
            label = "PAUSE",
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(12.dp))

        TripButton(
            label = "STOP",
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(12.dp))

        TripButton(
            label = "OPTIONS",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TripButton(
    label: String,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = {
            // v0.1 UI skeleton:
            // callbacks volontairement vides.
            // TripController sera branché dans un palier ultérieur.
        },
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text(
            text = label,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

private enum class TripValueEmphasis {
    Primary,
    Secondary,
    Tertiary
}

private fun previewTripState(): TripState {
    return TripState(
        totalDistanceMeters = 124370.0,
        partialDistanceMeters = 800.0,
        gpsStatus = GpsStatus.Fixed,
        sessionState = TripSessionState.Running
    )
}

@Preview(showBackground = true)
@Composable
private fun TripMeterScreenPreview() {
    RallyeTripMeterTheme {
        TripMeterScreen(
            state = previewTripState().toTripDisplayState()
        )
    }
}
