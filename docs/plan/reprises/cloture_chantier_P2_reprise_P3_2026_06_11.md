# Clôture de chantier — P1/P2 terminés, reprise P3

**Date :** 2026-06-11
**Nature :** rapport de fin de session. Aucun code, aucun patch, aucun contenu P4.
**Objet :** permettre la reprise du chantier filtrage GPS dans plusieurs jours
sans relire l'historique de conversation.

---

## 1. État des lieux factuel à l'instant T

- **HEAD :** `1a0a614` — `docs(gps): add quantified diagnosis of drift leak routes`.
  Dépôt propre, synchronisé avec origin/main.
- **Chantier GPS anti-dérive — commits livrés** (du plus ancien au plus récent) :
  `72dec40` audit + plan P1–P6 · `47ac5b6` P1.a socle domaine (verdicts, modèle,
  codec JSONL v1) · `724fc8e` P1.b verdicts miroirs du moteur · `28f4cbf` P1.c
  émission d'une TickLogEntry par tick dans le runtime · `dc364f3` P1.d
  persistance fichier Android · `a45e0bb` P1.d-bis export Downloads ·
  `4c36cbd` P2.a harness de replay JVM · `1a0a614` P2.b diagnostic chiffré.
- **Comportement GPS :** strictement identique à l'état pré-chantier. P1+P2
  n'ont rien changé à l'accumulation : seuils, gardes, ordre et calculs du
  moteur sont ceux de `1d74bba`. Le défaut structurel (ancre glissante dans
  `TripRuntime.applyLocationEngineSample`, mise à jour avant verdict) est
  **toujours présent, volontairement**.
- **Corpus de replay :** `app/src/test/resources/replay/` —
  `real_canape_20260611.jsonl` (terrain réel, Pixel 10 Pro Fold, 921 ticks),
  `real_canape_20260611_2351.jsonl` (terrain réel, épisode sévère, 52 ticks)
  et `sample_canape_synthetique.jsonl` (fixture générée par le pipeline réel,
  seed 42, 900 ticks).
- **Tests :** ~72 tests JVM verts (codec, moteur+verdicts, runtime+logging,
  sink fichier, holder, harness, log réel auto-découvert).
- **Documents de référence :** `docs/plan/p1_observabilite_jsonl_2026_06_11.md`
  (sous-système d'observabilité), `docs/plan/p2b_diagnostic_routes_fuite_gps_2026_06_11.md`
  (diagnostic), plus l'audit et le plan P1–P6 d'origine.

## 2. Ce que les logs terrain ont démontré

- **Route A (bypass vitesse) confirmée et mécaniquement documentée** : sur le
  log canapé réel, 2/2 segments acceptés et 100 % de la distance fantôme
  (4,43 m) proviennent de ticks à vitesse parasite ≥ 0,5 m/s. Les deux segments
  acceptés ont des deltas de 2,28 / 2,15 m (rasant le plancher fixe de 2 m)
  avec des accuracies de 37,2 / 36,7 m : le plancher accuracy, court-circuité
  par la vitesse, les aurait rejetés d'un ordre de grandeur.
- **Pression route A quantifiée** : 43/907 ticks (4,7 %) portent une vitesse
  parasite ≥ 0,5 m/s (max 2,05 m/s, téléphone immobile) ; 41 contenus par le
  plancher 2 m, 2 fuités.
- **Route B (jitter > accuracy, vitesse absente) non observée** sur cet
  appareil : le chipset rapporte un Doppler sur 907/921 ticks.
- **Le harness est fiable** : la ré-exécution du log réel dans le pipeline
  réel reproduit le total à la décimale (4.433407848695635 m) et les comptages
  de verdicts à l'identique. C'est l'instrument de preuve de P3.
- **La pire magnitude est démontrée** (second log, addendum P2.b §8) :
  45,03 m en 63 s d'immobilité, 13/13 segments route A, régime de
  stabilisation de fix (accuracy ~39 m, vitesses parasites jusqu'à 3,12 m/s).
  L'épisode initial ~130 m est expliqué : ce régime maintenu plus longtemps.
- **Contamination d'ancre inter-sessions observée** : le cache de
  localisation et l'ancre du runtime process-wide survivent à l'arrêt du
  service ; un point périmé d'une session morte sert d'ancre à la suivante
  (alimente I6/I7 pour P4 ; sans correction avant P3).
- **Le pipeline d'observabilité fonctionne en conditions réelles** : 1 verdict
  par tick, méta correcte, artefacts (14 IGNORED_NO_SAMPLE au démarrage,
  6 IGNORED_DUPLICATE) cohérents avec la conception.

## 3. Ce qui reste à démontrer

- ~~La pire magnitude de la fuite~~ — **démontrée le soir même** (second log :
  45 m / 63 s, addendum P2.b §8). Restent utiles : fenêtre, voiture garée
  moteur tournant (diversité de conditions, pas preuve de magnitude).
- **La route B sur d'autres conditions/appareils** (vitesse moins souvent
  rapportée, accuracies plus optimistes). Non observée ≠ inexistante.
- **Le comportement en mouvement** : aucun log de marche lente, urbain ou
  routier dans le corpus. Indispensable avant P4 (critère « ne pas casser le
  comptage réel ») et pour P5. Capture possible en parallèle de P3.
- Rien de tout cela ne bloque P3, qui est un refactor à comportement constant.

## 4. Périmètre de P3 — et uniquement P3

Transférer la **propriété de l'ancre** de `TripRuntime` vers le filtre, via un
état explicite immutable transporté, **sans changer le comportement** :

- Nouveaux types : `FilterState` (v0 : uniquement le dernier échantillon,
  sémantique actuelle conservée), `FilterResult`, contrat `GpsAccumulationFilter`.
- `DistanceTripProgressEngine` implémente le nouveau contrat ; `TripRuntime`
  supprime `previousLocationSample`, transporte `FilterState` opaque, route
  `ApplyLocationSample` **et** `SimulateLocationStep` par le même `apply()`.
- Schéma JSONL **v2** : champs `anchor_*`, `d_net_from_anchor_m`,
  `machine_state` ajoutés au log ; le parser du harness accepte v1 et v2.
- **La sémantique héritée est reproduite à l'identique, y compris le bug** :
  l'ancre avance même sur rejet, avec un test qui documente explicitement ce
  point de bascule (inversé seulement en P4).
- Hors périmètre P3 : toute machine d'états, tout changement de garde ou de
  constante, la purge du `calibrationFactor`, tout réglage.

## 5. Risques de P3

- **Refactor « presque neutre »** : un ordre de garde subtilement modifié, ou
  le chemin `SimulateLocationStep` oublié sur l'ancien contrat. Parade : replay
  ligne à ligne sur les verdicts, pas seulement sur les totaux, + test de
  routage dédié.
- **Tentation d'améliorer en passant** (corriger l'ancre « tant qu'on y
  est »). Règle dure : tout changement de comportement détecté = échec du
  palier.
