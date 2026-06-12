# P5.b — Cartographie de sensibilité du tuning (analyse directionnelle)

> **Statut : analyse directionnelle sur corpus incomplet. Non décisionnel.**
> Cette note **ouvre uniquement P5.b** (cartographie d'influence). Elle ne choisit
> **aucun tuple**, ne modifie **aucune constante**, ne met à jour **aucun verrou**,
> et **n'ouvre pas P5.c**. Les contrats `docs/contrats/` restent la référence ;
> `FilterTuning.kt` est inchangé. Toute conclusion ci-dessous est **orientante**,
> pas une décision de réglage.

## Méthode de balayage

Balayage **OAT** (*one-at-a-time*) : une constante variée à la fois, les six autres
figées au défaut de production, autour du tuple courant. Points par constante :
±20 % (cf. `plan_action_filtrage_gps_P1_P6_2026_06_11.md` §8) plus deux points
élargis pour révéler saturations et effets de seuil. Outillage : la grille P5.a
(`TuningGridReplay`, cf. `p5a_outillage_grille_baseline_2026_06_12.md`), qui rejoue
le moteur courant avec un `FilterTuning` **injecté** (jamais le défaut réécrit).

Mesures, sur le corpus existant (7 logs) : total accumulé sur les 3 logs d'arrêt
(**M1**), sur la **route référencée** (odomètre 6,80 km), sur l'**urbain référencé**
(odomètre 8,20 km), sur les 2 urbains non référencés (contexte), et la **répartition
des verdicts** (nombre de segments acceptés). Référence de fidélité : la baseline
documentée est reproduite (arrêts → 0,0 m ; route → 6680,0 m ; urbains → 2094,3 /
1818,1 / 7976,0 m).

## Résultats par constante

Effet au voisinage ±20 % (sauf points élargis indiqués), reste au défaut :

| Constante (défaut) | M1 (arrêts) | Route réf. | Urbain réf. | Répartition verdicts |
|---|---|---|---|---|
| `movementTriggerMeters` (15) | **effet de seuil** : 0 m à 12–18 ; **66 m à 7,5 (M1 cassé)** | ±0,02 % ; −1,22 % à 30 | +0,09 / −0,04 % ; −0,27 % à 30 | acc. route 486 ; 450 à 30 |
| `noiseFloorMeters` (2.0) | 0 | ±0,33 % ; −1,03 % à 4,0 | +0,12 / −0,36 % ; −2,33 % à 4,0 | acc. route 486 → 461 à 4,0 |
| `detectionHysteresisSamples` (8) | 0 | faible | +0,28 / −0,19 % ; +0,54 / −1,18 % à 4/16 | sensible (urbain) |
| `stillnessRadiusMeters` (1.0) | 0 | ±0,05 % | ≈ 0 | acc. route 488 → 454 à 3,0 |
| `stationarySpeedMetersPerSecond` (0.5) | 0 | 0 | −0,03 % ; urbain1 −1,6 % à 1,0 | quasi nul |
| `accuracyFloorFactor` (1.0) | 0 | **0** (0,5 → 2,0) | **0** | **nul** |
| `maxPlausibleSpeedKmh` (200) | 0 | **0** (100 → 300) | **0** | **nul** |

## Constantes dominantes

- **`movementTriggerMeters`** : seule constante gouvernant l'**intégrité M1** (effet
  de falaise sous ~12–15 m, où la dérive d'arrêt recommence à créditer). La baisser
  pour mieux capter un déplacement lent **rouvre la dérive d'arrêt** : arbitrage
  central.
- **`noiseFloorMeters`** : principal levier **monotone** du total en mouvement et du
  nombre de segments acceptés ; c'est le bouton qui déplace le plus la mesure
  roulante.

## Constantes secondaires

- **`detectionHysteresisSamples`** : influence modérée sur le mouvement urbain
  (transitions stop-and-go), nulle sur M1.
- **`stillnessRadiusMeters`** : quasi neutre sur les totaux, agit surtout sur la
  **classification MOVING↔STATIONARY** (observabilité), pas sur la distance.
- **`stationarySpeedMetersPerSecond`** : marginale sur ce corpus, ne pèse qu'aux
  valeurs élevées.

## Zones aveugles du corpus

- **`accuracyFloorFactor` — inobservable** : la branche plancher-accuracy ne
  s'active que si la vitesse source est absente ; or tous les samples du corpus
  portent une vitesse → branche jamais prise. Aucune valeur n'a d'effet.
- **`maxPlausibleSpeedKmh` — inobservable** : aucun saut/téléportation GPS dans le
  corpus (rejets « jump » = 0 partout). Aucune valeur n'a d'effet.
- **Régime de marche lente — non couvert** : le corpus n'a que des arrêts (0 m,
  trivialement robustes) et du roulage véhicule (assez rapide pour rester MOVING).
  La zone de transition à allure soutenue ~0,8–1,4 m/s — celle que
  `movementTriggerMeters`, `stillnessRadiusMeters` et `detectionHysteresisSamples`
  pilotent le plus pour le sous-comptage piéton — n'est jamais exercée. Ces
  constantes paraissent « plates/sûres » ici **uniquement parce que le corpus ne
  les sollicite pas dans ce régime**.
