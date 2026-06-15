# Spécification des tests filtre restants — batterie T1–T10 + M4 runtime

> **Navigation** — Contrat : [`gps_accumulation_filter_v0_1.md`](../contrats/gps_accumulation_filter_v0_1.md) ·
> Plan directeur : [`plan_action_filtrage_gps_P1_P6_2026_06_11.md`](plan_action_filtrage_gps_P1_P6_2026_06_11.md) (§10) ·
> État courant : [`../ETAT.md`](../ETAT.md)

> **Nature** : document de plan, **non normatif**. Il **spécifie** des tests JVM à
> écrire ; il n'en implémente aucun. Aucun code, moteur, seuil, JSONL ni borne
> modifié par ce document. L'implémentation Kotlin sera un chantier de tests
> ultérieur, sans toucher au moteur ni aux seuils.

## 1. Objet

Le plan §10 liste la batterie T1–T10 et un test M4 « transitions » comme **à
créer** ; aucun n'est aujourd'hui matérialisé (audit du 2026-06-15). Ce document
opérationnalise cette liste en spécifications implémentables : pour chaque test,
objectif, mécanisme visé, entrées, séquence, verdicts attendus, acceptation,
invariant verrouillé et emplacement cible.

Ce sont des tests **synthétiques déterministes** (horloge simulée, échantillons
fabriqués) — **aucune dépendance terrain**. Ils complètent, sans les remplacer :
- `GpsAccumulationFilterContractTest` (mapping par paire → verdict, déjà couvert) ;
- le harness de replay + `RealLogReplayTest` (corpus terrain, structurel) ;
- `LowSpeedRegressionReplayTest` (bandes de non-régression sur logs réels).

## 2. Constantes de référence (lues dans `FilterTuning`, HEAD courant)

| Constante | Valeur | Rôle |
|---|---|---|
| `noiseFloorMeters` | 2.0 m | plancher de bruit (hors mouvement établi) |
| `movingNoiseFloorMeters` | 1.4 m | plancher réduit en MOUVEMENT (P5.c-3) |
| `accuracyFloorFactor` | 1.0 | plancher lié à l'accuracy |
| `stationarySpeedMetersPerSecond` | 0.5 m/s | seuil de vitesse stationnaire |
| `maxPlausibleSpeedKmh` | 200 km/h | garde saut implausible |
| `movementTriggerMeters` | 15.0 m | déclenchement STATIONNAIRE → MOUVEMENT |
| `stillnessRadiusMeters` | 1.0 m | rayon d'immobilité |
| `detectionHysteresisSamples` | 8 | hystérésis de détection |

Verdicts réels (enum `SampleVerdict`) référencés ci-dessous : `ACCEPTED_SEGMENT`,
`ACCEPTED_NET_ON_TRANSITION`, `REJECTED_NOISE`, `REJECTED_STATIONARY`,
`REJECTED_IMPLAUSIBLE_JUMP`, `REJECTED_STALE`, `REANCHORED_AFTER_GAP`,
`IGNORED_DUPLICATE`, `IGNORED_NOT_RUNNING`, `IGNORED_NO_ANCHOR`, `IGNORED_NO_SAMPLE`.

> **Avertissement sur les seuils d'acceptation du plan §10** : certains chiffres du
> §10 ont été écrits pour l'ancienne approche « route A » (ancre glissante), p. ex.
> « le code HEAD échoue à T2 ». Le moteur courant est la **machine
> stationnaire/mouvement**. Chaque acceptation ci-dessous doit donc être **vérifiée
> contre le moteur courant** à l'implémentation ; là où le comportement attendu a
> changé, on documente l'écart plutôt que de forcer la valeur historique.

## 3. Outillage commun à écrire

- **Horloge simulée** : timestamps `sample_ts_ms` fournis explicitement par le test
  (pas de temps réel).
- **Fabrique d'échantillons** : helper construisant des `LocationSample`
  (lat/lon/accuracy/speed/ts) à partir d'un point de base + déplacements en mètres
  (conversion mètres→degrés locale, déterministe).
