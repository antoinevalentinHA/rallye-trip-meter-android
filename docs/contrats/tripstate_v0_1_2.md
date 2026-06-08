# Trip Meter Rallye Android — TripState v0.1.2

Statut : PROPOSITION (révision v0.1.2)  
Dépend de : Contrat fonctionnel v0.2 validé, LocationEngine v0.1  
Objet : état métier central de l’application

---

## Changelog v0.1.2 (passe de cohérence globale)

- **G4** — `last_gps_timestamp` est écrit **uniquement** par LocationEngine (exception technique
  documentée). Retrait de `last_gps_timestamp = now` des effets de `updateGpsStatus` (§7.12).
  Nouvel invariant I-21. Voir §5.8, §7.12, §8, §11.
- **D2** (recadrage navigation au roadbook) — note de mapping : `stage_distance_m` = compteur
  partiel de navigation (libellé UI PARTIEL). Champ interne conservé ; renommage `stage` → `partial`
  signalé comme candidat v0.2 sous arbitrage, non appliqué. Voir §5.3.

## Changelog v0.1.1

- **C4** — `updateSpeed` hors ACTIVE est ignoré sans mutation ni erreur ;
  `speed_kmh = 0` est garanti par la transition d'état, pas par les updates. Voir §5.4, §7.11.
- **C5** — Règle de vitesse périmée : `speed_kmh` retombe à 0 si aucune décision ACCEPTED
  ne l'a mis à jour depuis `speed_timeout_ms` (3000 ms). Voir §5.4.
- **A2** — Plancher stationnaire : vitesse forcée à 0 sous `seuil_stationnaire`. Voir §5.4.
- **M3** — `SET_TOTAL_DISTANCE` est l'unique commande pouvant réduire `total_distance_m`,
  de façon explicite, journalisée et confirmée. Voir §11 (I-05).

---

## 1. Rôle de TripState

`TripState` est la source de vérité métier de l’application.

Il contient l’état courant de la session rallye, les compteurs, le statut GPS, les corrections utilisateur et les informations nécessaires à la reprise après interruption.

Principe fondamental :

```text
TripState ne lit pas le GPS.
TripState ne dessine pas l’écran.
TripState ne décide pas seul.
TripState stocke l’état validé.
```

Les moteurs autour de lui font le travail :

```text
LocationEngine  → fournit les positions GPS brutes
DistanceEngine  → valide les distances
TripController  → applique les commandes utilisateur
TripDisplay     → affiche TripState
```

---

## 2. États de session

### 2.1 États autorisés

```text
STOPPED
ACTIVE
PAUSED
```

### 2.2 STOPPED

La session n’est pas en cours.

Effets :

```text
total_distance_m : conservé ou remis à zéro selon choix utilisateur
stage_distance_m : conservé ou remis à zéro selon choix utilisateur
speed_kmh        : 0
distance update  : refusée
GPS update       : accepté pour statut seulement
```

### 2.3 ACTIVE

La session est en cours.

Effets :

```text
total_distance_m : peut augmenter
stage_distance_m : peut augmenter
speed_kmh        : mise à jour
distance update  : acceptée si validée
GPS update       : accepté
```

### 2.4 PAUSED

La session est temporairement suspendue.

Effets :

```text
total_distance_m : figé
stage_distance_m : figé
speed_kmh        : 0 en v0.1
distance update  : refusée
GPS update       : accepté pour statut seulement
```

Décision v0.1 :

```text
En PAUSED, speed_kmh = 0
```

Raison : en pause, l’instrument ne mesure pas. Afficher une vitesse active pendant une pause crée une ambiguïté.

---

## 3. Données métier obligatoires

```text
session_state
total_distance_m
stage_distance_m
speed_kmh
gps_status
gps_accuracy_m
last_valid_position
last_gps_timestamp
calibration_factor
corrections_log
session_started_at
session_updated_at
```

---

## 4. Schéma conceptuel

