# Trip Meter Rallye Android — GpsAccumulationFilter v0.1

> **Navigation** (non normatif) — Implémentation / clôtures liées :
> [P4.2 gate stationnaire](../plan/reprises/cloture_P4_2_gate_stationnaire_actif_2026_06_12.md),
> [P5.c-3 plancher mouvement](../plan/reprises/cloture_p5c3_moving_noise_floor_2026_06_13.md),
> [P6.b purge `calibrationFactor`](../plan/reprises/cloture_p6b_purge_calibrationfactor_2026_06_14.md).
> État courant : [`ETAT.md`](../ETAT.md). Historique : [reprises](../plan/reprises/README.md).

Statut : VALIDÉ (descriptif)
Dépend de :

- Contrat fonctionnel v0.2 validé
- TripState v0.1.2 validé
- LocationEngine v0.1 validé
- DistanceEngine v0.1.2 validé

Objet : contrat normatif du filtre d'accumulation GPS — la couche de **mesure
brute** de la distance, qui décide quels échantillons GPS créditent le compteur.

Portée de ce document : il **décrit l'état implémenté** à HEAD `6449fe6`, à l'issue
de la série de chantiers P1→P4 (clôturée fonctionnellement). Il est déduit du code
(`domain/progress/`, `domain/diag/SampleVerdict.kt`) et des tests JVM existants. Il
n'introduit aucun comportement nouveau et ne planifie rien (cf. §10, articulation
avec P5).

---

## 0. Position dans l'architecture — mesure brute vs calibration utilisateur

Le projet sépare strictement deux étages, qui ne doivent jamais être confondus :

```text
GPS brut ─► [GpsAccumulationFilter] ─► distance brute accumulée (TripState)
                                                │
                                                ▼
                              [TripDisplayMapper] × coefficient utilisateur ─► AFFICHAGE
```

1. **Filtre d'accumulation GPS (le présent contrat) = mesure brute.**
   Il décide, échantillon par échantillon, ce qui est accumulé dans
   `TripState.totalDistanceMeters` / `partialDistanceMeters`. Il ne connaît ni
   l'UI, ni le coefficient utilisateur. Sa sortie est une distance **non corrigée
   par l'utilisateur**.

2. **Coefficient utilisateur de calibration = correction d'affichage.**
   `domain/calibration/CalibrationCoefficient` (borné [0.900, 1.100], neutre
   1.000, persisté via `CalibrationStore`) est appliqué **uniquement à
   l'affichage** des distances dans `ui/mapper/TripDisplayMapper`
   (`distance × coefficient`). C'est une fonctionnalité utilisateur à part
   entière, **hors du périmètre de ce contrat** et **non remise en cause** par
   lui.

Invariant de séparation (I-SEP) : le filtre n'applique **jamais** le coefficient
utilisateur ; le coefficient utilisateur ne modifie **jamais** la distance brute
accumulée. Les deux étages sont étanches. Régler l'un n'a pas vocation à compenser
l'autre.

> Note factuelle sur `calibrationFactor` (à ne pas confondre avec le coefficient
> utilisateur). Le moteur brut `DistanceTripProgressEngine` portait historiquement
> un paramètre interne `calibrationFactor: Double = 1.0`, multiplié à la distance
> retenue avant accumulation (neutre à 1.0, non câblé par `TripRuntime` ni le
> ViewModel). Ce vestige a été **purgé** (chantier P6.b, commit `4e1e350`) : le
> moteur accumule désormais la distance **brute non corrigée**, et le seul test qui
> validait ce paramètre a été supprimé. La purge est prouvée neutre par replay
> bit-à-bit du corpus réel (totaux identiques avant/après). Le moteur brut ne porte
> donc plus aucun mécanisme de correction utilisateur ; cette correction relève
> exclusivement de l'affichage (coefficient utilisateur, cf. ci-dessus et I-SEP).

---

## 1. Rôle du filtre

