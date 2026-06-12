# Préparation expérimentale — ce que le log de marche devra trancher

> **Statut : préparation expérimentale. Prépare P5.c sans l'ouvrir.**
> Cette note définit la **question scientifique** que le futur log de marche lente
> référencé devra résoudre. Elle ne choisit **aucun tuple**, ne modifie **aucune
> constante**, ne met à jour **aucun verrou**, **n'ouvre pas P5.c**. Les contrats
> `docs/contrats/` restent la référence ; `FilterTuning.kt` est inchangé.
> Sources : `p5b_cartographie_sensibilite_2026_06_12.md`,
> `reprises/protocole_capture_marche_lente_2026_06_12.md`,
> `docs/contrats/gps_accumulation_filter_v0_1.md`,
> `reprises/cloture_chantier_P4_gate_stationnaire_2026_06_12.md`, corpus existant.

## Hypothèses déjà validées (par le corpus actuel)

- **V1 — Le gate neutralise la dérive d'arrêt.** Les 3 logs d'arrêt accumulent
  0,0 m au défaut (M1 atteint). Source : clôtures P4.2, reproduit par replay.
- **V2 — La mesure roulante véhicule tient dans ±2 %** sur la route référencée
  (−1,76 % vs odomètre 6,80 km). Validé **faiblement** (référence odomètre imprécise).
- **V3 — `noiseFloorMeters` est le levier monotone dominant du total roulant**, et
  **`movementTriggerMeters` a un effet de seuil sur M1** (à 7,5 m les arrêts
  recommencent à créditer ~66 m). Source : cartographie P5.b.

## Hypothèses invalidées

- **I1 — « `accuracyFloorFactor` est réglable sur ce corpus » : faux.** Effet nul
  (0,5→2,0) : la branche plancher-accuracy n'est jamais prise, la vitesse étant
  toujours présente.
- **I2 — « `maxPlausibleSpeedKmh` est réglable sur ce corpus » : faux.** Effet nul
  (100→300) : aucun saut implausible dans le corpus.
- **I3 — « `stillnessRadiusMeters = 3.0` convient » : faux.** Réfuté en P4.2
  (classait la conduite lente comme immobile, distorsion urbaine +64 %).
- **I4 — « geler l'ancre sans état machine suffit » : faux.** Réfuté
  (`recalage_P4_anchor_only_invalide`).

## Hypothèses encore ouvertes

### H-O1 — Y a-t-il sous-comptage piéton au défaut, et de quelle amplitude ?
- **Question** : sur un déplacement piéton lent et soutenu, le total accumulé
  s'écarte-t-il de la distance réelle, et de combien ?
- **Constantes concernées** : `movementTriggerMeters`, `noiseFloorMeters`,
  `stillnessRadiusMeters`, `detectionHysteresisSamples` (interaction).
- **Ce que le corpus dit déjà** : rien sur ce régime. Il n'a que des arrêts (0 m,
  gate fermé) et du roulage véhicule (gate ouvert). La zone ~0,8–1,4 m/s est absente.
- **Ce que seul le log de marche dira** : l'écart réel total/référence sur ~500 m
  marchés, mesuré, pas extrapolé.
- **Critère de décision** : sous-comptage ≤ cible (le plan évoque un comptage
  ∈ [294, 300] m pour 300 m à 1 m/s, soit ≲ 2 %). En deçà : pas de problème piéton.
  Au-delà : H-O2 départage le mécanisme.

### H-O2 — Si sous-comptage, le mécanisme est-il le gate ou le plancher ?
- **Question** : le déficit vient-il de samples gatés en STATIONARY
  (`REJECTED_STATIONARY`) ou de pas rejetés sous le plancher de bruit
  (`REJECTED_NOISE`) ? Les deux appellent des constantes différentes.
- **Constantes concernées** : gate → `movementTriggerMeters`,
  `stillnessRadiusMeters`, `detectionHysteresisSamples` ; plancher →
  `noiseFloorMeters` (et `accuracyFloorFactor` **uniquement si** la vitesse est
  absente sur le log marche).
- **Ce que le corpus dit déjà** : il ne peut pas trancher — aucun régime marche.
  Une **sonde synthétique** (marche idéalisée sans bruit, 1 Hz) montre seulement que
  le pas piéton par tick (~0,8–2 m) est du même ordre que le plancher (2,0 m) : les
  **deux** mécanismes sont plausibles. C'est une illustration de mécanisme, **pas
  une mesure** et **pas une décision**.
- **Ce que seul le log de marche dira** : la **répartition réelle des verdicts** sur
  la phase de marche continue — quelle fraction est `REJECTED_STATIONARY` vs
  `REJECTED_NOISE` vs `ACCEPTED_SEGMENT`.
- **Critère de décision** : mécanisme dominant = verdict de rejet majoritaire sur la
  marche continue. Gate dominant → instruire trigger/stillness/hyst ; plancher
  dominant → instruire `noiseFloorMeters`. Mixte → les deux, par ordre de poids.

