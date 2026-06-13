# Capture marche lente post-P4.2 — référence 520 m (2026-06-13)

> **Nature** : fiche de métadonnées terrain, non normative. Elle conserve les
> informations d'une capture de **marche lente référencée** réalisée après la
> clôture fonctionnelle de P4, destinée à alimenter **P5** et à documenter le risque
> basse vitesse (point explicitement reporté en clôture P4, cf.
> `cloture_chantier_P4_gate_stationnaire_2026_06_12.md` §9–§10).

> **État d'intégration** : au moment de l'écriture de cette fiche, le fichier replay
> cible **n'est pas encore présent dans le dépôt**, et le log source (chemin
> appareil) n'est pas accessible depuis ce clone. Les champs **mesurés**
> (total final, ticks, durée, appareil, état de session final) sont donc **non
> lisibles ici** et marqués « à renseigner à l'intégration du replay ». Aucune
> valeur n'est inventée.

## Métadonnées terrain (renseignées)

| Champ | Valeur |
|---|---|
| Fichier replay (cible) | `app/src/test/resources/replay/real_marche_lente_20260613_ref_520m_p4_2.jsonl` |
| Fichier source Android | `gpslog_20260613_145317.jsonl.txt` (`/storage/emulated/0/Download/gpslogs/`) |
| Date | 2026-06-13 |
| Type de trajet | marche lente |
| Distance de référence | **520 m** |
| Moteur actif à la capture | **P4.2** (gate stationnaire actif) |
| Calibration | **1.0** (neutre — mesure brute) |
| Session proprement clôturée | **oui** |
| Contexte | test volontaire de marche lente, réalisé après clôture fonctionnelle P4, pour préparer P5 (risque basse vitesse). |

## Mesures (à renseigner à l'intégration du replay)

> Non lisibles dans ce clone (ni le replay ni la source ne sont accessibles ici).
> À compléter lorsque le `.jsonl` sera versé dans `app/src/test/resources/replay/`,
> en relisant le fichier (et non en réécrivant cette fiche de mémoire).

| Champ | Valeur |
|---|---|
| Trip meter affiché (terrain) | non renseigné (non fourni à ce stade) |
| `total_m` final enregistré | à renseigner (lire la dernière ligne tick du replay) |
| Nombre de ticks | à renseigner |
| Durée approximative | à renseigner |
| Appareil | à renseigner (champ `device` de la ligne meta, si présent) |
| Calibration lue dans le log | à renseigner (champ meta `commit`, attendu `1.0`) |
| État de session final | à renseigner (dernier `session_state`) |
| Écart vs référence (520 m) | à calculer une fois le `total_m` final lu |

## Statut

- **Métadonnées terrain conservées** ; le log ne sera pas orphelin.
- **Replay à intégrer** : la fiche devient pleinement exploitable pour P5 une fois le
  fichier `real_marche_lente_20260613_ref_520m_p4_2.jsonl` présent dans le corpus et
  les mesures ci-dessus relevées.
- Destination : **audit marche lente post-P4.2 / matière première de P5** (régime
  basse vitesse).

## Interprétation prudente

- Une fois intégré, ce log mesurera **le sous-comptage piéton au défaut** et, par la
  répartition des verdicts (`REJECTED_STATIONARY` vs `REJECTED_NOISE`), aidera à
  **départager le mécanisme** (gate vs plancher), conformément à la préparation
  expérimentale `p5c_preparation_experimentale_marche_2026_06_12.md`.
- C'est un **régime non couvert** par le corpus existant (arrêts à 0 m, roulage
  véhicule), d'où son intérêt direct.

## Avertissement

- **Ne pas tuner le moteur sur ce seul log.** Référence unique, appareil unique :
  insuffisant pour un calage robuste (même limite que les références odomètre). Le
  réglage des constantes reste **P5.c**, et seulement après vérification croisée
  (cf. cartographie `p5b_cartographie_sensibilite_2026_06_12.md` : risque de
  sur-ajustement au corpus restreint et au téléphone unique).
- Aucune constante, aucun seuil, aucun golden ne doit être modifié sur la base de
  cette capture.

## Intérêt

Documenter explicitement la **basse vitesse**, point **reporté en tête de P5** lors
de la clôture P4 (le corpus n'en contenait pas). Cette capture comble cette zone
aveugle dès que le replay est intégré.

---

Fiche de métadonnées. Aucun réglage, aucune décision, aucun changement moteur.
