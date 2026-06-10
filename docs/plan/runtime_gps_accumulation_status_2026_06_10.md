# Point d’étape — Accumulation hors lifecycle UI (post-B3)

> **Mise à jour 2026-06-10 (soir)** : première sortie route effectuée. Accumulation écran éteint observée fonctionnelle (à confirmer) ; régression du filtre anti-dérive identifiée puis corrigée (`fix(gps): avoid accuracy floor while moving`, HEAD `8530fa8`). Détails en §5. Calibration toujours bloquée.

## 1. Statut

- **Date** : 2026-06-10.
- **HEAD** : `28f421a` (`feat(runtime): accumulate samples from foreground service`).
- **CI** : verte (`testDebugUnitTest` + `assembleDebug`), après correctif du test de non-duplication (`TripRuntimeTest`).
- **Validation device** : partielle (cf. §4 et §5).
- **Nature du document** : synthèse d’avancement, **non normative**. Il actualise le point d’étape précédent (`runtime_gps_v0_1_status_2026_06_09.md`), dont certaines limites (« aucune persistance », « pas de foreground service ») sont désormais levées, sans le remplacer. Les contrats `docs/contrats/` restent la référence.

## 2. Résumé exécutif

Depuis le point d’étape du 2026-06-09, le runtime GPS a franchi plusieurs chantiers structurants : persistance minimale de l’état, foreground service propriétaire du GPS (chantier GPS-OWN), correctif anti-dérive stationnaire, puis extraction et montée en puissance d’un runtime autoritaire (paliers B1 → B3).

Le `TripState` n’est plus possédé par le ViewModel mais par un `TripRuntime` autoritaire (B1), promu en instance unique à l’échelle du process (B2). À partir de B3, le foreground service consomme périodiquement les samples GPS et alimente ce runtime partagé : l’accumulation de distance n’est donc plus pilotée par le seul pump UI. C’est le premier palier qui ouvre la voie à l’accumulation **écran éteint / arrière-plan** — dont la **validation route reste à réaliser**.

## 3. État architectural après B3 (implémenté, CI verte)

- **Runtime autoritaire** : `TripRuntime` possède le `TripState` et applique contrôle, samples et persistance (B1).
- **Runtime process-wide** : `TripRuntimeHolder` fournit une instance unique partagée par le ViewModel et le service (B2). À la première construction l’état est restauré depuis le store ; les appels suivants réutilisent l’instance vivante (pas de réinitialisation à chaque recréation du ViewModel).
- **ViewModel = adaptateur / miroir UI** : il délègue au runtime, projette `uiState`, et conserve la permission de localisation et le pilotage du foreground service (`syncForegroundService`).
- **Service propriétaire du GPS** (GPS-OWN) : `Running` = GPS actif ; `Paused` / `Stopped` = GPS coupé.
- **Service consommateur des samples** (B3) : tant que le service tourne, une boucle postée sur le **main Looper** appelle `runtime.onEvent(ApplyLocationSample)` à ~1 s.
- **Persistance via le runtime** : le service **n’écrit jamais** directement dans le store ; la sauvegarde (throttle) est faite par le runtime.
- **UI** : le pump `repeatOnLifecycle(STARTED)` reste présent (premier plan), et le ViewModel tient un miroir Compose de l’état runtime.

| Chantier / commit | Statut |
|---|---|
| Persistance SharedPreferences (`8cc0d7a`) + throttle (`39ed4b8`) | Implémenté, CI verte, **validé device** (persistance) |
| Foreground service + notification (`51b039c`, `32e7ea8`, `26837d5`) | Implémenté, CI verte, **validé device** (notification) |
| GPS-OWN-1 — holder moteur GPS (`0632589`) | Implémenté, CI verte |
| GPS-OWN-2 — service démarre l’acquisition (`135e097`) | Implémenté, CI verte |
| Correctif anti-dérive stationnaire (`3dd0cdd`) | Implémenté, CI verte, **validé bureau** (0 m fantôme) ; **test route à faire** |
| GPS-OWN-4 — service seul pilote du GPS (`8c3b16c`) | Implémenté, CI verte |
| GPS-OWN-3 — GPS coupé en `Paused` (`46c5b6d`) | Implémenté, CI verte |
| B1 — extraction du runtime autoritaire (`f67e0da`) | Implémenté, CI verte |
| B2 — holder runtime process-wide (`153db0a`) | Implémenté, CI verte |
| B3 — accumulation depuis le foreground service (`cc088e3`, correctif test `28f421a`) | Implémenté, CI verte, **validation route écran éteint à faire** |

