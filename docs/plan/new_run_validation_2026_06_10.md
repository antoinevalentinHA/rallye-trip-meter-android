# Validation device — Nouveau parcours

## 1. Statut

- **Date de validation** : 2026-06-10.
- **HEAD de référence** : `0c8d967` (`feat(ui): expose new run in options menu`).
- **CI** : verte (`testDebugUnitTest` + `assembleDebug`).
- **Nature du document** : retour de validation device, factuel et **non normatif**. Il complète les documents de plan existants sans les remplacer ; les contrats `docs/contrats/` restent la référence.

Ce compte rendu ne contient aucune métrique chiffrée : la validation a été qualitative (observation).

Cette validation porte **uniquement** sur : l'action `Nouveau parcours`, sa confirmation UI, la remise à zéro total + partiel, la conservation de la session, l'absence d'impact foreground service, et la persistance après fermeture / réouverture.

## 2. Sémantique validée

- `Nouveau parcours` = action de **gestion** (dans le menu secondaire).
- Total **remis à zéro**.
- Partiel **remis à zéro**.
- État de session **conservé** (`Stopped` / `Running` / `Paused` inchangé).
- GPS **non touché**.
- Foreground service / notification **non touchés**.
- **Persistance** assurée (les deux compteurs à zéro sont durables).

## 3. Résultats validés

- APK issue de GitHub Actions **installée sur Pixel**.
- Menu `⋮` **accessible**.
- Entrée `Nouveau parcours` **active**.
- Clic sur `Nouveau parcours` → **ouverture d'une confirmation**.
- `Annuler` (ou tap hors du dialogue) **ne modifie rien**.
- Confirmation `Nouveau parcours` → **total = 0**.
- Confirmation `Nouveau parcours` → **partiel = 0**.
- État de session **inchangé**.
- Notification / foreground service **inchangés** si la session reste active.
- Fermeture / réouverture de l'application : **total et partiel restent à zéro**.

## 4. Distinction avec les autres actions

- `RAZ PARTIEL` : remet **seulement le partiel** à zéro (action de conduite).
- `Réinitialiser total` : remet **seulement le total** à zéro et **conserve le partiel** (gestion, avec confirmation).
- `Nouveau parcours` : remet **les deux compteurs** à zéro, session conservée (gestion, avec confirmation).
- `Terminer la session` : **conserve les distances** et **arrête la session** (coupe le service / notification).

## 5. Limites

- Calibration **non implémentée**.
- **GPS arrière-plan non implémenté**.
- **Accumulation arrière-plan non validée**.
- Style final du menu encore perfectible.

## 6. Prochaine étape possible

- Revenir au chantier foreground service **propriétaire de l'acquisition GPS**.
- Ou améliorer l'UI de conduite.
- Ou travailler la calibration quand des mesures terrain fiables seront disponibles.
