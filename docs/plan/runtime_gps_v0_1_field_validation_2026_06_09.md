# Validation terrain — Runtime GPS v0.1

## 1. Statut

- **Date de validation** : 2026-06-09.
- **HEAD de référence** : `4ff17fe` (`refactor(ui): hide gps test control from main screen`).
- **CI** : verte (`testDebugUnitTest` + `assembleDebug`).
- **Nature du document** : retour de terrain, factuel et **non normatif**. Il complète le point d’étape (`docs/plan/runtime_gps_v0_1_status_2026_06_09.md`) sans le remplacer ; les contrats `docs/contrats/` restent la référence.

Ce compte rendu ne contient volontairement aucune métrique chiffrée : la validation a été qualitative (observation), non instrumentée.

## 2. Résultats validés

- **Arrêt immobile** : aucune dérive visible des compteurs moteur éteint.
- **Trajet voiture** : concluant.
- **Distance affichée** : nette et cohérente sur le trajet observé.
- **Sauts GPS** : aucun saut observé.
- **Chaîne `TEST GPS`** : vérifiée comme fonctionnelle **avant** son retrait de l’interface, ce qui confirme que le filtrage bruit/sauts n’avait pas cassé le mécanisme de simulation.
- **Bouton `TEST GPS`** : retiré ensuite de l’écran principal (commit `4ff17fe`), le mécanisme debug (`SimulateLocationStep`, handler ViewModel, tests) étant conservé dans le code.

## 3. Interprétation

- Le filtrage MVP (bruit à l’arrêt, rejet des sauts implausibles) est **validé en première approche terrain**.
- Sur le point critique de la dérive à l’arrêt, le comportement observé est rapporté comme supérieur à celui de l’application publique précédemment utilisée.
- Le socle runtime GPS v0.1 peut être considéré comme **validé pour passer au palier suivant**.

## 4. Limites

- Validation **non instrumentée** (observation, sans mesure chiffrée enregistrée).
- Pas encore de **coefficient de calibration réel** (le mécanisme existe, valeur identité par défaut).
- Pas de **test long multi-trajets**.
- Pas de validation en **conditions difficiles prolongées** (tunnels, urbain dense, météo, longue durée).
- Pas encore de **persistance** ni de **foreground service** (acquisition arrêtée en arrière-plan / écran verrouillé).

## 5. Prochaine étape recommandée

- **Calibration réelle du coefficient** à partir d’un trajet de référence mesuré (distance connue), afin de fixer une valeur crédible plutôt que l’identité.
- À défaut, mettre en place un **protocole de validation terrain plus formel** (trajets répétés, conditions variées) avant d’activer le coefficient.