Le filtre est le **point de décision unique** de l'accumulation de distance. Pour
chaque échantillon GPS exploitable, il décide d'une seule branche parmi un
ensemble fermé, met éventuellement à jour la distance brute, et transporte son
état interne (l'ancre et la machine) vers le tick suivant.

Il assure deux fonctions imbriquées :

- une **garde de cohérence par paire** (héritée P1–P3) : rejet de la
  quasi-immobilité par vitesse source, du bruit/dérive sous plancher, et des sauts
  implausibles ;
- une **machine d'état stationnaire/mouvement** (P4) qui neutralise la dérive GPS
  à l'arrêt tout en préservant la mesure en déplacement.

Il **ne fait pas** : acquisition GPS (rôle `LocationEngine`), calcul métrique brut
(rôle `DistanceEngine`, injecté), persistance, affichage, ni application du
coefficient utilisateur.

Implémentation de référence : `domain/progress/GpsAccumulationFilter` (contrat) et
`domain/progress/DistanceTripProgressEngine` (implémentation, qui réalise aussi le
contrat historique `TripProgressEngine`).

---

## 2. Entrée / sortie

### 2.1 Signature

```text
GpsAccumulationFilter.apply(
    tripState: TripState,          // état métier courant
    filterState: FilterState,      // état opaque transporté (ancre + machine)
    currentSample: LocationSample  // échantillon courant, supposé exploitable
): FilterResult
```

### 2.2 Entrée

- `tripState` : instantané métier courant (compteurs, état de session).
- `filterState` : état du filtre au tick précédent (cf. §3). Opaque pour
  l'appelant.
- `currentSample` : `LocationSample` = `GeoPoint(latitude, longitude,
  altitudeMeters?, timestampMillis?)` + `accuracyMeters?` + `speedMetersPerSecond?`.
  Les champs optionnels peuvent être absents ; le filtre adapte ses gardes en
  conséquence (cf. §6).

Précondition d'appel (garantie par le runtime, pas par le filtre) :
`currentSample` est **non nul** et **non identique** à l'ancre courante. Les cas
« aucun échantillon » et « relecture du cache sans nouveau fix » sont traités en
amont par `TripRuntime` et produisent des verdicts de niveau runtime
(`IGNORED_NO_SAMPLE`, `IGNORED_DUPLICATE`) que le filtre ne produit jamais.

### 2.3 Sortie

```text
FilterResult(
    state: TripState,      // nouvel état métier (inchangé si ignoré/rejeté/gaté)
    verdict: SampleVerdict, // miroir de la branche réellement prise (§5)
    nextState: FilterState  // état à transporter au tick suivant
)
```

Le runtime applique `filterState = result.nextState`, lit éventuellement `state`,
et journalise `verdict`. Il **ne lit ni n'écrit** le contenu de `FilterState` au
titre d'une décision (transport opaque, cf. §7).

---

## 3. FilterState

État pur, immutable, transporté entre deux applications. Source :
`domain/progress/FilterState`.

| Champ | Type | Rôle |
|---|---|---|
| `anchor` | `LocationSample?` | Référence depuis laquelle un segment est mesuré. `null` = pas d'ancre initiale. |
| `machineState` | `MachineState` | `STATIONARY` ou `MOVING`. Par défaut `STATIONARY`. |
| `stationaryCenter` | `LocationSample?` | Centre figé de la zone d'arrêt (référence du déplacement net en `STATIONARY`). |
| `movingStreak` | `Int` | Compteur d'échantillons consécutifs au-delà du seuil de départ (hystérésis de sortie d'arrêt). |
| `stationaryStreak` | `Int` | Compteur de pas consécutifs sous le rayon d'immobilité (hystérésis de retour à l'arrêt). |

Règles :

- L'état vide (`anchor == null`) signifie « aucune ancre » : la première
  application initialise l'ancre et le centre sur l'échantillon courant.
