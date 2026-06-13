# Audit post-P4.2 — gate stationnaire

Audit de clôture de la série P4 (détection + gate stationnaire/mouvement), sans
modification de code. Réalisé sur HEAD `fa4830d`
(`fix(gps): gate stationary drift accumulation`), arbre propre, 6 logs replay
présents, chaîne documentaire P4 complète.

## 0. Statut de vérification des données

- **Tests unitaires / `assembleDebug`** : rapportés verts en local (Windows) avant
  commit (`replay.*`, `domain.progress.*`, suite complète, `assembleDebug`,
  `git diff --check`). Le statut **CI GitHub Actions n'est pas vérifié** dans ce
  document.
- **Totaux M1/M2 ci-dessous** : produits par le **replay fidèle** du corpus (port
  reproduisant le pipeline). Ils ne sont **pas assertés comme totaux** par la suite
  de tests : `RealLogReplayTest` ne fige qu'un invariant structurel (somme des
  verdicts = nombre de ticks), et le golden synthétique vérifie le **fichier
  enregistré** (P4.a), pas le total rejoué. Ces chiffres sont donc des mesures de
  replay, non des seuils gardés par CI.

## 1. Architecture finale

État transporté (`FilterState`) : `anchor`, `machineState` (STATIONARY/MOVING),
`stationaryCenter`, `movingStreak`, `stationaryStreak`.

Détection (déplacement net, pas vitesse seule) + hystérésis :
- STATIONARY : centre **fixe** ; bascule MOVING après `detectionHysteresisSamples`
  échantillons consécutifs au-delà de `movementTriggerMeters`.
- MOVING : centre **suivant l'appareil** (pas-à-pas) ; bascule STATIONARY après
  `detectionHysteresisSamples` pas consécutifs sous `stillnessRadiusMeters`.

Gate (`DistanceTripProgressEngine.apply()`) :
- STATIONARY → accumulation **neutralisée** (état métier inchangé, verdict
  `REJECTED_STATIONARY` réutilisé, ancre **gelée au centre**).
