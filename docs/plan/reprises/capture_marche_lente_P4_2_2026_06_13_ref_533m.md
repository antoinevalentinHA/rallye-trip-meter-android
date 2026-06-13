# Capture marche lente post-P4.2 — référence 533 m (2026-06-13)

> **Nature** : fiche de métadonnées terrain, non normative. Elle conserve les
> informations d'une capture de **marche lente référencée** réalisée après la
> clôture fonctionnelle de P4, destinée à alimenter **P5** et à documenter le risque
> basse vitesse (point explicitement reporté en clôture P4, cf.
> `cloture_chantier_P4_gate_stationnaire_2026_06_12.md` §9–§10).

> **Correction de référence (2026-06-13)** : la distance de référence terrain
> initialement notée **520 m** a été **corrigée à 533 m** après revue du trajet. Le
> nom du replay et de cette fiche ont été mis à jour de `ref_520m` vers `ref_533m`
> en conséquence. Le **contenu du JSONL est inchangé** (seul le fichier est renommé) ;
> seuls les **écarts** vs référence sont recalculés ci-dessous.

> **État d'intégration** : le fichier replay cible **est présent dans le dépôt** et a
> été **réellement relu** ; les champs mesurés ci-dessous sont renseignés depuis le
> JSONL (et non de mémoire). Aucune valeur n'est inventée ; le `total_m` final est la
> mesure enregistrée par l'appareil, pas un souvenir utilisateur.

## Métadonnées terrain (renseignées)

| Champ | Valeur |
|---|---|
| Fichier replay (cible) | `app/src/test/resources/replay/real_marche_lente_20260613_ref_533m_p4_2.jsonl` |
| Fichier source Android | `gpslog_20260613_145317.jsonl.txt` (`/storage/emulated/0/Download/gpslogs/`) |
| Date | 2026-06-13 |
| Type de trajet | marche lente |
| Distance de référence | **533 m** (corrigée ; valeur initiale 520 m revue) |
| Moteur actif à la capture | **P4.2** (gate stationnaire actif) |
| Calibration | **1.0** (neutre — mesure brute) |
| Session proprement clôturée | **oui** |
| Contexte | test volontaire de marche lente, réalisé après clôture fonctionnelle P4, pour préparer P5 (risque basse vitesse). |

## Mesures (lues dans le replay intégré)

> Valeurs relevées en relisant réellement
> `app/src/test/resources/replay/real_marche_lente_20260613_ref_533m_p4_2.jsonl`.
> Le `total_m` final est la **mesure enregistrée** par l'appareil sous P4.2, pas un
> souvenir utilisateur. Vérification : un replay du moteur courant reproduit ce total
> au centième près (57,88 m), et l'invariant structurel tient (Σ verdicts = 580 =
> nombre de ticks).

| Champ | Valeur |
|---|---|
| Trip meter affiché (terrain) | non renseigné (non fourni) |
| `total_m` final enregistré | **57,88 m** (57.878938762195524) |
| Nombre de ticks | **580** |
| Durée approximative | **≈ 581 s (9 min 41 s)**, ~1 Hz |
| Appareil | **Pixel 10 Pro Fold** (meta `device`) |
| Calibration lue dans le log | **1.0** (meta `commit`) |
| État de session final | **`Running`** sur les 580 ticks (aucun tick `Stopped`/`Paused` enregistré ; le journal s'arrête alors que la session tourne — non contradictoire avec une clôture propre côté app, mais à signaler) |
| Vitesse | moyenne **1,19 m/s (≈ 4,3 km/h)**, max 4,27 m/s ; 576 ticks avec vitesse |
| GPS | 576 `Fixed`, 4 `Searching` |
| Écart absolu vs 533 m | **−475,1 m** |
| Écart relatif vs 533 m | **−89,1 %** |

### Répartition des verdicts (580 ticks)

| Verdict | Nombre | Part |
|---|---:|---:|
| `REJECTED_NOISE` | **510** | 87,9 % |
| `REJECTED_STATIONARY` | 47 | 8,1 % |
| `ACCEPTED_SEGMENT` | 15 | 2,6 % |
| `IGNORED_NO_SAMPLE` | 4 | 0,7 % |
| `IGNORED_DUPLICATE` | 3 | 0,5 % |
| `IGNORED_NO_ANCHOR` | 1 | 0,2 % |

## Statut

- **Replay intégré et lu** : `real_marche_lente_20260613_ref_533m_p4_2.jsonl` est dans
  le corpus, mesures relevées ci-dessus. Le log n'est pas orphelin.
- **Log exploitable pour P5** : matière première de l'**audit marche lente post-P4.2**
  (régime basse vitesse), auto-découvrable par le harness replay.
- Destination : **P5** (régime basse vitesse), **sans** modification moteur.

## Interprétation prudente

- **Sous-comptage massif au défaut** : 57,9 m mesurés pour 533 m parcourus, soit
  **−89,1 %**. À une allure moyenne de ~1,2 m/s à 1 Hz, le déplacement par tick
  (~1,2 m) est **inférieur au plancher de mouvement** (2,0 m).
- **Mécanisme dominant = le plancher de bruit, pas le gate.** La répartition des
  verdicts est sans ambiguïté : **`REJECTED_NOISE` 87,9 %** contre
  `REJECTED_STATIONARY` 8,1 %. Le déficit vient donc très majoritairement de pas
  rejetés sous le plancher (chaque seconde de marche < 2 m), pas de la machine
  stationnaire. Cela **départage l'hypothèse H-O2** de
  `p5c_preparation_experimentale_marche_2026_06_12.md` en faveur du **plancher**.
- Régime **non couvert** par le reste du corpus (arrêts à 0 m, roulage véhicule) :
  ce log comble précisément la zone aveugle « basse vitesse » de la cartographie
  `p5b_cartographie_sensibilite_2026_06_12.md`.
- **Sans surinterprétation** : c'est **un** log, **un** appareil, **une** allure. Le
  signal est net mais ne vaut pas conclusion de réglage. La correction de référence
  520 → 533 m ne change pas le mécanisme observé (l'écart reste ≈ −89 %).

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
de la clôture P4 (le corpus n'en contenait pas). Cette capture **comble cette zone
aveugle** : elle fournit la première mesure réelle du sous-comptage piéton et en
identifie le mécanisme dominant (plancher de bruit).

---

Fiche de métadonnées. Aucun réglage, aucune décision, aucun changement moteur.
