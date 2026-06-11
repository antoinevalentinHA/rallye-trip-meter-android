# Plan d'action — Correction du filtrage GPS anti-dérive

**Dépôt :** `antoinevalentinHA/rallye-trip-meter-android` — branche `main`
**HEAD de référence :** `1d74bba5915aca66f127bbcc4a936a1ced19aa1b`
**Date :** 2026-06-11
**Nature :** plan séquencé P1→P6, sans code. Chaque palier est conçu pour être confié tel quel à un agent de codage.
**Interdits transverses :** pas de réglage de seuil comme solution principale ; pas de calibration multiplicative ; aucun changement de comportement avant que l'observabilité (P1) soit livrée et exploitée (P2).

---

## 1. Diagnostic confirmé ou corrigé

Relecture à HEAD des cinq composants demandés. Le diagnostic du dernier audit est **confirmé**, avec deux précisions nouvelles.

### 1.1 `TripRuntime.applyLocationEngineSample` — confirmé, défaut structurel

```
previousLocationSample = currentSample   // AVANT l'appel au moteur, inconditionnel
```

L'ancre appartient au runtime, pas au filtre. Elle avance que le point soit accepté, rejeté, ou que la session soit en pause. Conséquence : à l'arrêt, le moteur intègre la longueur de chemin du bruit GPS (chaque excursion comptée aller **et** retour), au lieu du déplacement net depuis une référence stable. C'est la cause structurelle de la dérive ; aucun réglage de seuil ne peut la corriger.

### 1.2 `DistanceTripProgressEngine` — confirmé, trois fuites

- **Bypass vitesse** : `speed != null` (donc ≥ 0,5 m/s après le rejet stationnaire) → plancher fixe 2 m, accuracy ignorée. Une vitesse parasite à l'arrêt ouvre la vanne.
- **Plancher 1 × accuracy** quand `speed == null` : seuil posé exactement sur la frontière statistique d'une accuracy souvent optimiste en intérieur.
- **Aucune mémoire** : décision par paire (previous, current), pas d'état stationnaire/mouvement, pas d'hystérésis, pas d'âge d'ancre.
- **Précision nouvelle (a)** : le moteur porte un `calibrationFactor` (défaut 1.0, non câblé par `TripRuntime` ni par le ViewModel, mais couvert par le test `applyLocationSample_appliesCalibrationFactorToAccumulatedDistance`). Vestige contraire à la doctrine moteur brut — sa purge est planifiée (P6) et le test associé sera supprimé avec lui.
- **Précision nouvelle (b)** : l'en-tête du fichier déclare « Aucun état mutable ». La future propriété de l'ancre par le filtre doit donc être conçue en **état explicite immutable transporté**, pas en champ mutable interne (voir §2), sous peine de casser la doctrine du projet.

### 1.3 `HaversineDistanceEngine` — hors de cause

Implémentation correcte (formule standard, rayon 6 371 000 m, altitude ignorée volontairement). Aucun changement requis à aucun palier. Il reste le calculateur métrique unique ; le problème est entièrement dans la **décision** d'accumulation, pas dans la **mesure**.

### 1.4 `TripMeterForegroundService` — sain, mais à instrumenter

Pump unique à 1 Hz (`SAMPLE_INTERVAL_MS = 1000`) sur le main Looper, post-B4 (pump UI neutralisé). Il relit un cache (`AndroidLocationEngine.lastLocationSample`, abonnement GPS_PROVIDER minTime 1000 ms / minDistance 0). La relecture d'un fix inchangé produit Δt = 0 → rejet : pas de double comptage (couvert par `applyLocationSample_repeatedTicksOnSameFix_doNotDoubleAccumulate`). Le service est le bon point d'ancrage du flush du logger P1.

### 1.5 Tests existants du moteur — couverture par paire, zéro couverture par séquence

`DistanceTripProgressEngineTest` : 18 tests, tous sur **une seule paire** d'échantillons. `TripRuntimeTest` : 2 ticks maximum. Aucun test ne déroule une séquence (jitter stationnaire, excursion-retour, stop-and-go, trou GPS). C'est le trou de couverture qui a laissé passer les 130 m : chaque garde est correcte localement, leur composition dans le temps ne l'est pas.

### 1.6 Logger JSONL rejouable en JVM — faisable, sous une contrainte

