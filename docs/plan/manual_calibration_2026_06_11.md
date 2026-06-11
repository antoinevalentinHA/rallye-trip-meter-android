# Jalon — Calibration manuelle (2026-06-11)

> **Statut : calibration manuelle de distance livrée et utilisable.** Jalon **non normatif** (les contrats `docs/contrats/` restent la référence). Ce document **n'introduit aucun code** et **ne crée aucun tag Git**.

## 1. Statut du jalon

- **Date** : 2026-06-11.
- **HEAD au moment du jalon** : `393cc69` (`feat(ui): show calibration indicator when active`).
- **CI / Gradle local** : verts (`testDebugUnitTest` + `assembleDebug`).
- **Nature** : jalon **non normatif** ; acte une fonctionnalité **livrée**, pas une précision certifiée.

## 2. Ce qui est livré (paliers 1 → 4)

- **Coefficient + persistance** (`769fcfd`) : `CalibrationCoefficient` en **millièmes** (entier, sans dérive flottante), défaut **1.000**, bornes **0.900–1.100**, pas **±0.001 / ±0.010** ; store dédié SharedPreferences (`rallye_trip_meter_calibration`), écrivain unique, valeur absente → 1.000.
- **Application affichage** (`48d1376`) : le mapper applique le coefficient à **PARTIEL + TOTAL uniquement** ; vitesse / précision / statut GPS / session inchangés ; défaut 1.000 = identité.
- **Dialogue** (`6244920`) : **Options → Calibration** → réglage **±0.001 / ±0.010**, **Réinitialiser**, **Fermer** ; chaque modification **persistée**, affichage recalculé immédiatement.
- **Indicateur** (`393cc69`) : `CAL x.xxx` discret en haut d'écran **uniquement quand le coefficient ≠ 1.000** ; disparaît à 1.000.

## 3. Architecture / garanties

- **Couche affichage uniquement** : distance brute (`TripState`) **intacte** ; le **runtime**, le **moteur GPS**, le **service foreground** et le **filtre anti-dérive** ne connaissent **pas** le coefficient.
- **Manuel, explicite, réversible** : aucune correction cachée, aucune calibration automatique, retour à 1.000 toujours possible.
- Coefficient = **configuration**, séparé de la persistance du trip state ; **pas** de second propriétaire du `TripState`.

## 4. Limites connues

- Le coefficient est **réglé par l'utilisateur** depuis sa propre référence ; l'application n'en déduit aucune valeur.
- **Données terrain insuffisantes** pour figer un défaut : 3 trajets, sous-comptage ≈ **−1,7 % / −2,0 % / −2,6 %** (compteur voiture **non** vérité absolue).
- La calibration **ne corrige pas** le moteur GPS ; la dérive à l'arrêt reste un **chantier distinct**.

## 5. Ce qui ne doit pas être affirmé

- Pas de **précision certifiée** : il s'agit d'une **calibration manuelle**, pas d'une garantie métrologique.
- Le coefficient n'« améliore » pas la mesure brute ; il **met à l'échelle l'affichage** selon une référence choisie par l'utilisateur.

## 6. Suite

- **GPS anti-dérive à l'arrêt** (objectif : « canapé ≠ 130 m ») : chantier **séparé**, touchant le **filtre** — uniquement sur **preuve terrain**, sans modifier la calibration.
