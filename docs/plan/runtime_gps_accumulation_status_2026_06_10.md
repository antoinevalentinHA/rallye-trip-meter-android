# Point d’étape — Accumulation hors lifecycle UI (post-B5)

> **Mise à jour 2026-06-11 (B4 validé device complet — un appareil)** : un **second trajet route** couvre les scénarios manquants — **S6 pause/reprise PASS** (distance figée en pause, reprise correcte) et **S7 terminer PASS** (session terminée, notification foreground retirée). Avec le scénario critique déjà PASS, **B4 est validé device complet sur un appareil**. Second trajet : voiture **7,80 km** / appli **7,65 km**, écart **−0,15 km** (≈ **−1,9 %**). Le sous-comptage est **cohérent autour de −1,8 %** sur les deux trajets, mais **insuffisant pour calibrer**. **Réserves** : **un seul appareil**, **calibration non validée**, filtre anti-dérive à confirmer sur davantage de trajets, **compteur voiture ≠ vérité absolue**. Rapport : [`b4_device_validation_complete_2026_06_11.md`](b4_device_validation_complete_2026_06_11.md).

> **Mise à jour 2026-06-11 (flux permissions Android)** : flux de permissions Android consigné — demande **notifications** (`POST_NOTIFICATIONS`, Android 13+) au lancement (`28cbe72`), et demande **position** désormais déclenchée à l'appui sur **START** si absente/refusée (plus de demande automatique fragile à l'entrée de la Route ; `4414fcc` puis `40a53a8`). **Validé device** (notifications demandées ; START sans permission → demande ; accord → session ; refus → pas de démarrage silencieux). Ces correctifs **ne touchent pas** GPS / service / accumulation / filtre / calibration. Détail : [`android_permission_flow_2026_06_11.md`](android_permission_flow_2026_06_11.md).

> **Mise à jour 2026-06-11 (validation device B4)** : **PASS device sur le scénario critique** de B4 (écran éteint → retour app → resynchronisation UI ; notification foreground visible ; distance finale cohérente). Trajet route : voiture **8,20 km** / appli **8,06 km**, écart **−0,14 km** (≈ **−1,7 %**). **Réserves** : **pause/reprise non testée**, **terminer non documenté**, un seul appareil. Rapport : [`b4_device_validation_2026_06_11.md`](b4_device_validation_2026_06_11.md). Calibration toujours bloquée.

> **Mise à jour 2026-06-11 (suite)** : modèle d’événement runtime **purifié** — retrait des événements UI-only `Options` / `RefreshLocationPermission` (`dc910c5`) ; le runtime ne contient plus que des événements à sens métier et reste **sans dépendance `ui.*` / `android.*`**. Mapping `TripMeterUiEvent → TripRuntimeEvent` couvert par un test direct (`125a112`). **Protocole de validation device de B4 prêt** (à exécuter) : [`b4_device_validation_protocol_2026_06_11.md`](b4_device_validation_protocol_2026_06_11.md). B4 reste **device-pending**.

> **Mise à jour 2026-06-11** : paliers **B4** (neutralisation du pump UI comme source d’accumulation — `d7d4a9f`) et **B5** (modèle d’événement runtime pur `TripRuntimeEvent`, runtime découplé de l’UI — `e7b858b`) **appliqués, CI verte**. **B4 n’est pas encore validé device** ; sa validation device est la **prochaine étape prioritaire** (cf. §7). B5 est un refactor pur (aucune validation device requise).

> **Mise à jour 2026-06-10** : trajet réel corrigé effectué après le correctif anti-dérive — voiture **17,80 km** / appli **17,56 km**, écart **0,24 km** (≈ **1,35 %**), **écran éteint** avec **pause/reprise** et distance cohérente. B3 écran éteint et correctif anti-dérive **validés sur ce trajet** (à confirmer sur d’autres). Calibration toujours bloquée. Voir §5 et [`road_validation_2026_06_10.md`](road_validation_2026_06_10.md).

## 1. Statut

- **Date** : 2026-06-10 (mise à jour 2026-06-11).
- **HEAD** : `e7b858b` (`refactor(runtime): introduce runtime event model`).
- **CI** : verte (`testDebugUnitTest` + `assembleDebug`).
- **Validation device** : partielle (cf. §4 et §5) ; **B4 non encore validé device** (cf. §6, §7).
- **Nature du document** : synthèse d’avancement, **non normative**. Il actualise le point d’étape précédent (`runtime_gps_v0_1_status_2026_06_09.md`), dont certaines limites (« aucune persistance », « pas de foreground service ») sont désormais levées, sans le remplacer. Les contrats `docs/contrats/` restent la référence.

## 2. Résumé exécutif

