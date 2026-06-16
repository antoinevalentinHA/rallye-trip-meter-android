# Capture trajet voiture — référence 7,8 km (2026-06-16)

> **Navigation** — [← index des reprises](README.md) ·
> Régime concerné : contrat [`gps_accumulation_filter_v0_1.md`](../../contrats/gps_accumulation_filter_v0_1.md) ·
> Gate stationnaire : [P4.2 gate stationnaire actif](cloture_P4_2_gate_stationnaire_actif_2026_06_12.md) ·
> Capture sœur de la veille : [voiture réf. 7,8 km (2026-06-15)](capture_voiture_20260615_ref_7_8km.md) ·
> [état courant](../../ETAT.md)

> **Nature** : fiche de métadonnées terrain, non normative. Elle conserve les
> informations d'une capture de **trajet voiture référencé** (régime routier/urbain
> mixte), réalisée le 2026-06-16. Elle complète la capture voiture du même ordre de
> grandeur réalisée la veille ([réf. 7,8 km du 2026-06-15](capture_voiture_20260615_ref_7_8km.md)).

> **État d'intégration** : la paire de traces cible **est présente dans le dépôt**
> (`app/src/test/resources/replay/real_urbain_voiture_20260616_ref_7_8km.jsonl` +
> `.gpx`) et a été **réellement relue** ; les champs ci-dessous sont renseignés
> depuis le JSONL, pas de mémoire. Le `total_m` final est la mesure enregistrée par
> l'appareil. **Données brutes conservées telles quelles, sans correction.**

## Métadonnées terrain

| Champ | Valeur |
|---|---|
| Fichier replay (cible) | `app/src/test/resources/replay/real_urbain_voiture_20260616_ref_7_8km.jsonl` |
| Trace GPX associée (brute, inerte aux tests) | `app/src/test/resources/replay/real_urbain_voiture_20260616_ref_7_8km.gpx` |
| Fichier source Android | `gpslog_20260616_095554.jsonl` / `.gpx` (`/storage/emulated/0/Download/gpslogs/`) |
| Date | 2026-06-16 |
| Type de trajet | voiture (routier/urbain mixte, agglomération bordelaise) |
| Distance de référence (compteur) | **7,8 km** (compteur de trajet voiture, Audi) — référence terrain pratique, **pas une vérité métrologique** |
| Distance affichée par le Trip Meter | **7,71 km** (lecture utilisateur en fin de session) |
| Écart observé (utilisateur) | **≈ −90 m**, soit **≈ −1,15 %** (7,71 km vs 7,8 km) |
| Moteur actif à la capture | HEAD courant (post-P5.c-3 / P6.b) |
| Calibration | **1.0** (neutre — mesure brute ; `commit` du meta = `"1.0"`) |
| Appareil | Pixel 10 Pro Fold |
| Session proprement clôturée | **oui** (dernier tick `session_state = Running`, export JSONL + GPX produits) |
| Contexte | trajet réel pour enrichir le corpus de traces de référence terrain en régime de roulage. |

## Mesures (lues dans le replay intégré)

| Mesure | Valeur |
|---|---|
| Fenêtre couverte (samples) | 2026-06-16T07:52:46Z → 08:05:43Z (UTC) |
| Durée de la fenêtre journalisée | ~12,9 min (777 s d'horodatage échantillon ; ~590 s d'horloge app) |
| Ticks totaux | 590 |
| Points GPX | 590 (un par tick, dérivés du JSONL) |
| Vitesse max (samples) | ~62 km/h (17,23 m/s) |
| `total_m` au **premier** tick journalisé | **2231 m** (2,23 km) — voir §« Réserve de lecture » |
| `total_m` au **dernier** tick (= total Trip Meter) | **7706 m** (7,71 km) |
| Distance ajoutée **dans la fenêtre journalisée** (Σ `delta_total_m`) | ~5475 m (5,47 km) |
| Longueur brute **dans la fenêtre** (somme haversine, tous trkpt) | ~5512 m (5,51 km) |
| Verdicts | `ACCEPTED_SEGMENT` 497 · `REJECTED_STATIONARY` 87 · `IGNORED_DUPLICATE` 4 · `REJECTED_NOISE` 2 |

## Écarts vs référence (7,8 km) — au niveau du **total de session**

| Grandeur | Valeur | Écart vs 7,8 km |
|---|---|---|
| **Trip Meter (lecture utilisateur)** | 7,71 km | **≈ −1,15 %** (≈ −90 m) |
| **Trip Meter (`total_m` final exact, JSONL)** | 7,706 km | **−1,2 %** (−94 m) |

> Les deux lignes décrivent la **même grandeur** (le total affiché en fin de
> course), à l'arrondi près : 7,71 km est la lecture à l'écran, 7706 m la valeur
> exacte enregistrée dans le JSONL. L'écart de −1,15 % / −1,2 % se situe **dans
> l'incertitude propre du compteur voiture**.

## Réserve de lecture — fenêtre journalisée partielle (important)

Cette capture présente une particularité à ne pas masquer :

- Le `total_m` ne part **pas de 0** dans le fichier : le **premier** tick journalisé
  porte déjà `total_m ≈ 2231 m`. Le fichier (JSONL **et** GPX, qui couvrent la même
  fenêtre 07:52:46Z→08:05:43Z) ne contient donc **que le dernier segment** de
  l'accumulation, de ~2,23 km à ~7,71 km, soit ~5,5 km parcourus sur ~12,9 min.
- **Conséquence directe** : la longueur de la trace *contenue dans le fichier*
  (~5,5 km, que ce soit par la somme des `delta_total_m` ou par la somme haversine
  du GPX) **n'est pas** la distance du trajet. La distance du trajet (7,71 km) n'est
  lisible que via le `total_m` **final**, qui est un compteur courant transporté à
  l'entrée et à la sortie de la fenêtre.
- C'est pourquoi l'écart vs 7,8 km se raisonne **au niveau du total de session**
  (`total_m` final), et **non** au niveau de la longueur du segment journalisé.
- La cause exacte du démarrage à 2231 m (journalisation d'observabilité démarrée
  après le début de l'accumulation) n'est pas tranchée par cette fiche et **n'est
  pas une conclusion algorithmique** : c'est une simple note d'intégrité de la donnée.

## Rôle dans le corpus

Intégrée comme régression **structurelle** : auto-découverte et rejouée par
`RealLogReplayTest` (invariant « un verdict par tick », vérifié ici :
Σ verdicts = 590 = nombre de ticks).

**Aucune borne chiffrée** n'est ajoutée à `LowSpeedRegressionReplayTest` : c'est
hors périmètre, et ce serait par ailleurs **inapproprié** pour cette trace, dont la
fenêtre journalisée ne part pas de 0 (la longueur du segment ≠ distance de trajet).
L'intégration reste donc **documentaire + données**, sans assertion de distance.

La trace GPX est jointe comme **artefact brut** (souvenir/historique) ; elle n'est
**lue par aucun test** (le harnais de replay ne sélectionne que les `real_*.jsonl`).
Elle est strictement le compagnon a posteriori du JSONL, conforme à l'export GPX
clôturé le [2026-06-14](cloture_export_gpx_2026_06_14.md).
