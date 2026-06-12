# Clôture P4.2 — gate stationnaire actif

P4.2 active le gate stationnaire : en `MachineState.STATIONARY` la dérive GPS
n'est plus accumulée ; en `MachineState.MOVING` le comportement P3/P4.1 est
préservé. Objectif tenu : M1 fortement réduit sans dégrader M2.

## 1. Résumé technique

`DistanceTripProgressEngine.apply()` ne reproduit plus inconditionnellement la
primitive : l'état machine gouverne désormais l'accumulation.

- **STATIONARY** : déplacement net mesuré depuis le centre stationnaire FIGÉ.
  Sous le seuil (ou hystérésis non atteinte), l'accumulation est neutralisée —
  l'état métier est rendu inchangé, verdict `REJECTED_STATIONARY`, et l'ancre
  reste gelée au centre.
- **STATIONARY → MOVING** : après `detectionHysteresisSamples` échantillons
  consécutifs au-delà de `movementTriggerMeters`, on bascule en MOVING en
  créditant le segment de départ depuis le centre (via la primitive, mêmes
  gardes) : le vrai mouvement initial n'est pas perdu.
- **MOVING** : strictement P4.1 — accumulation depuis l'ancre, ancre avançant à
  chaque échantillon. Détection d'immobilité pas-à-pas en parallèle.
- **MOVING → STATIONARY** : après `detectionHysteresisSamples` pas consécutifs
  sous `stillnessRadiusMeters`, retour en STATIONARY (la dérive cesse d'être
  accumulée).

Seuils de détection (réglés par replay, ce ne sont pas des coefficients de
correction) : `movementTriggerMeters=15`, `stillnessRadiusMeters=1.0` (était 3.0
en P4.1), `detectionHysteresisSamples=8` (était 3). Justification du réglage :
au seuil 3.0 m/pas (≈10.8 km/h) la conduite urbaine lente était classée à tort
« immobile », provoquant des bascules parasites et une distorsion de +64 % sur un
log urbain ; à 1.0 m/pas (≈3.6 km/h) seule la quasi-immobilité réelle déclenche
l'arrêt. L'hystérésis 8 (~8 s à 1 Hz) protège le mouvement contre le flapping.

## 2. Périmètre exact

Modifié (domaine `progress` uniquement) :
- `DistanceTripProgressEngine.kt` : `apply()` gated (dispatch STATIONARY/MOVING),
  helpers `applyWhileStationary`, `applyWhileMoving`, `gatedStationary` ;
  remplace la détection neutre P4.1. Primitive `applyLocationSampleWithVerdict`
  INCHANGÉE.
- `FilterTuning.kt` : `stillnessRadiusMeters` 3.0→1.0, `detectionHysteresisSamples`
  3→8. Les 4 seuils d'accumulation historiques sont intacts.

Tests :
- `GpsAccumulationFilterContractTest.kt` : sentinelles d'ancre inversées (MOVING
  avance / STATIONARY gèle) ; tests miroir recâblés sur l'état MOVING.
- `FilterStateMachineDetectionTest.kt` : réécrit en tests du gate actif.
- `TickLogReplayHarnessTest.kt` : test de replay synthétique inversé (divergence
  P4.2 intentionnelle).

