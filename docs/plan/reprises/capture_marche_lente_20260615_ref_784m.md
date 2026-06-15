# Capture marche lente — référence 784 m (2026-06-15)

> **Navigation** — [← index des reprises](README.md) ·
> Régime concerné : contrat [`gps_accumulation_filter_v0_1.md`](../../contrats/gps_accumulation_filter_v0_1.md) ·
> Correctif validé : [P5.c-3 plancher mouvement](cloture_p5c3_moving_noise_floor_2026_06_13.md) ·
> [état courant](../../ETAT.md)

> **Nature** : fiche de métadonnées terrain, non normative. Elle conserve les
> informations d'une capture de **marche lente référencée**, réalisée **après** les
> clôtures P5.c-3 (plancher de bruit réduit en mouvement) et P6.b (purge
> `calibrationFactor`). Elle documente le **régime basse vitesse** — celui qui
> sous-comptait fortement avant P5.c-3 — sur un trajet frais, plus long et
> indépendant. Première vraie capture de validation basse vitesse **post-correctif**.

> **État d'intégration** : le fichier replay cible **est présent dans le dépôt**
> (`app/src/test/resources/replay/real_marche_lente_20260615_ref_784m.jsonl`) et a
> été **réellement relu** ; les champs ci-dessous sont renseignés depuis le JSONL,
> pas de mémoire. Le `total_m` final est la mesure enregistrée par l'appareil.

## Métadonnées terrain

| Champ | Valeur |
|---|---|
| Fichier replay (cible) | `app/src/test/resources/replay/real_marche_lente_20260615_ref_784m.jsonl` |
| Fichier source Android | `gpslog_20260615_092708.jsonl.txt` (`/storage/emulated/0/Download/gpslogs/`) |
| Date | 2026-06-15 |
| Type de trajet | marche lente |
| Distance de référence | **784 m** (Google Earth) |
| Moteur actif à la capture | **post-P5.c-3 / P6.b** (HEAD courant) |
| Calibration | **1.0** (neutre — mesure brute) |
| Appareil | Pixel 10 Pro Fold |
| Session proprement clôturée | **oui** |
| Contexte | reprise volontaire du test de marche lente après correctif basse vitesse, pour vérifier la fin du sous-comptage. |

## Mesures (lues dans le replay intégré)

| Mesure | Valeur |
|---|---|
| Durée (samples) | ~20,6 min (1234 s) |
| Ticks totaux | 563 |
| Samples nouveaux | 555 |
| `total_m` final (Trip Meter) | **799,9 m** |
| Longueur brute (somme haversine, tous samples) | ~1066 m |
| Verdicts | `ACCEPTED_SEGMENT` 319 · `REJECTED_NOISE` 222 · `REJECTED_STATIONARY` 14 · `IGNORED_DUPLICATE` 8 |

## Écarts vs référence (784 m)

| Grandeur | Valeur | Écart |
|---|---|---|
| **Trip Meter** | 799,9 m | **+2,0 %** |
| Brut sans filtre | ~1066 m | +36,0 % |

## Lecture

- Le filtre fait son travail : la trace brute était **gonflée de +36 %** par le
  bruit GPS ; après rejet du bruit (222) et du stationnaire (14), le total atterrit
  à **+2,0 %** de la référence.
- C'est le **régime basse vitesse** qui, sur l'ancien log de marche (533 m,
  pré-P5.c-3), sous-comptait à ~−44 %. Sur cette capture post-correctif, il passe à
  **+2,0 %** : le correctif P5.c-3 tient sur un trajet frais, plus long et
  indépendant.
- **Réserve** : point de validation **unique**. La référence Google Earth a sa
  propre incertitude (quelques %), et +2 % peut inclure une part de chance dans le
  trim du bruit. Ne certifie pas à lui seul la précision métrologique : P6.a requiert
  plusieurs captures référencées sur des régimes variés. Mais comme point basse
  vitesse, il est très favorable.

## Rôle dans le corpus

Intégrée comme régression permanente : auto-découverte et rejouée par
`RealLogReplayTest` (invariant structurel « un verdict par tick »). Aucune borne
chiffrée n'est ajoutée à `LowSpeedRegressionReplayTest` dans ce lot (réservé à un
chantier de test ultérieur si souhaité) ; l'intégration reste documentaire + données.
