# P5.c-3 — Décision d'implémentation basse vitesse (avant codage)

> **Nature** : document de décision, non normatif tant que l'implémentation n'est pas
> faite. Il **tranche** la trajectoire d'implémentation et **prépare** le patch de
> code suivant. **Aucun code, aucun seuil, aucun JSONL modifié dans ce tour.** Les
> chiffres sont des **estimations indicatives** (replay hors dépôt, port fidèle), à
> confirmer en CI lors de l'implémentation. Entrée : `p5c2_exploration_options_basse_vitesse_2026_06_13.md`.

## 1. Rappel des résultats P5.c-2

Baseline marche : 57,88 m / 533 m = **−89,1 %**. M1 = 0 sur les trois logs d'arrêt ;
route −1,76 % ; urbain −2,7 %. Le déficit marche vient de `REJECTED_NOISE` (510/580) :
les pas réels ~1,2 m/tick à 1 Hz passent sous le plancher de 2 m.

Courbe du plancher MOVING (indicatif ; route/urbain restent plats, M1 = 0 partout) :

| Plancher MOVING | marche | route | urbain |
|---:|---:|---:|---:|
| 1,5 m | −60,1 % | −1,4 % | −2,6 % |
| 1,4 m | **−43,7 %** | −1,4 % | −2,6 % |
| 1,3 m | −22,1 % | −1,4 % | −2,5 % |
| 1,2 m | −3,7 % | −1,4 % | −2,5 % |
| 1,1 m | +14,1 % | −1,4 % | −2,5 % |
| 1,0 m | +25,4 % | −1,4 % | −2,5 % |

Option E **minimale** testée (plancher MOVING ∝ `vitesse × Δt`) : recouvre aussi mais
**sur-compte** (+16 % à +33 %) — à vitesse ~constante, elle se comporte comme un
plancher bas et ré-admet le jitter. Elle ne réalise donc **pas** la promesse «
distinguer petit pas cohérent et jitter ».

## 2. Analyse critique

- **A 1,5 m est sûre mais insuffisante comme cible finale** : −60,1 %. C'est une forte
  amélioration vs −89 %, sans sur-comptage et sans toucher M1/route/urbain, mais lire
  213 m pour 533 m parcourus reste insatisfaisant pour un instrument de navigation.
- **A 1,0 m (et 1,1 m) sur-compte** : +25 % (+14 %). Pour un trip meter, sur-compter
  est aussi fautif que sous-compter : le PARTIEL dériverait long.
- **La valeur « parfaite » (~1,2 m → −3,7 %) est un piège de sur-ajustement** : la
  courbe est **raide** (~18–20 points par 0,1 m). ±0,1 m fait basculer la marche de
  −22 % à +14 %. Caler le plancher sur le point qui touche 533 m, c'est l'ajuster à
  **un seul log, un seul appareil, une seule allure** — exactement ce que la doctrine
  interdit. Sur un autre trajet (jitter/allure différents), ce même 1,2 m
  sur-compterait ou sous-compterait fortement.
- **B (accumulation) est risquée contractuellement** : elle recouvre (+30,8 %) mais
  **modifie l'invariant `I-AVANCE-MOUV`** du contrat `gps_accumulation_filter_v0_1.md`
  (« l'ancre avance même sur rejet ») et son test ; et elle sur-compte ici aussi.
- **E (cohérence vitesse) est conceptuellement plus juste mais plus complexe** : pour
  vraiment séparer pas cohérent et jitter, il ne faut pas un plancher de magnitude
  (même dérivé de la vitesse, cf. test ci-dessus) mais un **test de cohérence** —
  accepter quand la distance observée est proche de `vitesse × Δt`, rejeter sinon.
  C'est une **bande** autour du déplacement attendu, pas un seuil : surface et
  complexité réelles, à ne payer que si justifié.

**Conclusion structurelle** : un plancher MOVING fixe (Option A) **trade robustesse
contre recouvrement**, et la zone de bon recouvrement coïncide avec la zone fragile
(falaise). Aucune valeur fixe ne donne à la fois un recouvrement proche de 0 % **et**
de la robustesse. Le côté **sous-comptage** (plancher ≥ 1,4) est le côté sûr.

## 3. Décision recommandée — trajectoire en deux temps

**Décision : implémenter d'abord l'Option A, confinée à MOVING, à un plancher
conservateur côté sous-comptage (≈ 1,4 m), comme étape de sécurisation ; différer
l'Option E (version cohérence, bornée) comme second temps, déclenché uniquement si A
se révèle insuffisante sur de futurs trajets réels.**

Ce n'est pas un « peut-être » : P5.c-3 ouvre **un** patch d'implémentation (A
conservatrice), et **conditionne** E à un critère explicite (§4). On évite ainsi le
correctif trop faible (A 1,5 figée en finale) **et** l'usine à gaz (E maintenant).

Valeur retenue : **plancher MOVING ≈ 1,4 m** (marche ≈ −44 %, **clairement hors de la
zone −60 %**, sans sur-comptage, M1/route/urbain intacts). 1,3 m (−22 %) est une
variante plus agressive **mais plus proche de la falaise** ; on **ne** retient **pas**
1,2 m (sur-ajustement). La valeur exacte sera figée par l'implémentation **contre les
bornes de non-régression**, côté sous-comptage.

## 4. A retenue comme étape de sécurisation — critère de passage à E

- **A est explicitement intérimaire**, pas la solution finale : elle sécurise un gain
  massif (−89 % → ~−44 %) sans risque, en attendant mieux.
- **Critère de passage à E** : ouvrir E **si et seulement si** une preuve réelle
  montre qu'un plancher MOVING fixe ne peut pas satisfaire les bornes **sur plusieurs
  logs** — typiquement, lorsqu'un **deuxième log basse vitesse référencé** (autre
  allure/appareil/jitter) révèle que la valeur qui convient au premier log
  sur-compte ou sous-compte l'autre au-delà des bornes. Tant qu'un seul log existe,
  A conservatrice suffit ; E ne se justifie qu'à l'apparition de cette contradiction.