- **Casse mécanique des fakes de test** : `TripRuntimeTest` et
  `TripMeterViewModelTest` implémentent le contrat moteur ; adaptation
  attendue, assertions de distance inchangées.
- **Évolution v2 du codec** : risque de régression sur la lecture des logs v1
  du corpus. Parade : le lecteur reste rétrocompatible v1, testé sur le log
  réel existant.
- **Doctrine** : ne pas introduire d'état mutable caché dans le filtre
  (l'état est transporté, le runtime ne l'interprète jamais — I9 sémantique).

## 6. Critères de neutralité à vérifier après P3

1. **Replay bit-à-bit du corpus** : `real_canape_20260611.jsonl` →
   total exactement `4.433407848695635` m ; comptages :
   ACCEPTED 2, REJECTED_STATIONARY 857, REJECTED_NOISE 41, NO_ANCHOR 1,
   DUPLICATE 6, NO_SAMPLE 14. `real_canape_20260611_2351.jsonl` → total rejoué
   `45.031114671315514` m (verdicts en replay : voir addendum P2.b §8.4).
   `sample_canape_synthetique.jsonl` →
   29 ACCEPTED, 310 / 560, ~65,25 m. Séquence de verdicts identique ligne à
   ligne, pas seulement les totaux.
2. Suite de tests complète verte ; assertions de distance des tests existants
   intactes.
3. Constantes du moteur intactes (diff vérifiable : `NOISE_FLOOR_METERS`,
   `ACCURACY_FLOOR_FACTOR`, `STATIONARY_SPEED_MPS`, `MAX_PLAUSIBLE_SPEED_KMH`).
4. Test explicite (temporaire, à inverser en P4) : **un rejet déplace encore
   l'ancre** — la sémantique héritée est documentée, pas accidentelle.
5. Aucun fichier des goldens du corpus modifié.

## 7. Premier patch P3

**P3.a** : introduire `FilterState` / `FilterResult` / contrat
`GpsAccumulationFilter` + adaptation mécanique de `DistanceTripProgressEngine`
(sémantique strictement héritée) + `GpsAccumulationFilterContractTest`
(mapping des 18 cas existants vers les verdicts, et assertion explicite de
l'ancre qui avance sur rejet). Sans toucher `TripRuntime` — qui ne bascule
qu'en **P3.b**, le patch portant la preuve par replay bit-à-bit et le log v2.

## 8. Checklist de reprise de session

À donner tel quel à l'agent (ou à relire soi-même) au prochain démarrage :

1. `git clone` + `checkout main` + `pull` ; vérifier HEAD ≥ `1a0a614` et
   relire le HEAD réel avant toute conclusion.
2. Lire dans l'ordre : le plan P1–P6 (`docs/plan/`, document d'action),
   `p2b_diagnostic_routes_fuite_gps_2026_06_11.md`, puis ce document.
3. Vérifier l'état des tests :
   `./gradlew :app:testDebugUnitTest` (≈72 tests, tous verts attendus).
4. Vérifier le corpus : 2 fichiers dans `app/src/test/resources/replay/` ;
   si de nouveaux `real_*.jsonl` ont été capturés entre-temps, lire d'abord
   leurs rapports (`--tests "fr.arsenal.rallyetripmeter.replay.*"`, rapports
   écrits dans `app/build/reports/gpslog-replay/`).
5. Confirmer le périmètre : prochain patch = **P3.a** (§7), contraintes de
   neutralité = §6. Aucun changement de comportement, aucune constante,
   aucune machine d'états avant P4.
6. Rappels doctrine toujours en vigueur : pas de correction par coefficient
   (I10) ; la vitesse n'est jamais une autorité ; un golden ne se met à jour
   qu'avec justification écrite ; un patch = un palier partiel = une preuve.
7. Si une capture terrain est prévue (canapé conditions d'origine, fenêtre,
   marche, urbain, routier) : la faire **avant ou pendant** P3 — chaque log
   enrichit la preuve de neutralité sans bloquer le refactor.
