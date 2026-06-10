# Protocole de calibration — Runtime GPS v0.1

## 1. Statut

- **HEAD de référence** : `8876e03`.
- **Nature** : document pratique, utilisable sur le terrain, **non normatif**. Les contrats `docs/contrats/` restent la référence.
- **État du mécanisme** : `DistanceTripProgressEngine` expose `calibrationFactor: Double = 1.0` (identité). Aucune valeur réelle n’est définie : ce protocole sert à en mesurer une.

## 2. Objectif

Le coefficient de calibration corrige l’écart systématique entre la distance GPS accumulée par l’application et une distance de référence mesurée sur un trajet connu. Il s’applique uniquement aux distances issues du GPS (les corrections manuelles ±10/±100 m ne sont pas recalibrées). Le but de ce protocole est de **mesurer** ce coefficient, pas de le supposer.

## 3. Principe de calcul

```
calibrationFactor = distance_reelle / distance_app
```

- `distance_reelle` : distance de référence du trajet (borne kilométrique, plan cadastré, autre traceur fiable, etc.).
- `distance_app` : distance affichée par l’application en fin de trajet (compteur total ou partiel selon le relevé).

Exemple illustratif (valeurs fictives, non mesurées) : si `distance_reelle = 10 000 m` et `distance_app = 9 800 m`, alors `calibrationFactor = 10 000 / 9 800 ≈ 1,020`. Un coefficient > 1 signifie que l’application sous-estime ; < 1 qu’elle surestime. Ce document ne fixe aucune valeur réelle.

## 4. Conditions de test recommandées

- Utiliser un **trajet de référence connu ou mesuré** (distance fiable).
- Pour le premier essai, **éviter tunnels, parkings couverts et zones très masquées**.
- **Démarrer à l’arrêt**, GPS stable (`GPS OK`).
- Passer la session en **`ACTIF` avant le départ**.
- **Ne pas utiliser les corrections manuelles** (±10/±100, RAZ partiel) pendant la mesure : elles fausseraient le rapport.
- **Noter la distance app finale** à l’arrivée, avant toute manipulation.
- **Répéter** la mesure si possible (plusieurs essais sur le même trajet de référence).

## 5. Fiche de relevé terrain

| Essai | Distance réelle | Distance app | Écart | Coefficient calculé | Conditions / remarques |
|------:|-----------------|--------------|-------|---------------------|------------------------|
| 1 |  |  |  |  |  |
| 2 |  |  |  |  |  |
| 3 |  |  |  |  |  |

- **Écart** = `distance_reelle − distance_app`.
- **Coefficient calculé** = `distance_reelle / distance_app`.

## 6. Critères d’acceptation

- Aucune dérive des compteurs à l’arrêt pendant la session.
- Aucun saut GPS observé sur le trajet.
- Écart **stable** entre les essais (les coefficients calculés sont proches les uns des autres).
- Coefficient **cohérent et proche de 1.0** (un écart important suggère un problème de mesure ou de conditions, pas une simple calibration).
- **Ne pas activer** de coefficient si les mesures sont contradictoires ou trop dispersées.

## 7. Prochaine étape après mesure

Selon la qualité des relevés, le palier suivant pourra :

- **injecter une valeur fixe temporaire** dans la construction du moteur (coefficient figé issu de la moyenne des essais) ;
- **rendre le coefficient réglable** via l’état / l’UI (saisie utilisateur, ajustable sans recompilation) ;
- **reporter l’activation** si les mesures terrain sont insuffisantes ou incohérentes, et compléter d’abord le protocole (essais supplémentaires, conditions variées).

Tant qu’aucune valeur n’est validée, le coefficient reste à l’identité (`1.0`) et l’application accumule la distance GPS brute filtrée.
