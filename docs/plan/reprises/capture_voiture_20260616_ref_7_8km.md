# Capture trajet voiture — référence 7,8 km (2026-06-16)

> **Navigation** — [← index des reprises](README.md) ·
> Régime concerné : contrat [`gps_accumulation_filter_v0_1.md`](../../contrats/gps_accumulation_filter_v0_1.md) ·
> Gate stationnaire : [P4.2 gate stationnaire actif](cloture_P4_2_gate_stationnaire_actif_2026_06_12.md) ·
> Capture sœur de la veille : [voiture réf. 7,8 km (2026-06-15)](capture_voiture_20260615_ref_7_8km.md) ·
> [état courant](../../ETAT.md)

> **Nature** : fiche de métadonnées terrain, non normative. Elle conserve les
> informations d'une capture de **trajet voiture référencé** (régime routier/urbain
> mixte, agglomération bordelaise), réalisée le 2026-06-16.

> **Particularité — trajet en deux segments (pause au milieu)** : une **pause** a
> été faite au milieu du trajet, ce qui a produit **deux paires de fichiers** de
> log consécutives. Les deux segments se raccordent exactement (cf. §« Raccordement »)
> et reconstituent le trajet complet de `total_m = 0` à `total_m ≈ 7706 m`.

> **État d'intégration** : les **quatre** fichiers cible sont présents dans le dépôt
> et ont été **réellement relus** ; les champs ci-dessous sont renseignés depuis les
> JSONL, pas de mémoire. **Données brutes conservées telles quelles, sans correction.**

## Métadonnées terrain

| Champ | Valeur |
|---|---|
| Segment 1 (avant pause) — replay | `app/src/test/resources/replay/real_urbain_voiture_20260616_ref_7_8km_seg1.jsonl` |
| Segment 1 — GPX brut (inerte aux tests) | `…/real_urbain_voiture_20260616_ref_7_8km_seg1.gpx` |
| Segment 2 (après pause) — replay | `…/real_urbain_voiture_20260616_ref_7_8km_seg2.jsonl` |
| Segment 2 — GPX brut (inerte aux tests) | `…/real_urbain_voiture_20260616_ref_7_8km_seg2.gpx` |
| Fichiers source Android | `gpslog_20260616_094413.{jsonl,gpx}` (seg1) · `gpslog_20260616_095554.{jsonl,gpx}` (seg2) — `/storage/emulated/0/Download/gpslogs/` |
| Date | 2026-06-16 |
| Type de trajet | voiture (routier/urbain mixte, agglomération bordelaise), **avec une pause au milieu** |
| Distance de référence (compteur) | **7,8 km** (compteur de trajet voiture, Audi) — référence terrain pratique, **pas une vérité métrologique absolue** |
| Distance affichée par le Trip Meter (fin) | **7,71 km** (lecture utilisateur) |
| Écart observé | **≈ −90 m / −1,15 %** (lecture 7,71 km) ; **−94 m / −1,20 %** sur le `total_m` exact (7706 m) |
| Moteur actif à la capture | HEAD courant (post-P5.c-3 / P6.b) |
| Calibration | **1.0** (neutre — mesure brute ; `commit` du meta = `"1.0"`) |
| Appareil | Pixel 10 Pro Fold |
| Sessions proprement clôturées | **oui** (export JSONL + GPX produits pour chaque segment) |

## Mesures (lues dans les replays intégrés)

| Mesure | Segment 1 (avant pause) | Segment 2 (après pause) | Trajet complet |
|---|---|---|---|
| Fenêtre samples (UTC) | 07:44:12Z → 07:52:46Z | 07:52:46Z → 08:05:43Z | 07:44:12Z → 08:05:43Z |
| Durée fenêtre | ~8,6 min (514 s) | ~12,9 min (777 s) | ~21,5 min (hors durée de pause) |
| Ticks | 514 | 590 | 1104 |
| `total_m` début → fin | **0 → 2231,8 m** | **2231,8 → 7706,4 m** | **0 → 7706,4 m** |
| Σ `delta_total_m` | 2231,8 m | 5474,6 m | **7706,4 m** (7,71 km) |
| Vitesse max | ~59,7 km/h | ~62,0 km/h | ~62 km/h |
| Verdicts | `ACCEPTED_SEGMENT` 256 · `REJECTED_STATIONARY` 243 · `IGNORED_DUPLICATE` 7 · `REJECTED_NOISE` 6 · `IGNORED_NO_ANCHOR` 1 · `IGNORED_NO_SAMPLE` 1 | `ACCEPTED_SEGMENT` 497 · `REJECTED_STATIONARY` 87 · `IGNORED_DUPLICATE` 4 · `REJECTED_NOISE` 2 | — |
| Σ verdicts = ticks | **oui** (514) | **oui** (590) | — |

> Le segment 1 est nettement plus « arrêté » que le segment 2 (243 verdicts
> `REJECTED_STATIONARY` sur 514, ~47 %, contre ~15 % en seg2) : départ urbain dense.
> Les deux premiers ticks de seg1 sont `IGNORED_NO_SAMPLE` / `IGNORED_NO_ANCHOR`
> (initialisation de session, aucune ancre encore disponible) — normal.

## Raccordement des deux segments (vérifié, exact)

Au point de jonction (la pause), seg1.fin et seg2.début coïncident **au bit près** :

| Grandeur au raccord | Valeur (identique des deux côtés) |
|---|---|
| `total_m` | `2231.783833286014` |
| `(lat, lon)` | `(44.86005008686334, -0.609057666733861)` |
| `sample_ts_ms` | `1781596366000` (07:52:46Z) |

Conséquence : **le compteur de distance est continu à travers la pause** (le
`total_m` est persisté), et la somme des deux segments restitue exactement le total
final. Le démarrage du seg2 à 2231 m — qui, pris isolément, ressemblait à une fenêtre
amputée — s'explique **entièrement** par la pause : ce n'est pas un trou
d'observabilité, c'est le second des deux fichiers de log du trajet.

## Écart vs référence (7,8 km) — au niveau du **trajet complet**

| Grandeur | Valeur | Écart vs 7,8 km |
|---|---|---|
| Trip Meter (lecture utilisateur) | 7,71 km | **≈ −1,15 %** (≈ −90 m) |
| Trip Meter (`total_m` final exact) | 7,706 km | **−1,20 %** (−94 m) |

Même grandeur (le total affiché en fin de course), à l'arrondi près. L'écart se
situe **dans l'incertitude propre du compteur voiture** (un odomètre auto se trompe
couramment de +1 à +3 %) : c'est un **bon point terrain cohérent** — et cohérent
avec la capture de la veille (−0,7 %) — **pas une preuve métrologique absolue**.

## Rôle dans le corpus

Les deux JSONL sont intégrés comme régressions **structurelles** : auto-découverts
et rejoués **indépendamment** par `RealLogReplayTest` (invariant « un verdict par
tick », vérifié : 514 et 590). Le harnais ne recolle pas les segments ; la
reconstitution du trajet complet (0 → 7,71 km) est **documentaire** (cette fiche),
pas assertée par un test.

**Aucune borne chiffrée** n'est ajoutée à `LowSpeedRegressionReplayTest` (hors
périmètre). L'intégration reste **documentaire + données**, sans assertion de
distance.

Les deux GPX sont joints comme **artefacts bruts** (souvenir/historique) ; ils ne
sont **lus par aucun test** (le harnais ne sélectionne que les `real_*.jsonl`),
conformément à l'export GPX clôturé le
[2026-06-14](cloture_export_gpx_2026_06_14.md).
