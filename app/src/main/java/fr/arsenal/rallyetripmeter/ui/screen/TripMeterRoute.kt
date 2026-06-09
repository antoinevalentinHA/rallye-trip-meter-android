package fr.arsenal.rallyetripmeter.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.arsenal.rallyetripmeter.ui.model.TripMeterUiEvent
import fr.arsenal.rallyetripmeter.ui.viewmodel.TripMeterViewModel
import fr.arsenal.rallyetripmeter.ui.viewmodel.TripMeterViewModelFactory

/*
 * ARSENAL RALLYE — UI route
 *
 * Rôle :
 * - Relie le ViewModel TripMeter à l'écran Compose pur.
 * - Fournit au ViewModel ses dépendances Android via une factory dédiée.
 * - Rafraîchit l'état de permission de localisation à l'entrée dans la route.
 * - Relie le lifecycle de la route aux handles de localisation du ViewModel.
 *
 * Contraintes :
 * - Aucun requestLocationUpdates.
 * - Aucune demande de permission runtime.
 * - Aucune popup système.
 * - Aucune logique métier locale.
 *
 * Statut :
 * - Route UI avec injection contrôlée du LocationEngine Android.
 * - Lifecycle localisation câblé, moteur GPS encore no-op.
 */
@Composable
fun TripMeterRoute() {
    val applicationContext = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current

    val factory = remember(applicationContext) {
        TripMeterViewModelFactory(
            context = applicationContext
        )
    }

    val viewModel: TripMeterViewModel = viewModel(
        factory = factory
    )

    LaunchedEffect(viewModel) {
        viewModel.onEvent(TripMeterUiEvent.RefreshLocationPermission)
    }

    DisposableEffect(
        lifecycleOwner,
        viewModel
    ) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onStartLocation()
                Lifecycle.Event.ON_STOP -> viewModel.onStopLocation()
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onStopLocation()
        }
    }

    TripMeterScreen(
        state = viewModel.uiState,
        onEvent = viewModel::onEvent
    )
}