- **Générateur de bruit borné** : random walk à graine fixe (reproductible) pour T1.
- Emplacement proposé : `app/src/test/java/.../domain/progress/FilterSequenceTest.kt`
  (ou un fichier par test si plus lisible), plus un `FilterSequenceSupport.kt` pour
  les helpers.

## 4. Spécifications T1–T10

### T1 — Jitter stationnaire sans vitesse
- **Mécanisme** : gate stationnaire + plancher de bruit.
- **Entrées** : point fixe + random walk borné σ ∈ {3, 8} m, accuracy 5–15 m,
  `speed = 0`, 600 ticks à 1 Hz, graine fixe.
- **Attendu** : aucune sortie de l'état STATIONNAIRE ; verdicts dominés par
  `REJECTED_STATIONARY` / `REJECTED_NOISE`.
- **Acceptation** : `total_m ≤ ε` (ε petit, p. ex. ≤ 5 m sur 600 ticks).
- **Invariant** : I-GEL-ARRÊT (dérive neutralisée à l'arrêt).

### T2 — Jitter + vitesses parasites lentes
- **Mécanisme** : gate stationnaire (`speed < 0.5 m/s`) + plancher ; **pas** la garde
  implausible (réservée à > 200 km/h).
- **Entrées** : comme T1 mais `speed` parasite 0,5–1,5 m/s sans déplacement réel net.
- **Attendu** : pas d'accumulation parasite ; le déplacement net reste sous
  `movementTrigger`.
- **Acceptation** : `total_m ≤ ε`. *(Re-vérifier vs machine courante ; le §10 notait
  un échec sous l'ancienne route A.)*
- **Invariant** : I-GEL-ARRÊT.

### T3 — Excursion-retour 10 m
- **Mécanisme** : non-déplacement de l'ancre en STATIONNAIRE / gestion d'excursion.
- **Entrées** : départ immobile, excursion ~10 m puis retour au point initial.
- **Attendu** : crédit borné ; **jamais** ~20 m (pas de double comptage aller-retour).
- **Acceptation** : `total_m ≤ G` (G petit, à fixer selon mécanisme courant).
- **Invariant** : I4 (ancre non déplacée par un rejet) / anti double-comptage.

### T4 — Marche lente rectiligne 1 m/s, 300 s
- **Mécanisme** : passage MOUVEMENT, plancher réduit 1.4 m, crédit net à la bascule.
- **Entrées** : trajet rectiligne 1 m/s, 300 ticks, accuracy modérée.
- **Attendu** : comptage proche de la distance vraie (~300 m).
- **Acceptation** : `total_m ∈ [294, 300] m` *(borne §10 ; re-vérifier — inclut le
  crédit net `ACCEPTED_NET_ON_TRANSITION` à la bascule)*.
- **Invariant** : I8 (comptage en mouvement établi).

### T5 — Stop-and-go ×10
- **Mécanisme** : bascules répétées STATIONNAIRE↔MOUVEMENT.
- **Entrées** : 10 cycles (30 s arrêt / 20 s à 3 m/s).
- **Attendu** : aucun crédit pendant les arrêts ; redémarrages non avalés ; erreur
  totale bornée.
- **Acceptation** : `|total_m − distance_vraie| ≤ tolérance` ; segments d'arrêt à 0.
- **Invariant** : I-GEL-ARRÊT + I-AVANCE-MOUV (combinés sur séquence).

### T6 — Premier fix dégradé
- **Mécanisme** : qualification avant crédit.
- **Entrées** : premier fix à 40 m d'erreur, accuracy 50 m, puis convergence.
- **Attendu** : `IGNORED_NO_ANCHOR` / aucun crédit avant qualification.
- **Acceptation** : `total_m = 0` sur la phase de non-qualification.
- **Invariant** : I-DÉMARRAGE.

### T7 — Trou GPS et réancrage
- **Mécanisme** : détection de trou → `REANCHORED_AFTER_GAP`.
- **Entrées** : (a) trou 120 s puis fix à 500 m ; (b) trou 3 s plausible.
- **Attendu** : (a) `REANCHORED_AFTER_GAP`, crédit 0 sur le saut ; (b) crédit normal.
- **Acceptation** : (a) aucun crédit du saut de 500 m ; (b) crédit ~ déplacement réel.
- **Invariant** : I (réancrage après trou).

### T8 — Points périmés / dupliqués
- **Mécanisme** : rejet temporel.
- **Entrées** : timestamp ≤ précédent (périmé) ; timestamp identique (dupliqué).
- **Attendu** : `REJECTED_STALE` / `IGNORED_DUPLICATE` ; FilterState inchangé.
- **Acceptation** : aucun déplacement d'ancre, aucun crédit.
- **Invariant** : I (stabilité d'état sur entrée non informative).

### T9 — Rejet sans déplacement d'ancre
- **Mécanisme** : invariant I4 direct.
- **Entrées** : outlier injecté en MOUVEMENT, puis en STATIONNAIRE.
- **Attendu** : ancre **bit-à-bit identique** avant/après le rejet.
- **Acceptation** : égalité stricte des coordonnées d'ancre.
- **Invariant** : I4.

### T10 — Indépendance de cadence
- **Mécanisme** : robustesse à la fréquence d'échantillonnage.
- **Entrées** : séquences T1 et T4 rééchantillonnées à 0,5 Hz et 2 Hz.
- **Attendu** : mêmes verdicts qualitatifs, totaux équivalents (à tolérance près).
- **Acceptation** : `total_m` cohérent entre cadences ; pas d'inversion de verdict.
- **Invariant** : I (indépendance de cadence).

## 5. Test M4 — pause/reprise et trou au niveau runtime

- **Emplacement** : `app/src/test/java/.../runtime/` (à côté de `TripRuntimeTest`).
- **Manque actuel** : `TripRuntimeTest` couvre les **transitions d'état**
  (`fromRunning_pauses`, `fromPaused_resumesRunning`) mais **pas** la garantie de
  **distance** à travers une pause/coupure.
- **Spécification** :
  - injecter une séquence de ticks en `Running` (crédit normal), puis **PAUSE**,
    puis une série de ticks en déplacement **pendant la pause**, puis **REPRISE** ;
  - vérifier que `total_m` **n'augmente pas** pendant la phase pausée
    (verdicts `IGNORED_NOT_RUNNING`, `delta_total_m = 0`) ;
  - variante trou GPS : pas de crédit du saut à la reprise après un trou.
- **Acceptation** : `Δtotal = 0` sur toute la fenêtre pausée / le saut de reprise.
- **Invariant** : M4 (zéro crédit à travers pause/coupure). C'est exactement le
  comportement observé empiriquement sur la capture voiture du 2026-06-15
  ([réf. 7,8 km](reprises/capture_voiture_20260615_ref_7_8km.md)) ; ce test le
  **verrouille**.

## 6. Hors périmètre de ce chantier de tests

- Aucune modification du moteur, des seuils (`FilterTuning`), de l'accumulation,
  du format JSONL ni de l'export.
- Pas de nouvelle borne chiffrée dans `LowSpeedRegressionReplayTest` (chantier
  distinct) ; les captures terrain du 2026-06-15 restent rejouées structurellement
  par `RealLogReplayTest`.
- Pas de capture terrain ici (T1–T10 sont synthétiques).

## 7. Ordre d'implémentation suggéré

1. Outillage commun (horloge simulée, fabrique d'échantillons, bruit à graine fixe).
2. T1, T3, T9, T8 (les plus directs, verrouillent les invariants d'arrêt/ancre).
3. T4, T5, T6, T7 (séquences MOUVEMENT et transitions).
4. T2, T10 (cas fins / robustesse cadence).
5. Test M4 runtime.

À chaque étape : exécuter `:app:testDebugUnitTest`, vérifier vert, sans toucher au
moteur ni aux seuils. Si un test révèle un écart réel de comportement, le
**documenter** (et décider séparément), ne pas ajuster le moteur dans le même geste.
