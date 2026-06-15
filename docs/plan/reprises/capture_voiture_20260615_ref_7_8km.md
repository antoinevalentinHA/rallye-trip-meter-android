# Capture trajet voiture — référence 7,8 km (2026-06-15)

> **Navigation** — [← index des reprises](README.md) ·
> Régime concerné : contrat [`gps_accumulation_filter_v0_1.md`](../../contrats/gps_accumulation_filter_v0_1.md) ·
> Gate stationnaire : [P4.2 gate stationnaire actif](cloture_P4_2_gate_stationnaire_actif_2026_06_12.md) ·
> [état courant](../../ETAT.md)

> **Nature** : fiche de métadonnées terrain, non normative. Elle conserve les
> informations d'une capture de **trajet voiture référencé** (régime routier/urbain
> mixte), réalisée après les clôtures P5.c-3 et P6.b. Elle complète la capture de
> marche lente du même jour (régime basse vitesse) par un régime de **roulage**.

> **État d'intégration** : le fichier replay cible **est présent dans le dépôt**
> (`app/src/test/resources/replay/real_urbain_voiture_20260615_ref_7_8km.jsonl`) et
> a été **réellement relu** ; les champs ci-dessous sont renseignés depuis le JSONL,
> pas de mémoire. Le `total_m` final est la mesure enregistrée par l'appareil.

## Métadonnées terrain

| Champ | Valeur |
|---|---|
| Fichier replay (cible) | `app/src/test/resources/replay/real_urbain_voiture_20260615_ref_7_8km.jsonl` |
| Fichier source Android | `gpslog_20260615_100423.jsonl.txt` (`/storage/emulated/0/Download/gpslogs/`) |
| Date | 2026-06-15 |
| Type de trajet | voiture (routier/urbain mixte) |
| Distance de référence | **7,8 km** (compteur de trajet voiture, Audi A3 e-tron) |
| Moteur actif à la capture | **post-P5.c-3 / P6.b** (HEAD courant) |
| Calibration | **1.0** (neutre — mesure brute) |
| Appareil | Pixel 10 Pro Fold |
| Session proprement clôturée | **oui** |
| Contexte | trajet réel pour valider le régime de roulage et le comportement du gate stationnaire à travers des arrêts. |

## Mesures (lues dans le replay intégré)

| Mesure | Valeur |
|---|---|
| Durée de session (samples) | ~42,9 min (2571 s) |
| Durée de conduite (compteur voiture) | ~0:16 h |
| Ticks totaux | 899 |
| Samples nouveaux | 893 |
| Vitesse max (samples) | ~71 km/h |
| `total_m` final (Trip Meter) | **7743 m** (7,74 km) |
| Longueur brute (somme haversine, tous samples) | ~7777 m (7,78 km) |
| Verdicts | `ACCEPTED_SEGMENT` 749 · `REJECTED_STATIONARY` 127 · `REJECTED_NOISE` 17 · `IGNORED_DUPLICATE` 6 |

## Écarts vs référence (7,8 km)

| Grandeur | Valeur | Écart |
|---|---|---|
| **Trip Meter** | 7,74 km | **−0,7 %** |
| Brut sans filtre | 7,78 km | −0,3 % |

## Lecture

- À vitesse de roulage, le **bruit GPS est négligeable** : les points sont espacés
  et dominés par le déplacement réel, d'où brut et filtré quasi confondus (7,78 vs
  7,74 km). Le filtre ne retire presque rien sur la distance ; il neutralise surtout
  les **arrêts**.
- **Point central de cette capture** : la session a duré **~42,9 min pour ~16 min de
  conduite** (compteur voiture), soit ~27 min à l'arrêt cumulées, et 127 verdicts
  `REJECTED_STATIONARY`. Malgré cela, le total atterrit **légèrement en-dessous**
  (−0,7 %), **pas au-dessus** : le **gate stationnaire tient à travers de vrais
  arrêts**, sans accumuler de dérive. Validation M1/M4 en conditions réelles.
- **Réserve** : l'odomètre/compteur voiture a sa propre imprécision (souvent +1 à
  +3 %), donc −0,7 % est **dans l'incertitude de la référence**. Ce n'est pas une
  preuve métrologique absolue, mais un excellent point routier réel.

## Rôle dans le corpus

Intégrée comme régression permanente : auto-découverte et rejouée par
`RealLogReplayTest` (invariant structurel « un verdict par tick »). **Aucune borne
chiffrée** ajoutée à `LowSpeedRegressionReplayTest` (hors périmètre) ; l'intégration
reste documentaire + données. Avec la capture de marche lente
([réf. 784 m](capture_marche_lente_20260615_ref_784m.md), +2,0 %), le corpus couvre
désormais **deux régimes validés post-correctif** : basse vitesse et roulage.
