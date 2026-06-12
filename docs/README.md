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
3. **État courant** — `plan/usable_rally_milestone_2026_06_11.md` (jalon
   d'usabilité) puis `plan/reprises/cloture_chantier_P4_gate_stationnaire_2026_06_12.md`
   (état du filtre GPS après P4).

## Ce qui fait autorité — `docs/contrats/` (normatif)

Sept contrats, tous au statut VALIDÉ. Ils sont la référence ; le code s'y conforme.

| Contrat | Objet |
|---|---|
| `contrat_fonctionnel_v0_2.md` | Périmètre métier : trip meter de navigation au roadbook, ce qui est inclus/exclu. |
| `tripstate_v0_1_2.md` | État métier central. |
| `tripcontroller_v0_1_2.md` | Commandes utilisateur (session, reset, corrections). |
| `locationengine_v0_1.md` | Contrat d'acquisition GPS (cible). |
| `distanceengine_v0_1_2.md` | Calcul métrique brut (Haversine). |
| `gps_accumulation_filter_v0_1.md` | Filtre d'accumulation = **mesure brute** : machine stationnaire/mouvement, verdicts, invariants. |
| `tripdisplay_v0_1_2.md` | Affichage, et application du **coefficient utilisateur de calibration** (correction d'affichage). |

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

- MVP **validé sur appareil** (acquisition GPS runtime, foreground service,
  persistance, pause/reprise).
- Filtre anti-dérive **série P1→P4 clôturée** : la dérive à l'arrêt est neutralisée
  sans dégrader la mesure en mouvement (contrat `gps_accumulation_filter_v0_1`).
- **À venir** : P5 — calage fin des constantes internes du filtre par replay
  (indépendant de la calibration utilisateur, déjà implémentée).

Pour la vue d'ensemble du dépôt (code, build, CI), voir le `README.md` à la racine.
