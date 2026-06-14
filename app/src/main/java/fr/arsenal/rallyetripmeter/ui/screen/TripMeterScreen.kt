package fr.arsenal.rallyetripmeter.ui.screen

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.arsenal.rallyetripmeter.domain.model.GpsStatus
import fr.arsenal.rallyetripmeter.domain.model.TripSessionState
import fr.arsenal.rallyetripmeter.domain.model.TripState
import fr.arsenal.rallyetripmeter.ui.mapper.toTripDisplayState
import fr.arsenal.rallyetripmeter.ui.model.TripDisplayState
import fr.arsenal.rallyetripmeter.ui.model.TripMeterUiEvent
import fr.arsenal.rallyetripmeter.ui.model.UiSessionStatus
import fr.arsenal.rallyetripmeter.ui.theme.RallyeTripMeterTheme

@Composable
fun TripMeterScreen(
    state: TripDisplayState,
    onEvent: (TripMeterUiEvent) -> Unit
) {
    val view = LocalView.current

    DisposableEffect(state.sessionStatus) {
        view.keepScreenOn = state.sessionStatus == UiSessionStatus.Active
        onDispose {
            view.keepScreenOn = false
        }
    }

    val isLandscape = LocalConfiguration.current.orientation ==
        Configuration.ORIENTATION_LANDSCAPE

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (isLandscape) {
            LandscapeLayout(state = state, onEvent = onEvent)
        } else {
            PortraitLayout(state = state, onEvent = onEvent)
        }
    }
}

@Composable
private fun PortraitLayout(
    state: TripDisplayState,
    onEvent: (TripMeterUiEvent) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.statusBars.asPaddingValues())
            .padding(WindowInsets.navigationBars.asPaddingValues())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OptionsMenu(
                isStopEnabled = state.isStopEnabled,
                calibrationText = state.calibrationText,
                isCalibrationActive = state.isCalibrationActive,
                onEvent = onEvent
            )

            TripValueCard(
                label = "PARTIEL",
                value = state.partialDistanceText,
                emphasis = TripValueEmphasis.Primary,
                onClick = if (state.arePartialControlsEnabled) {
                    { onEvent(TripMeterUiEvent.ResetPartial) }
                } else {
                    null
                }
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
                gpsAccuracy = state.gpsAccuracyText,
                sessionStatus = state.sessionStatusText,
                locationPermissionStatus = state.locationPermissionStatusText
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PartialCorrectionControls(
                enabled = state.arePartialControlsEnabled,
                onEvent = onEvent
            )

            SessionControls(
                sessionActionLabel = state.sessionActionText,
                onEvent = onEvent
            )
        }
    }
}

/*
 * Disposition paysage (voiture, téléphone à l'horizontale).
 *
 * Objectif d'ergonomie : le PARTIEL — instrument de travail de la navigation au
 * roadbook — occupe une grande colonne gauche, lisible en un regard. La colonne
 * droite regroupe le secondaire (TOTAL, VITESSE, statut) et les commandes
 * (corrections du partiel, action de session) à portée du pouce. Aucune carte,
 * aucune aide à la navigation : strictement les mêmes informations et commandes
 * qu'en portrait, réagencées. La colonne droite défile si la hauteur manque.
 */
@Composable
private fun LandscapeLayout(
    state: TripDisplayState,
    onEvent: (TripMeterUiEvent) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.statusBars.asPaddingValues())
            .padding(WindowInsets.navigationBars.asPaddingValues())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OptionsMenu(
            isStopEnabled = state.isStopEnabled,
            calibrationText = state.calibrationText,
            isCalibrationActive = state.isCalibrationActive,
            onEvent = onEvent
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Colonne gauche : PARTIEL géant, pleine hauteur.
            TripValueCard(
                label = "PARTIEL",
                value = state.partialDistanceText,
                emphasis = TripValueEmphasis.Primary,
                onClick = if (state.arePartialControlsEnabled) {
                    { onEvent(TripMeterUiEvent.ResetPartial) }
                } else {
                    null
                },
                modifier = Modifier.weight(1.5f),
                fillHeight = true
            )

            // Colonne droite : secondaire + commandes, défilable si trop court.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                    gpsAccuracy = state.gpsAccuracyText,
                    sessionStatus = state.sessionStatusText,
                    locationPermissionStatus = state.locationPermissionStatusText
                )

                PartialCorrectionControls(
                    enabled = state.arePartialControlsEnabled,
                    onEvent = onEvent
                )

                SessionControls(
                    sessionActionLabel = state.sessionActionText,
                    onEvent = onEvent
                )
            }
        }
    }
}