### H-O3 — Une marche lente déclenche-t-elle des re-bascules MOVING→STATIONARY parasites ?
- **Question** : pendant une marche **continue** (sans arrêt réel), le gate se
  referme-t-il à tort (transition MOVING→STATIONARY), provoquant un sous-comptage ?
- **Constantes concernées** : `stillnessRadiusMeters` (1,0), `detectionHysteresisSamples` (8).
- **Ce que le corpus dit déjà** : à 1 Hz, un marcheur à ~1 m/s fait ~1 m/pas, pile
  au seuil d'immobilité — régime non testé par le corpus véhicule (pas > 1 m/pas).
- **Ce que seul le log de marche dira** : le **nombre et la position** des
  transitions MOVING→STATIONARY pendant une marche réputée continue.
- **Critère de décision** : transitions parasites ≈ 0 hors arrêts réels. Si > 0 sur
  marche continue → `stillnessRadiusMeters` trop haut pour ce régime à 1 Hz.

### H-O4 — Existe-t-il un réglage qui capte la marche SANS rouvrir la dérive d'arrêt ?
- **Question** : l'arbitrage central — un jeu de constantes corrige-t-il la marche
  tout en gardant les 3 logs d'arrêt à 0 m ?
- **Constantes concernées** : `movementTriggerMeters` au premier chef, en
  interaction avec `noiseFloorMeters` / `stillnessRadiusMeters`.
- **Ce que le corpus dit déjà** : il contraint **uniquement** le côté arrêt (falaise
  M1 sous ~12–15 m). Il ne dit rien du côté marche → l'intersection est inconnue.
- **Ce que seul le log de marche dira** : la **borne basse** (déplacement net qu'un
  vrai marcheur produit), à confronter aux 3 arrêts rejoués simultanément.
- **Critère de décision** : existe-t-il un réglage tel que (replay marche :
  sous-comptage ≤ cible) **ET** (replay des 3 arrêts : total reste 0) ? Si
  l'intersection est vide → **conclusion architecturale**, pas un réglage.

### H-O5 — Le coût d'amorçage du gate est-il borné ?
- **Question** : à l'entame d'une marche, l'hystérésis (8 échantillons) + le seuil
  de départ (15 m net) font-ils perdre des mètres, ou le crédit de départ depuis le
  centre les rattrape-t-il ?
- **Constantes concernées** : `movementTriggerMeters`, `detectionHysteresisSamples`.
- **Ce que le corpus dit déjà** : les départs du corpus sont des départs véhicule
  (franchissent 15 m en ~1 s). L'amorçage à allure piétonne (~15–19 s pour 15 m net)
  n'est pas observé.
- **Ce que seul le log de marche dira** : les mètres réellement crédités sur la phase
  d'amorçage réelle (segment de départ vs déplacement net).
- **Critère de décision** : crédit de départ ≈ déplacement net depuis le centre, sans
  perte au-delà de la fenêtre d'hystérésis.

## Plan de lecture du futur log de marche

### Indicateurs à regarder
1. **Total accumulé vs distance de référence connue** (sous/sur-comptage en %).
2. **Répartition des verdicts sur la phase de marche continue** : part
   `ACCEPTED_SEGMENT` / `REJECTED_STATIONARY` / `REJECTED_NOISE`.
3. **Transitions MOVING↔STATIONARY** pendant la marche continue (nombre, position).
4. **Distribution des segments par tick** vs plancher 2,0 m (combien de ticks sous le plancher).
5. **Présence/valeur de `speed_mps`** sur les ticks marche (détermine quelle branche
   de plancher s'applique, donc si `accuracyFloorFactor` devient observable).
6. **Accuracy médiane** (qualité du fix piéton, contexte de fiabilité).

### Conclusions qui seraient possibles
- Quantifier le sous-comptage piéton au défaut (un chiffre, sur ce log).
- Identifier le mécanisme dominant (gate vs plancher) par la répartition des verdicts.
- Dire s'il existe une **fenêtre** de réglage compatible M1 (replay conjoint marche + arrêts).
- Déclarer `accuracyFloorFactor` observable **si et seulement si** la vitesse est
  absente sur ce log.

### Conclusions qui seraient interdites
- **Choisir ou verrouiller un tuple** : un seul log marche est une référence unique,
  sur un seul appareil — insuffisant pour un calage robuste (même limite que l'odomètre).
- **Généraliser** à d'autres appareils, d'autres allures, d'autres conditions.
- **Régler `accuracyFloorFactor` ou `maxPlausibleSpeedKmh`** s'ils restent aveugles
  (vitesse présente / aucun saut).
- **Conclure sur M3 (stop-and-go)** à partir d'un log de marche pure.
- **Traiter ce log comme preuve métrologique** ou comme décision finale.

## Rappels (impératifs)

- `accuracyFloorFactor` et `maxPlausibleSpeedKmh` restent **aveugles** sur le corpus
  actuel.
- Le régime de marche lente reste **non couvert**.
- **Aucune constante ne doit être modifiée avant P5.c.**
- Le log de marche lente référencé reste **nécessaire avant tout verrouillage final**.

---

Préparation expérimentale. Aucun tuple retenu, aucune valeur changée, aucune
décision, P5.c non ouvert.
