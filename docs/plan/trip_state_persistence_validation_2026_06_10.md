# Validation device — Persistance minimale du tripmeter

## 1. Statut

- **Date de validation** : 2026-06-10.
- **HEAD de référence** : `8cc0d7a` (`feat(persistence): store trip state with shared preferences`).
- **CI** : verte (`testDebugUnitTest` + `assembleDebug`).
- **Nature du document** : retour de validation device, factuel et **non normatif**. Il complète les documents de plan existants sans les remplacer ; les contrats `docs/contrats/` restent la référence.

Ce compte rendu ne contient aucune métrique chiffrée : la validation a été qualitative (observation), non instrumentée.

## 2. Résultats validés

- APK issue de GitHub Actions **téléchargée et installée sur Pixel**.
- Persistance **SharedPreferences réelle** testée.
- Après fermeture puis réouverture de l’application :
  - **`totalDistanceMeters`** restauré ;
  - **`partialDistanceMeters`** restauré ;
  - **`sessionState`** restauré.
- **Aucun saut fantôme GPS** signalé au redémarrage.
- Les champs GPS éphémères (statut, précision, vitesse) ne sont pas persistés.

## 3. Interprétation

- La persistance minimale est **validée en première approche device**.
- L’application **ne perd plus son état principal** (total, partiel, état de session) lors d’une fermeture / réouverture.
- Le choix de **ne pas persister les champs GPS éphémères** est confirmé : ils se reconstruisent au premier fix, sans bond de distance au redémarrage.

## 4. Limites

- Validation **courte et non instrumentée** (observation, sans mesure enregistrée).
- Pas de **test longue durée**.
- Pas de **sauvegarde périodique** pendant une longue session au premier plan : un arrêt brutal sans passage en arrière-plan pourrait perdre l’accumulation depuis le dernier point de sauvegarde.
- Pas de **foreground service** : l’acquisition s’arrête en arrière-plan / écran verrouillé.
- Pas encore de **stratégie de reprise avancée** après arrêt brutal (péremption, cohérence temporelle).

## 5. Prochaine étape possible

- **Sauvegarde périodique throttlée** pendant la session active (filet pour les longues sessions au premier plan), sans écrire à chaque tick GPS.
- Ou **protocole / validation plus longue** (sessions répétées, conditions variées).
- Ou **passer à un autre chantier prioritaire** selon les besoins du moment.