```text
TripState
├── session_state
│   ├── STOPPED
│   ├── ACTIVE
│   └── PAUSED
│
├── distances
│   ├── total_distance_m
│   └── stage_distance_m
│
├── speed
│   └── speed_kmh
│
├── gps
│   ├── gps_status
│   ├── gps_accuracy_m
│   ├── last_valid_position
│   └── last_gps_timestamp
│
├── calibration
│   └── calibration_factor
│
├── corrections
│   └── corrections_log
│
└── timestamps
    ├── session_started_at
    └── session_updated_at
```

---

## 5. Champs détaillés

### 5.1 `session_state`

Type : `enum`

Valeurs autorisées :

```text
STOPPED
ACTIVE
PAUSED
```

Invariant : aucune autre valeur n’est autorisée.

### 5.2 `total_distance_m`

Type : `integer` ou `double`  
Unité : mètres

Rôle : distance totale validée depuis le début de session.

Règles :

- ne peut jamais diminuer automatiquement ;
- peut augmenter uniquement en session `ACTIVE` ;
- peut être modifié manuellement par correction explicite ;
- ne peut jamais être négatif.

Décision recommandée : stockage interne en mètres, affichage en kilomètres.

### 5.3 `stage_distance_m`

Type : `integer` ou `double`  
Unité : mètres

Rôle : distance validée depuis le dernier reset partiel.

Mapping métier/UI (D2, recadrage navigation au roadbook) : `stage_distance_m` est le **compteur
partiel de navigation** (contrat fonctionnel v0.2 §3.2). Côté affichage, son libellé est **PARTIEL**
(TripDisplay v0.1.2). Le nom interne `stage_distance_m` est **conservé** en l'état : un renommage
`stage` → `partial` propagé sur tous les contrats et la future implémentation créerait un bruit
disproportionné par rapport au gain. Le mapping documenté ici suffit à lever l'ambiguïté.

Renommage interne `stage` → `partial` : signalé comme **candidat v0.2 sous arbitrage**. À envisager
seulement si le code généré gagne en clarté à le faire, et jamais sans arbitrage explicite. En v0.1.x,
le nom interne reste `stage_distance_m`.

Règles :

- peut augmenter uniquement en session `ACTIVE` ;
- peut être remis à zéro par action explicite ;
- peut être corrigé manuellement ;
- ne peut jamais être négatif ;
- son reset ne modifie jamais `total_distance_m`.

### 5.4 `speed_kmh`

Type : `double`  
Unité : km/h

Règles :

- mise à jour en session `ACTIVE` uniquement ;
- mise à zéro en `STOPPED` ;
- mise à zéro en `PAUSED` pour la v0.1 ;
- ne peut jamais être négative.

Comportement hors ACTIVE (C4) :

- en `PAUSED` et `STOPPED`, `updateSpeed` est **ignoré** sans mutation et sans erreur ;
- `speed_kmh = 0` est garanti par la transition d'état (`pauseSession`, `stopSession`),
  pas par les mises à jour de vitesse ;
- aucune valeur postérieure à l'entrée en PAUSED/STOPPED n'est appliquée.

Plancher stationnaire (A2) :

- sous `seuil_stationnaire` (≈ 0.5 m/s), `speed_kmh = 0`.

Vitesse périmée (C5) :

- si aucune décision `ACCEPTED` n'a mis à jour `speed_kmh` depuis `speed_timeout_ms` (3000 ms),
  alors `speed_kmh = 0` ;
- invariant : la vitesse affichée ne reste jamais figée sur une valeur non nulle alors qu'aucune
  distance n'est plus produite.

Arrondi affichage : 0 décimale.

### 5.5 `gps_status`

Type : `enum`

Valeurs proposées :

```text
UNKNOWN
SEARCHING
OK
DEGRADED
LOST
INVALID
```

Définitions :

- `UNKNOWN` : état initial, aucune information GPS exploitable connue.
- `SEARCHING` : GPS en cours d’acquisition.
- `OK` : position récente et précision correcte.
- `DEGRADED` : précision médiocre mais pas totalement perdue.
- `LOST` : aucune position récente exploitable.
- `INVALID` : position reçue mais rejetée par `DistanceEngine`.

