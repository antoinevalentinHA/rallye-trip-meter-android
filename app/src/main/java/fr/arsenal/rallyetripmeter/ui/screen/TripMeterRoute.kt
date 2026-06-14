package fr.arsenal.rallyetripmeter.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.arsenal.rallyetripmeter.android.diag.TraceFileSource
import fr.arsenal.rallyetripmeter.domain.diag.GpxTrack
import fr.arsenal.rallyetripmeter.ui.model.TripMeterUiEvent
import fr.arsenal.rallyetripmeter.ui.model.UiSessionStatus
import fr.arsenal.rallyetripmeter.ui.viewmodel.TripMeterViewModel
import fr.arsenal.rallyetripmeter.ui.viewmodel.TripMeterViewModelFactory
import kotlinx.coroutines.delay

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
 * - Aucun requestLocationUpdates direct (délégué au ViewModel).
 * - Demande la permission de localisation à l'appui sur START, si elle est absente.
 * - Aucune logique métier locale.
 *
 * Statut :
 * - Route UI avec injection contrôlée du LocationEngine Android.
 * - Lifecycle localisation câblé, moteur GPS encore no-op.
 */
private const val LOCATION_PUMP_INTERVAL_MS = 1_000L

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

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Résultat de la demande déclenchée par START : on rafraîchit l'état de
        // permission, et si accordée on honore le START demandé par l'utilisateur.
        viewModel.onStartLocation()
        if (granted) {
            viewModel.onEvent(TripMeterUiEvent.SessionAction)
        }
    }

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
                Lifecycle.Event.ON_STOP -> {
                    viewModel.onStopLocation()
                    viewModel.persistCurrentState()
                }
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onStopLocation()
        }
    }

    LaunchedEffect(
        lifecycleOwner,
        viewModel
    ) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                viewModel.syncUiFromRuntime()
                delay(LOCATION_PUMP_INTERVAL_MS)
            }
        }
    }

    val handleEvent: (TripMeterUiEvent) -> Unit = { event ->
        val requestLocationFirst = event == TripMeterUiEvent.SessionAction &&
            viewModel.uiState.sessionStatus == UiSessionStatus.Stopped &&
            applicationContext.checkSelfPermission(
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED

        if (requestLocationFirst) {
            // START sans permission : on demande la position au moment utile
            // (pas de course avec la popup notifications au chargement de l'écran).
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            viewModel.onEvent(event)
        }
    }

    val context = LocalContext.current

    // Navigation locale minimale (sans Navigation Compose) pour la relecture
    // a posteriori. Garde-fou central : la relecture n'est JAMAIS montrée pendant
    // une session active ; toute session non arrêtée ramène à l'écran principal.
    var replayScreen by remember { mutableStateOf<ReplayScreen>(ReplayScreen.None) }

    val isSessionStopped = viewModel.uiState.sessionStatus == UiSessionStatus.Stopped
    if (!isSessionStopped && replayScreen != ReplayScreen.None) {
        replayScreen = ReplayScreen.None
    }

    when (val screen = replayScreen) {
        ReplayScreen.None -> {
            TripMeterScreen(
                state = viewModel.uiState,
                onEvent = handleEvent,
                onOpenTraces = {
                    // Double garde : n'ouvre la liste que session arrêtée.
                    if (viewModel.uiState.sessionStatus == UiSessionStatus.Stopped) {
                        replayScreen = ReplayScreen.List
                    }
                }
            )
        }

        ReplayScreen.List -> {
            val traces = remember(replayScreen) {
                TraceFileSource.listTraces(context).map {
                    TraceListItem(
                        id = it.file.absolutePath,
                        displayName = it.displayName,
                        lastModifiedMillis = it.lastModifiedMillis
                    )
                }
            }
            TraceListScreen(
                traces = traces,
                onOpenTrace = { item -> replayScreen = ReplayScreen.Detail(item) },
                onBack = { replayScreen = ReplayScreen.None }
            )
        }

        is ReplayScreen.Detail -> {
            val item = screen.item
            val track = remember(item.id) {
                val source = TraceFileSource.listTraces(context)
                    .firstOrNull { it.file.absolutePath == item.id }
                if (source != null) {
                    TraceFileSource.readTrack(source)
                } else {
                    GpxTrack(name = null, points = emptyList())
                }
            }
            TraceDetailScreen(
                title = item.displayName,
                track = track,
                onBack = { replayScreen = ReplayScreen.List }
            )
        }
    }
}

/** États de la navigation locale de relecture (hors session uniquement). */
private sealed interface ReplayScreen {
    data object None : ReplayScreen
    data object List : ReplayScreen
    data class Detail(val item: TraceListItem) : ReplayScreen
}