`gradle/libs.versions.toml` ne déclare **aucune bibliothèque JSON** (ni kotlinx-serialization, ni Moshi ; JUnit 4 seul en test). Décision de plan : writer et parser JSONL **maison**, schéma plat versionné (aucune imbrication, clés fixes, types primitifs), pur Kotlin, testés en round-trip. Aucune dépendance ajoutée. Le format plat rend le parser trivial et la doctrine zéro-dépendance est respectée.

### 1.7 Verdict

Diagnostic de l'audit confirmé. Corrections apportées au plan par rapport à une lecture naïve : (a) le filtre propriétaire de l'ancre sera **pur à état explicite**, pas stateful ; (b) le logger sera sans dépendance ; (c) le `FilterState` ne sera **pas persisté** (`TripStateSnapshot` reste inchangé : au redémarrage du process, le filtre repart en STATIONNAIRE non qualifié — comportement sûr par défaut).

---

## 2. Architecture cible

### 2.1 Principe

Le filtrage devient un composant de **décision** unique, pur, possédant sémantiquement l'ancre via un état explicite opaque :

```
GpsAccumulationFilter (domain/progress ou domain/filter)
  fun apply(
      filterState: FilterState,        // immutable, opaque pour l'appelant
      tripState: TripState,
      sample: LocationSample,
      nowMillis: Long                  // injecté, jamais System.currentTimeMillis interne
  ): FilterResult

FilterResult(
  filterState: FilterState,            // nouvel état du filtre (ancre, machine, fenêtres)
  tripState: TripState,                // état métier éventuellement crédité
  verdict: SampleVerdict,              // énum fermée, toujours renseignée
  verdictDetail: VerdictDetail         // distances, gates, d_net — pour le log
)
```

