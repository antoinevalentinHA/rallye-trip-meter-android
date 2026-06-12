# Design P4-machine — accumulation stationnaire / mouvement

Conception de P4-machine, sans code. Objectif : réduire fortement les distances
fantômes à l'arrêt (M1) **sans** dégrader le mouvement (M2), en couplant
l'inversion d'ancre à une détection stationnaire. Aucune modification de code,
test, golden ou JSONL dans ce chantier.

Documents amont :
- `docs/plan/plan_action_filtrage_gps_P1_P6_2026_06_11.md`
- `docs/plan/reprises/cloture_chantier_P3_reprise_P4_2026_06_12.md`
- `docs/plan/reprises/recalage_P4_anchor_only_invalide_2026_06_12.md`
- `docs/plan/reprises/preparation_capture_mouvement_P4_machine_2026_06_12.md`
- `docs/plan/reprises/audit_corpus_M1_M2_avant_P4_machine_2026_06_12.md`

## 1. État du dépôt

- Branche `main`, HEAD `4d65def`, arbre propre.
- Paliers présents : `ad437e5`, `2525056`, `ac1311e`, `e89af0d`, `24f89f5`,
  `e840e4f`, `b2e0c93`, `4d65def`.
- Comportement courant : P3 + P4.a (bug d'ancre sur rejet encore présent).
- Types actuels (état réel) :
  - `FilterState(anchor: LocationSample? = null)` — v0, ancre seule.
  - `FilterResult(state: TripState, verdict: SampleVerdict, nextState: FilterState)`.
  - `FilterTuning(noiseFloorMeters=2.0, accuracyFloorFactor=1.0, stationarySpeedMetersPerSecond=0.5, maxPlausibleSpeedKmh=200.0)`.
  - `GpsAccumulationFilter.apply(tripState, filterState, currentSample): FilterResult`.
  - `SampleVerdict` : ACCEPTED_SEGMENT, REJECTED_STATIONARY, REJECTED_NOISE,
    REJECTED_IMPLAUSIBLE_JUMP, IGNORED_NOT_RUNNING, IGNORED_NO_ANCHOR,
    IGNORED_DUPLICATE, IGNORED_NO_SAMPLE.
  - `DistanceTripProgressEngine.apply()` : avance l'ancre inconditionnellement
    (`nextState = FilterState(anchor = currentSample)`, ligne ~125).

## 2. Rappel des mesures M1 / M2

Arrêt (M1 ≤ 10 m / 15 min) : synth 65.25 m, canape1 4.43 m (conforme), 2351 45.03 m.
Mouvement (M2 ≤ 2 %) : urbain140454 2119.80 m, urbain142640 1822.07 m,
route 6680.77 m. Référence externe unique : odomètre route 6800 m → replay
−1.75 % (déjà dans la cible). Les deux urbains n'ont pas de référence externe.

## 3. Pourquoi anchor-only est invalidé (rappel)

Geler l'ancre sur rejet **sans** logique stationnaire aggrave l'arrêt :
65.25 → 280.81, 4.43 → 63.06, 45.03 → 59.34 m. L'ancien avancement d'ancre sur
rejet jouait incidemment un rôle de suppresseur de dérive ; le retirer seul
libère l'accumulation de dérive. Conclusion : l'inversion d'ancre n'est valable
que couplée à une suppression d'accumulation à l'arrêt.

## 4. Architecture proposée

Principe directeur, validé par simulation (§11) : **MOUVEMENT = comportement P3
inchangé ; ARRÊT = accumulation neutralisée.** La machine n'ajoute qu'un gate
stationnaire ; elle ne remplace pas l'accumulation de mouvement (la remplacer
distord le mouvement urbain — mesuré +64 %, §11).

États portés par `FilterState` (extension v1, à composer dans le premier patch) :
- `anchor: LocationSample?` — ancre d'accumulation de MOUVEMENT (inchangée).
- `machineState: { STATIONARY, MOVING }` — nouvel enum d'état machine.
- `stationaryCenter: LocationSample?` — centre tenu pendant l'arrêt (référence du
  déplacement net).
