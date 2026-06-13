# Cadrage P5 — correction du régime basse vitesse (post log marche lente)

> **Nature** : document de cadrage, non normatif. Il **ouvre P5** autour d'un
> problème précis révélé par le log de marche lente, **sans coder**, sans modifier le
> moteur, les seuils, les tests ou le JSONL. Il transforme un constat en plan
> d'action raisonnable ; il ne décide d'aucune valeur ni d'aucun mécanisme.

> **Problème posé** : sous P4.2, le gate stationnaire résout bien la dérive à
> l'arrêt, mais le **plancher de bruit de 2 m rejette massivement les petits pas GPS
> à basse vitesse**. Résultat : marche lente **sous-comptée d'environ 89 %**. P5 doit
> introduire une **stratégie basse vitesse compatible avec l'arrêt et le roulage
> voiture**.

## 1. Rappel du contexte P4

- **P4.2 validé fonctionnellement** (clôture `cloture_chantier_P4_gate_stationnaire_2026_06_12.md`).
- **Arrêt corrigé** : les logs d'arrêt (M1) accumulent 0 m — la dérive stationnaire
  est neutralisée par la machine STATIONARY + le gate.
- **Roulage voiture non dégradé** : route référencée 6,80 km à −1,76 % (dans ±2 %).
- **Risque basse vitesse explicitement reporté** : la clôture P4 posait la capture
  d'un log de marche lente référencé comme **prérequis avant tout réglage fin**
  (§9–§10). Ce prérequis est désormais **levé**.

## 2. Données d'entrée P5

Source : `capture_marche_lente_P4_2_2026_06_13_ref_533m.md` (log réellement relu).

- Log marche lente référencé **533 m** (corrigée depuis 520 m), Pixel 10 Pro Fold,
  calibration 1.0, 580 ticks, ~581 s (~1 Hz), allure moyenne ~1,2 m/s (~4,3 km/h).
- **Total enregistré : 57,88 m** → **écart −89,1 %** (≈ 11 % de la distance réelle
  seulement recouvrés).
- Verdicts : **`REJECTED_NOISE` 510 / 580 (87,9 %)**, `REJECTED_STATIONARY` 47
  (8,1 %), `ACCEPTED_SEGMENT` 15 (2,6 %), `IGNORED_*` 8.
- **Conclusion : plancher de bruit dominant.** Le déficit vient massivement de pas
  rejetés sous le plancher, **pas** du gate stationnaire. Cela **départage**
  l'hypothèse H-O2 de `p5c_preparation_experimentale_marche_2026_06_12.md` en faveur
  du **plancher**, cohérent avec la cartographie `p5b_cartographie_sensibilite_2026_06_12.md`
  (`noiseFloorMeters` = levier monotone dominant du total en mouvement).

## 3. Position méthodologique

- **Pas de campagne lourde multi-téléphones.** Le projet vise un trip meter de rallye
  pour un usage réel, d'abord sur ce téléphone et ce corpus, pas une certification
  scientifique.
- **Pas de réglage définitif sur un seul log.** Un log unique ne fixe pas une
  constante universelle.
- **Mais signal suffisant pour justifier une correction de régime.** Doctrine : un
  seul log ne suffit pas à *tuner finement*, mais suffit à *établir qu'un régime est
  cassé*. Ici le régime basse vitesse est manifestement cassé sous P4.2.
