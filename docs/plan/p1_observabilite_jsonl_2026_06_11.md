# Observabilité GPS — Palier P1 complet (pipeline JSONL)

**Date :** 2026-06-11
**Statut :** P1 livré en quatre patchs (P1.a → P1.d). Aucun changement de comportement GPS sur l'ensemble du palier.
**Référence amont :** audit anti-dérive et plan d'action P1–P6 (2026-06-11).

## Objet

Chaque tick `ApplyLocationSample` du runtime est désormais journalisé : un verdict
explicable par tick (invariant I11), persisté en JSONL sur l'appareil, relisible
en JVM pure pour le replay (P2). Ce document est la référence du sous-système.

## Architecture livrée

| Couche | Composants | Patch |
|---|---|---|
| Domaine — modèle | `SampleVerdict` (énum fermée), `TickLogEntry`, `TickLogMeta` | P1.a |
| Domaine — codec | `TickLogJsonl` (schéma plat versionné v1, zéro dépendance JSON) | P1.a |
| Domaine — contrat | `TickLogSink` + `NoOpTickLogSink` (défaut : zéro coût) | P1.a |
| Moteur | `applyLocationSampleWithVerdict` : verdict miroir des branches existantes, via `TripProgressResult` | P1.b |
| Runtime | une `TickLogEntry` par tick ; verdicts runtime `IGNORED_NO_SAMPLE`, `IGNORED_DUPLICATE`, `IGNORED_NO_ANCHOR` ; verdicts moteur propagés | P1.c |
| Android | `FileTickLogSink` (java.io, bufferisé), `TickLogSinkHolder` (délégateur process-wide), `TickLogSessionFactory` (fichier de session + meta), câblage service + factory ViewModel | P1.d |

Flux : le `TripRuntime` process-wide est construit avec le sink **délégateur**
stable (`TickLogSinkHolder.getSink()`), passé par les deux coutures de
construction (ViewModelFactory et service). Le foreground service installe un
sink fichier au démarrage (s'il n'y en a pas déjà un) et le retire/ferme à
l'arrêt. Sans session, le délégué est no-op.

## Fichiers de log

- **Emplacement :** `<stockage externe de l'app>/gpslogs/`
  (chemin typique : `/storage/emulated/0/Android/data/fr.arsenal.rallyetripmeter/files/gpslogs/`),
  repli sur le stockage interne si l'externe est indisponible. Lisible via
  l'app Fichiers ou Termux, sans adb ; supprimé à la désinstallation.
- **Nommage :** `gpslog_yyyyMMdd_HHmmss.jsonl` (heure locale de démarrage de la
  session de service), suffixe `_n` en cas de collision.
- **Cycle de vie :** un fichier par session de foreground service. Flush
  automatique toutes les 30 lignes (~30 s à 1 Hz) + flush/fermeture à l'arrêt
  du service. Aucune rotation ni purge automatique à ce palier : le nettoyage
  est manuel.

## Schéma JSONL v1 (rappel)

- Ligne 1 (meta) : `{"v":1,"type":"meta","commit":<version app>,"device":<modèle>,"started_at_ms":...}`
- Lignes suivantes (tick) : `v, type, tick_elapsed_ms, sample_ts_ms, sample_is_new, lat, lon, accuracy_m, speed_mps, gps_status, session_state, prev_ts_ms, segment_m, verdict, floor_m, implied_speed_kmh, delta_total_m, total_m` — champs absents émis `null`, clés jamais omises.

## Limites connues (assumées à ce palier)

- `segment_m`, `floor_m`, `implied_speed_kmh` sont `null` : les renseigner
  exigerait que le moteur expose un détail de verdict — prévu avec le schéma v2
  (P3), jamais recalculé côté runtime.
- `tick_elapsed_ms` utilise l'horloge epoch (`System.currentTimeMillis`)
  injectée par défaut, pas une horloge monotone Android. Suffisant pour P2
  (le replay s'appuie sur `sample_ts_ms` et l'ordre des lignes).
- `commit` porte le versionName applicatif, pas un hash git.
- Écritures sur le main Looper : tamponnées (64 Ko), flush disque toutes les
  30 lignes — coût négligeable à 1 Hz, à réévaluer si la cadence augmente.
- Toute IOException neutralise silencieusement le sink : l'observabilité ne
  fait jamais échouer une session.

## Consommation prévue (P2)

Le harness de replay JVM lira ces fichiers via `TickLogJsonl.parseMeta` /
`parseEntry` pour reconstituer les séquences d'échantillons et rejouer le
pipeline de décision. Protocole de capture terrain : voir plan P2 (canapé
15 min, fenêtre/voiture garée, marche lente, urbain, routier).