- un petit compteur d'hystérésis (entiers) pour confirmer les transitions sur
  plusieurs échantillons (anti-flapping). **Hypothèse** : composition exacte à
  arrêter au premier patch ; tout champ doit rester pur, immutable, transporté
  opaque.

Comportement par état :
- **STATIONARY** : on tient `stationaryCenter`. Le déplacement net
  `haversine(center, courant)` reste borné par la dérive ; aucune accumulation
  n'est créditée tant qu'on ne sort pas de la zone. L'ancre d'accumulation n'avance
  pas. La dérive d'oscillation s'annule (net ≈ 0).
- **MOVING** : comportement P3/P4.a **inchangé** (ancre avance sur ACCEPTED,
  gardes et seuils identiques). C'est ce qui préserve M2 par construction (la
  route reste à −1.75 %).

## 5. Transitions stationnaire ↔ mouvement (hypothèses à valider par replay)

- **STATIONARY → MOVING** : quand le déplacement net depuis `stationaryCenter`
  dépasse un seuil de sortie de zone (deadband, **hypothèse** ~10–20 m, cf. §11),
  confirmé sur quelques échantillons (hystérésis). À la transition : on ré-ancre
  l'accumulation au point de sortie et on crédite, au plus, le déplacement net
  réel (verdict `ACCEPTED_NET_ON_TRANSITION`), puis on passe en MOVING.
- **MOVING → STATIONARY** : quand le déplacement net reste sous le seuil sur une
  fenêtre (et/ou vitesse durablement < `stationarySpeedMetersPerSecond`), confirmé
  par hystérésis. On fige `stationaryCenter` au point courant.
- **Anti-flapping** : les deux sens exigent une confirmation sur N échantillons
  (hypothèse N à régler) pour ne pas osciller sur un pic de bruit.
- Le détecteur ne doit **pas** reposer sur la seule vitesse instantanée : l'audit
  montre qu'un log d'arrêt peut afficher 77 % de `speed ≥ 0.5` (2351). Le
  déplacement net sur fenêtre est plus robuste (**hypothèse** à confirmer).

## 6. Politique d'ancre et traitement des verdicts