- **Corpus minimal acceptable** pour instruire P5 (pas d'élargissement folklorique) :
  arrêts M1 existants ; route référencée 6,80 km ; urbain référencé 8,20 km ; marche
  lente référencée 533 m.

## 4. Hypothèse technique principale

- Le **plancher fixe de 2 m** est adapté à l'**arrêt / bruit GPS** : à l'immobilité,
  le jitter doit être absorbé pour ne pas accumuler de fantôme.
- Il est **destructeur pour un déplacement lent à ~1 Hz** : un marcheur à ~1,2 m/s
  produit ~1,2 m de déplacement réel par tick, **sous** le plancher de 2 m. Chaque
  seconde de marche est alors rejetée en `REJECTED_NOISE`, l'ancre avance, et la
  distance est **perdue**.
- Donc P5 doit probablement **distinguer le bruit stationnaire des petits pas
  cohérents en mouvement lent**, plutôt que d'abaisser un plancher unique (ce qui
  rouvrirait la dérive d'arrêt — `noiseFloorMeters` plus bas réaccepterait le jitter
  à l'immobilité).

## 5. Pistes à instruire (sans coder)

Hypothèses de travail, **à explorer par replay**, aucune n'est retenue ici :

- **Accumulation basse vitesse après confirmation de mouvement** : n'autoriser la
  prise en compte des petits pas qu'une fois l'état MOVING confirmé.
- **Seuil dynamique selon l'état machine** : plancher strict en STATIONARY, plancher
  réduit (ou logique différente) en MOVING.
- **Fenêtre temporelle / accumulation cumulée de petits pas** : agréger plusieurs
  petits déplacements cohérents avant de décider, au lieu de juger chaque tick isolé.
- **Traitement différencié STATIONARY vs MOVING** : la décision « bruit vs pas réel »
  dépend de l'état, pas d'un seuil unique global.
- **Garde-fous transverses** : ne pas rouvrir la dérive à l'arrêt ; ne pas surcompter
  les tremblements GPS (jitter à vitesse nulle) ; rester cohérent avec le contrat
  `gps_accumulation_filter_v0_1.md` (mesure brute, un verdict par tick).

## 6. Critères de réussite P5

- **Marche lente** : ne plus tomber à ~10 % de la distance réelle ; recouvrer une
  part nettement majoritaire des 533 m (cible exacte à fixer **par mesure** en P5,
  pas par ajustement au seul log).
- **Arrêt** : conserver ≈ 0 m sur les logs M1.
- **Route voiture** : pas de régression significative sur la route référencée 6,80 km.
- **Urbain** : surveiller l'impact sur l'urbain référencé 8,20 km (déjà à ≈ −2,7 %).
- **Tests replay verts** (auto-découverte du corpus, invariant structurel tenu).

Ces quatre régimes doivent être tenus **simultanément** par toute correction
candidate ; une amélioration de la marche qui casse M1 ou la route est rejetée.

## 7. Non-objectifs

- Pas de support multi-appareils généralisé à ce stade.
- Pas de calibration universelle.
- Pas de carte (cf. interdiction ferme du contrat fonctionnel).
- Pas de modification UI.
- Pas de refonte complète de la chaîne GPS.
- Pas de tuning aveugle ni de sur-ajustement au seul log piéton.

## 8. Découpage P5 proposé

P5.a et P5.b sont **acquis** ; la correction basse vitesse constitue la substance de
**P5.c**, désormais débloquée par le log marche et sous-divisée en lots ordonnés.

- **P5.a — acquis** : outillage de grille + baseline du tuple par défaut
  (`p5a_outillage_grille_baseline_2026_06_12.md`).
- **P5.b — acquis** : cartographie de sensibilité des constantes
  (`p5b_cartographie_sensibilite_2026_06_12.md`).
- **P5.c — correction du régime basse vitesse** (objet de ce cadrage), en trois lots :
  - **P5.c-1 — Métrique & baseline basse vitesse** : définir la métrique de
    recouvrement marche (total / référence, répartition des verdicts) et l'évaluer
    sur le corpus minimal (M1 + route 6,80 + urbain 8,20 + marche 533). Mesure
    uniquement, aucun changement moteur. Débloqué.
  - **P5.c-2 — Exploration d'hypothèses par replay** : rejouer les pistes du §5 sur le
    corpus minimal, en mesurant **conjointement** les quatre régimes (§6), sans figer
    de valeur ni de mécanisme. Exploratoire, réversible.
  - **P5.c-3 — Décision, garde-fous, verrou** : ne retenir qu'un mécanisme minimal qui
    recouvre la marche **sans** casser M1 ni la route ; alors seulement ouvrir
    l'implémentation correspondante (chantier séparé) avec mise à jour du verrou
    `FilterTuningTest` et, si nécessaire, du contrat filtre.

Tant que P5.c-3 n'est pas atteint, **aucune constante, aucun seuil, aucun golden, ni
le moteur** ne sont modifiés.

---

Cadrage uniquement. Aucun code, aucun JSONL modifié, aucun seuil moteur changé,
aucune décision de réglage.