- `TripRuntime` **transporte** `FilterState` mais ne le lit ni ne l'écrit jamais (autorité unique I9, version sémantique).
- `FilterState` contient : ancre (`GeoPoint` + timestamp + accuracy d'ancrage), état machine (`STATIONARY` / `MOVING` / `UNQUALIFIED`), compteurs de persistance, dernier échantillon vu (pour Δt et fraîcheur).
- `SampleVerdict` (énum fermée, invariant I11) : `ACCEPTED_SEGMENT`, `ACCEPTED_NET_ON_TRANSITION`, `REJECTED_STATIONARY`, `REJECTED_NOISE`, `REJECTED_IMPLAUSIBLE_JUMP`, `REJECTED_STALE`, `REANCHORED_AFTER_GAP`, `IGNORED_NOT_RUNNING`, `IGNORED_DUPLICATE`, `IGNORED_NO_ANCHOR`.
- `HaversineDistanceEngine` reste injecté, inchangé.
- Le logger (`TickLogSink`) est un contrat domaine no-op par défaut ; l'adaptateur Android écrit le JSONL.

### 2.2 Invariants portés par cette architecture

| Invariant | Garantie architecturale |
|---|---|
| Rejet sans déplacement d'ancre (I4) | Seul `apply()` produit un `FilterState` ; un verdict REJECTED_* retourne l'ancre inchangée par construction, testable directement |
| Zéro dérive bornée à l'arrêt (I1) | État STATIONARY : aucune accumulation, ancre fixe ; le crédit n'existe qu'à la transition, borné par le déplacement net |
| Pas de téléportation après trou GPS (I7) | `Δt > T_gap` → verdict `REANCHORED_AFTER_GAP`, crédit zéro, ancre = nouveau point |
| Comptage acceptable en mouvement lent (I8) | Sortie de STATIONARY par déplacement net cumulé + crédit rétroactif `d_net` à la bascule : les premiers mètres ne sont pas perdus |
| Verdict explicable pour chaque tick (I11) | `apply()` retourne toujours un verdict ; le pipeline ne peut pas accumuler sans verdict ACCEPTED_* |

### 2.3 Cible des flux

```
Service (1 Hz) ──ApplyLocationSample──► TripRuntime
TripRuntime: sample = locationEngine.getLastLocationSample()
             result = filter.apply(filterState, state, sample, now)
             filterState = result.filterState ; state = result.tripState
             tickLogSink.log(result → TickLogEntry)
```

`SimulateLocationStep` passe par le même `apply()` (cohérence, pas de chemin parallèle).

---

## 3. Plan par paliers P1 → P6 (vue d'ensemble)

| Palier | Contenu | Changement de comportement d'accumulation |
|---|---|---|
| P1 | Observabilité : verdicts + logger JSONL + flush | **Aucun** |
| P2 | Corpus terrain + harness de replay JVM | **Aucun** (aucun code app, hors outillage de test) |
| P3 | Refactor neutre : propriété de l'ancre par le filtre, `FilterState` explicite | **Aucun** (prouvé par replay bit-à-bit) |
| P4 | Machine STATIONNAIRE/MOUVEMENT, gardes I1/I4/I6/I7 | **Oui — le seul palier qui change l'accumulation** |
| P5 | Calage des constantes par replay | Paramétrique uniquement |
| P6 | Validation terrain finale + purge `calibrationFactor` | Aucun (purge à comportement constant, facteur = 1.0) |

Règle de séquencement dure : P4 ne démarre pas tant que P2 n'a pas produit au moins un log canapé et un log mouvement exploitables. P5 ne modifie jamais de logique, uniquement des constantes nommées.

---

## 4. Détail P1 — Observabilité

**Objectif.** Rendre chaque tick explicable et rejouable, sans changer d'un millimètre le comportement d'accumulation. Livrer : énum de verdict instrumentée par-dessus la logique actuelle, schéma JSONL v1, writer Android, accès au fichier depuis le téléphone.

**Fichiers probablement concernés.**
- Nouveaux, domaine : `domain/diag/SampleVerdict.kt`, `domain/diag/TickLogEntry.kt`, `domain/diag/TickLogSink.kt` (interface + `NoOpTickLogSink`), `domain/diag/TickLogJsonl.kt` (encode/parse, pur Kotlin).
- Nouveaux, Android : `android/diag/FileTickLogSink.kt` (écriture JSONL bufferisée, fichier par session dans `context.getExternalFilesDir("gpslogs")` — récupérable depuis le téléphone sans adb, compatible flux Termux).
- Modifiés : `runtime/TripRuntime.kt` (injection `TickLogSink` défaut no-op ; émission d'une entrée par `ApplyLocationSample`), `DistanceTripProgressEngine.kt` (**instrumentation seulement** : la logique de décision actuelle est annotée pour exposer la raison du rejet — soit par un retour enrichi interne, soit par un callback de verdict — sans modifier aucun seuil ni aucun ordre de garde), `TripMeterForegroundService.kt` (flush du sink sur `onDestroy` et à intervalle), `TripMeterViewModelFactory.kt` (câblage du sink réel).

**Changements attendus.**
- Schéma JSONL v1, plat, une ligne par tick :
  `v, tick_elapsed_ms, sample_ts_ms, sample_is_new, lat, lon, accuracy_m, speed_mps, gps_status, session_state, prev_ts_ms, segment_m, verdict, floor_m, implied_speed_kmh, delta_total_m, total_m`
  (champs absents = `null` littéral ; en-tête de fichier : 1 ligne meta `v, commit, device, started_at`).
- Le verdict est calculé en miroir exact des branches existantes du moteur : `IGNORED_NO_ANCHOR`, `IGNORED_NOT_RUNNING`, `REJECTED_STATIONARY`, `REJECTED_NOISE` (plancher), `REJECTED_IMPLAUSIBLE_JUMP`, `ACCEPTED_SEGMENT`, `IGNORED_DUPLICATE` (sample_is_new = false). Aucune nouvelle branche de décision.
- Aucune dépendance ajoutée à `libs.versions.toml`.

**Tests à ajouter.**
- `TickLogJsonlTest` : round-trip encode→parse pour chaque verdict, valeurs null, valeurs extrêmes (lat négative, accuracy null).
- `TripRuntimeLoggingTest` : un tick avec fake engine → exactement une entrée, champs cohérents avec l'état ; ticks dupliqués → `IGNORED_DUPLICATE` ; total inchangé à l'octet près sur les scénarios des tests runtime existants.
- Extension de `DistanceTripProgressEngineTest` : chaque test existant vérifie en plus le verdict attendu (sans modifier les assertions de distance).

**Critères d'acceptation.**
- Tous les tests existants verts sans modification de leurs assertions de distance.
- Une session réelle produit un fichier JSONL lisible, une ligne par tick, zéro tick sans verdict.
- Sink no-op par défaut : aucun coût quand non câblé.

**Risques.**
- I/O sur le main Looper (le pump y vit) → écriture bufferisée + flush périodique hors chemin chaud ; jamais de flush synchrone par tick.
- Dérive de miroir (le verdict loggé ne reflète pas la vraie branche prise) → mitigation : le verdict est produit **dans** le moteur, pas reconstruit à l'extérieur.

**Preuve de non-régression.** Suite de tests inchangée verte ; comparaison avant/après sur les scénarios `TripRuntimeTest` : totaux identiques bit-à-bit ; revue : aucun diff dans les constantes ni dans l'ordre des gardes du moteur.

---

## 5. Détail P2 — Corpus / replay

**Objectif.** Constituer un corpus de logs terrain de référence et un harness JVM qui rejoue un JSONL dans le pipeline de décision. Sortie : diagnostic chiffré de la route de fuite (bypass vitesse vs jitter > accuracy) et base de non-régression de tous les paliers suivants.

**Fichiers probablement concernés.**
- Nouveaux, test uniquement : `app/src/test/.../replay/JsonlReplayHarness.kt` (parse → séquence de ticks → injection dans `TripRuntime` avec fake `LocationEngine` piloté + fake clock), `app/src/test/.../replay/ReplayReport.kt` (total accumulé, comptage par verdict, histogramme des segments acceptés, distance par minute).
- Nouvelles ressources : `app/src/test/resources/replay/` (corpus, fichiers nommés `YYYYMMDD_scenario_device.jsonl`).
- Documentation : `docs/plan/p2_corpus_replay_<date>.md` (protocole de capture + résultats).
- **Aucun fichier de production modifié.**

**Changements attendus.**
- Protocole de capture terrain exécuté avec l'app P1 : (a) canapé 15 min écran éteint ; (b) rebord de fenêtre ou voiture garée moteur tournant 15 min ; (c) marche lente ~500 m mesurée ; (d) trajet urbain stop-and-go ~5 km avec trace GPX tierce de référence ; (e) trajet routier ~20 km idem.
- Analyse rendue dans le doc P2 : pour chaque log d'arrêt, décomposition de la dérive par verdict et par condition (`speed != null` vs `null`, segments vs accuracy) → la route A (bypass vitesse) et la route B (jitter > 1 × accuracy) sont quantifiées, pas supposées.

**Tests à ajouter.**
- `ReplayHarnessSelfTest` : un mini-log synthétique embarqué rejoue à un total connu (prouve le harness lui-même).
- Tests « golden » : pour chaque fichier du corpus, le replay sur le code à HEAD produit un total enregistré comme valeur de référence (ces goldens seront **intentionnellement mis à jour** en P4, avec justification par fichier).

**Critères d'acceptation.**
- Replay du log canapé ≈ total observé sur le téléphone (tolérance < 1 %) : le pipeline JVM est fidèle.
- Le doc P2 conclut explicitement : « X % de la dérive canapé passe par la route A, Y % par la route B », avec les distributions à l'appui.
- Au minimum scénarios (a) et (c) ou (d) capturés ; (b) et (e) souhaités.

**Risques.**
- Corpus mono-téléphone → biais chipset ; mitigation : le noter dans le doc, prévoir un second appareil si disponible, et dimensionner P5 avec marges.
- Logs volumineux dans le dépôt → tronquer aux fenêtres utiles (les goldens n'exigent pas des heures de trace).

**Preuve de non-régression.** Par définition aucun code de production ne change ; CI verte ; le harness est validé par son self-test et par la concordance replay/terrain.

---

## 6. Détail P3 — Refactor neutre de propriété d'ancre

**Objectif.** Transférer la propriété de l'ancre de `TripRuntime` vers le filtre via `FilterState` explicite immutable, introduire `FilterResult`, **sans changer le comportement** : mêmes seuils, mêmes gardes, même ordre, mêmes verdicts — y compris la sémantique actuelle « l'ancre avance même sur rejet », reproduite à l'identique dans ce palier (c'est P4 qui la corrigera).

**Fichiers probablement concernés.**
- Nouveaux : `domain/progress/FilterState.kt`, `domain/progress/FilterResult.kt`, `domain/progress/GpsAccumulationFilter.kt` (nouveau contrat remplaçant `TripProgressEngine` pour le chemin GPS).
- Modifiés : `DistanceTripProgressEngine.kt` → implémente le nouveau contrat (l'ancienne signature `(state, previousSample, currentSample)` disparaît ou devient un adaptateur transitoire) ; `runtime/TripRuntime.kt` → supprime `previousLocationSample`, transporte `filterState`, route `ApplyLocationSample` **et** `SimulateLocationStep` par le même `apply()` ; `TickLogEntry` enrichi (`anchor_lat, anchor_lon, anchor_age_ms, d_net_from_anchor_m, machine_state`) → schéma JSONL **v2** (le parser du harness accepte v1 et v2).
- Tests modifiés mécaniquement : `DistanceTripProgressEngineTest`, `TripRuntimeTest` (adaptation de signature, assertions de distance inchangées).

**Changements attendus.**
- `FilterState` v0 : `lastSample` (l'« ancre » actuelle, qui dans ce palier avance à chaque tick comme aujourd'hui), rien d'autre. La structure existe, la sémantique n'a pas encore changé.
- `TripRuntime` ne contient plus aucune connaissance de localisation au-delà du transport opaque.
- Décision actée : `FilterState` **non persisté** (TripStateSnapshot inchangé ; au restart, filtre vierge).

**Tests à ajouter.**
- `GpsAccumulationFilterContractTest` : pour chaque cas des 18 tests moteur existants, vérifier (distance, verdict, filterState.lastSample) — en particulier : **sur rejet, lastSample avance quand même** (assertion explicite de la sémantique héritée, qui sera inversée en P4 ; ce test documente le point de bascule).
- `TripRuntimeNeutralityTest` : séquences multi-ticks (mêmes que `TripRuntimeTest`) → totaux identiques à l'implémentation précédente.

**Critères d'acceptation (le cœur du palier).**
- **Replay bit-à-bit** : tous les fichiers du corpus P2 rejoués sur P3 donnent un total strictement identique aux goldens HEAD, et la séquence de verdicts est identique ligne à ligne.
- Suite complète verte ; aucune constante modifiée (diff vérifiable : `NOISE_FLOOR_METERS`, `ACCURACY_FLOOR_FACTOR`, `STATIONARY_SPEED_MPS`, `MAX_PLAUSIBLE_SPEED_KMH` inchangés).

**Risques.**
- Refactor « presque neutre » qui change subtilement un ordre de garde → mitigation : replay ligne à ligne sur verdicts, pas seulement sur totaux.
- `SimulateLocationStep` oublié sur l'ancien chemin → mitigation : test dédié de routage.
- Tentation d'améliorer en passant → interdit : tout changement de comportement détecté = échec du palier.

**Preuve de non-régression.** Le replay bit-à-bit du corpus P2 **est** la preuve. C'est la raison d'être de l'ordre P2 → P3.

---

## 7. Détail P4 — Machine STATIONNAIRE / MOUVEMENT

**Objectif.** Implémenter la nouvelle décision d'accumulation : ancre fixe à l'arrêt, sortie par déplacement net persistant, crédit rétroactif du net à la bascule, hystérésis de retour, gardes fraîcheur/gap/saut. Seul palier qui change l'accumulation.

**Fichiers probablement concernés.**
- Modifiés : `DistanceTripProgressEngine.kt` (ou nouveau `StationaryAwareAccumulationFilter.kt` remplaçant l'implémentation, l'ancienne restant dans l'historique git), `FilterState.kt` (ancre qualifiée, état machine, compteur de persistance, fenêtre de retour), `SampleVerdict.kt` (ajout `ACCEPTED_NET_ON_TRANSITION`, `REJECTED_STALE`, `REANCHORED_AFTER_GAP`, `IGNORED_NO_ANCHOR` au sens « ancre non qualifiée »), `TickLogJsonl` (v2 déjà prêt depuis P3).
- Non modifiés : `HaversineDistanceEngine`, `TripRuntime` (le contrat P3 absorbe tout), `TripMeterForegroundService`, persistance, UI.

**Changements attendus (spécification de la machine, constantes nommées avec valeurs initiales à caler en P5).**
- États : `UNQUALIFIED` (pas d'ancre fiable) → `STATIONARY` → `MOVING`.
- `UNQUALIFIED` : premier fix = candidat ; ancre qualifiée quand accuracy ≤ `A_QUALIFY` (init 25 m) **ou** position stable (d_net < gate) sur `N_QUALIFY` (init 3) échantillons. Aucun crédit possible dans cet état.
- `STATIONARY` : zéro accumulation ; ancre immobile ; `d_net = dist(ancre, courant)` ; gate `G = max(G_MIN, K × accuracy)` (init `G_MIN = 8 m`, `K = 1.5`) ; bascule vers `MOVING` si `d_net > G` sur `N_EXIT` ticks consécutifs (init 3) — la vitesse rapportée ≥ `V_HINT` (init 1,5 m/s) réduit `N_EXIT` à 2, ne décide jamais seule ; à la bascule : crédit `d_net` (verdict `ACCEPTED_NET_ON_TRANSITION`), ancre = point courant. Ré-ancrage sans crédit autorisé uniquement si un fix améliore l'accuracy d'au moins un facteur `R_REANCHOR` (init 2×), journalisé.
- `MOVING` : accumulation par segments entre points acceptés ; gardes : `timestamp ≤ prev` → `REJECTED_STALE` (ancre intacte) ; vitesse implicite > `V_MAX` (200 km/h conservé) → `REJECTED_IMPLAUSIBLE_JUMP` (ancre intacte) ; `Δt > T_GAP` (init 10 s) → `REANCHORED_AFTER_GAP`, crédit zéro ; retour `STATIONARY` si `d_net` sur fenêtre glissante `T_STOP` (init 8 s) reste < `G`.
- **Invariant I4 inversé par rapport à P3** : tout verdict REJECTED_* laisse `FilterState` (ancre comprise) inchangé. Le test P3 qui documentait l'ancienne sémantique est mis à jour en miroir, avec commentaire de traçabilité.

**Tests à ajouter (séquences JVM — la liste détaillée est en §10, tests T1–T10).** Tous écrits contre le contrat `GpsAccumulationFilter`, fake clock, traces synthétiques génératrices (jitter gaussien paramétré, marche rectiligne, stop-and-go).

**Critères d'acceptation.**
- Tests T1–T10 verts.
- **Replay corpus P2, double condition** : logs d'arrêt → total ≤ M1 (10 m / 15 min) ; logs de mouvement → écart ≤ 2 % par rapport au total P3 (et au GPX de référence quand disponible).
- 100 % des ticks rejoués portent un verdict de l'énum fermée ; somme des `delta_total_m` des verdicts ACCEPTED_* = total final (M5, vérifié par le harness).
- Goldens P2 mis à jour avec justification par fichier dans le message de commit.

**Risques.**
- Sous-comptage en démarrage lent / embouteillage → couvert par T4, T5 et le replay du log stop-and-go ; c'est un critère d'acceptation, pas un espoir.
- Ancre figée pendant un déplacement très lent réel (< gate pendant longtemps) → impossible par construction : un déplacement réel finit par franchir `G` car `d_net` croît de façon monotone ; T4 le prouve à 1 m/s.
- Oscillation d'état en trafic → hystérésis (G sortie ≠ fenêtre T_STOP retour) + T5.
- Complexité de la machine → l'énum de verdict et le log v2 rendent chaque décision inspectable ; pas de chemin implicite.

**Preuve de non-régression.** Le replay P2 en double condition (arrêt corrigé **et** mouvement préservé) est la définition même du succès. Aucune validation au feeling : si un log de mouvement dévie > 2 %, le palier échoue.

---

## 8. Détail P5 — Calage des constantes

**Objectif.** Fixer `G_MIN, K, N_EXIT, N_QUALIFY, A_QUALIFY, T_GAP, T_STOP, V_HINT` sur données rejouées. Aucune modification de logique.

**Fichiers probablement concernés.** Uniquement le fichier de constantes du filtre (idéalement extrait en `domain/progress/FilterTuning.kt`, objet de valeurs nommées injecté dans le filtre — extraction faite en P4 pour que P5 ne touche qu'un fichier) ; `docs/plan/p5_tuning_<date>.md` (matrice de résultats).

**Changements attendus.** Balayage paramétrique via le harness (le harness P2 est étendu d'un mode « grille » : rejouer le corpus pour un ensemble de tuples de constantes, sortir M1/M2/M3 par tuple). Choix du tuple final documenté : valeurs, marges, sensibilité (de combien M1 et M2 bougent à ±20 % de chaque constante).

**Tests à ajouter.** Les tests T1–T10 sont re-exécutés avec le tuple final (les traces synthétiques paramétrées doivent rester vertes) ; ajout d'un test verrou `FilterTuningTest` qui fige les valeurs retenues (tout changement futur de constante casse un test et exige une justification).

**Critères d'acceptation.** M1, M2, M3 satisfaites simultanément sur tout le corpus avec le même tuple ; analyse de sensibilité documentée ; aucune constante « magique » hors `FilterTuning`.

**Risques.** Sur-ajustement au corpus (et au téléphone unique) → mitigation : choisir le tuple le plus **plat** en sensibilité, pas le meilleur score ponctuel ; noter la dette « second appareil ».

**Preuve de non-régression.** Tests T1–T10 verts au tuple final ; replays mouvement toujours ≤ 2 % d'écart ; diff limité à `FilterTuning.kt` + doc.

---

## 9. Détail P6 — Validation finale

**Objectif.** Valider sur le terrain la version P5, transformer les nouvelles traces en régressions permanentes, purger le vestige `calibrationFactor`.

**Fichiers probablement concernés.** `DistanceTripProgressEngine`/filtre (suppression du paramètre `calibrationFactor` et de la multiplication, suppression du test associé), `app/src/test/resources/replay/` (nouvelles traces), `docs/plan/p6_validation_finale_<date>.md`, éventuellement `README.md` (doctrine du filtre).

**Changements attendus.**
- Campagne terrain : canapé 15 min, fenêtre/voiture garée 15 min, marche 500 m, urbain ~5 km, routier ~20 km, scénario coupure (parking couvert/tunnel) — chacun loggé, chacun versé au corpus avec golden.
- Purge `calibrationFactor` : à facteur 1.0 non câblé, la suppression est un no-op comportemental — vérifié par replay bit-à-bit avant/après sur tout le corpus.
- Vérification explicite des transitions : pause/reprise et coupure GPS dans les logs terrain → aucun segment crédité à travers la coupure au-delà des bornes (M4).

**Tests à ajouter.** Goldens des nouvelles traces ; test explicite « pause → ticks → reprise » au niveau `TripRuntime` (séquence d'événements réels) vérifiant zéro crédit de coupure.

**Critères d'acceptation.** M1 à M5 satisfaites sur la campagne complète ; corpus enrichi et goldens verts ; plus aucune référence à `calibrationFactor` dans le pipeline brut (I10 clos).

**Risques.** Conditions terrain non reproductibles → c'est précisément pourquoi chaque sortie devient un replay permanent ; un échec terrain = un nouveau fichier de corpus + retour ciblé en P5 (constantes) ou P4 (logique), jamais un hotfix au feeling.

**Preuve de non-régression.** L'ensemble du corpus (P2 + P6) vert en CI ; suppression du facteur prouvée neutre par replay.

---

## 10. Liste des tests JVM à créer

Outillage : `TickLogJsonlTest`, `ReplayHarnessSelfTest`, goldens corpus (P1–P2).
Contrat filtre (P3) : `GpsAccumulationFilterContractTest` (mapping 18 cas existants → verdicts), `TripRuntimeNeutralityTest`, test de routage `SimulateLocationStep`.
Séquences (P4) :
- **T1** Jitter stationnaire sans vitesse : random walk borné σ ∈ {3, 8} m, accuracy 5–15 m, 600 ticks → total ≤ ε.
- **T2** Jitter + vitesses parasites 0,5–1,5 m/s → total ≤ ε (tue la route A ; le code HEAD échoue à ce test).
- **T3** Excursion-retour 10 m → crédit ≤ G, jamais ~20 m (tue le double comptage de l'ancre glissante).
- **T4** Marche lente 1 m/s rectiligne 300 s → comptage ∈ [294, 300] m (I8 ; vérifie aussi le crédit net à la bascule).
- **T5** Stop-and-go ×10 (30 s arrêt / 20 s à 3 m/s) → erreur totale bornée, aucun crédit pendant les arrêts, redémarrages non avalés.
- **T6** Premier fix dégradé (40 m d'erreur, accuracy 50 m, convergence) → zéro crédit avant qualification.
- **T7** Trou GPS : 120 s puis fix à 500 m → `REANCHORED_AFTER_GAP`, crédit 0 ; trou 3 s plausible → crédit normal.
- **T8** Points périmés/dupliqués (timestamp ≤ précédent) → `REJECTED_STALE`/`IGNORED_DUPLICATE`, FilterState inchangé.
- **T9** Rejet sans déplacement d'ancre : outlier injecté en MOVING et en STATIONARY → ancre bit-à-bit identique (I4 direct).
- **T10** Indépendance de cadence : T1 et T4 rééchantillonnés à 0,5 Hz et 2 Hz → mêmes verdicts qualitatifs, totaux équivalents.
Verrou (P5) : `FilterTuningTest`. Transitions (P6) : test pause/reprise au niveau runtime.

## 11. Liste des métriques d'acceptation

- **M1** Arrêt : 15 min immobile → ΔD ≤ 10 m, pente d'accumulation ~nulle après stabilisation (pas seulement un petit total).
- **M2** Mouvement : trajets 3–20 km → |D − référence GPX| / référence ≤ 2 % ; et |D_P4 − D_P3| ≤ 2 % sur les replays mouvement.
- **M3** Stop-and-go urbain : sous-comptage ≤ 3 %.
- **M4** Transitions : zéro segment crédité à travers pause/reprise ou trou > T_GAP (vérifié dans les logs, verdict par verdict).
- **M5** Explicabilité : 100 % des ticks ont un verdict ; Σ(delta des ACCEPTED_*) = total final.
La référence mouvement est une trace GPS indépendante — jamais le compteur voiture.

## 12. Anti-patterns interdits

- Régler `NOISE_FLOOR` / `ACCURACY_FLOOR_FACTOR` / `STATIONARY_SPEED` pour « faire passer » le canapé : déplace la fuite vers le sous-comptage sans toucher la cause structurelle.
- Toute correction multiplicative (calibration, coefficient moteur) : la dérive est additive et dépend du temps d'arrêt ; interdit par I10.
- Modifier le comportement avant P1/P2 livrés, ou fusionner deux paliers « pour gagner du temps ».
- Traiter `accuracy` comme une vérité ou `speed` comme une autorité de décision.
- Kalman/lissage/map-matching comme solution : complexité sans l'invariant I1.
- Mettre à jour un golden sans justification écrite par fichier.
- État mutable caché dans le filtre (champ interne non transporté) : casse la testabilité et la doctrine du projet.
- Persister `FilterState` « tant qu'on y est » : hors périmètre, décision actée non.
- Valider une constante en la changeant pendant un test terrain.

## 13. Ordre recommandé des futurs patchs

Chaque patch est petit, indépendant, reviewable seul, CI verte à chaque étape.

1. **P1.a** `domain/diag` : `SampleVerdict`, `TickLogEntry`, `TickLogSink`+no-op, `TickLogJsonl` + `TickLogJsonlTest`. (Pur domaine, zéro câblage.)
2. **P1.b** Instrumentation du moteur : exposition du verdict miroir des branches existantes + extension des tests moteur (verdicts). Aucun seuil touché.
3. **P1.c** `TripRuntime` : injection sink + émission par tick + `TripRuntimeLoggingTest`.
4. **P1.d** Android : `FileTickLogSink`, flush dans le service, câblage factory. (Seul patch touchant Android de tout P1.)
5. **P2.a** Harness de replay + self-test + mini-log synthétique embarqué.
6. **P2.b** Corpus terrain + goldens HEAD + doc de diagnostic chiffré routes A/B. (Patch ressources + doc, zéro code prod.)
7. **P3.a** `FilterState`/`FilterResult`/contrat `GpsAccumulationFilter` + adaptation mécanique du moteur, sémantique strictement héritée + `GpsAccumulationFilterContractTest`.
8. **P3.b** `TripRuntime` : suppression de `previousLocationSample`, transport opaque, routage unique (ApplyLocationSample + SimulateLocationStep) + `TripRuntimeNeutralityTest` + **replay bit-à-bit du corpus** ; log v2.
9. **P4.a** Extraction `FilterTuning` (valeurs actuelles + valeurs initiales nommées, comportement constant).
10. **P4.b** Machine d'états complète + T1–T10 + mise à jour justifiée des goldens. (Le patch central ; tout ce qui précède existe pour que celui-ci soit prouvable.)
11. **P5** Mode grille du harness + tuple final + `FilterTuningTest` + doc sensibilité.
12. **P6.a** Campagne terrain → nouvelles traces + goldens + test pause/reprise runtime.
13. **P6.b** Purge `calibrationFactor` (prouvée neutre par replay) + suppression du test associé.
14. **P6.c** Doc de clôture : doctrine du filtre, invariants I1–I11 et leur emplacement de preuve.

Règle de review pour chaque patch : un patch = un palier partiel = une preuve attachée (tests verts + replay quand applicable). Un patch qui mélange instrumentation et changement de comportement est refusé d'office.
