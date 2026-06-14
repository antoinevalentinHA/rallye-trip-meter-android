# Documentation — guide de lecture

Point d'entrée de la documentation du projet. **Ce n'est pas un catalogue** : il
indique seulement quoi lire, dans quel ordre, et **quels documents font foi**.

> Règle d'or : en cas de désaccord entre deux documents, **les contrats de
> `docs/contrats/` l'emportent**. Tout le reste (`docs/plan/`, `docs/plan/reprises/`,
> `docs/features/`) est du travail ou de l'historique, non normatif.

## Par où commencer

1. **`contrats/contrat_fonctionnel_v0_2.md`** — ce qu'est l'application (et ce
   qu'elle n'est pas). À lire en premier.
2. **Les contrats de composants** (ci-dessous) — l'architecture et ses invariants.
3. **État courant** — [`plan/usable_rally_milestone_2026_06_11.md`](plan/usable_rally_milestone_2026_06_11.md)
   (jalon d'usabilité) puis [`plan/reprises/cloture_p6b_purge_calibrationfactor_2026_06_14.md`](plan/reprises/cloture_p6b_purge_calibrationfactor_2026_06_14.md)
   (état du filtre GPS après P5/P6.b).

## Ce qui fait autorité — `docs/contrats/` (normatif)

Huit contrats, tous au statut VALIDÉ. Ils sont la référence ; le code s'y conforme.

| Contrat | Objet |
|---|---|
| [`contrat_fonctionnel_v0_2.md`](contrats/contrat_fonctionnel_v0_2.md) | Périmètre métier : trip meter de navigation au roadbook, ce qui est inclus/exclu. |
| [`tripstate_v0_1_2.md`](contrats/tripstate_v0_1_2.md) | État métier central. |
| [`tripcontroller_v0_1_2.md`](contrats/tripcontroller_v0_1_2.md) | Commandes utilisateur (session, reset, corrections). |
| [`locationengine_v0_1.md`](contrats/locationengine_v0_1.md) | Contrat d'acquisition GPS (cible). |
| [`distanceengine_v0_1_2.md`](contrats/distanceengine_v0_1_2.md) | Calcul métrique brut (Haversine). |
| [`gps_accumulation_filter_v0_1.md`](contrats/gps_accumulation_filter_v0_1.md) | Filtre d'accumulation = **mesure brute** : machine stationnaire/mouvement, verdicts, invariants. |
| [`observabilite_jsonl_v0_1.md`](contrats/observabilite_jsonl_v0_1.md) | Format JSONL d'observabilité (schéma versionné, sémantique des verdicts) — interface du replay et de la CI. |
| [`tripdisplay_v0_1_2.md`](contrats/tripdisplay_v0_1_2.md) | Affichage, et application du **coefficient utilisateur de calibration** (correction d'affichage). |

> Séparation clé portée par les contrats : le **filtre GPS** produit une distance
> **brute** ; le **coefficient utilisateur de calibration** ne corrige que
> l'**affichage**. Les deux étages sont étanches.

## Ce qui ne fait pas autorité

- **`docs/plan/`** — plans, audits et validations de chantier (non normatifs).
  Document directeur du filtre anti-dérive : `plan/plan_action_filtrage_gps_P1_P6_2026_06_11.md`.
  Certains documents anciens portent un bandeau **« Document historique »** : à lire
  comme du contexte, pas comme l'état actuel.
- **`docs/plan/reprises/`** — clôtures et notes de reprise de chantier (P2→P4,
  design, captures de logs). Utile pour comprendre *comment* on en est arrivé là.
- **`docs/features/`** — idées de fonctionnalités **non planifiées ni validées**
  (ex. `features/trace_rallye_osm.md`, visualisation de trace a posteriori).

## État du projet (synthèse)

Tag courant : **`v0.3.0-preview`** (utilisable, validé sur appareil).

Livré et validé sur appareil : trip meter fonctionnel ; runtime GPS foreground
(acquisition, permissions, pause/reprise, persistance) ; **filtre anti-dérive**
(série P1→P5 — la dérive à l'arrêt est neutralisée sans dégrader la mesure en
mouvement, contrat `gps_accumulation_filter_v0_1`) ; **observabilité JSONL** +
harness de replay ; **export GPX a posteriori** ; **mode paysage** exploitable en
voiture ; **relecture locale de trace GPX** en polyligne (Canvas), hors session.

À ne pas surpromettre : pas encore de **validation terrain finale (P6.a)**, pas de
précision métrologique certifiée, pas de vraie carte (fond géographique), pas de
Play Store, pas d'Android Auto.

**Prochaine étape** : P6.a (campagne terrain / validation réelle), puis P6.c
(doctrine de clôture du filtre, une fois les données terrain disponibles).

Pour la vue d'ensemble du dépôt (code, build, CI), voir le `README.md` à la racine.
