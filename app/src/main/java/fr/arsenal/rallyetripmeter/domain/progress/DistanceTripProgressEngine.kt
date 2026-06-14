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
 * - Accumule la distance brute retenue (aucune correction utilisateur ici : le
 *   coefficient de calibration ne s'applique qu'a l'affichage, via TripDisplayMapper).
 * - Expose un verdict d'observabilite miroir de la branche prise (P1.b),
 *   sans aucune nouvelle decision.
 */
class DistanceTripProgressEngine(
    private val distanceEngine: DistanceEngine,
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
    ): TripProgressResult = applyLocationSampleWithVerdict(
        state = state,
        previousSample = previousSample,
        currentSample = currentSample,
        moving = false
    )

    /*
     * P5.c-3 étape A — plancher de bruit dépendant de l'état machine. Le paramètre
     * `moving` ne change que le plancher de REJECTED_NOISE en présence de vitesse :
     * movingNoiseFloorMeters en mouvement confirmé (MOVING), noiseFloorMeters sinon.
     * Toutes les autres gardes, l'ordre, les verdicts et I-AVANCE-MOUV sont inchangés.
     */
    private fun applyLocationSampleWithVerdict(
        state: TripState,
        previousSample: LocationSample?,
        currentSample: LocationSample,
        moving: Boolean
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
        if (distanceMeters < movementFloorMeters(previousSample, currentSample, moving)) {
            return TripProgressResult(state, SampleVerdict.REJECTED_NOISE)
        }

        if (isImplausibleJump(previousSample, currentSample, distanceMeters)) {
            return TripProgressResult(state, SampleVerdict.REJECTED_IMPLAUSIBLE_JUMP)
        }

        return TripProgressResult(
            state = state.copy(
                totalDistanceMeters = state.totalDistanceMeters + distanceMeters,
                partialDistanceMeters = state.partialDistanceMeters + distanceMeters
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
        // Cas neutres (pas d'ancre / session non active) : on délègue à la
        // primitive (IGNORED_NO_ANCHOR / IGNORED_NOT_RUNNING, état inchangé) et on
        // initialise/maintient la machine. Le gate ne s'applique qu'en course.
        if (filterState.anchor == null || tripState.sessionState != TripSessionState.Running) {
            val progress = applyLocationSampleWithVerdict(
                state = tripState,
                previousSample = filterState.anchor,
                currentSample = currentSample
            )
            return FilterResult(
                state = progress.state,
                verdict = progress.verdict,
                nextState = filterState.copy(
                    anchor = currentSample,
                    stationaryCenter = filterState.stationaryCenter ?: currentSample
                )
            )
        }

        // P4.2 — gate stationnaire actif : l'état machine gouverne l'accumulation.
        return when (filterState.machineState) {
            MachineState.STATIONARY -> applyWhileStationary(tripState, filterState, currentSample)
            MachineState.MOVING -> applyWhileMoving(tripState, filterState, currentSample)
        }
    }

    /*
     * STATIONARY — gate actif : la dérive ne s'accumule pas.
     *
     * Le déplacement net est mesuré depuis le centre stationnaire FIGÉ. Tant qu'il
     * reste sous movementTriggerMeters (ou que l'hystérésis n'est pas atteinte), on
     * neutralise l'accumulation : l'état métier est rendu inchangé et l'ancre reste
     * gelée au centre (on n'avance pas sur la dérive — c'est ce qui évite l'effet
     * pervers de P4.b anchor-only). Au franchissement (detectionHysteresisSamples
     * échantillons consécutifs au-delà du seuil), on bascule en MOVING en créditant
     * le segment de départ depuis le centre : le vrai mouvement initial n'est pas perdu.
     */
    private fun applyWhileStationary(
        tripState: TripState,
        filterState: FilterState,
        currentSample: LocationSample
    ): FilterResult {
        val center = filterState.stationaryCenter ?: currentSample
        val displacementMeters = distanceEngine.computeDistanceMeters(
            center.point,
            currentSample.point
        )

        if (displacementMeters > tuning.movementTriggerMeters) {
            val movingStreak = filterState.movingStreak + 1
            if (movingStreak >= tuning.detectionHysteresisSamples) {
                // Transition STATIONARY -> MOVING : crédite le segment de départ
                // depuis le centre via la primitive (mêmes gardes que P3/P4.1).
                val progress = applyLocationSampleWithVerdict(
                    state = tripState,
                    previousSample = center,
                    currentSample = currentSample
                )
                return FilterResult(
                    state = progress.state,
                    verdict = progress.verdict,
                    nextState = FilterState(
                        anchor = currentSample,
                        machineState = MachineState.MOVING,
                        stationaryCenter = currentSample,
                        movingStreak = 0,
                        stationaryStreak = 0
                    )
                )
            }
            return gatedStationary(tripState, center, movingStreak = movingStreak, stationaryStreak = 0)
        }

        return gatedStationary(tripState, center, movingStreak = 0, stationaryStreak = filterState.stationaryStreak)
    }

    /*
     * Échantillon neutralisé par le gate : aucune accumulation, ancre gelée au
     * centre. Verdict REJECTED_STATIONARY (réutilisé — pas de nouveau verdict,
     * JSONL inchangé) : le sample est rejeté au titre de la stationnarité machine.
     */
    private fun gatedStationary(
        tripState: TripState,
        center: LocationSample,
        movingStreak: Int,
        stationaryStreak: Int
    ): FilterResult {
        return FilterResult(
            state = tripState,
            verdict = SampleVerdict.REJECTED_STATIONARY,
            nextState = FilterState(
                anchor = center,
                machineState = MachineState.STATIONARY,
                stationaryCenter = center,
                movingStreak = movingStreak,
                stationaryStreak = stationaryStreak
            )
        )
    }

    /*
     * MOVING — comportement P3/P4.1 strictement préservé : accumulation depuis
     * l'ancre via la primitive, l'ancre avance à chaque échantillon. En plus, on
     * détecte l'immobilité pas-à-pas (centre suivant l'appareil) : après
     * detectionHysteresisSamples pas consécutifs sous stillnessRadiusMeters, on
     * rebascule en STATIONARY (la dérive cessera alors d'être accumulée).
     */
    private fun applyWhileMoving(
        tripState: TripState,
        filterState: FilterState,
        currentSample: LocationSample
    ): FilterResult {
        val progress = applyLocationSampleWithVerdict(
            state = tripState,
            previousSample = filterState.anchor,
            currentSample = currentSample,
            moving = true
        )

        val previousPoint = filterState.stationaryCenter ?: filterState.anchor!!
        val stepMeters = distanceEngine.computeDistanceMeters(
            previousPoint.point,
            currentSample.point
        )
        val stationaryStreak =
            if (stepMeters < tuning.stillnessRadiusMeters) filterState.stationaryStreak + 1 else 0
        val nextMachineState =
            if (stationaryStreak >= tuning.detectionHysteresisSamples) MachineState.STATIONARY
            else MachineState.MOVING

        return FilterResult(
            state = progress.state,
            verdict = progress.verdict,
            nextState = FilterState(
                anchor = currentSample,
                machineState = nextMachineState,
                stationaryCenter = currentSample,
                movingStreak = 0,
                stationaryStreak = if (nextMachineState == MachineState.STATIONARY) 0 else stationaryStreak
            )
        )
    }

    private fun isStationarySpeed(sample: LocationSample): Boolean {
        val speed = sample.speedMetersPerSecond ?: return false
        return speed < tuning.stationarySpeedMetersPerSecond
    }

    private fun movementFloorMeters(
        previousSample: LocationSample,
        currentSample: LocationSample,
        moving: Boolean
    ): Double {
        // Vitesse source présente : le rejet stationnaire a déjà écarté les vitesses
        // sous le seuil de quasi-immobilité, donc on est en déplacement réel -> plancher
        // minimal (le plancher accuracy guillotinerait de vrais segments urbains lents à 1 Hz).
        // En mouvement confirmé (MOVING), plancher réduit pour ne pas détruire les petits
        // pas piétons cohérents à ~1 Hz (P5.c-3 étape A) ; sinon plancher historique.
        if (currentSample.speedMetersPerSecond != null) {
            return if (moving) tuning.movingNoiseFloorMeters else tuning.noiseFloorMeters
        }

        // Vitesse absente : impossible de distinguer mouvement et dérive -> garde
        // anti-dérive basé sur l'incertitude GPS (préserve le 0 m à l'arrêt). INCHANGÉ.
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
