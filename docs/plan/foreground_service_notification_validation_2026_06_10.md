# Validation device — Foreground service notification

## 1. Statut

- **Date de validation** : 2026-06-10.
- **HEAD de référence** : `26837d5` (`feat(service): start foreground service for active session`).
- **CI** : verte (`testDebugUnitTest` + `assembleDebug`).
- **Nature du document** : retour de validation device, factuel et **non normatif**. Il complète les documents de plan existants sans les remplacer ; les contrats `docs/contrats/` restent la référence.

Ce compte rendu ne contient aucune métrique chiffrée : la validation a été qualitative (observation).

## 2. Résultats validés

- APK issue de GitHub Actions **installée sur Pixel**.
- Permission de notifications **activée manuellement** côté réglages Android pour l’application.
- **Notification visible** au démarrage d’une session active.
- Texte affiché : **`Service GPS prêt`**.
- Tap sur la notification → **retour à l’application**.
- En **`PAUSE`**, la notification **reste affichée**.
- Au **`STOP`**, la notification **disparaît**.
- **Aucun crash `startForeground`** observé.
- **Small icon corrigée** (drawable monochrome dédié) affichée correctement.

## 3. Interprétation

- Le socle **foreground service + notification** est validé en première approche device.
- Le service peut être **démarré / arrêté sur le cycle de session** (démarrage à l’activation, arrêt au `STOP`, maintien en `PAUSE`).
- La **brique notification est prête** pour le futur chantier d’acquisition.

## 4. Limites

- La permission `POST_NOTIFICATIONS` **n’est pas encore demandée au runtime** par l’application (ici activée manuellement côté Android).
- Le service **ne possède pas encore l’acquisition GPS**.
- **Aucune accumulation en arrière-plan** n’est validée.
- Pas encore de **propriétaire unique de l’acquisition**.
- **Aucun test écran verrouillé / acquisition GPS en arrière-plan**.

## 5. Prochaine étape possible

- Faire du service le **propriétaire de l’acquisition GPS** (déplacer l’abonnement hors de la Route, éviter la double acquisition).
- Ou **cadrer la demande runtime `POST_NOTIFICATIONS`** avant d’aller plus loin.
- Ou **différer le foreground service** et revenir à un chantier métier / UI.
