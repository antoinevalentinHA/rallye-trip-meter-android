# Protocole — capture d'un log de marche lente référencé (prérequis P5)

> **Nature** : note opérationnelle de terrain, **non normative**. Elle décrit
> *comment capturer* le log de marche lente référencé posé en prérequis par la
> clôture P4 (`cloture_chantier_P4_gate_stationnaire_2026_06_12.md` §9–§10) et le
> contrat `docs/contrats/gps_accumulation_filter_v0_1.md` §10. **Aucun code, aucun
> tuning, P5 non ouvert.** But unique : borner empiriquement la sous-accumulation
> piétonne du gate avant tout réglage de seuils.

## Protocole terrain (10 lignes)

1. **Conditions** : extérieur ciel dégagé (éviter immeubles, arbres, tunnels), sol plat, parcours **mesuré à l'avance** (longueur connue).
2. **Distance cible** : **~500 m** (cf. `plan_action_filtrage_gps_P1_P6_2026_06_11.md` §6, item « marche lente ~500 m mesurée »).
3. **Allure** : marche **lente et régulière** (~1 m/s ≈ 3,6 km/h), sans arrêt, en ligne droite ou boucle connue.
4. **Calibration** : laissée **neutre (1.000)** — on capture la **mesure brute**, pas une distance corrigée.
5. **Capture** : **START** au point de départ exact → marcher → **STOP** au point d'arrivée exact ; une seule session continue, écran allumé.
6. **Référence** : distance **mesurée indépendamment** (roue de mesure, plan cadastral, tour de piste). **Jamais l'odomètre voiture.**
7. **Récupération** : fichier `gpslog_<yyyyMMdd>_<HHmmss>.jsonl` dans `Downloads/gpslogs` (accessible Termux via `~/storage/downloads/gpslogs`).
8. **Renommage corpus** : `real_marche_<yyyyMMdd>_ref_<dist>.jsonl` (ex. `real_marche_20260613_ref_0_50km.jsonl`).
9. **Dépôt** : copier le fichier renommé dans `app/src/test/resources/replay/`.
10. **Validation** : relancer le harness ; le log doit être auto-découvert et passer l'invariant structurel.

## Critères d'acceptation (avant intégration au corpus)

- **Méta-ligne présente** en tête (`type:"meta"`), calibration **`1.0`** (mesure brute).
- **Nom `real_…​.jsonl`** : auto-découverte par `RealLogReplayTest` (préfixe `real_`, extension `.jsonl`).
- **Invariant structurel vert** : somme des verdicts = nombre de ticks (la seule assertion du harness).
- **Session unique et continue** (pas de pause), **GPS acquis** (capture en extérieur, pas en intérieur), accuracy raisonnable.
- **Distance de référence connue et notée** : dans le nom de fichier **et** en une ligne de métadonnée (méthode de mesure + valeur).

## Hors périmètre de cette note

Aucun réglage de seuil, aucune assertion de total, aucune ouverture de P5. La note
livre seulement la donnée manquante ; l'exploitation (mode grille du harness,
choix du tuple, sensibilité) appartient à P5.