## 5. Si E est ouverte plus tard — version minimale bornée

- **Principe** : en MOVING avec vitesse présente, accepter un segment **cohérent** avec
  `vitesse × Δt` (distance dans une bande autour de l'attendu), rejeter hors bande.
- **Bornes** (anti-usine-à-gaz) : bande **bornée** par des constantes simples
  (tolérance relative + plancher absolu = `noiseFloorMeters` côté haut pour ne jamais
  être plus permissif que le bruit, plancher bas pour ne pas accumuler le jitter) ;
  **aucune** dépendance à l'historique long, **aucun** filtrage statistique lourd.
- **Garde-fous** : E reste **confinée à MOVING** ; STATIONARY et la branche
  vitesse-absente gardent le plancher actuel ; E **n'altère pas** `I-AVANCE-MOUV` ;
  validée sur le **même** corpus minimal + le(s) nouveau(x) log(s) déclencheur(s).

## 6. Patch d'implémentation attendu (étape A — tour suivant)

- **Fichiers Kotlin** :
  - `domain/.../FilterTuning.kt` : **ajouter** une constante `movingNoiseFloorMeters`
    (≈ 1,4). **Conserver** `noiseFloorMeters = 2.0`.
  - `domain/progress/DistanceTripProgressEngine.kt` : rendre `movementFloorMeters`
    **conscient de l'état MOVING**. La primitive `applyLocationSampleWithVerdict` est
    aujourd'hui agnostique de l'état : faire passer un drapeau `moving` (ou calculer
    le plancher dans le wrapper MOVING) — choix d'implémentation minimal, sans changer
    la sémantique des verdicts.
  - `FilterState` / wrapper `apply()` : déjà porteur de l'état machine ; le transmettre
    au calcul de plancher.
- **Constantes** : **ajouter** `movingNoiseFloorMeters` uniquement. Ne **pas** toucher
  `noiseFloorMeters`, `stationarySpeed*`, `trigger`, `stillness`, `hyst`.
- **Tests à créer/adapter** :
  - `FilterTuningTest` : **verrouiller** la nouvelle constante par défaut.
  - `DistanceTripProgressEngineTest` : cas prouvant que **MOVING** utilise le plancher
    réduit et que **STATIONARY / crédit de départ / vitesse-absente** gardent 2 m.
  - Test replay de non-régression (étendre `TuningGridBaselineTest` /
    `RealLogReplayTest`) sur le corpus minimal.
  - Contrat `gps_accumulation_filter_v0_1.md` : documenter le **plancher dépendant de
    l'état** (raffinement) ; `I-AVANCE-MOUV` **inchangé**.
- **Assertions de non-régression** : M1 **strictement 0** sur les 3 logs d'arrêt ;
  route **pas pire** que −1,76 % au-delà d'une marge (p. ex. 0,5 pt) ; urbain **pas
  pire** que −2,7 % au-delà d'une marge ; marche **strictement améliorée** et **hors
  de la zone −60 %** ; **pas de sur-comptage** (marche ne franchit pas 0 % côté +).
- **Métriques attendues** (plancher ≈ 1,4) : marche ≈ **−44 %** (~300 m vs 57 m, ×5),
  route ≈ −1,4 %, urbain ≈ −2,5/−2,6 %, M1 = 0 ; tests replay verts.

## 7. Critères d'acceptation

- **M1** : 0 m sur les trois logs d'arrêt (strict).
- **Route** : pas de régression significative vs baseline (−1,76 %).
- **Urbain** : pas d'aggravation vs baseline (−2,7 %).
- **Marche** : amélioration **massive**, **sortir clairement de la zone −60 %**
  (cible indicative ≈ −44 % avec ~1,4 m), **sans** sur-comptage.
- **Tests replay verts** (auto-découverte, invariant structurel tenu).

Décision d'acceptation : retenir la **plus petite** modification (A conservatrice
MOVING) qui tient ces critères **simultanément** ; ne pas viser 0 % d'écart marche
(sur-ajustement) ; rester côté sous-comptage.

## 8. Non-objectifs

- Pas de refonte GPS. Pas de carte. Pas d'UI. Pas de campagne multi-téléphones. Pas
  de modification JSONL. (Et, dans ce tour de décision : aucun code, aucun seuil.)

---

Décision P5.c-3. Aucun code, aucun seuil, aucun JSONL modifié ; trajectoire tranchée,
implémentation préparée pour le tour suivant.