- `IGNORED_NO_ANCHOR` : initialise l'ancre **et** `stationaryCenter` ; état initial
  **STATIONARY** (un run démarre typiquement à l'arrêt — hypothèse).
- `ACCEPTED_SEGMENT` (MOVING) : avance l'ancre (comme P3).
- `REJECTED_STATIONARY` / `REJECTED_NOISE` / `REJECTED_IMPLAUSIBLE_JUMP` :
  n'avancent **pas** l'ancre d'accumulation et ne créditent rien. En STATIONARY
  ils sont absorbés (dérive) ; en MOVING ils sont rares et le mouvement ré-accepte
  vite (geler l'ancre y est sans effet, mesuré).
- `IGNORED_NO_SAMPLE` / `IGNORED_DUPLICATE` : restent de niveau runtime, ne
  changent ni l'état machine ni l'accumulation.
- **Point d'attention déduplication** : le runtime déduplique aujourd'hui contre
  `filterState.anchor`. Comme l'ancre n'avance plus à chaque échantillon, ce
  champ devient une référence périmée pour la déduplication. Recommandation :
  porter dans `FilterState` un champ dédié « dernier échantillon vu » qui avance
  toujours, lu par le runtime pour la déduplication — le runtime reste
  transporteur opaque (il lit un champ, n'en possède aucun). À défaut, une
  déduplication manquée est bénigne sous la machine (l'échantillon relu est
  ré-évalué puis absorbé à l'arrêt), mais la solution propre est le champ dédié.

## 7. Maintien de `TripRuntime` comme transporteur opaque

`TripRuntime` continue de faire `filterState = result.nextState` et de ne lire
qu'un champ de `FilterState` (l'ancre aujourd'hui, le « dernier échantillon vu »
demain) pour la déduplication. Aucun état machine n'est lu ni possédé par le
runtime ; aucune ré-introduction de `previousLocationSample`. L'enrichissement de
`FilterState` est transporté sans interprétation.

## 8. Verdicts

- Conservés : les huit verdicts actuels gardent leur sens.
- Nouveaux (à introduire avec la machine, prévus par le plan-maître §7) :
  - `ACCEPTED_NET_ON_TRANSITION` : crédit du déplacement net à la sortie d'arrêt.
  - `REANCHORED_AFTER_GAP` : ré-ancrage sans crédit (ex. après un trou de signal
    ou un arrêt prolongé) pour éviter un faux saut implausible.
  - `REJECTED_STALE` (**hypothèse**, à n'ajouter que si un cas le justifie).
- Chaque nouveau verdict doit être justifié par un cas réel du corpus avant
  introduction ; ne pas ajouter de verdict spéculatif.

## 9. JSONL — décision

**Différer JSONL v2.** La correction est prouvable par replay (verdicts + total)
sans nouveau champ de log ; ajouter `machine_state` / `anchor_*` changerait le
JSONL émis et les goldens pour un bénéfice purement d'observabilité. À introduire
plus tard, séparément, si le debug des transitions l'exige.

Nuance importante : les **nouveaux verdicts** apparaîtront dans le champ `verdict`
des nouveaux logs et du replay. C'est une **extension additive du vocabulaire**,
pas un changement de schéma : les logs v1 du corpus ne contiennent que les anciens
verdicts et restent lisibles ; le lecteur de replay doit seulement reconnaître les
nouvelles chaînes. Aucun bump de `SCHEMA_VERSION` n'est requis pour cela.

## 10. Tests nécessaires

- **Unitaires moteur/machine** : transitions STATIONARY↔MOVING (hystérésis),
  initialisation à `IGNORED_NO_ANCHOR`, non-accumulation en STATIONARY,
  accumulation P3 en MOVING, crédit net à la transition.
- **Sentinelles inversées** : les tests P3 figés
  (`apply_advancesAnchorEvenWhenSampleRejected`,
  `rejectedSample_stillAdvancesAnchor_carriedByFilterState`) sont **inversés** (et
  non supprimés), avec commentaire de traçabilité (comportement conservé jusqu'à
  P3/P4.a, corrigé par la machine).
- **Replay M1** : chaque log d'arrêt ≤ 10 m / 15 min.
- **Replay M2** : route ≤ 2 % vs odomètre 6800 m (préserver ≈ −1.75 %) ; urbains
  ≤ 2 % vs leur total P3 (2119.80 / 1822.07 m).
- **Runtime** : `ApplyLocationSample` et `SimulateLocationStep` toujours sur le
  même chemin filtre ; runtime sans ancre propre ; transport opaque de l'état
  machine.

## 11. Simulation illustrative (HYPOTHÈSE, non engageante)

Un accumulateur de déplacement net à bande morte `T` (ré-ancrage quand le net
dépasse `T`) a été simulé sur le corpus, **uniquement pour éclairer le design** :

| Fichier (P3) | T = 15 m | T = 20 m |
|---|---|---|
| arrêt synth (65.25) | 0.0 | 0.0 |
| arrêt canape1 (4.43) | 0.0 | 0.0 |
| arrêt 2351 (45.03) | 0.0 | 0.0 |
| route odo 6800 (P3 6680.77) | 6708.1 (−1.35 % vs odo) | 6673.5 (−1.86 %) |
| urbain140454 (P3 2119.80) | 2281.3 (+7.6 %) | 2278.6 |
| urbain142640 (P3 1822.07) | 2996.4 (+64 %) | 2976.7 |

Enseignements (marqués comme hypothèses) :
- Un gate net peut ramener les arrêts à ~0 m (M1 atteignable) avec une bande morte
  ≥ ~15 m, tout en gardant la route à ±2 %.
- **Mais** remplacer toute l'accumulation par un net-deadband **distord le
  mouvement urbain** (+64 % sur 142640). D'où l'architecture retenue : MOUVEMENT =
  P3 inchangé, gate net réservé à l'ARRÊT et aux transitions. Les valeurs de `T`,
  d'hystérésis et de fenêtre restent des hypothèses à régler par replay, sans
  toucher `FilterTuning` sauf justification forte.

## 12. Stratégie de patchs incrémentaux

- **P4.1 — détection neutre (à comportement constant).** Introduit l'enum d'état
  machine, étend `FilterState` (état + centre + compteur, défauts reproduisant
  l'existant), calcule la détection **sans** changer l'accumulation. Prouvé par
  replay bit-à-bit (goldens inchangés), comme P3.a / P4.a. Fichiers : types
  `domain/progress`, moteur (calcul d'état porté, accumulation inchangée), tests
  unitaires de détection + replay inchangé. **Aucune régression sur `main`.**
- **P4.2 — suppression stationnaire (changement comportemental).** Le moteur
  utilise l'état pour neutraliser l'accumulation en STATIONARY et gérer la
  transition. Change les goldens d'arrêt (à la baisse, justifié). Préserve le
  mouvement. Inverse les sentinelles. Tests M1/M2. C'est un incrément **complet**
  (corrige M1 sans casser M2), donc un progrès — pas une demi-correction comme
  anchor-only.
- **P4.3 — (optionnel, différé)** : JSONL v2 / observabilité des transitions, si
  besoin de debug.
- **Éviter une régression sur `main`** : ne jamais merger un palier qui dégrade un
  corpus. P4.1 est neutre ; P4.2 n'est mergé que lorsqu'il tient M1 **et** M2
  simultanément, vérifié par replay (idéalement après ajout d'un log de marche
  lente, cf. risques).

## 13. Risques

- **Pas de log de marche lente** : la sous-accumulation en mouvement lent (le
  risque principal d'un gate trop large) n'est **pas mesurable** aujourd'hui.
  Prérequis : capturer une marche lente référencée avant de figer `T` / hystérésis.
- **Distorsion du mouvement** si le gate déborde sur le mouvement (mesuré +64 %
  urbain) : d'où MOUVEMENT = P3 strict.
- **Référence M2 fragile** : un seul log référencé, odomètre imprécis (±quelques
  %). −1.75 % = cohérence, pas exactitude prouvée.
- **Faux positifs de transition** sur dérive → crédit net parasite à l'arrêt ;
  bordé par l'hystérésis (à valider).
- **Tentation de figer des goldens « parce que ça passe »** : proscrit (pas de
  coefficient, justification fichier par fichier).

## 14. Critères d'acceptation

- M1 : chaque log d'arrêt ≤ 10 m / 15 min (proportionnel à la durée).
- M2 : route ≤ 2 % vs odomètre (préserver ≈ −1.75 %) ; urbains ≤ 2 % vs P3.
- Non-régression : aucun arrêt déjà conforme ne remonte ; aucun mouvement réel
  perdu > 2 %.
- `TripRuntime` reste transporteur opaque ; pas d'ancre réintroduite ;
  `FilterTuning` inchangé (sauf justification forte) ; pas de correction par
  coefficient.
- Tout golden modifié justifié fichier par fichier (fantôme supprimé à l'arrêt,
  distance préservée en mouvement).

## 15. Prochain patch recommandé

**P4.1 — détection stationnaire/mouvement neutre** : le plus petit incrément
vérifiable. Il étend `FilterState` (état machine + centre, transportés opaque),
calcule la détection sans changer l'accumulation, et se prouve par replay
bit-à-bit (goldens inchangés, route inchangée). En parallèle, capturer un log de
marche lente référencé pour débloquer la validation de P4.2.

## 16. Conclusion

Le prochain patch recommandé doit implémenter le plus petit incrément vérifiable
de P4-machine, sans modifier `TripRuntime` autrement que par transport opaque de
`FilterState`, et sans dégrader le log route référencé.