NON modifié : `TripRuntime` (reste transporteur opaque ; voir stratégie d'ancre),
`SampleVerdict` (verdict `REJECTED_STATIONARY` réutilisé — aucun nouveau verdict),
UI, runtime Android GPS, JSONL, fixtures/goldens (fichiers `.jsonl` inchangés).

## 3. Stratégie d'ancre

Le piège connu (`recalage_P4_anchor_only_invalide`) : geler l'ancre sur rejet
SANS logique stationnaire amplifie la dérive (l'écart ancre→courant grandit puis
franchit le plancher → segments acceptés plus gros). P4.2 l'évite en neutralisant
l'accumulation *au niveau de l'état métier*, pas seulement de l'ancre :

- En STATIONARY, l'ancre est gelée au **centre** et l'état métier est rendu
  inchangé (delta 0). La dérive ne peut donc jamais créditer de distance.
- Le passage en MOVING ré-ancre sur le centre en créditant le segment de départ :
  le mouvement initial est capturé une fois, sans double comptage.
- `TripRuntime` n'est pas modifié : le dédoublonnage runtime (`== anchor`) reste
  correct en MOVING (l'ancre avance) ; en STATIONARY, une relecture non
  dédoublonnée est de toute façon neutralisée par le gate (bénin). Un champ
  « dernier échantillon » dédié serait inutile pour la correction → différé.

## 4. M1 — arrêt (avant/après)

Cible : ≤ 10 m / 15 min.

| Log d'arrêt | P4.1 | P4.2 | Cible |
|---|---|---|---|
| `sample_canape_synthetique` (15 min) | 65.25 m | **0.0 m** | ✓ |
| `real_canape_20260611` (15.2 min) | 4.43 m | **0.0 m** | ✓ |
| `real_canape_20260611_2351` (1.1 min) | 45.03 m | **0.0 m** | ✓ (qualifié à part, cf. limites) |

Les trois logs d'arrêt passent à 0 m : restant STATIONARY (déplacement net jamais
> 15 m), tous leurs segments de dérive sont neutralisés.

## 5. M2 — mouvement (avant/après)

Cible : route dans ±2 % de 6800 m ; ne pas distordre les urbains.

| Log mouvement | P4.1 | P4.2 | Δ vs P4.1 | vs odo |
|---|---|---|---|---|
| `real_route_…odom_6_80km` (réf. 6800) | 6680.77 m | **6680.0 m** | −0.0 % | **−1.76 %** |
| `real_urbain_…140454` (sans réf.) | 2119.80 m | 2094.3 m | −1.2 % | — |
| `real_urbain_…142640` (sans réf.) | 1822.07 m | 1818.1 m | −0.2 % | — |

Aucune distorsion manifeste : les urbains restent à ≤1.2 % de P4.1, le log route
référencé est préservé quasi à l'identique.

## 6. Analyse du log route référencé

Le log route est le seul point M2 avec vérité terrain (odomètre 6800 m). En P4.2
il vaut 6680.0 m, soit **−1.76 % vs odomètre** — pratiquement identique au
−1.75 % de P4.1 (6680.77 m). Le gate y agit aux phases stationnaires (arrêt
initial, arrêts intermédiaires) sans toucher au corps roulant : la conduite reste
en MOVING (= P4.1) grâce au seuil d'immobilité abaissé à 1.0 m/pas. Le gate ne
dégrade donc pas la mesure de référence ; il en retire seulement la dérive d'arrêt.

## 7. Limites

- Un seul log avec référence (route, odomètre imprécis ~±x %). M2 reste
  faiblement contraint ; les urbains sont jugés par non-régression vs P4.1, pas
  par vérité terrain.
- `real_canape_20260611_2351` est court (1.1 min) et de mauvaise qualité (accuracy
  médiane ~36 m, 77 % de vitesses ≥ 0.5). Il passe à 0 m mais ne doit pas être lu
  comme une preuve M1 robuste ; il est qualifié séparément.
- Aucun log de marche lente référencé : la sous-accumulation possible du gate sur
  un déplacement piéton lent n'est pas bornée empiriquement.

## 8. Risques

- **Sous-comptage d'un déplacement très lent** (< ~3.6 km/h soutenu) : pourrait
  être classé stationnaire. Atténué par le seuil net de départ (15 m) qui finit
  par déclencher MOVING ; à valider avec un log de marche.
- **Arrêt très bref pris pour conduite** : l'hystérésis 8 retarde l'entrée en
  STATIONARY ; un arrêt < 8 s reste en MOVING et est géré par les gardes P4.1
  (REJECTED_STATIONARY), donc non accumulé. Risque faible.
- **Réglage sur corpus restreint** : les seuils tiennent sur 6 logs ; un corpus
  élargi pourra affiner sans changer l'architecture.

## 9. Verdict d'acceptabilité

**Acceptable.** M1 atteint (arrêts à 0 m), M2 préservé (route −1.76 % vs odomètre,
dans ±2 % ; urbains ≤1.2 % vs P4.1, sans distorsion manifeste). La règle
« si M1 s'améliore mais M2 se dégrade → patch suspect » est satisfaite : M1
s'améliore fortement ET M2 est conservé.

## 10. Prochain chantier recommandé

Capturer un **log de marche lente référencé** (distance connue) pour borner la
sous-accumulation du gate, puis P4.3 : réglage fin des seuils sur corpus élargi
(et éventuel verdict dédié `REJECTED_STATIONARY_GATED` pour l'observabilité).

## 11. Conclusion

P4.2 active le gate stationnaire. Les distances fantômes à l'arrêt sont fortement
réduites, tandis que le mouvement reste dans la cible M2, notamment le log route
référencé à 6.80 km.

---

Suite : voir `audit_post_P4_2_gate_stationnaire_2026_06_12.md` (audit de clôture de
la série P4 et options pour P5).
