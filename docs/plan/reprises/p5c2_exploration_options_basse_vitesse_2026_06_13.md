# P5.c-2 — Exploration des options de correction basse vitesse

> **Nature** : document d'exploration, non normatif. Il **compare des stratégies**
> de correction du régime basse vitesse contre la baseline P5.c-1, **sans modifier le
> code**. Aucun moteur, seuil, JSONL ou golden touché. Les chiffres d'impact sont des
> **estimations indicatives** obtenues par replay **hors dépôt** (port fidèle du
> moteur, déjà validé par concordance exacte avec les totaux P4.2) ; ils servent à
> **classer** les options, pas à fixer une valeur. La validation réelle est P5.c-3.

## 1. Rappel de la baseline P5.c-1

| Régime | Total P4.2 | Réf | Écart | Verdict dominant | Statut |
|---|---:|---|---:|---|---|
| Arrêt M1 (×3) | 0,00 m | 0 (cible) | 0 | `REJECTED_STATIONARY` | tenu |
| Route | 6680,0 m | 6800 m | −1,76 % | `ACCEPTED_SEGMENT` | tenu |
| Urbain | 7975,96 m | 8200 m | −2,7 % | `ACCEPTED_SEGMENT` | à surveiller |
| Marche lente | 57,88 m | 533 m | **−89,1 %** | **`REJECTED_NOISE` 510/580** | **cassé** |

**Problème exact** : en marche lente à ~1,2 m/s à 1 Hz, le déplacement réel par tick
(~1,2 m) est **inférieur au plancher de mouvement de 2 m**. Ces **petits pas
cohérents** sont rejetés en `REJECTED_NOISE`, l'ancre avance, la distance est perdue.
Ce ne sont pas du bruit : ce sont de vrais pas, sous le plancher.

## 2. Analyse du mécanisme actuel (vérifiée dans le code)

Dans `DistanceTripProgressEngine`, la primitive `applyLocationSampleWithVerdict`
rejette en `REJECTED_NOISE` tout segment `< movementFloorMeters(prev, cur)`. Avec
vitesse source présente, ce plancher vaut `noiseFloorMeters` (2,0 m), **plat et
indépendant de l'état machine** (la primitive ne reçoit pas STATIONARY/MOVING).

- **Pourquoi 2 m protège l'arrêt** : à l'immobilité, le jitter GPS produit des
  micro-segments ; les écraser sous 2 m empêche d'accumuler un fantôme. **Mais** —
  point mesuré — à l'arrêt c'est surtout la **machine STATIONARY** qui protège : elle
  **gèle l'ancre** au centre et rejette en `REJECTED_STATIONARY` sans même appeler la
  primitive. Le plancher est une seconde ligne, pas la principale.
- **Pourquoi 2 m détruit la marche lente à ~1 Hz** : une fois en MOVING, chaque tick
  mesure ~1,2 m depuis l'ancre, < 2 m → `REJECTED_NOISE`, ancre avancée, distance
  perdue. 510/580 ticks subissent ce sort.
