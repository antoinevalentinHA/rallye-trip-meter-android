package fr.arsenal.rallyetripmeter.domain.progress

import fr.arsenal.rallyetripmeter.domain.diag.SampleVerdict
import fr.arsenal.rallyetripmeter.domain.distance.DistanceEngine
import fr.arsenal.rallyetripmeter.domain.geo.LocationSample
import fr.arsenal.rallyetripmeter.domain.model.TripSessionState
import fr.arsenal.rallyetripmeter.domain.model.TripState

/*
 * ARSENAL RALLYE — Distance trip progress engine
 *
 * Rôle :
 * - Applique une mesure de localisation au TripState.
 * - Ajoute la distance parcourue au total et au partiel lorsque la session est active.
 *
 * Contraintes :
 * - Aucun accès GPS réel.
 * - Aucun lien avec Android Location.
 * - Aucun état mutable.
 * - Aucune dépendance UI.
 * - Aucune persistance.
 *
 * Principe :
 * - Ne fait rien sans échantillon précédent.
 * - Ne fait rien si la session n'est pas Running.
 * - Délègue le calcul métrique au DistanceEngine injecté.
 * - Ignore un échantillon dont la vitesse source indique une quasi-immobilité.
 * - Ignore un segment plus court que l'incertitude GPS (plancher lié a l'accuracy).
 * - Ignore les sauts implausibles (vitesse calculée trop élevée).
 * - Applique un coefficient de calibration global a la distance retenue.
 * - Expose un verdict d'observabilite miroir de la branche prise (P1.b),
 *   sans aucune nouvelle decision.
 */
