# Audit du corpus M1 / M2 avant P4-machine

Audit chiffré du corpus arrêt + mouvement, préparatoire à P4-machine. Aucun code,
aucun test, aucun golden, aucun JSONL modifié. Les chiffres proviennent d'un
replay fidèle du pipeline (portage validé : il reproduit exactement les `total_m`
enregistrés des six fichiers, au quatrième décimale).

Documents amont :
- Plan d'ensemble : `docs/plan/plan_action_filtrage_gps_P1_P6_2026_06_11.md`
- Recalage P4 : `docs/plan/reprises/recalage_P4_anchor_only_invalide_2026_06_12.md`
- Préparation capture : `docs/plan/reprises/preparation_capture_mouvement_P4_machine_2026_06_12.md`

## 1. État du dépôt

- Branche `main`, HEAD `b2e0c93` (`test(replay): add driving movement logs`), arbre propre.
- Paliers présents : `ad437e5` (P3.a), `2525056` (P3.b), `ac1311e` (clôture P3),
  `e89af0d` (P4.a), `24f89f5` (recalage P4), `e840e4f` (préparation capture),
  `b2e0c93` (corpus mouvement).
- Comportement courant : P3 + P4.a (le bug d'ancre sur rejet est encore présent ;
  P4.b anchor-only a été invalidé).

## 2. Fixtures du corpus (6 fichiers)

Toutes dans `app/src/test/resources/replay/`.

Arrêt (M1) :
- `sample_canape_synthetique.jsonl`
- `real_canape_20260611.jsonl`
- `real_canape_20260611_2351.jsonl`

Mouvement (M2) :
- `real_urbain_voiture_20260612_140454.jsonl`
- `real_urbain_voiture_20260612_142640.jsonl`
- `real_route_voiture_20260612_odom_6_80km.jsonl`

Le « total replay » ci-dessous est le total forcé-Running ; il coïncide avec le
`total_m` enregistré de chaque fichier (sessions exploitables, accumulation
identique).

## 3. Tableau — logs d'arrêt (M1)

| Fichier | Ticks | Durée | Total replay | Accepted | GPS | Accuracy méd. | Remarques |
|---|---|---|---|---|---|---|---|
| `sample_canape_synthetique` | 900 | 15.0 min | **65.250 m** | 29 (3.2 %) | 900 Fixed | 16.27 m | synthétique, seed 42 ; tout Running |
| `real_canape_20260611` | 921 | 15.2 min | **4.433 m** | 2 (0.2 %) | 907 Fixed / 14 Searching | 8.67 m | 14 ticks sans fix (acquisition), 6 doublons |
| `real_canape_20260611_2351` | 52 | 1.1 min | **45.031 m** | 13 (25.0 %) | 52 Fixed | 35.71 m | court, accuracy médiocre, 77 % speed ≥ 0.5 |

## 4. Tableau — logs de mouvement (M2)

| Fichier | Ticks | Durée | Total replay | Accepted | GPS | Accuracy méd. | Arrêt init./fin. | Référence ext. |
|---|---|---|---|---|---|---|---|---|
| `real_urbain_voiture_…140454` | 700 | 18.9 min | **2119.80 m** | 313 (44.7 %) | 700 Fixed | 3.06 m | ~57 / ~19 ticks | aucune |
| `real_urbain_voiture_…142640` | 465 | 17.8 min | **1822.07 m** | 196 (42.2 %) | 465 Fixed | 2.55 m | ~34 / ~5 ticks | aucune |
| `real_route_voiture_…odom_6_80km` | 666 | 25.8 min | **6680.77 m** | 506 (76.0 %) | 666 Fixed | 3.06 m | ~17 / ~5 ticks | odomètre 6.80 km |

## 5. Analyse M1 (arrêt → total ≤ 10 m / 15 min)

| Fichier | Durée | Borne M1 (proportionnelle) | Total actuel | Verdict M1 |
|---|---|---|---|---|
| `sample_canape_synthetique` | 15.0 min | ≤ 10 m | 65.250 m | **ÉCHEC** (~6.5×) |
| `real_canape_20260611` | 15.2 min | ≤ ~10.1 m | 4.433 m | **conforme** |
| `real_canape_20260611_2351` | 1.1 min | ≤ ~0.7 m | 45.031 m | **ÉCHEC** (qualité médiocre) |

