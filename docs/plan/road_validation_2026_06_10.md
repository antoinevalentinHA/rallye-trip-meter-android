# Rapport de validation route — Trip meter (2026-06-10)

## 1. Statut

- **Date** : 2026-06-10.
- **HEAD de référence** : `9075592`, après le correctif `fix(gps): avoid accuracy floor while moving` (`8530fa8`).
- **Nature** : rapport de validation device, **non normatif**. Les contrats `docs/contrats/` restent la référence.
- **Portée** : **un seul trajet réel**, sur l'appareil de test. Résultat encourageant, **à confirmer** sur d'autres trajets et d'autres appareils. Une mesure unique ne constitue pas une validation définitive.

## 2. Mesure

- Compteur voiture : **17,80 km**.
- Trip meter (appli) : **17,56 km**.
- Écart : **0,24 km**, soit ≈ **1,35 %**.

## 3. Conditions du trajet

- **Écran éteint** pendant le trajet.
- **Pause** au milieu du trajet, puis **reprise**.
- **Distance finale cohérente**.

## 4. Interprétation (prudente)

- **B3 — accumulation écran éteint** : observée **fonctionnelle sur un trajet réel**. La validation terrain est nettement renforcée par rapport à la première sortie, mais elle reste limitée à **un trajet** sur **un appareil** — ce n'est pas une preuve multi-appareils.
- **Pause / reprise** : observées **fonctionnelles** en conditions réelles (pas de perte ni de double comptage à la reprise, distance finale cohérente).
- **Correctif anti-dérive** : **efficace sur ce trajet**. Le sous-comptage structurel précédent (≈ 6,3 % sur la sortie d'avant correctif) **n'est plus observé** ; l'écart est redevenu faible (≈ 1,35 %, proche du ≈ 1,2 % constaté avant l'introduction du filtre). Une **confirmation sur un autre trajet** reste utile.
- **Re-test bureau post-correctif** (rappel) : ≈ **0,02 km / 5 min** téléphone posé — nette amélioration vs le footing de bureau initial, **à surveiller**.

## 5. Calibration — toujours bloquée

- L'écart théorique de ce trajet serait un coefficient ≈ **1,014**, mais **il ne faut pas le transformer en réglage** : une mesure unique ne fait pas une vérité générale. Attendre **plusieurs trajets de référence cohérents** avant d'envisager un coefficient.

## 6. Portée et limites

- **Validé sur un trajet réel** sur l'appareil de test — **pas** « validé définitivement sur tous appareils ».
- Robustesse écran éteint **dépendante des optimisations batterie OEM** : à confirmer sur d'autres appareils.
- **B4** (neutralisation du pump UI) reste **souhaitable** et **différé** ; le service comme moteur d'accumulation dispose désormais d'un **résultat terrain solide**.

## 7. Rappels

- Document **non normatif** ; les contrats `docs/contrats/` restent la référence.
- Ce rapport **ne décrit pas une application finalisée** et ne vaut **pas** validation multi-appareils.