### 5.6 `gps_accuracy_m`

Type : `double` ou `null`  
Unité : mètres

Règles :

- peut être null si inconnue ;
- affichable si disponible ;
- n’entre pas seule dans le calcul métier.

### 5.7 `last_valid_position`

Type conceptuel : `Position` ou `null`

Contenu :

```text
latitude
longitude
timestamp
accuracy_m
```

Règles :

- mise à jour uniquement par `DistanceEngine` ;
- jamais mise à jour avec une position rejetée ;
- peut être null au démarrage.

### 5.8 `last_gps_timestamp`

Type : `timestamp` ou `null`

Rôle : horodatage de la dernière information GPS reçue.

Différence importante :

```text
last_gps_timestamp ≠ timestamp de dernière position valide
```

On peut recevoir du GPS invalide. Il faut pouvoir dire que le GPS parle encore, mais que ce qu’il raconte n’est pas exploitable.

Writer (G4) : `last_gps_timestamp` est écrit **uniquement** par LocationEngine (LocationEngine §11,
LE-08b), à chaque position reçue, valide ou non. C'est le **seul** champ de TripState échappant au
writer souverain TripController/DistanceEngine. Cette exception est assumée : le watchdog de perte
GPS en dépend en temps réel et ce champ n'a aucun effet métier (ce n'est pas un compteur). Aucune
commande TripController ni décision DistanceEngine ne l'écrit.

### 5.9 `calibration_factor`

Type : `double`  
Valeur par défaut : `1.0`

Formule métier :

```text
distance_appliquée = distance_validée_brute × calibration_factor
```

Règles :

- par défaut : `1.0` ;
- doit être strictement positif ;
- ne modifie pas rétroactivement les distances déjà enregistrées en v0.1.

Décision v0.1 : le coefficient agit uniquement sur les nouvelles distances.

### 5.10 `corrections_log`

Type : liste d’événements.

Événements v0.1 :

```text
SESSION_STARTED
SESSION_PAUSED
SESSION_RESUMED
SESSION_STOPPED
STAGE_RESET
TOTAL_CORRECTED
STAGE_CORRECTED
GPS_LOST
GPS_RECOVERED
CALIBRATION_CHANGED
```

Structure proposée :

```text
CorrectionEvent
├── timestamp
├── type
├── target
├── delta_m
├── previous_value_m
├── new_value_m
└── note
```

---

## 6. Transitions d’état

### 6.1 Transitions autorisées

```text
STOPPED → ACTIVE
ACTIVE  → PAUSED
PAUSED  → ACTIVE
ACTIVE  → STOPPED
PAUSED  → STOPPED
STOPPED → STOPPED
ACTIVE  → ACTIVE
PAUSED  → PAUSED
```

Les transitions identiques sont tolérées uniquement si elles sont idempotentes.

### 6.2 Transitions interdites

Aucune transition technique supplémentaire n’est autorisée.

Exemples refusés :

```text
UNKNOWN → ACTIVE
ACTIVE → UNKNOWN
PAUSED → UNKNOWN
STOPPED → PAUSED
```

`UNKNOWN` n’est pas un état de session. C’est un statut GPS possible.

---

## 7. Commandes et effets

### 7.1 `startSession`

Autorisé si : `session_state = STOPPED`

Effets :

```text
session_state = ACTIVE
session_started_at = now
session_updated_at = now
speed_kmh = 0
total_distance_m = 0
stage_distance_m = 0
```

Décision v0.1 : démarrer une nouvelle session remet total et étape à zéro. Reprendre une ancienne session est une commande séparée.

### 7.2 `pauseSession`

Autorisé si : `session_state = ACTIVE`

Effets :

```text
session_state = PAUSED
speed_kmh = 0
session_updated_at = now
```

Distances inchangées.

### 7.3 `resumeSession`

Autorisé si : `session_state = PAUSED`

Effets :