- `machineState` démarre à `STATIONARY` (hypothèse : un run commence typiquement à
  l'arrêt).
- `FilterState` **n'est pas persisté** (`TripStateSnapshot` ne le porte pas). Au
  redémarrage du process, le filtre repart à `STATIONARY`, ancre nulle —
  comportement sûr par défaut (pas de crédit possible sans ancre).

---

## 4. Machine STATIONARY / MOVING

Source : `applyWhileStationary` / `applyWhileMoving` / `gatedStationary` dans
`DistanceTripProgressEngine`. La détection repose sur le **déplacement net** (et
non la vitesse seule), avec hystérésis.

### 4.0 Cas neutres (avant la machine)

Si `anchor == null` **ou** `tripState.sessionState != Running` : le filtre délègue
à la primitive de garde par paire (verdict `IGNORED_NO_ANCHOR` ou
`IGNORED_NOT_RUNNING`, état métier inchangé), et `nextState` initialise/maintient
l'ancre (= échantillon courant) et le centre (= centre existant sinon échantillon
courant). Le gate ne s'applique qu'en course.

### 4.1 État STATIONARY (gate actif)

Le déplacement net est mesuré depuis le **centre figé** : `d_net = dist(center,
current)`.

- `d_net ≤ movementTriggerMeters` : échantillon **gaté**. Aucune accumulation,
  état métier inchangé, ancre **gelée au centre**, verdict `REJECTED_STATIONARY`.
  Le compteur `stationaryStreak` est conservé, `movingStreak` remis à 0.
- `d_net > movementTriggerMeters` : départ candidat. `movingStreak` incrémenté.
  - tant que `movingStreak < detectionHysteresisSamples` : échantillon **gaté**
    (mêmes effets que ci-dessus, ancre gelée au centre), `movingStreak` reporté.
  - dès que `movingStreak ≥ detectionHysteresisSamples` : **transition vers
    MOVING**. Le segment de départ est crédité **depuis le centre** via la
    primitive (mêmes gardes que §6) — le mouvement initial n'est pas perdu.
    `nextState` : ancre = échantillon courant, `machineState = MOVING`, centre =
    courant, compteurs remis à 0.

Effet d'ensemble : à l'arrêt, l'ancre ne « glisse » pas sur le bruit ; la longueur
de chemin du jitter n'est donc jamais comptée (cause structurelle de la dérive,
neutralisée).

### 4.2 État MOVING (comportement P3/P4.1 préservé)

- Accumulation par segment depuis l'ancre via la primitive (§6) ; l'ancre **avance
  à chaque échantillon**, y compris lorsque le verdict est un rejet.