Lecture : le pipeline actuel crédite déjà très peu sur un arrêt « propre »
(`real_canape_20260611` : 4.4 m, conforme), mais accumule du fantôme sur l'arrêt
synthétique bruité (65 m) et sur le log court/médiocre 2351 (45 m). C'est ce
fantôme d'arrêt que P4-machine doit ramener sous M1, **sans** la régression
mesurée de l'anchor-only (qui portait ces trois logs respectivement à 280.81 m,
63.06 m, 59.34 m).

## 6. Analyse M2 (mouvement → écart ≤ 2 %)

Référence externe disponible uniquement sur le log route.

`real_route_voiture_20260612_odom_6_80km` — trois grandeurs **distinctes** :
- référence odomètre voiture : **6800 m** (6.80 km) ;
- distance affichée par le tripmeter pendant le trajet : **6.68 km** ;
- total enregistré / rejoué par le pipeline : **6680.77 m** (= 6.68 km, cohérent
  avec l'affichage).

Écart replay vs odomètre : **(6680.77 − 6800) / 6800 = −1.75 %** (l'écart de
−1.76 % indiqué provient de l'affichage arrondi à 6.68 km). **Le comportement
actuel P3/P4.a est donc déjà dans la cible M2 (≤ 2 %) sur ce log.**

Les deux logs urbains n'ont **pas** de référence externe : leurs totaux
(2119.80 m, 1822.07 m) ne valent que comme **base de comparaison P3 → P4**
(la machine devra rester à ≤ 2 % de ces valeurs), pas comme vérité terrain.

## 7. Qualification de chaque nouveau log mouvement

- `real_urbain_voiture_…140454` : session continue, `session_state = Running`
  intégralement, GPS 100 % `Fixed`, accuracy excellente (méd. 3.06 m), arrêts
  initial (~57 ticks) et final (~19 ticks) présents, 18.9 min, 2.12 km, 52 %
  des ticks en mouvement. **Exploitable** comme fixture de non-régression.
  Limite : pas de référence externe.
- `real_urbain_voiture_…142640` : session continue, `Running` intégral, GPS 100 %
  `Fixed`, accuracy excellente (méd. 2.55 m), arrêts init. (~34) / fin. (~5),
  17.8 min, 1.82 km, 50 % en mouvement. **Exploitable**. Limite : pas de
  référence externe.
- `real_route_voiture_…odom_6_80km` : GPS 100 % `Fixed`, accuracy bonne
  (méd. 3.06 m), 25.8 min, 6.68 km, 85 % en mouvement, arrêts init. (~17) /
  fin. (~5). **Exploitable et porteur de la seule référence externe** (odomètre).
  Nuance : la queue contient **1 tick `session_state = Stopped`**
  (`IGNORED_NOT_RUNNING`) — l'arrêt du run précède d'un tick la fin du log ;
  sans effet sur le total (le tick est stationnaire, delta 0), et le replay
  forcé-Running redonne exactement le même total.

Remarques transverses (anomalies bénignes) :
- Doublons de cache présents (`IGNORED_DUPLICATE` : 6 / 9 / 8 / 2 / 14 selon les
  logs) — relectures du même fix, delta 0, attendus.
- `real_canape_20260611` : 14 ticks `Searching` sans position en tête
  (acquisition du fix, `IGNORED_NO_SAMPLE`).
- `real_canape_20260611_2351` : le premier tick enregistré porte
  `REJECTED_NOISE` là où un replay à froid produit `IGNORED_NO_ANCHOR` (l'ancre
  préexistait à l'ouverture du fichier) ; totaux identiques, écart sans
  conséquence.

## 8. Ce que P4-machine devra PRÉSERVER

- Le mouvement : totaux 2119.80 m, 1822.07 m (urbains) et 6680.77 m (route).
  Tolérance ≤ 2 % par rapport à P3, et ≤ 2 % vs odomètre sur le log route
  (actuellement −1.75 %, conforme — à ne pas dégrader).
- Le bon comportement déjà obtenu sur l'arrêt propre `real_canape_20260611`
  (4.43 m, conforme) : ne pas le faire remonter.
- Les transitions arrêt → mouvement → arrêt visibles dans les trois logs
  mouvement (bornes stationnaires en tête/queue).

## 9. Ce que P4-machine devra CORRIGER

- Le fantôme d'arrêt : `sample_canape_synthetique` (65.25 m) et
  `real_canape_20260611_2351` (45.03 m) doivent passer sous M1.
- Coupler l'inversion de l'ancre (REJECTED_* n'avance plus) à une détection
  stationnaire qui annule l'accumulation de dérive — faute de quoi la correction
  d'ancre seule aggrave ces totaux (mesuré : 280.81 / 59.34 m).

## 10. Limites du corpus

- **Pas de log de marche lente** : tous les logs de mouvement sont voiture. Le cas
  le plus exposé à la **sous-accumulation** (marche lente, segments proches des
  planchers) n'est pas couvert. À capturer avant de figer la machine.
- **Référence externe sur un seul log** : seul le log route a un odomètre ; les
  deux urbains n'ont aucune référence (ni odomètre, ni GPX).
- **Imprécision de l'odomètre** : un odomètre voiture est typiquement arrondi et
  peut dévier de 1–3 % ; −1.75 % est dans cette incertitude — cela montre une
  **cohérence**, pas une exactitude prouvée. Un GPX de référence serait plus sûr.
- **Un log d'arrêt médiocre** : `real_canape_20260611_2351` est court (1.1 min) et
  bruité (accuracy méd. 35.7 m, 77 % speed ≥ 0.5) ; sa valeur de référence M1 est
  faible. Le conserver comme cas limite, pas comme arrêt « propre ».

## 11. Critères d'acceptation affinés (pour P4-machine)

- **M1 (arrêt)** : chaque log d'arrêt ≤ 10 m / 15 min (proportionnel à la durée).
  Cibles : `sample_canape_synthetique` 65.25 → ≤ 10 m ; `real_canape_20260611`
  rester ≤ ~10 m (déjà 4.43) ; `real_canape_20260611_2351` à interpréter avec
  prudence (qualité).
- **M2 (mouvement)** : log route ≤ 2 % vs odomètre (préserver ≈ −1.75 %) ; chaque
  log mouvement ≤ 2 % vs son total P3 (2119.80 / 1822.07 / 6680.77 m).
- **Non-régression** : aucun log d'arrêt déjà conforme ne doit remonter ; aucun
  log de mouvement ne doit perdre de distance réelle au-delà de 2 %.
- **Preuve** : replay bit-à-bit ; tout golden modifié justifié fichier par
  fichier, en distinguant fantôme supprimé (arrêt) et distance préservée
  (mouvement).

## 12. Risques

- Machine trop agressive → sous-accumulation en mouvement, surtout en marche
  lente (non couverte) : risque non mesurable avec le corpus actuel.
- Référence M2 fragile (un seul log, odomètre imprécis) : conclure « exact »
  serait abusif ; rester sur « cohérent à ≤ 2 % ».
- Sur-ajustement aux logs voiture (vitesses élevées, 85 % en mouvement sur la
  route) au détriment des régimes lents.
- Tentation de figer des goldens « parce que ça passe » : proscrit (doctrine I10,
  pas de coefficient).

## 13. Prochain chantier recommandé

Compléter le corpus par **au moins un log de marche lente avec référence**
(parcours mesuré ou GPX), puis démarrer **P4-machine** (machine stationnaire /
mouvement couplée à l'inversion de l'ancre), bornée par M1 (arrêt) et M2
(mouvement), avec preuve par replay.

## 14. Conclusion

Le corpus contient désormais assez d'éléments pour préparer P4-machine : les logs
d'arrêt bornent M1, les logs mouvement bornent M2, et le log route avec odomètre
6.80 km sert de première référence externe.

## 15. Suite — design P4-machine

L'architecture de P4-machine (machine stationnaire/mouvement : MOUVEMENT = P3
préservé, ARRÊT neutralisé par gate net, transitions, verdicts, stratégie de
patchs P4.1 neutre puis P4.2 comportemental) est décrite dans
`design_P4_machine_stationnaire_mouvement_2026_06_12.md`.