```text
session_state = ACTIVE
speed_kmh = 0
session_updated_at = now
```

Règle critique : à la reprise, la prochaine position GPS valide devient le nouveau point de référence. Aucune distance n’est ajoutée entre la pause et la reprise.

### 7.4 `stopSession`

Autorisé si : `session_state = ACTIVE` ou `session_state = PAUSED`

Effets :

```text
session_state = STOPPED
speed_kmh = 0
session_updated_at = now
```

Distances conservées.

### 7.5 `resetStage`

Autorisé dans tous les états.

Effets :

```text
stage_distance_m = 0
session_updated_at = now
```

`total_distance_m` inchangé. Log obligatoire : `STAGE_RESET`.

### 7.6 `resetTotal`

Autorisé avec confirmation utilisateur explicite.

Effets :

```text
total_distance_m = 0
stage_distance_m = 0
speed_kmh = 0
session_updated_at = now
```

Décision v0.1 : reset total remet aussi l’étape à zéro.

### 7.7 `correctStage(delta_m)`

Autorisé si : `delta_m ≠ 0`

Effets :

```text
stage_distance_m = max(0, stage_distance_m + delta_m)
session_updated_at = now
```

Log obligatoire : `STAGE_CORRECTED`.

Invariant : la correction étape ne modifie jamais `total_distance_m`.

### 7.8 `correctTotal(delta_m)`

Autorisé si : `delta_m ≠ 0`

Effets :

```text
total_distance_m = max(0, total_distance_m + delta_m)
session_updated_at = now
```

Log obligatoire : `TOTAL_CORRECTED`.

Invariant : la correction total ne modifie jamais `stage_distance_m`.

### 7.9 `setCalibrationFactor(value)`

Autorisé si : `value > 0`

Effets :

```text
calibration_factor = value
session_updated_at = now
```

Log obligatoire : `CALIBRATION_CHANGED`.

Décision v0.1 : calibration non rétroactive.

### 7.10 `applyDistanceUpdate(raw_distance_m)`

Appelée par : `DistanceEngine`

Autorisé si :

```text
session_state = ACTIVE
raw_distance_m > 0
```

Effets :

```text
applied_distance_m = raw_distance_m × calibration_factor
total_distance_m += applied_distance_m
stage_distance_m += applied_distance_m
session_updated_at = now
```

Refus hors `ACTIVE`.

### 7.11 `updateSpeed(speed_kmh)`

Autorisé si :

```text
session_state = ACTIVE
speed_kmh >= 0
```

En `STOPPED` ou `PAUSED`, `updateSpeed` est **ignoré** (ni mutation, ni erreur) ; `speed_kmh`
reste à 0, garanti par la transition d'état. (C4)

### 7.12 `updateGpsStatus(status, accuracy_m)`

Autorisé toujours.

Effets :

```text
gps_status = status
gps_accuracy_m = accuracy_m
session_updated_at = now
```

Note (G4) : `updateGpsStatus` n'écrit **pas** `last_gps_timestamp`. Ce champ est alimenté
exclusivement par LocationEngine (§5.8, LE-08b).

---

## 8. Matrice des effets

```text
Commande              État requis        Total     Étape     Vitesse   GPS   Log
--------------------------------------------------------------------------------------
startSession          STOPPED            reset     reset     0         -     oui
pauseSession          ACTIVE             idem      idem      0         -     oui
resumeSession         PAUSED             idem      idem      0         -     oui
stopSession           ACTIVE/PAUSED       idem      idem      0         -     oui
resetStage            tous               idem      reset     idem      -     oui
resetTotal            confirmé           reset     reset     0         -     oui
correctStage          tous               idem      +/-       idem      -     oui
correctTotal          tous               +/-       idem      idem      -     oui
setCalibrationFactor  tous               idem      idem      idem      -     oui
applyDistanceUpdate   ACTIVE             +         +         idem      -     non
updateSpeed           ACTIVE             idem      idem      update    -     non
updateGpsStatus       tous               idem      idem      idem      +     selon cas
```

