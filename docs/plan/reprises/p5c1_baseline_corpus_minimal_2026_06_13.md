# P5.c-1 — Baseline du moteur P4.2 sur le corpus minimal

> **Nature** : document de mesure, non normatif. **P5.c-1 ne corrige rien** : il
> établit la baseline du moteur **P4.2 actuel** sur le corpus minimal, point de
> comparaison obligatoire pour P5.c-2. Aucun code, aucun seuil, aucun JSONL, aucun
> golden modifié. Cadre : `p5_cadrage_basse_vitesse_post_marche_2026_06_13.md`.

## 1. Contexte

- **P4.2 est fonctionnellement clôturé** (`cloture_chantier_P4_gate_stationnaire_2026_06_12.md`).
- **P5 est ouvert** pour corriger le régime basse vitesse (plancher de bruit
  destructeur en marche lente).
- **P5.c-1 mesure la situation actuelle**, sans la modifier ; il fige la référence
  contre laquelle les options P5.c-2 seront jugées.

## 2. Corpus minimal

Quatre régimes, six fichiers replay (tous présents sous `app/src/test/resources/replay/`) :

- **Arrêt M1** : `real_canape_20260611.jsonl`, `real_canape_20260611_2351.jsonl`,
  `sample_canape_synthetique.jsonl`.
- **Route voiture référencée 6,80 km** : `real_route_voiture_20260612_odom_6_80km.jsonl`.
- **Urbain voiture référencé 8,20 km** : `real_urbain_voiture_20260612_odom_8_20km_p4_2.jsonl`.
- **Marche lente référencée 533 m** : `real_marche_lente_20260613_ref_533m_p4_2.jsonl`.

## 3. Métriques par log (mesurées)

> **Méthode** : chiffres obtenus en relisant réellement chaque JSONL et en rejouant
> le **moteur P4.2 courant** sur chaque log. Validation : pour les logs capturés sous
> P4.2 (urbain, marche), le total rejoué est **identique** au `total_m` enregistré.
> Pour les logs antérieurs (arrêts, route, capturés sous P4.1), le `total_m`
> **enregistré** reflète le moteur de l'époque ; la baseline ci-dessous est le
> **replay du moteur courant** (la valeur P4.2 pertinente). L'invariant structurel
> (Σ verdicts = ticks) tient sur les six logs.

**Arrêt M1 — `real_canape_20260611`** : total P4.2 **0,00 m** ; 921 ticks ; ≈923 s ;
verdicts `REJECTED_STATIONARY` 906 (dominant), IGNORED 15. (`total_m` enregistré
4,43 m = P4.1.) Conclusion : arrêt neutralisé.

**Arrêt M1 — `real_canape_20260611_2351`** : total P4.2 **0,00 m** ; 52 ticks ;
≈51 s ; `REJECTED_STATIONARY` 42, IGNORED 10. (enregistré 45,03 m = P4.1.) Arrêt
neutralisé.

**Arrêt M1 — `sample_canape_synthetique`** : total P4.2 **0,00 m** ; 900 ticks ;
≈899 s ; device `synthetic-couch-seed42` ; `REJECTED_STATIONARY` 899, IGNORED 1.
(enregistré 65,25 m.) Arrêt neutralisé.

**Route réf. — `real_route_voiture_20260612_odom_6_80km`** : total P4.2 **6680,0 m** ;
réf **6800 m** ; écart **−120,0 m / −1,76 %** ; 666 ticks ; ≈667 s ; verdicts
`ACCEPTED_SEGMENT` 486 (dominant), `REJECTED_STATIONARY` 142, `REJECTED_NOISE` 25,
IGNORED 13. Conclusion : route tenue (dans ±2 %).

**Urbain réf. — `real_urbain_voiture_20260612_odom_8_20km_p4_2`** : total P4.2
**7975,96 m** ; réf **8200 m** ; écart **−224,0 m / −2,7 %** ; 1127 ticks ; ≈1129 s ;
`ACCEPTED_SEGMENT` 808 (dominant), `REJECTED_STATIONARY` 290, `REJECTED_NOISE` 21,
IGNORED 8. Conclusion : urbain au-delà de ±2 %, à surveiller.

**Marche lente — `real_marche_lente_20260613_ref_533m_p4_2`** : total P4.2 **57,88 m** ;
réf **533 m** ; écart **−475,1 m / −89,1 %** ; 580 ticks ; ≈581 s ; verdicts
`REJECTED_NOISE` **510 (dominant)**, `REJECTED_STATIONARY` 47, `ACCEPTED_SEGMENT` 15,
IGNORED 8. Conclusion : **régime cassé**, déficit dominé par le plancher de bruit.