- **Pourquoi un abaissement *global* du plancher est à éviter — précisément** :
  contre-intuitivement, **sur ce corpus M1 reste à 0 m même avec un plancher global
  à 0,5 m** (l'arrêt est tenu par le gate, pas par le plancher). Le danger d'un
  abaissement global n'est donc **pas** mesuré ici sur M1 ; il est **précautionneux**
  : il affaiblit la garde anti-dérive de la **branche vitesse-absente** (plancher lié
  à l'accuracy) et tout moment stationnaire non encore gaté — régimes que le corpus
  actuel n'exerce pas. D'où la préférence pour une correction **confinée à MOVING**,
  qui laisse intacte la garde d'inactivité.

## 3. Options candidates

> Estimations indicatives (replay hors dépôt, corpus minimal) :

| Variante | M1 | route | urbain | marche |
|---|---:|---:|---:|---:|
| Baseline (plancher 2,0) | 0 | −1,8 % | −2,7 % | −89,1 % |
| A — plancher MOVING 1,5 | 0 | −1,4 % | −2,6 % | −60,1 % |
| A — plancher MOVING 1,0 | 0 | −1,4 % | −2,5 % | **+25,4 %** |
| A — plancher MOVING 0,5 | 0 | −1,3 % | −2,4 % | +33,4 % |
| B — accumulation (ancre non avancée sur noise en MOVING) | 0 | −1,3 % | −2,5 % | +30,8 % |
| (réf) plancher *global* 1,0 | 0 | −1,4 % | −2,5 % | +25,4 % |

Lecture transverse : **toutes** les variantes gardent **M1 = 0** et laissent route et
urbain à ±0,5 % de la baseline. La marche, elle, passe d'un **sous-comptage** (−89 %)
à un **sur-comptage** dès que le plancher descend à 1,0 (+25 %). Le plancher global
≈ le plancher MOVING (le plancher mord surtout en MOVING). **Le sur-comptage révèle
que la marche contient du vrai jitter** qu'un plancher bas ré-admet : la magnitude
seule ne sépare pas proprement « petit pas cohérent » et « tremblement ».

### Option A — plancher réduit uniquement en MOVING
- **Principe** : conserver 2 m à l'arrêt / au crédit de départ, réduire le plancher
  une fois MOVING confirmé.
- **Impact marche** : fort (−89 % → −60 % à 1,5 ; recouvrement complet voire
  sur-comptage à 1,0).
- **Risque M1** : nul mesuré (gate protège l'arrêt). **Risque route** : faible
  (légère réduction du sous-comptage). **Risque urbain** : faible (idem).
- **Complexité** : modérée — rendre le plancher conscient de l'état machine (la
  primitive est aujourd'hui agnostique ; il faut lui passer un drapeau MOVING ou
  calculer le plancher dans le wrapper MOVING).
- **Compatibilité P4.2** : bonne — n'altère ni le gate ni la garde d'arrêt.
- **Tests** : replay 4 régimes ; verrou de la nouvelle constante (plancher MOVING).

### Option B — accumulation de petits pas cohérents en MOVING
- **Principe** : en MOVING, sur un segment sous le plancher, **ne pas avancer
  l'ancre** ; laisser la distance s'accumuler depuis l'ancre jusqu'à franchir le
  plancher, puis créditer. Aucune nouvelle constante de magnitude.
- **Impact marche** : fort (+30,8 % ici — recouvre, mais sur-compte le jitter).
- **Risque M1** : nul mesuré (l'arrêt est gaté, hors de la branche MOVING).
  **Risque route/urbain** : faible.
- **Complexité** : modérée-élevée — **change l'invariant `I-AVANCE-MOUV`** du contrat
  `gps_accumulation_filter_v0_1.md` (« l'ancre avance même sur rejet ») et son test.
- **Compatibilité P4.2** : touche une garantie contractuelle → maj contrat + test.
- **Tests** : replay 4 régimes ; réécriture du test d'invariant d'avance d'ancre.

### Option C — crédit différé après confirmation de mouvement
- **Principe** : n'accumuler les petits pas qu'une fois MOVING confirmé.
- **Constat** : la machine **confirme déjà** le mouvement (hystérésis 8 échantillons,
  15 m net) **avant** d'entrer en MOVING. Le déficit survient **après** cette
  confirmation, au plancher par tick. C n'est donc **pas une option autonome** : sa
  substance (« ne relâcher qu'en mouvement confirmé ») **est déjà le périmètre MOVING
  de A et B**. À retenir comme **qualificateur** (relâcher uniquement post-confirmation),
  pas comme mécanisme séparé.

### Option D — seuil dynamique (vitesse / précision / état)
- **Principe** : au lieu d'un plancher de magnitude, accepter un segment **cohérent
  avec la vitesse rapportée** (distance ≈ vitesse × Δt) et rejeter ce qui n'est pas
  cohérent (jitter à vitesse ~nulle, sauts). Utilise `speed_mps`, déjà présent.
- **Impact marche** : potentiellement le meilleur — **sépare petit pas cohérent et
  jitter** par cohérence, pas par magnitude → éviterait le sur-comptage de A/B.
- **Risque M1/route/urbain** : faible si bien borné ; à mesurer.
- **Complexité** : élevée — nouvelle logique de cohérence (la plus « riche »).
- **Compatibilité P4.2** : bonne en principe, mais plus de surface.
- **Tests** : replay 4 régimes + cas de cohérence vitesse/distance.

### Option E — piste minimale suggérée par le code
- Le code porte déjà `speed_mps` par tick : la forme **minimale** de D est un
  **plancher MOVING dérivé de la vitesse** (p. ex. plancher = fraction de
  `vitesse × Δt`), qui laisse passer un pas cohérent ~1,2 m sans laisser passer un
  jitter à vitesse ~0. C'est D sous sa forme la plus économe ; à n'instruire que si A
  conservateur se révèle trop fruste.

## 4. Recommandation — ordre d'expérimentation

1. **Option A, confinée à MOVING, plancher conservateur (~1,5 m)** en premier :
   changement **minimal**, **réversible** (une valeur de plancher consciente de
   l'état), **testable**, qui transforme −89 % en **~−60 %** (forte amélioration)
   **sans sur-comptage** et **sans toucher** M1, route, urbain ni le contrat. On
   **n'ajuste pas** la valeur pour viser exactement 533 m (sur-ajustement à un seul
   log) : on retient une amélioration franche et sûre.
2. **Si A conservateur est jugé insuffisant ou trop fruste** (compromis
   recouvrement/jitter trop serré sur de futurs trajets réels) → **Option D/E**
   (plancher MOVING dérivé de la vitesse), plus principielle.
3. **Option B** (accumulation) en **alternative** : recouvre aussi, mais sur-compte
   ici et **modifie un invariant contractuel** (I-AVANCE-MOUV) → à n'ouvrir que si A
   et D échouent, avec mise à jour du contrat.

Justification : A confiné à MOVING a le plus petit rayon d'impact, ne dépend d'aucun
nouveau corpus, est trivialement réversible, et la mesure montre qu'il tient les
quatre régimes. On évite l'usine à gaz (D) et le changement d'invariant (B) tant que
le minimal suffit.

## 5. Plan de validation P5.c-3

- **Tests à ajouter/adapter** : un test replay « recouvrement marche » sur le corpus
  minimal, et des **assertions de non-régression** : M1 = 0 sur les trois logs
  d'arrêt ; route dans une bande (p. ex. écart pas pire que −1,76 % au-delà d'une
  marge) ; urbain pas pire que −2,7 % au-delà d'une marge. Si A ajoute une constante
  (plancher MOVING), **verrou** dans `FilterTuningTest`. Si B est retenu, **réécriture**
  du test d'invariant d'avance d'ancre + maj du contrat filtre.
- **Métriques comparées** : total et écart par régime, et répartition des verdicts
  (suivre la chute de `REJECTED_NOISE` en marche).
- **Bornes de non-régression** : M1 strictement 0 ; route et urbain dans une bande
  explicite autour de la baseline ; marche **fortement** améliorée sans viser 0 %
  (anti-sur-ajustement).
- **Critère d'acceptation** : retenir la **plus petite** modification qui améliore
  fortement la marche **tout en** tenant M1 = 0 et route/urbain dans les bornes, sur
  le corpus minimal, avec **tests replay verts**. Pas de réglage fin sur le seul log
  piéton ; viser une amélioration robuste, pas un chiffre exact.

## 6. Non-objectifs

- Pas de modification moteur dans ce patch. Pas de seuil modifié. Pas de JSONL
  modifié. Pas de golden modifié.
- Pas de campagne multi-téléphones. Pas de refonte GPS complète. Pas de carte. Pas
  d'UI.

---

Exploration uniquement. Aucun code, aucun seuil, aucun JSONL, aucun golden modifié ;
aucune valeur retenue, aucune décision figée.