Note (G4) : la colonne GPS de `updateGpsStatus` couvre `gps_status` et `gps_accuracy_m` uniquement.
`last_gps_timestamp` n'est écrit par aucune commande de cette matrice : il est alimenté
exclusivement par LocationEngine (§5.8).

---

## 9. Reprise après interruption

### 9.1 Sauvegarde obligatoire

TripState doit être persisté après chaque mutation métier.

Mutations concernées :

```text
startSession
pauseSession
resumeSession
stopSession
resetStage
resetTotal
correctStage
correctTotal
setCalibrationFactor
applyDistanceUpdate
updateGpsStatus
```

### 9.2 Redémarrage application

Au lancement, l’application recharge le dernier `TripState`.

Cas `STOPPED` : afficher les dernières valeurs, session non active.  
Cas `PAUSED` : afficher les dernières valeurs, session en pause.  
Cas `ACTIVE` : restaurer en `PAUSED`, pas en `ACTIVE`.

Effet d’un `ACTIVE` restauré :

```text
session_state = PAUSED
speed_kmh = 0
log = SESSION_RESTORED_AS_PAUSED
```

---

## 10. Règles GPS associées au TripState

### 10.1 GPS OK

```text
gps_status = OK
gps_accuracy_m <= seuil_ok
```

Distance updates acceptées si `DistanceEngine` les valide.

### 10.2 GPS dégradé

```text
gps_status = DEGRADED
```

Affichage d’un avertissement. Distance updates possibles selon `DistanceEngine`.

### 10.3 GPS perdu

```text
gps_status = LOST
```

Effets : aucune distance ajoutée, `speed_kmh = 0` après temporisation éventuelle, log `GPS_LOST` si changement d’état.

### 10.4 GPS récupéré

Quand `gps_status` passe de `LOST` à `OK` : la première position valide après récupération sert de référence. Aucune distance n’est ajoutée entre la dernière position avant perte et la première position après récupération.

---

## 11. Invariants TripState v0.1

```text
I-01 — session_state ∈ {STOPPED, ACTIVE, PAUSED}
I-02 — total_distance_m >= 0
I-03 — stage_distance_m >= 0
I-04 — speed_kmh >= 0
I-05 — total_distance_m ne diminue jamais sans correction explicite, reset total confirmé ou SET_TOTAL_DISTANCE confirmé
I-05b — SET_TOTAL_DISTANCE est l'unique commande pouvant réduire total_distance_m ; cette diminution est volontaire, journalisée (TOTAL_SET) et soumise à confirmation. (M3)
I-06 — stage_distance_m ne diminue jamais sans correction explicite, reset étape ou reset total confirmé
I-07 — resetStage ne modifie jamais total_distance_m
I-08 — correctStage ne modifie jamais total_distance_m
I-09 — correctTotal ne modifie jamais stage_distance_m
I-10 — aucune distance n’est ajoutée hors session ACTIVE
I-11 — pauseSession fige les distances
I-12 — resumeSession ne rattrape jamais la distance parcourue pendant la pause
I-13 — stopSession conserve les distances
I-14 — resetTotal remet total_distance_m et stage_distance_m à zéro
I-15 — calibration_factor > 0
I-16 — calibration_factor n’est pas rétroactif en v0.1
I-17 — après restauration d’une session ACTIVE interrompue, l’état restauré est PAUSED
I-18 — toute correction utilisateur est journalisée
I-19 — last_valid_position n’est jamais mise à jour par une position GPS rejetée
I-20 — TripState est persisté après chaque mutation métier
I-21 — last_gps_timestamp est écrit uniquement par LocationEngine ; aucune commande TripController ni décision DistanceEngine ne l'écrit. (G4)
```

---

## 12. Format d’affichage dérivé

```text
display_total_km = total_distance_m / 1000
display_stage_km = stage_distance_m / 1000
display_speed_kmh = speed_kmh
```

Arrondis v0.1 :

- total : 2 décimales ;
- étape : 2 décimales ;
- vitesse : 0 décimale.