## 4. Baseline attendue (confirmée par la mesure)

- **M1 arrêt** : ≈ 0 m → **confirmé** (0,00 m sur les trois logs).
- **Route 6,80 km** : ≈ −1,76 % → **confirmé** (−120,0 m).
- **Urbain 8,20 km** : ≈ −2,7 % → **confirmé** (−224,0 m).
- **Marche 533 m** : 57,88 m, ≈ −89,1 %, `REJECTED_NOISE` dominant → **confirmé**.

## 5. Tableau de synthèse

| Régime | Replay | Référence | Total P4.2 | Écart | Verdict dominant | Statut |
|---|---|---|---:|---:|---|---|
| Arrêt M1 | `real_canape_20260611` | 0 m (cible) | 0,00 m | 0 | `REJECTED_STATIONARY` | tenu |
| Arrêt M1 | `real_canape_20260611_2351` | 0 m (cible) | 0,00 m | 0 | `REJECTED_STATIONARY` | tenu |
| Arrêt M1 | `sample_canape_synthetique` | 0 m (cible) | 0,00 m | 0 | `REJECTED_STATIONARY` | tenu |
| Route réf. | `real_route_…odom_6_80km` | 6800 m | 6680,0 m | −1,76 % | `ACCEPTED_SEGMENT` | tenu |
| Urbain réf. | `real_urbain_…odom_8_20km_p4_2` | 8200 m | 7975,96 m | −2,7 % | `ACCEPTED_SEGMENT` | à surveiller |
| Marche lente | `real_marche_lente_…ref_533m_p4_2` | 533 m | 57,88 m | −89,1 % | `REJECTED_NOISE` | **cassé** |

## 6. Lecture technique

- **P4.2 tient l'arrêt et la route** : arrêts à 0 m (gate stationnaire efficace),
  route référencée dans ±2 %.
- **L'urbain est à surveiller** : −2,7 %, au-delà de la cible ±2 % mais sans rupture.
- **La marche lente est le régime cassé** : −89,1 %, et la cause est claire dans les
  verdicts — `REJECTED_NOISE` 510/580. À ~1,2 m/s à 1 Hz, le pas réel par tick
  (~1,2 m) passe sous le plancher de 2 m et est rejeté ; l'ancre avance, la distance
  est perdue.
- **Le plancher de bruit est le levier principal à instruire** (cohérent avec la
  cartographie `p5b_cartographie_sensibilite_2026_06_12.md`), en gardant à l'esprit
  qu'un simple abaissement global rouvrirait la dérive à l'arrêt — d'où la piste d'un
  traitement différencié STATIONARY vs MOVING (cf. cadrage §4–§5).

## 7. Critères de comparaison pour P5.c-2

Toute option P5.c-2 sera jugée contre **cette baseline**, sur les quatre régimes
**simultanément** :

- **marche lente** : améliorer **fortement** le recouvrement (sortir des ~11 %
  actuels) ;
- **arrêt M1** : conserver ≈ 0 m sur les trois logs (ne pas rouvrir la dérive) ;
- **route** : pas de régression significative vs −1,76 % ;
- **urbain** : ne pas aggraver vs −2,7 % ;
- **tests replay verts** (auto-découverte, invariant structurel tenu).

Une option qui améliore la marche mais casse M1 ou dégrade la route est **rejetée**.

## 8. Non-objectifs

- Pas de modification moteur. Pas de seuil modifié. Pas de JSONL modifié. Pas de
  golden modifié.
- Pas de nouveau corpus exigé. Pas de campagne multi-téléphones.

## Méthode & ce qui reste à automatiser

- Chiffres produits par **relecture réelle des JSONL + replay fidèle du moteur P4.2**
  (concordance exacte avec les totaux enregistrés des logs P4.2). Reproductibles en
  CI via `TuningGridBaselineTest` (P5.a), qui imprime total + verdicts par log.
- **Reste à automatiser** (côté code, hors de ce patch documentaire) : enrichir le
  harness P5.a pour émettre, par log, la **référence terrain** et les **écarts
  absolu/relatif** par régime — aujourd'hui la grille sort total + verdicts, mais pas
  les références (qui vivent dans les fiches). À traiter comme un lot d'outillage
  séparé si souhaité, sans changement moteur.

---

Baseline P5.c-1. Aucun moteur, seuil, JSONL ou golden modifié ; mesure uniquement.
