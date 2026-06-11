# P2.b — Diagnostic chiffré des routes de fuite GPS (dérive à l'arrêt)

**Date :** 2026-06-11
**Statut :** P2.b clos. Documentation uniquement, aucun changement comportemental.
**Amont :** audit anti-dérive, plan P1–P6, pipeline d'observabilité P1
(`p1_observabilite_jsonl_2026_06_11.md`), harness de replay P2.a.
**Corpus :** `app/src/test/resources/replay/real_canape_20260611.jsonl`
(terrain réel) + `sample_canape_synthetique.jsonl` (fixture pipeline, seed 42).

## 1. Contexte

L'audit anti-dérive a identifié deux routes de fuite plausibles pour la
distance fantôme à l'arrêt, indiscernables sans données par tick :

- **Route A — bypass vitesse** : une vitesse parasite ≥ 0,5 m/s rapportée à
  l'arrêt court-circuite le rejet stationnaire **et** le plancher accuracy ;
  le segment n'a plus qu'à dépasser le plancher fixe de 2 m pour être accepté.
- **Route B — jitter > accuracy** : vitesse absente, plancher = 1 × accuracy,
  accuracy optimiste en intérieur ; les excursions de jitter dépassant ce
  plancher sont acceptées (et comptées aller-retour par l'ancre glissante).

P2.b tranche entre A et B sur données réelles, avant toute modification du
filtre. Règle du chantier : aucune correction tant que la preuve n'est pas
chiffrée.

## 2. Protocole du test canapé

- Appareil : Pixel 10 Pro Fold, app version 1.0 (pipeline P1 complet).
- Téléphone immobile en intérieur (canapé), session démarrée, écran éteint,
  foreground service actif, ~15 minutes (909 s de fixes), arrêt de session.
- Log récupéré via l'export `Downloads/gpslogs/` (P1.d-bis), renommé
  `real_canape_20260611.jsonl`, déposé dans les ressources de test.
- Rejeu : harness P2.a (`TickLogReplayAnalyzer` + ré-exécution
  `TickLogPipelineReplay` dans le pipeline réel).

## 3. Résultats du log réel

### 3.1 Rapport global

```
ticks: 921            lignes_malformees: 0
verdicts:
  ACCEPTED_SEGMENT:          2
  REJECTED_STATIONARY:     857
  REJECTED_NOISE:           41
  REJECTED_IMPLAUSIBLE_JUMP: 0
  IGNORED_NOT_RUNNING:       0
  IGNORED_NO_ANCHOR:         1
  IGNORED_DUPLICATE:         6
  IGNORED_NO_SAMPLE:        14
distance_totale_accumulee_m: 4.43   (= total final enregistré)
distance_moyenne_par_segment_m: 2.22
accuracy_m: min=4.59 max=58.67 moyenne=11.52 (n=907)
vitesse_mps: min=0.00 max=2.05 moyenne=0.06 (n=907)
accepted_segments_with_speed_ge_0_5: 2
distance_from_segments_with_speed_ge_0_5_m: 4.43
```

Fidélité de ré-exécution : total rejoué = total enregistré à la décimale près
(4.433407848695635 m), comptages de verdicts identiques. Le log est fidèle au
pipeline ; le harness est validé sur données réelles.

### 3.2 Les deux segments acceptés (la fuite, en détail)

| ts (epoch ms) | delta (m) | accuracy (m) | vitesse (m/s) |
|---|---|---|---|
| 1781212785000 | 2.28 | **37.24** | 1.72 |
| 1781212844000 | 2.15 | **36.73** | 0.53 |

Lecture : deltas rasant le plancher fixe de 2 m, avec des accuracies ~37 m.
Si le plancher accuracy s'était appliqué (`max(2, 37 × 1.0)` ≈ 37 m), ces
segments auraient été rejetés **par un ordre de grandeur**. Seule la vitesse
parasite (1,72 et 0,53 m/s) a ouvert la vanne. C'est la signature exacte de
la route A.

### 3.3 Pression de la route A sur la session

43 ticks sur 907 (4,7 %) portent une vitesse parasite ≥ 0,5 m/s (max observé :
2,05 m/s, téléphone immobile). Répartition de ces 43 ticks :
**41 → REJECTED_NOISE** (pas de jitter < 2 m), **2 → ACCEPTED_SEGMENT**.
Autrement dit : sur ce log, le plancher fixe de 2 m a contenu 95 % de la
pression route A ; les 5 % restants sont 100 % de la distance fantôme.

### 3.4 Route B sur ce log

Inexistante : 907 ticks sur 921 portent une vitesse (ce chipset rapporte
quasi systématiquement un Doppler), donc la branche « vitesse absente →
plancher accuracy » n'a presque jamais été empruntée, et aucun segment n'y a
été accepté.

## 4. Conclusion

**Route A confirmée sur ce log : 2/2 segments acceptés, 4,43 m / 4,43 m
(100 %) de la distance fantôme proviennent de segments à vitesse parasite
≥ 0,5 m/s, avec bypass démontré du plancher accuracy (accuracy ~37 m,
deltas ~2,2 m).** Route B : non observée sur cet appareil dans ces conditions.

Mise en garde de magnitude, à lire honnêtement : 4,43 m / 15 min est **sous**
le seuil M1 (≤ 10 m) — ce log, seul, ne reproduit pas l'épisode initial de
~130 m / 0,10 km UI, qui appartient à une session antérieure non journalisée.
Le mécanisme est prouvé ; sa pire magnitude ne l'est pas encore. Extrapolation
raisonnée (étiquetée comme telle) : la fuite est le produit
`P(vitesse parasite) × P(pas de jitter > 2 m)`. Ici le jitter était faible
(95 % des pas < 2 m). Dans des conditions de multipath plus dures (intérieur
profond, premier fix, autre emplacement), les mêmes 43 ticks à vitesse
parasite seraient majoritairement acceptés — l'ordre de grandeur 100–130 m
redevient atteignable par cette seule route. La fixture synthétique (jitter
±2,2 m, 11 % de vitesses parasites → 65 m / 15 min, 29/29 acceptés route A)
illustre cette sensibilité.

## 5. Limites

- **Un seul log terrain**, un seul appareil (Pixel 10 Pro Fold), une seule
  condition intérieure. Le comportement Doppler varie fortement par chipset.
- L'épisode ~130 m n'est pas dans le corpus : la pire magnitude reste à
  capturer (refaire le canapé dans les conditions d'origine, + fenêtre /
  voiture garée moteur tournant).
- Route B non observée ≠ route B inexistante : un appareil rapportant moins
  souvent la vitesse, ou des accuracies plus optimistes, l'exposerait.
  Les gardes P4 doivent traiter les deux routes.
- `segment_m` / `floor_m` sont null dans le log v1 (assumé P1) : le détail
  ci-dessus est reconstruit depuis `delta_total_m` et les champs du tick.

## 6. Impact sur P3 / P4

- **P3 (refactor neutre)** : inchangé. Le corpus de référence existe
  (1 log réel + 1 fixture pipeline) ; le replay bit-à-bit (totaux à la
  décimale + verdicts ligne à ligne) est démontré opérationnel — c'est la
  preuve de neutralité exigée.
- **P4 (machine d'états)** : le diagnostic valide les choix de conception et
  en durcit deux :
  1. la vitesse ne doit **jamais** être une autorité d'acceptation (ici elle
     a accepté des segments à accuracy 37 m) — corroborant uniquement ;
  2. aucun chemin ne doit court-circuiter le dimensionnement du gate par
     l'accuracy.
  Le test **T2** (jitter + vitesses parasites 0,5–2,1 m/s → accumulation ≈ 0)
  est confirmé comme le test tueur : le code actuel y échoue par construction.
  Les constantes P5 devront être calées en gardant ce log **et** des logs à
  jitter plus fort.

## 7. Critères à préserver pour les prochains patchs

1. Ce log et la fixture restent verts en replay : P3 doit reproduire
   **exactement** 4.433407848695635 m et les comptages de verdicts ci-dessus
   (goldens de neutralité).
2. Toute mise à jour de golden (P4) est justifiée fichier par fichier dans le
   message de commit, avec le double critère : logs d'arrêt → M1, logs de
   mouvement → écart ≤ 2 %.
3. Aucun nouveau patch ne touche au filtre avant P3 ; aucun réglage de
   constante avant P5 ; jamais de correction par coefficient (I10).
4. Chaque future sortie terrain produit un `real_*.jsonl` versé au corpus
   (auto-découvert par `RealLogReplayTest`).
