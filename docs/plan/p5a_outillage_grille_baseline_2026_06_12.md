# P5.a — Outillage de grille + baseline (calage non engagé)

> **Nature** : note non normative. Ouvre **uniquement P5.a** (outillage + baseline).
> **P5.b et P5.c ne sont pas ouverts.** Aucun tuple candidat n'est choisi, aucun
> défaut n'est modifié, `FilterTuning.kt` est inchangé. Prérequis P5.c (log de
> marche lente référencé) **non requis** ici. Voir le découpage P5.a/b/c acté
> précédemment et le contrat `docs/contrats/gps_accumulation_filter_v0_1.md` §10.

## Ce que mesure la grille

L'outillage (`app/src/test/java/.../replay/TuningGridReplay.kt`) rejoue chaque log
du corpus à travers le **pipeline réel** pour un `FilterTuning` **injecté** (moteur
éphémère, jamais le défaut de production). Pour chaque couple (tuple × log) il
restitue :

- le **total accumulé** (m) ;
- la **répartition des verdicts** (`ACCEPTED_SEGMENT`, `REJECTED_STATIONARY`,
  `REJECTED_NOISE`, `REJECTED_IMPLAUSIBLE_JUMP`, `IGNORED_*`).

En balayant plusieurs tuples on obtient une **grille** : c'est le socle de mesure
que P5.b/P5.c exploiteront. P5.a se limite à fournir l'outil et à figer la
**baseline** du tuple par défaut.

Garanties vérifiées par le test (`TuningGridBaselineTest`) sans rien régler :
1. au tuple par défaut, la grille **reproduit** le pipeline de fidélité existant
   (`TickLogPipelineReplay`), total identique à 1e-6 près ;
2. **invariant structurel** par cellule : somme des verdicts = nombre de ticks ;
3. la **mécanique** de balayage produit bien une ligne par tuple (tuples
   d'épreuve étiquetés `non_candidat_*`, sans valeur de réglage, non retenus).

## Baseline — tuple par défaut `FilterTuning()`

Valeurs de **replay du moteur courant** (gate P4.2 actif), reprises de
`cloture_P4_2_gate_stationnaire_actif_2026_06_12.md` et
`audit_post_P4_2_gate_stationnaire_2026_06_12.md`, et de
`capture_urbain_P4_2_2026_06_12_odom_8_20km.md` pour le log urbain 8,20 km. Le
harness les reproduit (il rejoue le même moteur).

| Log | ticks | total_m (replay défaut) | Référence terrain |
|---|---:|---:|---|
| `sample_canape_synthetique` | 900 | 0.0 | arrêt (M1 ✓) |
| `real_canape_20260611` | 921 | 0.0 | arrêt (M1 ✓) |
| `real_canape_20260611_2351` | 52 | 0.0 | arrêt court, qualifié à part |
| `real_route_…odom_6_80km` | 666 | 6680.0 | odomètre 6,80 km (−1,76 %) |
| `real_urbain_…140454` | 700 | 2094.3 | aucune |
| `real_urbain_…142640` | 465 | 1818.1 | aucune |
| `real_urbain_…odom_8_20km_p4_2` | 1127 | ≈ 7976.0 | odomètre 8,20 km (−2,7 %) |

> **Attention — `total_m` enregistré ≠ baseline replay.** La dernière ligne
> `total_m` d'un log reflète le moteur **actif lors de la capture**. Les logs
> d'arrêt et de route/urbain antérieurs au gate P4.2 enregistrent donc des valeurs
> P4.1 (ex. `real_canape_20260611` enregistre 4,43 m), alors que le **replay du
> moteur courant** donne la baseline ci-dessus (0,0 m). Le harness rejoue toujours
> le moteur courant ; il ne lit jamais le `total_m` enregistré.

## Lecture (constats bruts, sans décision)

- **Arrêt** : les trois logs tombent à 0,0 m au défaut courant (M1 satisfait).
- **Route référencée** : 6680,0 m, soit −1,76 % vs odomètre 6,80 km (dans ±2 %).
- **Urbain référencé** : ≈ −2,7 % vs odomètre 8,20 km, **au-delà** de ±2 %.

Ces chiffres sont **descriptifs**. P5.a n'en tire aucune conclusion de réglage :
le choix d'un tuple (P5.b) et la borne piétonne (P5.c, dépendante du log de marche)
restent fermés.

## Comment lancer

Tests JVM standard (PC) :

```bash
./gradlew :app:testDebugUnitTest --tests "fr.arsenal.rallyetripmeter.replay.TuningGridBaselineTest"
```

Le rapport baseline est aussi écrit sous `app/build/reports/gpslog-tuning-grid/baseline.md`.

## Hors périmètre P5.a

Aucun réglage, aucun tuple retenu, aucun défaut modifié, aucune ouverture de P5.b
ou P5.c, aucun log de marche requis. Comportement applicatif inchangé.