- **M3 (stop-and-go référencé) — non mesurable** : urbains non référencés, sauf le
  8,20 km à l'odomètre imprécis.

## Risques de sur-ajustement

- **Appareil unique** (Pixel 10 Pro Fold) : un seul profil de bruit GPS ; tout
  calage en hérite.
- **Références = odomètres voiture** arrondis au dixième (±0,05 km ≈ ±0,6 %).
  Régler `noiseFloorMeters` pour effacer le −1,76 % de la route reviendrait à coller
  au **bruit de l'odomètre**, pas à une vérité terrain.
- **Fausse aisance sur M1** : les arrêts sont déjà à 0, donc M1 semble « gratuit » —
  mais `trigger = 7,5` montre qu'il est fragile. Tout calage abaissant
  `movementTriggerMeters` pour capter la marche **menace M1** ; les deux objectifs
  tirent en sens inverse et **ce corpus ne contraint qu'un seul côté**.
- **Constantes aveugles** : sur ce corpus, n'importe quelle valeur de
  `accuracyFloorFactor` / `maxPlausibleSpeedKmh` « passe » → piège (elles
  pourraient être mal réglées sans que rien ne le détecte).

## Recommandation pour P5.c

Le log de marche lente référencé reste **nécessaire et bien ciblé** : les constantes
qui décident du sous-comptage piéton (`movementTriggerMeters`,
`stillnessRadiusMeters`, `detectionHysteresisSamples`) sont précisément celles dont
l'effet est **invisible** sur le corpus actuel dans ce régime, alors que
`movementTriggerMeters` arbitre simultanément l'intégrité M1. P5.c devra capturer la
marche (protocole : `reprises/protocole_capture_marche_lente_2026_06_12.md`), puis
caler en priorité `movementTriggerMeters` à l'équilibre marche-captée / arrêt-
neutralisé, et `noiseFloorMeters` contre une référence de mouvement **plus précise
qu'un odomètre** (idéalement GPX).

## Rappels (impératifs avant tout verrouillage)

- **`accuracyFloorFactor` et `maxPlausibleSpeedKmh` sont aveugles** sur le corpus
  actuel : ne pas les régler ici ; les laisser au défaut tant qu'un log à vitesse
  absente (pour le premier) ou à saut GPS (pour le second) n'enrichit pas le corpus.
- **Le régime de marche lente reste non couvert.**
- **Aucune constante ne doit être modifiée avant P5.c.**
- **Le log de marche lente référencé reste nécessaire avant tout verrouillage
  final** (choix de tuple, mise à jour du verrou `FilterTuningTest`).

---

Analyse directionnelle, corpus incomplet. Aucun tuple retenu, aucune valeur changée,
aucune décision finale, P5.c non ouvert.