- STATIONARY → MOVING → crédit du **segment de départ depuis le centre** (le
  mouvement initial n'est pas perdu).
- MOVING → strictement **P4.1** (accumulation depuis l'ancre via la primitive,
  ancre avançant à chaque échantillon).

Seuils par défaut : `movementTriggerMeters=15`, `stillnessRadiusMeters=1.0`,
`detectionHysteresisSamples=8`. Ce ne sont pas des coefficients de correction.

Invariants tenus : `TripRuntime` reste transporteur opaque de `FilterState` (le
pas simulé déclare un état transitoire MOVING pour rester mesurable) ; aucun
nouveau verdict ; aucun JSONL v2 ; aucun golden de fichier modifié.

## 2. M1 — arrêt (avant/après)

Cible : ≤ 10 m / 15 min.

| Log d'arrêt | P4.1 | P4.2 | Cible |
|---|---|---|---|
| `sample_canape_synthetique` | 65.25 m | **0.0 m** | ✓ |
| `real_canape_20260611` | 4.43 m | **0.0 m** | ✓ |
| `real_canape_20260611_2351` | 45.03 m | **0.0 m** | ✓ (qualifié à part) |

## 3. M2 — mouvement (avant/après)

Cible : route dans ±2 % de 6800 m ; pas de distorsion manifeste des urbains.

| Log mouvement | P4.1 | P4.2 | Δ vs P4.1 | vs odo |
|---|---|---|---|---|
| `real_route_…odom_6_80km` (réf.) | 6680.77 m | **6680.0 m** | −0.0 % | **−1.76 %** |
| `real_urbain_…140454` (sans réf.) | 2119.80 m | 2094.3 m | −1.2 % | — |
| `real_urbain_…142640` (sans réf.) | 1822.07 m | 1818.1 m | −0.2 % | — |

## 4. Effet réel sur les logs d'arrêt

Les trois logs d'arrêt tombent à **0 m**. Mécanisme : la machine démarre
STATIONARY, le déplacement net depuis le centre fixe ne franchit jamais 15 m, donc
aucune transition vers MOVING ; tous les segments de dérive (y compris ceux que
P4.a acceptait à cause d'une vitesse parasite) sont neutralisés. La distance
fantôme à l'arrêt est éliminée, pas seulement réduite.

## 5. Effet réel sur les logs mouvement

Les urbains restent à ≤1.2 % de P4.1 : le seuil d'immobilité abaissé à 1.0 m/pas
(≈3.6 km/h) évite de classer la conduite lente comme arrêt — c'était la cause du
+64 % de distorsion observé pendant la conception avant correction. Le gate n'agit
que sur les phases réellement stationnaires (arrêt initial/final, arrêts longs),
le corps roulant restant en MOVING (= P4.1). Aucune distorsion manifeste.

## 6. Analyse du log route référencé 6.80 km

Seul point M2 avec vérité terrain (odomètre 6800 m). P4.2 le mesure à **6680.0 m**,
soit **−1.76 % vs odomètre** — pratiquement identique au −1.75 % de P4.1
(6680.77 m). Le gate y retire la dérive d'arrêt sans toucher au trajet roulant. La
mesure de référence n'est donc pas dégradée par l'activation du gate.

Réserve : l'odomètre est une référence unique et de précision limitée ; le −1.76 %
n'est pas une preuve d'exactitude absolue, seulement de non-régression vs P4.1 et
de conformité à la cible ±2 %.

## 7. Risques restants

- **Sous-comptage d'un déplacement très lent et soutenu** (< ~3.6 km/h) :
  potentiellement classé stationnaire. Atténué par le seuil net de départ (15 m)
  qui finit par déclencher MOVING. **Non borné empiriquement** (pas de log de
  marche référencé).
- **Mouvement très court** (< hystérésis = 8 échantillons ≈ 8 s) entièrement
  contenu dans la fenêtre de confirmation : non crédité. Négligeable pour un usage
  rallye (trajets longs), mais réel pour des micro-déplacements.
- **Verdict conflé** : les samples neutralisés par le gate réutilisent
  `REJECTED_STATIONARY`, mêlés aux rejets vitesse. Sans impact distance ;
  observabilité plus fine possible (verdict dédié) si besoin.

## 8. Limites du corpus

- **Une seule référence terrain** (route, odomètre imprécis). M2 reste faiblement
  contraint ; les urbains sont jugés par non-régression vs P4.1, pas par vérité
  terrain.
- **`real_canape_20260611_2351`** court (1.1 min) et de mauvaise qualité : passe à
  0 m mais ne constitue pas une preuve M1 robuste, qualifié séparément.
- **Aucun log de marche lente** : le risque de sous-accumulation piétonne n'est pas
  mesuré.
- 6 logs au total : suffisant pour valider l'architecture, insuffisant pour un
  réglage fin statistiquement robuste des seuils.

## 9. Verdict : P4 peut-il être clôturé ?

**Oui, fonctionnellement.** Les objectifs P4 sont atteints : machine
stationnaire/mouvement en place (P4.1), gate actif réduisant fortement M1 sans
dégrader M2 (P4.2), log route référencé conservé dans ±2 %, tests verts en local
avant commit. L'architecture est stable et documentée.

La clôture est **fonctionnelle, pas définitive sur le plan métrologique** : la
borne de sous-accumulation piétonne et la robustesse statistique des seuils
restent ouvertes, faute de corpus élargi et de référence de marche.

## 10. Suite recommandée

Deux options non exclusives, par ordre de valeur :

1. **Capturer un log de marche lente référencé** (distance connue) avant d'ouvrir
   P5 : c'est le seul moyen de borner le risque de sous-accumulation du gate, et le
   prérequis le plus utile. Recommandé en priorité.
2. **Clôturer P4** en l'état (contrat stable, M1/M2 tenus) et ouvrir P5, en
   inscrivant la capture marche et le réglage fin des seuils comme premiers items
   de P5.

Une étape **P4.3 (consolidation test/doc)** n'apparaît **pas nécessaire** : la
suite de tests reflète déjà le contrat P4.2 (sentinelles inversées, gate testé,
runtime corrigé), et la chaîne documentaire est complète. Un éventuel verdict
dédié `REJECTED_STATIONARY_GATED` relève d'une amélioration d'observabilité, pas
d'une dette bloquante.

Passage direct à **P5 sans capture marche** : possible mais déconseillé tant que la
sous-accumulation piétonne n'est pas bornée.

## 11. Conclusion

P4.2 est validé fonctionnellement. La suite recommandée est soit la clôture P4,
soit la capture d'un log marche lente avant d'ouvrir P5.

---

Clôture : voir `cloture_chantier_P4_gate_stationnaire_2026_06_12.md` (clôture
formelle de la série P4 et prérequis pour P5).

Capture urbaine référencée post-P4.2 : voir
`capture_urbain_P4_2_2026_06_12_odom_8_20km.md` (odomètre 8,20 km / trip meter
7,98 km ≈ −2,7 %, possible sous-accumulation urbaine à instruire en P5).

Capture marche lente référencée post-P4.2 : voir
`capture_marche_lente_P4_2_2026_06_13_ref_520m.md` (référence 520 m ; régime basse
vitesse explicitement reporté en clôture P4 ; replay à intégrer, mesures à
relever lors de l'intégration).
