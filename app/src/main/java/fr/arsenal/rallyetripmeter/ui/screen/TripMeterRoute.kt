package fr.arsenal.rallyetripmeter.ui.screen

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.arsenal.rallyetripmeter.ui.viewmodel.TripMeterViewModel

/*
 * ARSENAL RALLYE — UI route
 *
 * Rôle :
 * - Relie le ViewModel TripMeter à l'écran Compose pur.
 *
 * Contraintes :
 * - Aucun GPS réel.
 * - Aucun moteur de distance.
 * - Aucune logique métier locale.
 *
 * Statut :
 * - Route UI simple, en attente des moteurs réels.
 */
@Composable
fun TripMeterRoute(
    viewModel: TripMeterViewModel = viewModel()
) {
    TripMeterScreen(
        state = viewModel.uiState,
        onEvent = viewModel::onEvent
    )
}