Depuis le point d’étape du 2026-06-09, le runtime GPS a franchi plusieurs chantiers structurants : persistance minimale de l’état, foreground service propriétaire du GPS (chantier GPS-OWN), correctif anti-dérive stationnaire, puis extraction et montée en puissance d’un runtime autoritaire (paliers B1 → B3).

Le `TripState` n’est plus possédé par le ViewModel mais par un `TripRuntime` autoritaire (B1), promu en instance unique à l’échelle du process (B2). À partir de B3, le foreground service consomme périodiquement les samples GPS et alimente ce runtime partagé : l’accumulation de distance n’est donc plus pilotée par le seul pump UI. C’est le premier palier qui ouvre la voie à l’accumulation **écran éteint / arrière-plan** — désormais **observée sur un trajet réel** (cf. §5).

Depuis, le pump UI a été **neutralisé** comme source d’accumulation (**B4** : le service est l’unique moteur, l’UI se resynchronise depuis le runtime), et le runtime a été **découplé de l’UI** via un modèle d’événement pur `TripRuntimeEvent` (**B5**). Ces deux paliers sont **appliqués et CI verts** ; **B4 reste à valider device**.

## 3. État architectural (B1 → B5, implémenté, CI verte)

- **Runtime autoritaire** : `TripRuntime` possède le `TripState` et applique contrôle, samples et persistance (B1).
- **Runtime process-wide** : `TripRuntimeHolder` fournit une instance unique partagée par le ViewModel et le service (B2). À la première construction l’état est restauré depuis le store ; les appels suivants réutilisent l’instance vivante (pas de réinitialisation à chaque recréation du ViewModel).
- **ViewModel = adaptateur / miroir UI** : il délègue au runtime, projette `uiState`, et conserve la permission de localisation et le pilotage du foreground service (`syncForegroundService`).
- **Service propriétaire du GPS** (GPS-OWN) : `Running` = GPS actif ; `Paused` / `Stopped` = GPS coupé.
- **Service consommateur des samples** (B3) : tant que le service tourne, une boucle postée sur le **main Looper** appelle `runtime.onEvent(ApplyLocationSample)` à ~1 s.
- **Persistance via le runtime** : le service **n’écrit jamais** directement dans le store ; la sauvegarde (throttle) est faite par le runtime.
- **UI = miroir resynchronisé** (B4) : le pump `repeatOnLifecycle(STARTED)` **ne pilote plus l’accumulation** ; il appelle `syncUiFromRuntime()` (lecture seule) pour resynchroniser le miroir Compose depuis le runtime. Le service est l’**unique moteur d’accumulation**. *Comportement device de B4 non encore validé (cf. §6, §7).*
- **Runtime découplé de l’UI** (B5) : le `TripRuntime` n’importe plus aucun type `ui.*` ; il consomme un `TripRuntimeEvent` pur. Le ViewModel traduit `TripMeterUiEvent → TripRuntimeEvent` ; le service appelle directement `runtime.onEvent(TripRuntimeEvent.ApplyLocationSample)`. Le modèle runtime a ensuite été **purifié** (`dc910c5`) : retrait des événements UI-only (`Options`, `RefreshLocationPermission`), désormais traités côté ViewModel ; le mapping UI→runtime est couvert par un test direct (`125a112`).

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
| B4 — neutralisation du pump UI comme source d’accumulation (`d7d4a9f`) | Implémenté, CI verte, **validé device complet sur un appareil** (2026-06-11, deux trajets : scénario critique écran éteint / retour / resynchro / notification + **S6 pause/reprise** + **S7 terminer**) ; **un seul appareil**, calibration non validée (cf. rapports et §6) |
| B5 — modèle d’événement runtime pur / découplage runtime–UI (`e7b858b`) | Implémenté, CI verte ; refactor pur (mapping 1:1 garanti par le compilateur), **sans validation device requise** |
| Mapping UI→runtime — test direct des 13 variantes (`125a112`) | Implémenté, CI verte (test-only) |
| Retrait des événements UI-only du modèle runtime (`dc910c5`) | Implémenté, CI verte ; comportement préservé (`Options` / `RefreshLocationPermission` traités côté ViewModel) |

## 4. Validations réalisées

- **CI verte** (`testDebugUnitTest` + `assembleDebug`) sur le HEAD courant.
- **Validé device** : notification du foreground service ; persistance minimale (fermeture/réouverture) ; anti-dérive en **conditions bureau avec le filtre initial** (téléphone immobile, aucun mètre fantôme observé ; re-test post-correctif en §5).
- **Validé device antérieurement** (cf. `runtime_gps_v0_1_status_2026_06_09.md`) : permission in-app, GPS réel, distance réelle accumulée en roulant, écran maintenu en session active.

## 5. Validation route (2026-06-10)

