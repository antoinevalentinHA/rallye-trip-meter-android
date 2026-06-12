# Recalage P4 — le palier « anchor-only » est invalidé

Document de recalage : la correction isolée de l'avancement d'ancre sur rejet
(« P4.b anchor-only ») a été simulée fidèlement et **aggrave** les distances
fantômes sur le corpus d'arrêt. Le découpage P4 est révisé en conséquence.
Aucun code applicatif n'est produit ni recommandé sous cette forme.

Documents amont :
- Plan d'ensemble : `docs/plan/plan_action_filtrage_gps_P1_P6_2026_06_11.md`
- Clôture P3 / reprise P4 : `docs/plan/reprises/cloture_chantier_P3_reprise_P4_2026_06_12.md`

## 1. État du dépôt

- Branche : `main`. HEAD : `e89af0d` (`refactor(gps): extract filter tuning constants`, P4.a).
- Paliers présents : `ad437e5` (P3.a), `2525056` (P3.b), `ac1311e` (clôture P3), `e89af0d` (P4.a).
- Le bug d'ancre est encore présent : un échantillon rejeté fait avancer l'ancre.
- Aucune modification moteur/test/golden n'a été faite dans le cadre de ce recalage.

## 2. Ce qui a été testé

La règle « P4.b anchor-only » envisagée :
- `ACCEPTED_SEGMENT` et `IGNORED_NO_ANCHOR` font avancer l'ancre ;
- `REJECTED_STATIONARY`, `REJECTED_NOISE`, `REJECTED_IMPLAUSIBLE_JUMP` ne la font
  plus avancer ;
- `IGNORED_NO_SAMPLE` / `IGNORED_DUPLICATE` restent runtime ;
- runtime inchangé (transporteur opaque de `FilterState`).

Cette règle a été simulée par un portage fidèle du pipeline (runtime +
`DistanceTripProgressEngine` + Haversine), rejoué sur les trois fichiers du
corpus `app/src/test/resources/replay/`.

**Fidélité du portage prouvée.** Sous l'ancienne logique, le portage reproduit
exactement les goldens du pipeline Kotlin sur `sample_canape_synthetique.jsonl` :
900 ticks, 29 `ACCEPTED_SEGMENT`, 310 `REJECTED_STATIONARY`,
560 `REJECTED_NOISE`, 1 `IGNORED_NO_ANCHOR`, total `65.25024315676384` m
(identique au golden pinné de `TickLogReplayHarnessTest`). Le portage est donc
fidèle au bit près ; ses résultats sous la nouvelle logique sont exploitables.

## 3. Mesures — la correction isolée aggrave les trois corpus d'arrêt

