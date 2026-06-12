# Clôture du chantier P4 — gate stationnaire

Clôture documentaire de la série P4 (machine stationnaire/mouvement + gate). Aucun
code, test, JSONL, golden, configuration Gradle, UI ni runtime Android n'est
modifié par cette clôture.

État à la clôture : HEAD `1f1e9ca`, arbre propre, `origin/main` aligné, 6 logs
replay présents, chaîne documentaire P4 complète.

## 1. Résumé exécutif

P4 dote le compteur d'une machine d'état stationnaire/mouvement qui **neutralise
la dérive GPS à l'arrêt** tout en **préservant la mesure en mouvement**. Le
résultat : les trois logs d'arrêt du corpus passent à 0 m (M1 atteint), et le log
route référencé reste à 6680.0 m, soit −1.76 % vs odomètre 6800 m (M2 dans la
cible ±2 %, sans régression vs P4.1). Le chantier est clôturé **fonctionnellement**.

## 2. Périmètre P4

Inclus :
- P4.1 — détection stationnaire/mouvement neutre (observée, sans effet sur
  l'accumulation).
- P4.2 — gate stationnaire actif (la machine gouverne l'accumulation) et correctif
  d'intégration runtime (pas simulé).
- Audit post-P4.2.

Exclu (volontairement, hors P4) :
- réglage fin des seuils sur corpus élargi ;
- capture d'un log de marche lente référencé ;
- verdict dédié d'observabilité pour les samples neutralisés par le gate.

## 3. Commits principaux

- `05341d5` docs(plan): design P4 stationary movement machine
- `9ab898f` refactor(gps): add neutral stationary movement detection (P4.1)
- `fa4830d` fix(gps): gate stationary drift accumulation (P4.2, correctif runtime inclus)
- `1f1e9ca` docs(plan): audit post P4.2 stationary gate

## 4. Architecture finale retenue

`FilterState` transporte : `anchor`, `machineState` (STATIONARY/MOVING),
`stationaryCenter`, `movingStreak`, `stationaryStreak`.

Détection par déplacement net (pas vitesse seule) avec hystérésis : STATIONARY à
centre fixe (bascule MOVING au-delà de `movementTriggerMeters` soutenu) ; MOVING à
centre suivant l'appareil (bascule STATIONARY sous `stillnessRadiusMeters` soutenu).

Gate dans `DistanceTripProgressEngine.apply()` : STATIONARY neutralise
l'accumulation (état inchangé, verdict `REJECTED_STATIONARY` réutilisé, ancre gelée
au centre) ; la transition STATIONARY→MOVING crédite le segment de départ depuis le
centre ; MOVING reste strictement P4.1 (accumulation depuis l'ancre, ancre
avançant).

Seuils : `movementTriggerMeters=15`, `stillnessRadiusMeters=1.0`,
`detectionHysteresisSamples=8` (réglés par replay, pas des coefficients de
correction).

Invariants tenus : `TripRuntime` reste transporteur opaque de `FilterState` (le
pas simulé déclare un état transitoire MOVING pour rester mesurable) ; aucun
nouveau verdict ; aucun JSONL v2 ; aucun golden de fichier modifié.

## 5. Résultats M1/M2

M1 — arrêt (cible ≤ 10 m / 15 min) :

| Log d'arrêt | P4.1 | P4.2 |
|---|---|---|
| `sample_canape_synthetique` | 65.25 m | **0.0 m** |
| `real_canape_20260611` | 4.43 m | **0.0 m** |
| `real_canape_20260611_2351` | 45.03 m | **0.0 m** (qualifié à part) |

M2 — mouvement (route dans ±2 % de 6800 m) :

| Log mouvement | P4.1 | P4.2 | vs odo |
|---|---|---|---|
| `real_route_…odom_6_80km` (réf.) | 6680.77 m | **6680.0 m** | −1.76 % |
| `real_urbain_…140454` | 2119.80 m | 2094.3 m | — |
| `real_urbain_…142640` | 1822.07 m | 1818.1 m | — |

Ces totaux proviennent du **replay fidèle** du corpus ; ils ne sont pas assertés
comme totaux par la suite de tests (cf. validation ci-dessous).

## 6. Validation locale et CI

- **Tests locaux (Windows)** : verts avant commit — `replay.*`,
  `domain.progress.*`, suite complète `:app:testDebugUnitTest`, `assembleDebug`,
  `git diff --check`. Rapporté par l'utilisateur.
- **GitHub Actions** : vert, confirmé par le contexte utilisateur (non vérifié
  indépendamment dans ce document).
- **Portée des assertions** : `RealLogReplayTest` ne fige qu'un invariant
  structurel (somme des verdicts = nombre de ticks) ; le golden synthétique
  vérifie le fichier enregistré (P4.a). Les totaux M1/M2 sont donc des mesures de
  replay, **non des seuils gardés par CI**.

## 7. Décisions actées

- Le gate est activé avec re-ancrage sur le centre au départ (le mouvement initial
  n'est pas perdu) et ancre gelée à l'arrêt (évite l'effet pervers anchor-only
  documenté dans `recalage_P4_anchor_only_invalide`).
- Seuils figés : trigger 15 m, immobilité 1.0 m/pas, hystérésis 8 — justifiés par
  replay (le seuil d'immobilité 3.0 m/pas misclassait la conduite lente).
- Réutilisation du verdict `REJECTED_STATIONARY` (pas de nouveau verdict, pas de
  JSONL v2).
- `TripRuntime` non modifié dans son rôle de transport ; seul le pas simulé déclare
  un état transitoire MOVING.

## 8. Limites restantes

- P4 **n'est pas une validation métrologique exhaustive** : une seule référence
  terrain (odomètre route, imprécis), corpus de 6 logs.
- Le corpus **ne contient pas encore de log de marche lente référencé** : le risque
  de sous-accumulation d'un déplacement piéton lent et soutenu n'est pas borné.
- `real_canape_20260611_2351` (court, basse qualité) est qualifié séparément.
- Observabilité : les samples neutralisés par le gate sont confondus avec les
  rejets vitesse sous `REJECTED_STATIONARY` (sans impact distance).

## 9. Décision de clôture

**P4 est clôturé fonctionnellement.** Les objectifs sont atteints (machine en
place, gate actif, M1 fortement réduit, M2 préservé dans la cible, tests verts en
local et CI verte rapportée). L'architecture est stable et documentée.

Cette clôture précise explicitement :
- P4 n'est **pas** une validation métrologique exhaustive ;
- le corpus ne contient **pas** de log de marche lente référencé ;
- la **capture d'un log de marche lente référencé est reportée en tête de P5**, et
  posée en **prérequis avant tout réglage fin** des seuils ;
- **aucune modification moteur supplémentaire n'est recommandée dans P4**.

## 10. Suite recommandée

Ouvrir **P5** avec, en **prérequis**, la **capture d'un log de marche lente
référencé** (distance connue) avant tout tuning fin. Cette capture est le seul
moyen de borner la sous-accumulation du gate ; tout réglage de seuils antérieur
serait non vérifiable. Une étape P4.3 n'est pas nécessaire (tests et docs déjà
alignés sur le contrat P4.2).

## 11. Conclusion

Le chantier P4 est clôturé fonctionnellement. La suite recommandée est d'ouvrir P5
avec, en prérequis, la capture d'un log marche lente référencé avant tout réglage
fin.