- **B3 — accumulation écran éteint en roulant** : **observée fonctionnelle sur un trajet réel**. Validation terrain nettement renforcée par le trajet corrigé du 2026-06-10 (**écran éteint**, **pause** au milieu puis **reprise**, **distance finale cohérente**). Confirmation sur d’autres trajets / appareils encore utile. Rapport : [`road_validation_2026_06_10.md`](road_validation_2026_06_10.md). *Note : ce trajet est **antérieur à B4** — il valide le mécanisme service (B3), pas le comportement premier plan propre à B4.*
- **Filtre anti-dérive — corrigé et vérifié sur route**. La régression de sous-comptage (≈ 6,3 %, ≈ 510 m / 8,1 km) a été corrigée par `fix(gps): avoid accuracy floor while moving` (HEAD `8530fa8`) — plancher régime-dépendant (vitesse présente → plancher de bruit seul ; vitesse absente → garde anti-dérive accuracy conservé). **Re-test bureau post-correctif** : ≈ 0,02 km / 5 min (nette amélioration, à surveiller). **Trajet réel corrigé** : voiture **17,80 km** / appli **17,56 km** → écart **0,24 km** (≈ **1,35 %**) ; le sous-comptage de 6,3 % **n’est plus observé**. Confirmation sur un autre trajet encore utile.
- **Calibration réelle** : **toujours différée**. L’écart théorique serait un coefficient ≈ 1,014, mais une mesure unique ne fait pas une vérité générale : attendre **plusieurs trajets de référence** avant tout coefficient.

## 6. Limites connues

- **B4 — validé device complet sur un appareil** : scénario critique (écran éteint, retour app, resynchro UI, notification, distance cohérente) + **S6 pause/reprise** + **S7 terminer**, sur **deux trajets** (2026-06-11 : [`b4_device_validation_2026_06_11.md`](b4_device_validation_2026_06_11.md) et [`b4_device_validation_complete_2026_06_11.md`](b4_device_validation_complete_2026_06_11.md)). **Non couvert** : **autres appareils** ; sous-comptage cohérent (≈ −1,8 %) mais **insuffisant pour calibrer**.
- **`START_NOT_STICKY`** : si le système tue le process, le service n’est pas redémarré automatiquement (pas de `ACCESS_BACKGROUND_LOCATION`).
- **Robustesse écran éteint dépendante de l’OEM** : selon les optimisations batterie du constructeur, le foreground service peut être throttlé ; observé fonctionnel sur l’appareil de test, à confirmer sur d’autres.
- **UI finale / polish** : à faire.

*Levées par B4/B5 : le double rôle du pump UI (B4 — le pump ne pilote plus l’accumulation) et le passage de `TripMeterUiEvent` vers le runtime (B5 — runtime sur `TripRuntimeEvent` pur).*

## 7. Prochaines étapes recommandées

1. **B4 validé device complet sur un appareil** (scénario critique + **S6** + **S7**, deux trajets 2026-06-11 : [`b4_device_validation_2026_06_11.md`](b4_device_validation_2026_06_11.md), [`b4_device_validation_complete_2026_06_11.md`](b4_device_validation_complete_2026_06_11.md)). **Reste à couvrir** : un **second appareil**. Protocole : [`b4_device_validation_protocol_2026_06_11.md`](b4_device_validation_protocol_2026_06_11.md).
2. **Confirmer le correctif anti-dérive sur d’autres trajets** : le trajet du 2026-06-10 donne ≈ 1,35 % (sous-comptage 6,3 % non reproduit) ; surveiller aussi le bureau (≈ 0,02 km / 5 min).
3. **Étendre la validation B3 écran éteint** (autres trajets, autres appareils). Rapport du 2026-06-10 : [`road_validation_2026_06_10.md`](road_validation_2026_06_10.md).
4. **Calibration terrain** : seulement après plusieurs trajets de référence cohérents (pas sur une mesure unique).
5. **UI finale**.

## 8. Rappels

- Document **non normatif** ; les contrats `docs/contrats/` restent la référence.
- **B4 et B5 appliqués, CI verte.** B4 est **validé device complet sur un appareil** (scénario critique + S6 pause/reprise + S7 terminer, deux trajets 2026-06-11) ; la validation reste limitée à **un appareil** et **sans calibration** : ne pas la présenter comme multi-appareils ni comme une précision certifiée.
- Cet état **ne décrit pas une application finalisée**. L’accumulation écran éteint a été **observée et validée sur un trajet réel** (écran éteint + pause/reprise, distance cohérente), **à confirmer** sur d’autres trajets et appareils ; le correctif anti-dérive est **efficace sur ce trajet** (≈ 1,35 %), une confirmation supplémentaire reste utile. Une mesure unique ne constitue pas une validation définitive.