| Fixture (scénario d'arrêt) | Durée | Total actuel | Total « anchor-only » | Segments acceptés | Direction |
|---|---|---|---|---|---|
| `sample_canape_synthetique` | ~15 min | **65.25 m** | **280.81 m** | 29 → 71 | aggravé |
| `real_canape_20260611` | ~15 min | **4.43 m** | **63.06 m** | 2 → 17 | aggravé |
| `real_canape_20260611_2351` | ~0.9 min | **45.03 m** | **59.34 m** | 13 → 16 | aggravé |

Sur les trois fichiers, la distance fantôme **augmente**. Le cas
`real_canape_20260611` est le plus parlant : il était conforme au seuil d'arrêt
M1 (≤ 10 m / 15 min) sous l'ancien comportement (4.43 m) et passe à 63.06 m sous
la correction isolée — il devient non conforme.

## 4. Interprétation — pourquoi l'anchor-only aggrave l'arrêt

Sur un appareil immobile qui dérive autour d'un centre :
- **Ancien comportement** (ancre avance à chaque échantillon) : le segment
  évalué est la dérive **entre deux échantillons consécutifs** (~1 Hz), petite,
  le plus souvent sous le plancher de 2 m → `REJECTED_NOISE`. Peu d'accumulation.
- **Anchor-only** (ancre gelée sur rejet) : tant que les échantillons sont
  rejetés, l'ancre reste figée pendant que l'appareil continue de dériver. La
  distance **ancre figée → échantillon courant** croît tick après tick jusqu'à
  franchir le plancher → `ACCEPTED_SEGMENT` plus gros, puis l'ancre saute. Des
  segments acceptés plus nombreux et plus longs → davantage de fantôme.

Conséquence : **l'ancien avancement d'ancre sur rejet jouait accidentellement le
rôle de suppresseur de dérive à l'arrêt.** Le retirer sans rien mettre à la place
ne corrige rien : il libère l'accumulation de dérive.

## 5. Décision — invalidation du découpage « anchor-only »

- Le palier « P4.b = correction isolée de l'ancre » est **invalidé** : il ne
  supprime pas une distance fantôme, il en ajoute, sur les trois corpus d'arrêt.
- **Aucun patch code P4.b sous cette forme ne doit être produit ni mergé.**
- Le découpage « anchor fix seul, puis machine plus tard » est abandonné : la
  correction d'ancre et la suppression de dérive à l'arrêt ne sont **pas
  séparables**. La règle « `REJECTED_*` ne fait plus avancer l'ancre » n'est
  correcte que **couplée** à une détection stationnaire qui empêche
  l'accumulation de dérive.

Ce constat est cohérent avec le plan-maître, qui définit P4 comme la **machine
STATIONNAIRE/MOUVEMENT** (§7), et non comme une correction d'ancre isolée : c'est
le couplage qui atteint M1.

## 6. Recalage de la stratégie P4

- P4 redevient **un chantier unique et cohérent** : une machine d'accumulation
  stationnaire/mouvement.
- L'inversion de l'ancre (I4 : `REJECTED_*` laisse `FilterState` inchangé)
  n'est introduite **qu'en même temps** que la logique stationnaire qui annule
  l'accumulation de dérive à l'arrêt.
- Le sous-découpage interne de la machine (par exemple détection d'abord, net
  depuis l'ancre ensuite) est admis, mais aucun sous-palier ne doit livrer la
  correction d'ancre sans sa contrepartie stationnaire.

## 7. Nouveau périmètre recommandé — P4-machine

Cadre (cohérent avec le plan-maître §7 ; aucun contrat ci-dessous n'existe encore
dans le code) :

- **Machine d'état minimale dans le filtre** : états STATIONNAIRE / MOUVEMENT
  portés par `FilterState` (ancre qualifiée, état machine, éventuels compteur de
  persistance et fenêtre de retour). À l'arrêt, l'accumulation est neutralisée
  (net depuis l'ancre, pas de crédit segment par segment) ; en mouvement, le
  comportement d'accumulation reste celui validé en P3.
- **Runtime inchangé** : `TripRuntime` reste un transporteur opaque de
  `FilterState`. Aucune propriété d'ancre n'y est réintroduite ; il ne lit
  l'ancre que pour la déduplication de cache, comme aujourd'hui.
- **Verdicts / transitions** (prévus par le plan-maître, à introduire avec la
  machine) : `ACCEPTED_NET_ON_TRANSITION`, `REJECTED_STALE`,
  `REANCHORED_AFTER_GAP`. Les verdicts existants sont conservés tant qu'ils
  gardent leur sens.
- **Point de logique unique** : `DistanceTripProgressEngine.apply()` (ou une
  implémentation dédiée `StationaryAwareAccumulationFilter` remplaçant
  l'implémentation, l'ancienne restant dans l'historique git). `FilterTuning`,
  les seuils et les conversions d'unités restent inchangés sauf nécessité
  démontrée.
- **JSONL** : v1 préservé en lecture ; v2 (champs d'ancre / état machine) à
  n'introduire que si la machine l'exige réellement, et de façon justifiée — pas
  automatiquement.

## 8. Critères d'acceptation M1 / M2

- **M1 (arrêt)** : logs d'arrêt → total ≤ 10 m / 15 min. Les trois fixtures
  actuelles sont des logs d'arrêt ; cible attendue après P4-machine : total
  proche de 0, en tout cas ≤ M1 (à comparer aux 65.25 / 4.43 / 45.03 m actuels).
- **M2 (mouvement)** : trajets → |D − référence| / référence ≤ 2 %, et
  |D_P4 − D_P3| ≤ 2 % sur les replays de mouvement.
- **Prérequis bloquant** : il n'existe à ce jour **aucun log de mouvement** dans
  le corpus (les trois fichiers sont des arrêts). M2 ne pourra être vérifié
  qu'après capture d'au moins une trace de mouvement exploitable (cf. plan-maître
  §117 : P4 ne démarre pas sans un log d'arrêt **et** un log de mouvement). La
  machine doit être validée sur les deux régimes pour éviter qu'elle ne réduise
  une distance réelle en mouvement.

## 9. Impact attendu sur les goldens et méthode de justification

- Les goldens de replay **changeront** (à la baisse pour les arrêts) : c'est le
  but. Chaque changement sera justifié **fichier par fichier**.
- Pour chaque fichier : indiquer son régime (arrêt vs mouvement), la valeur
  avant/après, et **pourquoi** l'écart correspond à une distance fantôme
  supprimée (à l'arrêt) ou à une distance réelle préservée (en mouvement, ≤ 2 %).
- **Jamais** de correction par coefficient ; **jamais** de « mise à jour parce que
  ça casse » sans explication ; le replay doit rester explicable ligne à ligne.
- La preuve reste le **replay bit-à-bit** : le portage fidèle (déjà validé contre
  les goldens actuels) peut servir à pré-calculer les nouvelles valeurs avant de
  figer les goldens, la validation lourde restant assurée par GitHub Actions.

## 10. Risques

- **Pas de corpus de mouvement** : sans trace de mouvement, M2 est invérifiable
  et la machine pourrait masquer une régression en mouvement. Capture préalable
  nécessaire.
- **Sur-suppression à l'arrêt** : une détection stationnaire trop agressive
  pourrait écraser de vrais déplacements lents (urbain, marche). À border par des
  cas de test dédiés et des fixtures.
- **Complexité de la machine** : préserver la séparation des couches et le
  transport opaque de `FilterState` ; ne pas réintroduire d'état dans le runtime.
- **Goldens** : tentation de « faire passer » en actant des valeurs sans
  justification ; à proscrire (doctrine I10, pas de coefficient).

## 11. Conclusion

P4.b anchor-only est invalidé. Le prochain chantier recommandé est P4-machine :
correction couplée de l'ancre et de l'état stationnaire/mouvement, avec preuve
par replay.

## Prérequis avant P4-machine

P4-machine ne peut démarrer sans un corpus permettant de vérifier arrêt **et**
mouvement. Aucun log de mouvement n'existe à ce jour. Procédure de capture :
voir `preparation_capture_mouvement_P4_machine_2026_06_12.md`.