- En parallèle, détection d'immobilité pas-à-pas : `step = dist(previousPoint,
  current)` (où `previousPoint = stationaryCenter ?: anchor`).
  - `step < stillnessRadiusMeters` : `stationaryStreak` incrémenté ; sinon remis à 0.
  - `stationaryStreak ≥ detectionHysteresisSamples` : **transition vers
    STATIONARY** (la dérive cessera alors d'être accumulée).
- `nextState` : ancre = courant, centre = courant, `machineState` selon la règle
  ci-dessus.

### 4.3 Résumé des transitions

| État courant | Condition | État suivant | Accumulation |
|---|---|---|---|
| STATIONARY | `d_net ≤ trigger` (ou hystérésis non atteinte) | STATIONARY | aucune (ancre gelée) |
| STATIONARY | `d_net > trigger` sur `detectionHysteresisSamples` ticks | MOVING | crédit du départ depuis le centre |
| MOVING | `step ≥ stillnessRadius` (ou hystérésis non atteinte) | MOVING | segment depuis l'ancre |
| MOVING | `step < stillnessRadius` sur `detectionHysteresisSamples` ticks | STATIONARY | segment du tick courant puis arrêt |

---

## 5. SampleVerdict

Énumération **fermée** (`domain/diag/SampleVerdict`) : chaque tick reçoit
exactement un verdict explicable (invariant I11). Verdicts **produits par le
filtre** :

| Verdict | Signification | Effet distance |
|---|---|---|
| `ACCEPTED_SEGMENT` | Segment plausible accepté (branche nominale, et crédit de départ à la bascule STATIONARY→MOVING). | total & partiel crédités |
| `REJECTED_STATIONARY` | Quasi-immobilité (vitesse source sous seuil) **ou** échantillon gaté par la machine STATIONARY. | aucun |
| `REJECTED_NOISE` | Segment sous le plancher bruit/incertitude. | aucun |
| `REJECTED_IMPLAUSIBLE_JUMP` | Vitesse implicite > plafond, ou délai non positif. | aucun |
| `IGNORED_NOT_RUNNING` | Session non `Running` (Stopped/Paused). | aucun |
| `IGNORED_NO_ANCHOR` | Aucune ancre exploitable comme référence. | aucun |

Verdicts de l'énumération **non produits par le filtre** (niveau runtime, cache de
localisation) : `IGNORED_DUPLICATE`, `IGNORED_NO_SAMPLE`.

Note de conception (P4.2) : les échantillons gatés par la machine **réutilisent**
`REJECTED_STATIONARY` plutôt que d'introduire un verdict dédié. Conséquence
d'observabilité : un `REJECTED_STATIONARY` ne distingue pas, à lui seul, un rejet
par vitesse d'un rejet par gate machine. Sans impact sur la distance. Un verdict
dédié reste une amélioration d'observabilité possible, **non implémentée** à ce
HEAD (cf. §9).

---

## 6. Garde de cohérence par paire (primitive)

Ordre exact des gardes dans `applyLocationSampleWithVerdict` (la primitive
appelée par la machine pour tout crédit réel). Toute branche retourne l'état
inchangé sauf la dernière.

1. `previousSample == null` → `IGNORED_NO_ANCHOR`.
2. `sessionState != Running` → `IGNORED_NOT_RUNNING`.
3. **Quasi-immobilité** : `speed != null && speed < stationarySpeedMetersPerSecond`
   → `REJECTED_STATIONARY`.
4. Calcul `distance = DistanceEngine.computeDistanceMeters(prev, current)`
   (Haversine, altitude ignorée).
5. **Plancher de mouvement** : `distance < plancher` → `REJECTED_NOISE`, où
   - si `current.speed != null` : plancher = `noiseFloorMeters`, **réduit à
     `movingNoiseFloorMeters` lorsque la machine est en état MOVING** (mouvement
     confirmé) — un pas piéton lent (~1,2 m/tick à 1 Hz) passait sous 2 m et était
     détruit (P5.c-3 étape A). Hors MOVING (crédit de départ, primitive sans état),
     le plancher reste `noiseFloorMeters`. Dans tous les cas, le plancher accuracy
     guillotinerait de vrais segments lents à 1 Hz, d'où la branche vitesse-présente ;
   - sinon : plancher = `max(noiseFloorMeters, pireAccuracy × accuracyFloorFactor)`
     (garde anti-dérive quand la vitesse est absente — **inchangée**).
6. **Saut implausible** : `Δt ≤ 0` **ou** vitesse implicite `> maxPlausibleSpeedKmh`
   → `REJECTED_IMPLAUSIBLE_JUMP`.
7. Sinon → `ACCEPTED_SEGMENT` : `total += distance` et `partiel += distance`
   (distance brute non corrigée ; le coefficient utilisateur n'intervient qu'à
   l'affichage, cf. §0 et I-SEP — le vestige `calibrationFactor` est purgé, P6.b).

---

## 7. Invariants

Chaque invariant est garanti par construction et couvert par au moins un test JVM
(nom de test entre parenthèses).

- **I-VERDICT (I11)** — toute application retourne exactement un verdict de
  l'énumération fermée
  (`apply_*_mirrors*` dans `GpsAccumulationFilterContractTest`).
- **I-GEL-ARRÊT** — en STATIONARY gaté, aucune accumulation et ancre gelée au
  centre (`stationaryState_gatesDriftAndFreezesAnchor`,
  `stationaryDrift_isNotAccumulated_andAnchorStaysFrozen`).
- **I-AVANCE-MOUV** — en MOVING, l'ancre avance vers l'échantillon courant **même
  sur rejet** (`movingState_advancesAnchorEvenWhenSampleRejected`,
  `movingState_advancesAnchorOnAcceptedSegment`).
- **I-CRÉDIT-DÉPART** — à la bascule STATIONARY→MOVING, le segment de départ est
  crédité depuis le centre (le mouvement initial n'est pas perdu)
  (`sustainedDeparture_transitionsToMoving_afterHysteresis_andCreditsDeparture`).
- **I-RETOUR-ARRÊT** — un mouvement qui s'immobilise durablement rebascule en
  STATIONARY (`sustainedStillness_whileMoving_transitionsBackToStationary`).
- **I-DÉMARRAGE** — la première application est STATIONARY et se centre sur le
  premier échantillon (`firstApply_isStationary_andCentersOnFirstSample`).
- **I-MIROIR-PRIMITIVE** — en MOVING, le filtre reproduit la primitive de garde
  par paire (`movingState_mirrorsPrimitive_andAdvancesAnchor`,
  `apply_*_mirrors*`).
- **I-TUNING-FIGÉ** — les valeurs par défaut de `FilterTuning` reproduisent les
  constantes historiques ; tout changement casse un test verrou
  (`defaultTuning_reproducesHistoricalConstants`,
  `engineWithDefaultTuning_matchesExplicitHistoricalTuning` dans `FilterTuningTest`).
- **I-SEP (séparation mesure/calibration)** — le filtre n'applique jamais le
  coefficient utilisateur ; celui-ci est appliqué exclusivement à l'affichage
  (`TripDisplayMapper`, couvert par `TripDisplayMapperTest`). Voir §0.
- **I-TRANSPORT** — `TripRuntime` transporte `FilterState` sans le lire pour
  décider ; seul `result.nextState` fait avancer l'ancre
  (`TripRuntimeFilterStateTest`).

---

## 8. Constantes internes de tuning

Source : `domain/progress/FilterTuning` (objet de valeurs nommées, injectable).
Ce sont des **seuils de mesure**, jamais des coefficients de correction de
distance.

| Constante | Valeur | Rôle |
|---|---|---|
| `noiseFloorMeters` | 2.0 | Plancher de bruit minimal (m). |
| `movingNoiseFloorMeters` | 1.4 | Plancher de bruit en MOVING confirmé (m), &lt; noiseFloorMeters (P5.c-3 étape A). |
| `accuracyFloorFactor` | 1.0 | Facteur appliqué au plancher d'incertitude. |
| `stationarySpeedMetersPerSecond` | 0.5 | Seuil de quasi-immobilité par vitesse source (m/s). |
| `maxPlausibleSpeedKmh` | 200.0 | Vitesse implicite maximale plausible (km/h). |
| `movementTriggerMeters` | 15.0 | Déplacement net de sortie d'arrêt (m). |
| `stillnessRadiusMeters` | 1.0 | Déplacement pas-à-pas sous lequel l'appareil est jugé immobile (m). |
| `detectionHysteresisSamples` | 8 | Échantillons consécutifs confirmant une transition (anti-flapping). |

Justifications de réglage (issues du replay du corpus M1/M2, documentées dans les
reprises P4) :

- `stillnessRadiusMeters = 1.0` : à 3.0 (~10,8 km/h) la conduite urbaine lente
  était classée à tort « immobile » (distorsion observée +64 % sur un log urbain) ;
  1.0 (~3,6 km/h) ne traite en arrêt que la quasi-immobilité réelle.
- `detectionHysteresisSamples = 8` : ~8 s à 1 Hz pour confirmer une transition,
  protège le mouvement contre le flapping.

Les conversions d'unités physiques (`MILLIS_PER_SECOND`,
`METERS_PER_SECOND_TO_KMH`) ne sont **pas** du tuning et restent internes au
moteur.

---

## 9. Limites connues

Déduites des reprises P4 (`cloture_chantier_P4_gate_stationnaire`,
`audit_post_P4_2_gate_stationnaire`, `capture_urbain_P4_2_…`).

- **Validation métrologique partielle** : une seule référence terrain (odomètre
  route 6,80 km, imprécis), corpus de 6–7 logs. Suffisant pour valider
  l'architecture, insuffisant pour un réglage statistiquement robuste.
- **Sous-accumulation urbaine possible** : un log urbain référencé (8,20 km
  odomètre) lit ~−2,7 %, au-delà de la cible ±2 %. Signal à instruire, non
  conclusion.
- **Marche lente non bornée** : aucun log de marche lente référencé ; le risque de
  sous-comptage d'un déplacement piéton lent et soutenu (< ~3,6 km/h) n'est pas
  mesuré empiriquement. Atténué par le seuil de départ (15 m) qui finit par
  déclencher MOVING.
- **Micro-déplacement court** : un mouvement entièrement contenu dans la fenêtre
  d'hystérésis (< 8 échantillons ≈ 8 s) peut ne pas être crédité. Négligeable pour
  un usage rallye (trajets longs), réel pour des micro-déplacements.
- **Verdict conflé** : gate machine et rejet vitesse partagent
  `REJECTED_STATIONARY` (cf. §5).
- **Hors périmètre à ce HEAD** : pas de modèle watchdog / `REFERENCE_ONLY`, pas de
  ré-ancrage explicite après trou GPS, pas de verdicts `REANCHORED_AFTER_GAP` /
  `REJECTED_STALE` / `ACCEPTED_NET_ON_TRANSITION` (évoqués au plan, **non
  implémentés**). Un trou GPS est traité par les gardes existantes (saut
  implausible) et la mécanique d'ancre, sans verdict dédié.

---

## 10. Articulation avec P5

P5 (calage des constantes) **n'est pas ouvert par ce contrat**. Ce document en fixe
seulement le cadre :

- P5 agit **uniquement** sur les valeurs de `FilterTuning` (§8), par replay du
  corpus, **sans modifier la logique** de la machine ni les gardes.
- P5 est **indépendant du coefficient utilisateur de calibration** : il ne le
  règle pas, ne le compense pas, ne le remet pas en cause (I-SEP, §0).
- Prérequis posé par la clôture P4, et **non levé** : capturer un **log de marche
  lente référencé** (distance connue) avant tout réglage fin, seul moyen de borner
  la sous-accumulation piétonne.
- Le verrou `FilterTuningTest` (§7) garantit qu'aucune constante ne dérive sans
  justification : P5 mettra à jour ce verrou avec la matrice de sensibilité.

Toute évolution de la **logique** (nouveaux verdicts, watchdog, ré-ancrage après
gap) relève de chantiers ultérieurs à contractualiser séparément, pas de P5.

---

## 11. Statut normatif du document

- **Normatif et descriptif** : ce contrat décrit le comportement **implémenté et
  testé** à HEAD `6449fe6` (série P1→P4 close). En cas d'écart entre ce document et
  le code, l'écart est un défaut à corriger — le code et les tests faisant foi
  pour le comportement, ce contrat faisant foi pour l'**intention** et les
  frontières (notamment I-SEP).
- **Référence, pas planification** : il ne crée aucun tag, n'engage aucun code, et
  ne lance pas P5.
- **Version** : v0.1. Une révision sera émise si la logique du filtre évolue
  (P-series ultérieures) ; le calage P5 des constantes (§8) ne requiert pas de
  nouvelle version de ce contrat, seulement la mise à jour des valeurs et du verrou.
