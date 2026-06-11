# Point d’étape — Accumulation hors lifecycle UI (post-B3)

> **Mise à jour 2026-06-10** : trajet réel corrigé effectué après le correctif anti-dérive — voiture **17,80 km** / appli **17,56 km**, écart **0,24 km** (≈ **1,35 %**), **écran éteint** avec **pause/reprise** et distance cohérente. B3 écran éteint et correctif anti-dérive **validés sur ce trajet** (à confirmer sur d’autres). Calibration toujours bloquée. Voir §5 et [`road_validation_2026_06_10.md`](road_validation_2026_06_10.md).

## 1. Statut

- **Date** : 2026-06-10.
- **HEAD** : `28f421a` (`feat(runtime): accumulate samples from foreground service`).
- **CI** : verte (`testDebugUnitTest` + `assembleDebug`), après correctif du test de non-duplication (`TripRuntimeTest`).
- **Validation device** : partielle (cf. §4 et §5).
- **Nature du document** : synthèse d’avancement, **non normative**. Il actualise le point d’étape précédent (`runtime_gps_v0_1_status_2026_06_09.md`), dont certaines limites (« aucune persistance », « pas de foreground service ») sont désormais levées, sans le remplacer. Les contrats `docs/contrats/` restent la référence.

## 2. Résumé exécutif

Depuis le point d’étape du 2026-06-09, le runtime GPS a franchi plusieurs chantiers structurants : persistance minimale de l’état, foreground service propriétaire du GPS (chantier GPS-OWN), correctif anti-dérive stationnaire, puis extraction et montée en puissance d’un runtime autoritaire (paliers B1 → B3).

Le `TripState` n’est plus possédé par le ViewModel mais par un `TripRuntime` autoritaire (B1), promu en instance unique à l’échelle du process (B2). À partir de B3, le foreground service consomme périodiquement les samples GPS et alimente ce runtime partagé : l’accumulation de distance n’est donc plus pilotée par le seul pump UI. C’est le premier palier qui ouvre la voie à l’accumulation **écran éteint / arrière-plan** — désormais **observée sur un trajet réel** (cf. §5).

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
| Correctif anti-dérive stationnaire (`3dd0cdd`) | Implémenté, CI verte, **validé bureau** (0 m fantôme) ; **test route fait** (voir §5) |
| GPS-OWN-4 — service seul pilote du GPS (`8c3b16c`) | Implémenté, CI verte |
| GPS-OWN-3 — GPS coupé en `Paused` (`46c5b6d`) | Implémenté, CI verte |
| B1 — extraction du runtime autoritaire (`f67e0da`) | Implémenté, CI verte |
| B2 — holder runtime process-wide (`153db0a`) | Implémenté, CI verte |
| B3 — accumulation depuis le foreground service (`cc088e3`, correctif test `28f421a`) | Implémenté, CI verte, **accumulation écran éteint observée sur 1 trajet réel** (écran éteint + pause/reprise ; voir §5) |
| Correctif filtre — plancher selon disponibilité de la vitesse (`8530fa8`) | Implémenté, CI verte, **vérifié sur route** (écart ≈ 1,35 % ; voir §5) |

## 4. Validations réalisées

- **CI verte** (`testDebugUnitTest` + `assembleDebug`) sur le HEAD courant.
- **Validé device** : notification du foreground service ; persistance minimale (fermeture/réouverture) ; anti-dérive en **conditions bureau avec le filtre initial** (téléphone immobile, aucun mètre fantôme observé ; re-test post-correctif en §5).
- **Validé device antérieurement** (cf. `runtime_gps_v0_1_status_2026_06_09.md`) : permission in-app, GPS réel, distance réelle accumulée en roulant, écran maintenu en session active.

## 5. Validation route (2026-06-10)

- **B3 — accumulation écran éteint en roulant** : **observée fonctionnelle sur un trajet réel**. Validation terrain nettement renforcée par le trajet corrigé du 2026-06-10 (**écran éteint**, **pause** au milieu puis **reprise**, **distance finale cohérente**). Confirmation sur d’autres trajets / appareils encore utile. Rapport : [`road_validation_2026_06_10.md`](road_validation_2026_06_10.md).
- **Filtre anti-dérive — corrigé et vérifié sur route**. La régression de sous-comptage (≈ 6,3 %, ≈ 510 m / 8,1 km) a été corrigée par `fix(gps): avoid accuracy floor while moving` (HEAD `8530fa8`) — plancher régime-dépendant (vitesse présente → plancher de bruit seul ; vitesse absente → garde anti-dérive accuracy conservé). **Re-test bureau post-correctif** : ≈ 0,02 km / 5 min (nette amélioration, à surveiller). **Trajet réel corrigé** : voiture **17,80 km** / appli **17,56 km** → écart **0,24 km** (≈ **1,35 %**) ; le sous-comptage de 6,3 % **n’est plus observé**. Confirmation sur un autre trajet encore utile.
- **Calibration réelle** : **toujours différée**. L’écart théorique serait un coefficient ≈ 1,014, mais une mesure unique ne fait pas une vérité générale : attendre **plusieurs trajets de référence** avant tout coefficient.

## 6. Limites connues

- **Pump UI encore présent** : au premier plan, le pump UI **et** la boucle service tournent. Le runtime étant unique (un seul `previousLocationSample`) et les ticks sérialisés sur le main thread, le trajet est pavé **une seule fois** → pas de double comptage ni de course. Le nettoyage / la neutralisation du pump est l’objet du **palier B4** (souhaitable).
- **`TripMeterUiEvent` traversant** : le service et le runtime échangent encore via `TripMeterUiEvent` ; un type `RuntimeEvent` pur reste différé.
- **`START_NOT_STICKY`** : si le système tue le process, le service n’est pas redémarré automatiquement (pas de `ACCESS_BACKGROUND_LOCATION`).
- **Robustesse écran éteint dépendante de l’OEM** : selon les optimisations batterie du constructeur, le foreground service peut être throttlé ; observé fonctionnel sur l’appareil de test, à confirmer sur d’autres.
- **UI finale / polish** : à faire.

## 7. Prochaines étapes recommandées

1. **Confirmer le correctif anti-dérive sur d’autres trajets** : le trajet du 2026-06-10 donne ≈ 1,35 % (sous-comptage 6,3 % non reproduit) ; surveiller aussi le bureau (≈ 0,02 km / 5 min).
2. **Étendre la validation B3 écran éteint** (autres trajets, autres appareils). Rapport du 2026-06-10 : [`road_validation_2026_06_10.md`](road_validation_2026_06_10.md).
3. **B4 — neutraliser le pump UI** : faire du service l’unique moteur d’accumulation, avec resynchronisation du miroir VM à la reprise. Reste souhaitable, désormais conforté par un résultat terrain solide du service comme moteur d’accumulation. Plan détaillé : [`runtime_gps_b4_pump_neutralization_plan_2026_06_10.md`](runtime_gps_b4_pump_neutralization_plan_2026_06_10.md).
4. **Calibration terrain** : seulement après plusieurs trajets de référence cohérents (pas sur une mesure unique).
5. **UI finale**.

## 8. Rappels

- Document **non normatif** ; les contrats `docs/contrats/` restent la référence.
- Cet état **ne décrit pas une application finalisée**. L’accumulation écran éteint a été **observée et validée sur un trajet réel** (écran éteint + pause/reprise, distance cohérente), **à confirmer** sur d’autres trajets et appareils ; le correctif anti-dérive est **efficace sur ce trajet** (≈ 1,35 %), une confirmation supplémentaire reste utile. Une mesure unique ne constitue pas une validation définitive.
