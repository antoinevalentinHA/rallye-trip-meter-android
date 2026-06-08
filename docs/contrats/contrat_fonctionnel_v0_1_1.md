# Trip Meter Rallye Android — Contrat fonctionnel v0.1

Statut : VALIDÉ  
Objet : application Android de trip meter rallye  
Priorité : affichage fiable du kilométrage total, du kilométrage étape et de la vitesse instantanée.

---

## 1. Décision v0.1

L’application est un instrument de mesure embarqué, inspiré des trip meters / Terratrip.

Elle n’est pas :

- une application de navigation ;
- une carte GPS ;
- un roadbook numérique ;
- un outil de classement ;
- un système cloud.

---

## 2. Rôle de l’application

L’application sert à afficher en temps réel trois informations principales pendant un rallye automobile :

- kilométrage total ;
- kilométrage étape ;
- vitesse instantanée.

Elle doit être utilisable en situation de conduite/copilotage, avec une lecture immédiate, sans interaction complexe, et avec une tolérance correcte aux pertes temporaires de signal GPS.

---

## 3. Définitions métier

### 3.1 Kilométrage total

Distance cumulée depuis le départ global du rallye ou de la session.

Exemple :

```text
TOTAL : 124.37 km
```

Ce compteur représente la progression générale. Il ne se remet pas à zéro à chaque étape, sauf action explicite de l’utilisateur.

### 3.2 Kilométrage étape

Distance cumulée depuis le début de l’étape courante.

Exemple :

```text
ÉTAPE : 12.84 km
```

Ce compteur doit pouvoir être remis à zéro rapidement au départ d’une spéciale, d’une liaison ou d’un secteur.

### 3.3 Vitesse instantanée

Vitesse actuelle du véhicule, calculée prioritairement à partir du GPS.

Exemple :

```text
VITESSE : 76 km/h
```

Elle doit être lisible, mais elle n’est pas forcément l’information la plus critique. En rallye, le kilométrage prime.

---

## 4. Périmètre validé

### 4.1 Affichage principal

L’écran principal affiche :

- kilométrage total ;
- kilométrage étape ;
- vitesse instantanée ;
- statut GPS minimal.

### 4.2 Source de mesure

La v0.1 utilise le GPS Android comme source unique de distance.

La distance affichée est une distance validée, pas une addition naïve de points GPS.

### 4.3 Commandes utilisateur

Commandes minimales validées :

- démarrer session ;
- pause session ;
- reprendre session ;
- arrêter session ;
- reset étape ;
- correction étape +10 m ;
- correction étape -10 m ;
- correction étape +100 m ;
- correction étape -100 m ;
- correction total ;
- reset total avec confirmation.

---

## 5. Modes de fonctionnement

### 5.1 Mode Session

États globaux de l’application :

- session arrêtée ;
- session active ;
- session en pause.

Effets attendus :

| Mode | Total | Étape | Vitesse |
|---|---:|---:|---:|
| Arrêtée | figé | figé | 0 ou dernière valeur |
| Active | incrémente | incrémente | active |
| Pause | figé | figé | affichée ou figée selon choix |

Point important : pause ne doit pas effacer les compteurs.

### 5.2 Mode Étape

Gestion du compteur partiel :

- étape inactive ;
- étape active ;
- étape remise à zéro.

Actions minimales :

- démarrer étape ;
- réinitialiser étape ;
- corriger kilométrage étape.

### 5.3 Mode Correction

L’application doit permettre une correction manuelle.

Actions prévues :

- +10 m ;
- -10 m ;
- +100 m ;
- -100 m ;
- définir distance étape exacte ;
- définir distance totale exacte.

Sans correction manuelle, l’application serait un compteur GPS décoratif, pas un vrai instrument de rallye.

---

## 6. Invariants validés

- Le kilométrage total ne diminue jamais automatiquement.
- Le reset étape ne modifie jamais le total.
- Toute correction est explicite.
- Les points GPS incohérents sont ignorés.
- Une session active est sauvegardée automatiquement.
- L’écran reste allumé pendant une session active.
- L’affichage doit rester lisible, stable et sobre.

---

## 7. Sources de distance

### 7.1 Version 1 — GPS uniquement

Avantages :

- simple ;
- aucun matériel externe ;
- suffisant pour prototype.

Limites :

- moins précis qu’un vrai Terratrip ;
- mauvais en tunnels, forêts, vallées, zones urbaines ;
- sensibilité aux arrêts et aux sauts GPS.

### 7.2 Version 2 — GPS + calibration

L’utilisateur peut appliquer un coefficient correcteur.

```text
distance_affichée = distance_gps × coefficient_calibration
```

Exemple :

```text
GPS mesure 9.850 km
Roadbook indique 10.000 km
Coefficient = 10.000 / 9.850 = 1.01523
```

C’est le bon compromis pour une première version sérieuse.

### 7.3 Version 3 — Capteur roue / OBD / Bluetooth

Plus proche d’un vrai trip meter, mais hors périmètre initial.

L’architecture doit seulement permettre d’abstraire la source de distance plus tard :

```text
DistanceProvider
  - GPS
  - OBD
  - capteur externe
```

---

## 8. Précision et filtrage GPS

Le contrat doit prévoir un filtre.

Règles possibles :

- ne pas intégrer un point GPS si précision trop mauvaise ;
- ne pas intégrer une distance si vitesse calculée incohérente ;
- ne pas intégrer les déplacements inférieurs à un seuil de bruit ;
- ne pas extrapoler longtemps en cas de perte GPS.

Seuils à rendre configurables :

- précision GPS maximale : 10 / 20 / 30 m ;
- distance minimale entre deux points : 2 m ;
- vitesse maximale plausible : 200 km/h ;
- durée max sans GPS avant alerte : 5 s / 10 s / 30 s.

---

## 9. Persistance

L’application doit survivre à :

- rotation écran ;
- mise en arrière-plan courte ;
- verrouillage écran ;
- redémarrage accidentel de l’application.

Elle doit sauvegarder régulièrement :

- kilométrage total ;
- kilométrage étape ;
- état session ;
- heure de départ ;
- coefficient de calibration ;
- historique minimal des corrections.

Règle :

```text
Aucune session active ne doit être perdue sans confirmation utilisateur.
```

Précision v0.1.1 (A4) : l'état mémoire est la source de vérité immédiate. Les mutations métier
explicites (session, reset, correction, set, calibration) sont persistées immédiatement.
L'accumulation fine de distance GPS est persistée de façon throttlée (toutes les 5 s, et forcée
à chaque mutation explicite et changement d'état). Au pire, une interruption brutale perd
quelques secondes d'accumulation de distance, jamais la session ni les compteurs. Voir
TripController §16.

---

## 10. Architecture logique validée

Séparation stricte :

- `LocationEngine` : acquisition GPS brute ;
- `DistanceEngine` : validation et calcul de distance ;
- `TripState` : état métier ;
- `TripController` : commandes utilisateur ;
- `TripDisplay` : affichage.

Principe central :

```text
GPS brut ≠ distance validée ≠ affichage
```

---

## 11. MVP validé

Pour une première version exploitable :

- écran unique ;
- total km ;
- étape km ;
- vitesse instantanée ;
- start / pause / stop ;
- reset étape ;
- correction étape ±10 m / ±100 m ;
- statut GPS ;
- écran maintenu allumé ;
- sauvegarde automatique de la session ;
- coefficient de calibration global.

---

## 12. Hors périmètre v0.1

- roadbook ;
- carte ;
- navigation ;
- capteur roue ;
- OBD ;
- Bluetooth externe ;
- classement ;
- synchronisation cloud ;
- partage live.