@Composable
private fun TripValueCard(
    label: String,
    value: String,
    emphasis: TripValueEmphasis,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    fillHeight: Boolean = false
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
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (fillHeight) {
                    Modifier.fillMaxHeight()
                } else {
                    Modifier.height(cardHeight)
                }
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            )
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
    gpsAccuracy: String?,
    sessionStatus: String,
    locationPermissionStatus: String
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
        Column {
            Text(
                text = gpsStatus,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (gpsAccuracy != null) {
                Text(
                    text = gpsAccuracy,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = locationPermissionStatus,
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
private fun PartialCorrectionControls(
    enabled: Boolean,
    onEvent: (TripMeterUiEvent) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TripButton(
            label = "−100",
            onClick = { onEvent(TripMeterUiEvent.AdjustPartialMinus100) },
            enabled = enabled,
            modifier = Modifier.weight(1f),
            height = 80.dp
        )

        TripButton(
            label = "−10",
            onClick = { onEvent(TripMeterUiEvent.AdjustPartialMinus10) },
            enabled = enabled,
            modifier = Modifier.weight(1f),
            height = 80.dp
        )

        TripButton(
            label = "+10",
            onClick = { onEvent(TripMeterUiEvent.AdjustPartialPlus10) },
            enabled = enabled,
            modifier = Modifier.weight(1f),
            height = 80.dp
        )

        TripButton(
            label = "+100",
            onClick = { onEvent(TripMeterUiEvent.AdjustPartialPlus100) },
            enabled = enabled,
            modifier = Modifier.weight(1f),
            height = 80.dp
        )
    }
}

@Composable
private fun OptionsMenu(
    isStopEnabled: Boolean,
    calibrationText: String,
    isCalibrationActive: Boolean,
    onEvent: (TripMeterUiEvent) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showResetTotalConfirmation by remember { mutableStateOf(false) }
    var showStopConfirmation by remember { mutableStateOf(false) }
    var showNewRunConfirmation by remember { mutableStateOf(false) }
    var showCalibrationDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isCalibrationActive) {
            Text(
                text = "CAL $calibrationText",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp)
            )
        }

        Box {
            IconButton(
                onClick = {
                    onEvent(TripMeterUiEvent.Options)
                    expanded = true
                }
            ) {
                Text(
                    text = "⋮",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Terminer la session") },
                    onClick = {
                        expanded = false
                        showStopConfirmation = true
                    },
                    enabled = isStopEnabled
                )

                DropdownMenuItem(
                    text = { Text("Réinitialiser total") },
                    onClick = {
                        expanded = false
                        showResetTotalConfirmation = true
                    }
                )

                DropdownMenuItem(
                    text = { Text("Nouveau parcours") },
                    onClick = {
                        expanded = false
                        showNewRunConfirmation = true
                    }
                )

                DropdownMenuItem(
                    text = { Text("Calibration") },
                    onClick = {
                        expanded = false
                        showCalibrationDialog = true
                    }
                )
            }
        }
    }

    if (showCalibrationDialog) {
        AlertDialog(
            onDismissRequest = { showCalibrationDialog = false },
            title = { Text("Calibration") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Coefficient : $calibrationText")

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton(
                            onClick = { onEvent(TripMeterUiEvent.AdjustCalibrationMinus10) },
                            modifier = Modifier.weight(1f)
                        ) { Text("−0.010") }
                        TextButton(
                            onClick = { onEvent(TripMeterUiEvent.AdjustCalibrationMinus1) },
                            modifier = Modifier.weight(1f)
                        ) { Text("−0.001") }
                        TextButton(
                            onClick = { onEvent(TripMeterUiEvent.AdjustCalibrationPlus1) },
                            modifier = Modifier.weight(1f)
                        ) { Text("+0.001") }
                        TextButton(
                            onClick = { onEvent(TripMeterUiEvent.AdjustCalibrationPlus10) },
                            modifier = Modifier.weight(1f)
                        ) { Text("+0.010") }
                    }

                    Text(
                        text = "coefficient = distance de référence ÷ distance mesurée",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onEvent(TripMeterUiEvent.ResetCalibration) }
                ) {
                    Text("Réinitialiser")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCalibrationDialog = false }
                ) {
                    Text("Fermer")
                }
            }
        )
    }

    if (showStopConfirmation) {
        AlertDialog(
            onDismissRequest = { showStopConfirmation = false },
            title = { Text("Terminer la session ?") },
            text = { Text("La session sera arrêtée et la notification retirée. La distance sera figée.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEvent(TripMeterUiEvent.Stop)
                        showStopConfirmation = false
                    }
                ) {
                    Text("Terminer")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showStopConfirmation = false }
                ) {
                    Text("Annuler")
                }
            }
        )
    }

    if (showResetTotalConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetTotalConfirmation = false },
            title = { Text("Réinitialiser le total ?") },
            text = { Text("Le total sera remis à zéro. Le partiel sera conservé.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEvent(TripMeterUiEvent.ResetTotal)
                        showResetTotalConfirmation = false
                    }
                ) {
                    Text("Réinitialiser")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetTotalConfirmation = false }
                ) {
                    Text("Annuler")
                }
            }
        )
    }

    if (showNewRunConfirmation) {
        AlertDialog(
            onDismissRequest = { showNewRunConfirmation = false },
            title = { Text("Démarrer un nouveau parcours ?") },
            text = { Text("Le total et le partiel seront remis à zéro. La session en cours est conservée.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEvent(TripMeterUiEvent.NewRun)
                        showNewRunConfirmation = false
                    }
                ) {
                    Text("Nouveau parcours")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showNewRunConfirmation = false }
                ) {
                    Text("Annuler")
                }
            }
        )
    }
}

@Composable
private fun SessionControls(
    sessionActionLabel: String,
    onEvent: (TripMeterUiEvent) -> Unit
) {
    TripButton(
        label = sessionActionLabel,
        onClick = { onEvent(TripMeterUiEvent.SessionAction) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun TripButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 72.dp
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(height),
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
            state = previewTripState().toTripDisplayState(),
            onEvent = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 740, heightDp = 360)
@Composable
private fun TripMeterScreenLandscapePreview() {
    RallyeTripMeterTheme {
        TripMeterScreen(
            state = previewTripState().toTripDisplayState(),
            onEvent = {}
        )
    }
}