class DistanceTripProgressEngine(
    private val distanceEngine: DistanceEngine,
    private val calibrationFactor: Double = 1.0,
    private val tuning: FilterTuning = FilterTuning()
) : TripProgressEngine, GpsAccumulationFilter {
    override fun applyLocationSample(
        state: TripState,
        previousSample: LocationSample?,
        currentSample: LocationSample
    ): TripState {
        return applyLocationSampleWithVerdict(
            state = state,
            previousSample = previousSample,
            currentSample = currentSample
        ).state
    }

    /*
     * Variante observable (P1.b) : strictement la même logique que le contrat
     * historique — mêmes gardes, même ordre, mêmes calculs — avec en plus le
     * verdict miroir de la branche réellement prise. Aucune nouvelle décision.
     */
    override fun applyLocationSampleWithVerdict(
        state: TripState,
        previousSample: LocationSample?,
        currentSample: LocationSample
    ): TripProgressResult {
        if (previousSample == null) {
            return TripProgressResult(state, SampleVerdict.IGNORED_NO_ANCHOR)
        }

        if (state.sessionState != TripSessionState.Running) {
            return TripProgressResult(state, SampleVerdict.IGNORED_NOT_RUNNING)
        }

        if (isStationarySpeed(currentSample)) {
            return TripProgressResult(state, SampleVerdict.REJECTED_STATIONARY)
        }

        val distanceMeters = distanceEngine.computeDistanceMeters(
            previous = previousSample.point,
            current = currentSample.point
        )

        // Filtrage : immobilite (vitesse source), bruit/derive (plancher lie a
        // l'accuracy), et sauts implausibles. Hors perimetre : REFERENCE_ONLY / watchdog.
        if (distanceMeters < movementFloorMeters(previousSample, currentSample)) {
            return TripProgressResult(state, SampleVerdict.REJECTED_NOISE)
        }

        if (isImplausibleJump(previousSample, currentSample, distanceMeters)) {
            return TripProgressResult(state, SampleVerdict.REJECTED_IMPLAUSIBLE_JUMP)
        }

        val correctedDistanceMeters = distanceMeters * calibrationFactor

        return TripProgressResult(
            state = state.copy(
                totalDistanceMeters = state.totalDistanceMeters + correctedDistanceMeters,
                partialDistanceMeters = state.partialDistanceMeters + correctedDistanceMeters
            ),
            verdict = SampleVerdict.ACCEPTED_SEGMENT
        )
    }

    /*
     * Contrat P3.a (GpsAccumulationFilter) : adaptation mécanique du moteur
     * existant. Strictement la même logique de verdict et d'accumulation que
     * `applyLocationSampleWithVerdict` — mêmes gardes, même ordre, mêmes
     * constantes — à laquelle s'ajoute le seul transport explicite de l'ancre.
     *
     * Sémantique d'ancre héritée et conservée volontairement : l'ancre du
     * FilterState suivant devient l'échantillon courant à chaque application,
     * y compris lorsque le verdict est un rejet. C'est le miroir exact du
     * runtime actuel, qui fait avancer `previousLocationSample` avant de
     * calculer le verdict. Ce point n'est pas corrigé ici (inversé en P4).
     */
    override fun apply(
        tripState: TripState,
        filterState: FilterState,
        currentSample: LocationSample
    ): FilterResult {
        val progress = applyLocationSampleWithVerdict(
            state = tripState,
            previousSample = filterState.anchor,
            currentSample = currentSample
        )

        return FilterResult(
            state = progress.state,
            verdict = progress.verdict,
            // P4.1 : l'ancre avance comme en P4.a (accumulation strictement
            // inchangée). On calcule en plus l'état machine observé, transporté
            // par le FilterState suivant, sans gouverner l'accumulation. Le gate
            // stationnaire qui exploitera cet état arrive en P4.2.
            nextState = detectMachineState(filterState, currentSample)
        )
    }

    /*
     * P4.1 — détection stationnaire/mouvement, NEUTRE.
     *
     * Produit le FilterState suivant. L'ancre avance exactement comme en P4.a
     * (`anchor = currentSample`), donc l'accumulation est strictement inchangée :
     * l'état machine est observé et transporté, il ne gouverne pas encore
     * l'accumulation (gate activé en P4.2).
     *
     * Détection par déplacement net (et non vitesse seule) avec hystérésis :
     * - STATIONARY : centre fixe ; bascule en MOVING après
     *   detectionHysteresisSamples échantillons consécutifs à plus de
     *   movementTriggerMeters du centre.
     * - MOVING : centre suivant l'appareil (déplacement pas-à-pas) ; rebascule en
     *   STATIONARY après detectionHysteresisSamples pas consécutifs sous
     *   stillnessRadiusMeters.
     */
    private fun detectMachineState(
        filterState: FilterState,
        currentSample: LocationSample
    ): FilterState {
        val center = filterState.stationaryCenter ?: currentSample
        val displacementMeters = distanceEngine.computeDistanceMeters(
            center.point,
            currentSample.point
        )

        var machineState = filterState.machineState
        var stationaryCenter = center
        var movingStreak = filterState.movingStreak
        var stationaryStreak = filterState.stationaryStreak

        when (filterState.machineState) {
            MachineState.STATIONARY -> {
                if (displacementMeters > tuning.movementTriggerMeters) {
                    movingStreak += 1
                    if (movingStreak >= tuning.detectionHysteresisSamples) {
                        machineState = MachineState.MOVING
                        stationaryCenter = currentSample
                        movingStreak = 0
                        stationaryStreak = 0
                    }
                } else {
                    movingStreak = 0
                }
            }

            MachineState.MOVING -> {
                if (displacementMeters < tuning.stillnessRadiusMeters) {
                    stationaryStreak += 1
                } else {
                    stationaryStreak = 0
                }
                if (stationaryStreak >= tuning.detectionHysteresisSamples) {
                    machineState = MachineState.STATIONARY
                    movingStreak = 0
                    stationaryStreak = 0
                }
                // En mouvement, le centre suit l'appareil (mesure pas-à-pas).
                stationaryCenter = currentSample
            }
        }

        return FilterState(
            anchor = currentSample,
            machineState = machineState,
            stationaryCenter = stationaryCenter,
            movingStreak = movingStreak,
            stationaryStreak = stationaryStreak
        )
    }

    private fun isStationarySpeed(sample: LocationSample): Boolean {
        val speed = sample.speedMetersPerSecond ?: return false
        return speed < tuning.stationarySpeedMetersPerSecond
    }

    private fun movementFloorMeters(
        previousSample: LocationSample,
        currentSample: LocationSample
    ): Double {
        // Vitesse source présente : le rejet stationnaire a déjà écarté les vitesses
        // sous le seuil de quasi-immobilité, donc on est en déplacement réel -> plancher
        // minimal (le plancher accuracy guillotinerait de vrais segments urbains lents à 1 Hz).
        if (currentSample.speedMetersPerSecond != null) {
            return tuning.noiseFloorMeters
        }

        // Vitesse absente : impossible de distinguer mouvement et dérive -> garde
        // anti-dérive basé sur l'incertitude GPS (préserve le 0 m à l'arrêt).
        val accuracyFloor = worstAccuracyMeters(previousSample, currentSample) *
            tuning.accuracyFloorFactor
        return maxOf(tuning.noiseFloorMeters, accuracyFloor)
    }

    private fun worstAccuracyMeters(
        previousSample: LocationSample,
        currentSample: LocationSample
    ): Double {
        val previousAccuracy = previousSample.accuracyMeters ?: 0.0
        val currentAccuracy = currentSample.accuracyMeters ?: 0.0
        return maxOf(previousAccuracy, currentAccuracy)
    }

    private fun isImplausibleJump(
        previousSample: LocationSample,
        currentSample: LocationSample,
        distanceMeters: Double
    ): Boolean {
        val previousMillis = previousSample.point.timestampMillis ?: return false
        val currentMillis = currentSample.point.timestampMillis ?: return false

        val elapsedMillis = currentMillis - previousMillis
        if (elapsedMillis <= 0L) {
            return true
        }

        val elapsedSeconds = elapsedMillis / MILLIS_PER_SECOND
        val speedKmh = distanceMeters / elapsedSeconds * METERS_PER_SECOND_TO_KMH
        return speedKmh > tuning.maxPlausibleSpeedKmh
    }

    private companion object {
        // Conversions d'unités physiques (non accordables) : restent internes.
        const val MILLIS_PER_SECOND = 1000.0
        const val METERS_PER_SECOND_TO_KMH = 3.6
    }
}
