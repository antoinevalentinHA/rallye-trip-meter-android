# Jalon — Version utilisable en rallye simple (2026-06-11)

> **Statut : version proche utilisable en conditions simples de rallye / roadbook.** Jalon de fin de journée, **non normatif** (les contrats `docs/contrats/` restent la référence). Ce document **n'introduit aucun code** et **ne crée aucun tag Git**.

## 1. Statut du jalon

- **Date** : 2026-06-11.
- **HEAD au moment du jalon** : `2b61c5b` (`feat(ui): confirm before ending session`).
- **CI / Gradle local** : verts (`testDebugUnitTest` + `assembleDebug`) après les derniers paliers.
- **Nature** : point de jalon **non normatif**. Il acte un **état utilisable**, pas une application finalisée.

## 2. Résumé de ce qui est validé

- **Moteur d'accumulation validé device sur un appareil** (B4 complet) : écran éteint, retour app / resynchronisation UI, **pause/reprise**, **terminer**, sur deux trajets réels. Rapports : [`b4_device_validation_2026_06_11.md`](b4_device_validation_2026_06_11.md), [`b4_device_validation_complete_2026_06_11.md`](b4_device_validation_complete_2026_06_11.md).
- **Service foreground fonctionnel** : notification visible en session, **retirée après Terminer**.
- **Permissions Android corrigées** : notifications demandées au lancement (Android 13+), position demandée **sur START** si absente/refusée. Détail : [`android_permission_flow_2026_06_11.md`](android_permission_flow_2026_06_11.md).
- **Précision GPS affichée** dans l'UI (`feat(ui): display GPS accuracy`, `7b15bc9`).
- **Action « Terminer » protégée par confirmation** (`feat(ui): confirm before ending session`, `2b61c5b`).
- **Architecture runtime propre** : runtime et UI découplés (runtime sur événements purs, sans dépendance `ui.*` / `android.*`) ; **le service est l'unique moteur d'accumulation**, l'UI est un miroir resynchronisé.
- **Écarts terrain** : trajet 1 voiture **8,20** / appli **8,06 km** (≈ **−1,7 %**) ; trajet 2 voiture **7,80** / appli **7,65 km** (≈ **−1,9 %**). Sous-comptage **cohérent autour de −1,8 %**, **insuffisant pour calibrer**.

## 3. Fonctionnalités utilisables aujourd'hui

- **PARTIEL** (mis en avant) **remis à zéro** à chaque note, avec **ajustement ±10 / ±100 m** pour resynchroniser au roadbook.
- **TOTAL**, **VITESSE**, **précision GPS (±N m)**, et barre de statut **GPS / POSITION / SESSION**.
- **Contrôle de session** : **START / PAUSE / REPRISE**, **Terminer** (confirmé), **Réinitialiser total** (confirmé), **Nouveau parcours** (confirmé).
- **Écran maintenu allumé** en session active ; **accumulation écran éteint** en arrière-plan.

## 4. Limites connues

- Validation device sur **un seul appareil**.
- **Calibration non validée** ; aucun coefficient activé.
- Sous-comptage ≈ −1,8 % **observé mais non certifié** ; à confirmer sur davantage de trajets.
- **Filtre anti-dérive** inchangé, à confirmer sur d'autres trajets.
- **Robustesse OEM non exhaustive** (optimisations batterie constructeur) ; `START_NOT_STICKY` (pas de redémarrage auto si le process est tué).
- **UI non finalisée** (pas de polish complet).
- **Mode paysage** : disposition deux colonnes **réalisée et validée sur appareil**
  (PARTIEL dominant à gauche ; TOTAL/VITESSE, StatusBar, corrections et bouton de
  session START/PAUSE/REPRISE/TERMINER à droite, **visibles sans scroll**). Le mode
  portrait reste préservé. Détail : [`reprises/cloture_ui_paysage_2026_06_14.md`](reprises/cloture_ui_paysage_2026_06_14.md).

## 5. Hors périmètre (inchangé)

- Calibration active / coefficient ; modification du moteur GPS, du filtre, du foreground service lifecycle, de l'accumulation.
- Refonte d'architecture ; second propriétaire du `TripState` ; `ACCESS_BACKGROUND_LOCATION`.
- Validation multi-appareils.
- **Publication Play Store** : build release signé, fiche store, et — du fait de la localisation + foreground service location — **politique de confidentialité** et **déclaration d'usage de la localisation** ; non réalisé.
- **Android Auto** : nécessiterait la **Car App Library** (`CarAppService` + templates imposés) et la **revue anti-distraction** de Google ; chantier séparé non engagé (l'UI Compose actuelle ne s'y exécute pas telle quelle).

## 6. Ce qui ne doit pas être affirmé

- **Aucune calibration validée**.
- **Aucune précision certifiée**.
- **Aucune validation multi-appareils**.
- **Aucune robustesse OEM exhaustive**.
- **Aucune version Play Store**.
- **Aucune finalisation UI complète**.
- **Aucun tag Git** créé automatiquement par ce jalon.

## 7. Prochaines étapes recommandées

1. **Confirmer le comportement sur davantage de trajets** (anti-dérive, écart ≈ −1,8 %).
2. **Étendre la validation à un second appareil**.
3. **Calibration** : seulement **après plusieurs trajets de référence cohérents** — pas maintenant.
4. **Finition UI** selon l'usage réel.
5. **Tag de version** : possible **manuellement** plus tard si pertinent ; **non créé** par ce jalon.

## 8. Liens

- Point d'étape central : [`runtime_gps_accumulation_status_2026_06_10.md`](runtime_gps_accumulation_status_2026_06_10.md).
- Validation device B4 : [`b4_device_validation_2026_06_11.md`](b4_device_validation_2026_06_11.md), [`b4_device_validation_complete_2026_06_11.md`](b4_device_validation_complete_2026_06_11.md).
- Flux de permissions Android : [`android_permission_flow_2026_06_11.md`](android_permission_flow_2026_06_11.md).
- Protocole de validation device B4 : [`b4_device_validation_protocol_2026_06_11.md`](b4_device_validation_protocol_2026_06_11.md).