## 4. Validations réalisées

- **CI verte** (`testDebugUnitTest` + `assembleDebug`) sur le HEAD courant.
- **Validé device** : notification du foreground service ; persistance minimale (fermeture/réouverture) ; anti-dérive en **conditions bureau avec le filtre initial** (téléphone immobile, aucun mètre fantôme observé ; à re-tester après le correctif, cf. §5).
- **Validé device antérieurement** (cf. `runtime_gps_v0_1_status_2026_06_09.md`) : permission in-app, GPS réel, distance réelle accumulée en roulant, écran maintenu en session active.

## 5. Validation route du 2026-06-10 (première sortie)

- **B3 — accumulation écran éteint en roulant** : **première sortie effectuée**. L’accumulation hors premier plan a été observée comme fonctionnelle. À confirmer sur un second trajet et à formaliser dans un rapport de validation device dédié.
- **Filtre anti-dérive — test route réalisé** : **régression identifiée puis corrigée**. Sous-comptage ≈ 6,3 % (≈ 510 m sur 8,1 km) contre ≈ 1,2 % avant filtre. Cause : le plancher de mouvement basé sur `accuracyMeters` était appliqué inconditionnellement et écartait de vrais segments urbains lents (≈ 5–11 m à 1 Hz) passant sous l’incertitude GPS (≈ 8–15 m). **Correctif appliqué** : `fix(gps): avoid accuracy floor while moving` (HEAD `8530fa8`) — plancher régime-dépendant (vitesse présente → plancher de bruit seul ; vitesse absente → garde anti-dérive accuracy conservé). **Re-validation en attente** : re-test bureau (doit rester 0 m) **et** nouvelle sortie route (écart attendu ≈ 1,2 %).
- **Calibration réelle** : **toujours différée**. Ne pas appliquer de coefficient tant que la distance corrigée n’est pas jugée fiable sur un trajet de référence : un coefficient appliqué maintenant masquerait le comportement réel du filtre corrigé.

## 6. Limites connues

- **Pump UI encore présent** : au premier plan, le pump UI **et** la boucle service tournent. Le runtime étant unique (un seul `previousLocationSample`) et les ticks sérialisés sur le main thread, le trajet est pavé **une seule fois** → pas de double comptage ni de course. Le nettoyage / la neutralisation du pump est l’objet du **palier B4** (souhaitable).
- **`TripMeterUiEvent` traversant** : le service et le runtime échangent encore via `TripMeterUiEvent` ; un type `RuntimeEvent` pur reste différé.
- **`START_NOT_STICKY`** : si le système tue le process, le service n’est pas redémarré automatiquement (pas de `ACCESS_BACKGROUND_LOCATION`).
- **Robustesse écran éteint dépendante de l’OEM** : selon les optimisations batterie du constructeur, le foreground service peut être throttlé ; à observer en validation route.
- **UI finale / polish** : à faire.

## 7. Prochaines étapes recommandées

1. **Re-valider le correctif anti-dérive** : re-test bureau (doit rester 0 m) puis sortie route (écart attendu ≈ 1,2 %).
2. **Confirmer et documenter B3 écran éteint** : second trajet + rapport de validation device daté (pattern `*_validation_2026_*.md`).
3. **B4 — neutraliser le pump UI** (après 1 et 2) : faire du service l’unique moteur d’accumulation, avec resynchronisation du miroir VM à la reprise. Plan détaillé : [`runtime_gps_b4_pump_neutralization_plan_2026_06_10.md`](runtime_gps_b4_pump_neutralization_plan_2026_06_10.md).
4. **Calibration terrain** : seulement une fois la distance corrigée jugée fiable sur un trajet de référence.
5. **UI finale**.

## 8. Rappels

- Document **non normatif** ; les contrats `docs/contrats/` restent la référence.
- Cet état **ne décrit pas une application finalisée** ; en particulier, l’accumulation écran éteint a été **observée** mais n’a pas encore fait l’objet d’une **validation formelle documentée**, et le correctif anti-dérive **reste à re-valider** sur route.
