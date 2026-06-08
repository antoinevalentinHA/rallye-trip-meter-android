# Trip Meter Rallye Android — DistanceEngine v0.1

Statut : PROPOSITION  
Dépend de :

- Contrat fonctionnel v0.1 validé
- TripState v0.1

Objet : validation des positions GPS et production de distances métier applicables à TripState

---

## 1. Rôle de DistanceEngine

`DistanceEngine` transforme des positions GPS brutes en distance validée.

Il ne stocke pas la session complète.  
Il ne décide pas si l’application est en pause ou active.  
Il ne dessine rien.  
Il ne modifie pas directement l’interface.

Son rôle est strict :

```text
Position GPS brute
→ validation
→ filtrage
→ calcul distance brute
→ décision d’intégration
→ distance validée transmise à TripState
```

Principe central :

```text
DistanceEngine produit une distance admissible.
TripState décide si cette distance peut être ajoutée selon l’état de session.
```

---

## 2. Entrées

### 2.1 Position GPS brute

Une position GPS entrante contient au minimum :

```text
latitude
longitude
timestamp
accuracy_m
speed_kmh optionnel
provider
```

Exemple conceptuel :

```text
RawLocation
├── latitude
├── longitude
├── timestamp
├── accuracy_m
├── speed_kmh
└── provider
```

### 2.2 État de référence

`DistanceEngine` a besoin de connaître :

```text
last_valid_position
session_state
gps_status courant
configuration de filtrage
```

Mais :

```text
DistanceEngine ne possède pas TripState.
Il le consulte ou reçoit un extrait de contexte.
```

Exemple :

```text
DistanceContext
├── session_state
├── last_valid_position
├── calibration_factor
└── filter_config
```

---

## 3. Sorties

`DistanceEngine` retourne une décision explicite.

Résultats possibles :

```text
ACCEPTED
REFERENCE_ONLY
REJECTED
GPS_STATUS_ONLY
```

### 3.1 ACCEPTED

La position est valide et produit une distance intégrable.

```text
distance_raw_m > 0
new_reference_position = position courante
```

### 3.2 REFERENCE_ONLY

La position est valide comme nouvelle référence, mais aucune distance ne doit être ajoutée.

Cas typiques :

```text
première position valide
reprise après pause
récupération GPS après perte
réinitialisation de référence
```

### 3.3 REJECTED

La position est refusée.

Cas typiques :

```text
précision trop mauvaise
timestamp incohérent
saut GPS
vitesse impossible
coordonnées invalides
```

### 3.4 GPS_STATUS_ONLY

La position ne produit aucune distance, mais met à jour le statut GPS.

---

## 4. Structure de sortie recommandée

```text
DistanceDecision
├── decision
├── raw_distance_m
├── applied_distance_m
├── gps_status
├── gps_accuracy_m
├── accepted_position
├── should_update_reference
├── rejection_reason
└── event_type
```

Décision normative v0.1 : `DistanceEngine` produit uniquement `raw_distance_m`. `TripState` applique la calibration.

---

## 5. Configuration v0.1

Valeurs recommandées :

```text
max_accuracy_ok_m              = 15
max_accuracy_degraded_m        = 30
max_speed_kmh                  = 200
max_speed_jump_kmh             = 80
min_distance_delta_m           = 1.5
max_distance_delta_m           = 150
max_location_age_ms            = 5000
gps_lost_after_ms              = 10000
reference_reset_after_lost_ms  = 5000
```

Lecture métier :

- `accuracy <= 15 m` → GPS OK ;
- `15 m < accuracy <= 30 m` → GPS DEGRADED ;
- `accuracy > 30 m` → GPS INVALID ou LOST selon fraîcheur, distance refusée ;
- vitesse au-dessus de 200 km/h → rejet ;
- distance sous 1,5 m → ignorée ;
- distance trop grande entre deux points → suspicion de saut GPS à croiser avec le temps écoulé et la vitesse calculée.

---

## 6. Statuts GPS produits

```text
UNKNOWN
SEARCHING
OK
DEGRADED
LOST
INVALID
```

Règles de statut :

```text
Aucune position jamais reçue
→ SEARCHING

Position récente + précision <= 15 m
→ OK

Position récente + précision > 15 m et <= 30 m
→ DEGRADED

Position récente mais précision > 30 m
→ INVALID

Aucune position récente depuis plus de 10 s
→ LOST

Position impossible ou incohérente
→ INVALID
```

---

## 7. Validation d’une position

### 7.1 Coordonnées valides

Refus si :

```text
latitude absente
longitude absente
latitude hors [-90 ; 90]
longitude hors [-180 ; 180]
timestamp absent
```

Décision :

```text
REJECTED
gps_status = INVALID
rejection_reason = INVALID_COORDINATES
```

### 7.2 Timestamp cohérent

Refus si :

```text
timestamp <= last_valid_position.timestamp
```

Décision :

```text
REJECTED
gps_status = INVALID
rejection_reason = NON_MONOTONIC_TIMESTAMP
```

Refus si :

```text
now - timestamp > max_location_age_ms
```

Décision v0.1 :

```text
REJECTED
gps_status = LOST ou INVALID
rejection_reason = STALE_LOCATION
```

### 7.3 Précision exploitable

Règle :

```text
accuracy_m <= max_accuracy_degraded_m
```

Sinon :

```text
REJECTED
gps_status = INVALID
rejection_reason = ACCURACY_TOO_POOR
```

Une position imprécise peut mettre à jour `last_gps_timestamp`. Elle ne doit pas mettre à jour `last_valid_position`.

---

## 8. Première position valide

Si :

```text
last_valid_position = null
position valide
```

Alors :

```text
decision = REFERENCE_ONLY
raw_distance_m = 0
applied_distance_m = 0
should_update_reference = true
accepted_position = current_position
gps_status = OK ou DEGRADED
```

Invariant : jamais de distance sans point de référence préalable.

---

## 9. Calcul de distance brute

Distance entre :

```text
last_valid_position
current_position
```

Méthode v0.1 :

```text
distance géodésique Android / Location.distanceBetween / distanceTo
```

Contrat abstrait :

```text
raw_distance_m = distance(last_valid_position, current_position)
```

Règles :

```text
raw_distance_m >= 0
raw_distance_m doit être fini
raw_distance_m ne doit pas être NaN
```

Refus si `raw_distance_m` invalide :

```text
REJECTED
rejection_reason = INVALID_DISTANCE
```

---

## 10. Filtrage du bruit à faible distance

Si :

```text
raw_distance_m < min_distance_delta_m
```

Alors :

```text
decision = REFERENCE_ONLY ou GPS_STATUS_ONLY
applied_distance_m = 0
```

Décision v0.1 :

```text
Si raw_distance_m < 1.5 m :
- ne pas ajouter de distance ;
- mettre à jour la référence uniquement si accuracy_m <= max_accuracy_ok_m.
```

---

## 11. Validation par vitesse calculée

Calcul :

```text
elapsed_s = current.timestamp - last_valid_position.timestamp
calculated_speed_kmh = raw_distance_m / elapsed_s * 3.6
```

Refus si :

```text
elapsed_s <= 0
calculated_speed_kmh > max_speed_kmh
```

Décision :

```text
REJECTED
gps_status = INVALID
rejection_reason = SPEED_TOO_HIGH
should_update_reference = false
```

Une position rejetée pour vitesse impossible ne devient pas référence.

---

## 12. Filtrage des grands sauts

Si :

```text
raw_distance_m > max_distance_delta_m
```

Alors on vérifie la vitesse calculée.

Règle v0.1 :

```text
raw_distance_m > max_distance_delta_m
ET calculated_speed_kmh > max_speed_kmh
→ REJECTED
```

Sinon :

```text
ACCEPTED
```

---

## 13. Utilisation de la vitesse GPS native

Règle v0.1 :

```text
La vitesse GPS native peut être utilisée pour l’affichage.
Elle ne remplace pas la validation par distance calculée.
```

Validation :

```text
native_speed_kmh < 0
→ ignorée

native_speed_kmh > max_speed_kmh
→ vitesse ignorée ou GPS INVALID

native_speed_kmh absente
→ utiliser calculated_speed_kmh
```

Décision recommandée :

```text
speed_for_display_kmh = native_speed_kmh si fiable
sinon calculated_speed_kmh
```

La distance ajoutée reste basée sur la distance entre positions validées.

---

## 14. Calibration

Décision normative v0.1 :

```text
DistanceEngine produit uniquement raw_distance_m.
TripState applique la calibration.
```

`DistanceEngine` ne doit pas appliquer `calibration_factor` en v0.1 pour éviter tout double comptage.

---

## 15. Pause, reprise et reset de référence

### 15.1 À la pause

Quand `TripState` passe à `PAUSED` :

```text
DistanceEngine peut conserver last_valid_position
mais aucune distance ne sera intégrée
```

### 15.2 À la reprise

Quand `TripState` passe de `PAUSED` à `ACTIVE` :

```text
DistanceEngine doit neutraliser la référence de distance
```

Effet :

```text
La prochaine position valide devient REFERENCE_ONLY.
Aucune distance n’est ajoutée entre avant-pause et après-reprise.
```

Commande conceptuelle :

```text
resetDistanceReference(reason = RESUME_AFTER_PAUSE)
```

### 15.3 Après perte GPS

Quand GPS passe de `LOST` à `OK` ou `DEGRADED` :

```text
La première position valide après récupération devient REFERENCE_ONLY.
Aucune distance de rattrapage n’est ajoutée.
```

---

## 16. Perte GPS

### 16.1 Détection

Si :

```text
now - last_gps_timestamp > gps_lost_after_ms
```

Alors :

```text
gps_status = LOST
```

Effets : aucune distance ajoutée, `speed_kmh = 0` via TripState, événement `GPS_LOST` si transition nouvelle.

### 16.2 Récupération

Si :

```text
gps_status = LOST
et nouvelle position valide
```

Alors :

```text
decision = REFERENCE_ONLY
should_update_reference = true
gps_status = OK ou DEGRADED
event_type = GPS_RECOVERED
```

Aucune distance ne doit être produite.

---

## 17. Matrice de décision

```text
Situation                                      Décision          Distance   Référence
-------------------------------------------------------------------------------------
Première position valide                       REFERENCE_ONLY    0          update
Position valide, distance >= seuil             ACCEPTED          +          update
Position valide, distance < seuil, GPS OK      REFERENCE_ONLY    0          update
Position valide, distance < seuil, dégradé     GPS_STATUS_ONLY   0          no update
Précision trop mauvaise                        REJECTED          0          no update
Timestamp non monotone                         REJECTED          0          no update
Distance NaN/infinie                           REJECTED          0          no update
Vitesse calculée impossible                    REJECTED          0          no update
GPS perdu                                      GPS_STATUS_ONLY   0          no update
GPS récupéré après perte                       REFERENCE_ONLY    0          update
Reprise après pause                            REFERENCE_ONLY    0          update
Session STOPPED                                GPS_STATUS_ONLY   0          selon statut
Session PAUSED                                 GPS_STATUS_ONLY   0          selon statut
```

---

## 18. Interaction avec TripState

### 18.1 En session ACTIVE

Si décision `ACCEPTED` :

```text
TripState.applyDistanceUpdate(raw_distance_m)
TripState.updateSpeed(speed_kmh)
TripState.updateGpsStatus(gps_status, accuracy_m)
TripState.last_valid_position = accepted_position
```

Si décision `REFERENCE_ONLY` :

```text
TripState.updateGpsStatus(gps_status, accuracy_m)
TripState.last_valid_position = accepted_position
Aucune distance ajoutée
```

Si décision `REJECTED` :

```text
TripState.updateGpsStatus(INVALID, accuracy_m)
Aucune distance ajoutée
last_valid_position inchangée
```

### 18.2 En session PAUSED

Règle : aucune distance ne peut être ajoutée.

Positions GPS :

- peuvent mettre à jour `gps_status` ;
- ne doivent pas produire de distance.

Décision recommandée :

```text
En PAUSED :
- update GPS status ;
- speed = 0 ;
- ne pas modifier last_valid_position pour la distance ;
- marquer distance_reference_dirty = true.
```

### 18.3 En session STOPPED

Règle : aucune distance ne peut être ajoutée.

Au `startSession`, reset de référence distance. La première position valide en `ACTIVE` est `REFERENCE_ONLY`.

---

## 19. Événements produits

Événements proposés :

```text
GPS_LOST
GPS_RECOVERED
GPS_INVALID_POSITION
DISTANCE_REJECTED
REFERENCE_RESET
```

Journal utilisateur minimal :

```text
GPS_LOST
GPS_RECOVERED
REFERENCE_RESET
```

Journal technique possible :

```text
GPS_INVALID_POSITION
DISTANCE_REJECTED
ACCURACY_TOO_POOR
SPEED_TOO_HIGH
NON_MONOTONIC_TIMESTAMP
```

---

## 20. Raisons de rejet v0.1

```text
INVALID_COORDINATES
MISSING_TIMESTAMP
NON_MONOTONIC_TIMESTAMP
STALE_LOCATION
ACCURACY_MISSING
ACCURACY_TOO_POOR
INVALID_DISTANCE
DISTANCE_TOO_SMALL
SPEED_TOO_HIGH
GPS_LOST
SESSION_NOT_ACTIVE
REFERENCE_MISSING
```

`DISTANCE_TOO_SMALL` n’est pas une erreur ; c’est un refus d’intégration normal.

---

## 21. Pseudo-algorithme normatif

```text
onLocationReceived(location, context):

  if location coordinates invalid:
      return REJECTED INVALID_COORDINATES

  if timestamp missing:
      return REJECTED MISSING_TIMESTAMP

  update last_gps_timestamp candidate

  if location too old:
      return REJECTED STALE_LOCATION

  gps_status = classifyGpsStatus(location.accuracy_m)

  if accuracy_m missing:
      return REJECTED ACCURACY_MISSING

  if accuracy_m > max_accuracy_degraded_m:
      return REJECTED ACCURACY_TOO_POOR

  if context.session_state != ACTIVE:
      return GPS_STATUS_ONLY

  if context.distance_reference_dirty:
      return REFERENCE_ONLY with current location

  if context.last_valid_position is null:
      return REFERENCE_ONLY with current location

  if location.timestamp <= last_valid_position.timestamp:
      return REJECTED NON_MONOTONIC_TIMESTAMP

  raw_distance_m = distance(last_valid_position, location)

  if raw_distance_m invalid:
      return REJECTED INVALID_DISTANCE

  elapsed_s = location.timestamp - last_valid_position.timestamp

  if elapsed_s <= 0:
      return REJECTED NON_MONOTONIC_TIMESTAMP

  calculated_speed_kmh = raw_distance_m / elapsed_s * 3.6

  if calculated_speed_kmh > max_speed_kmh:
      return REJECTED SPEED_TOO_HIGH

  if raw_distance_m < min_distance_delta_m:
      if gps_status == OK:
          return REFERENCE_ONLY
      else:
          return GPS_STATUS_ONLY

  return ACCEPTED raw_distance_m
```

---

## 22. Paramètres configurables utilisateur

À exposer éventuellement :

- précision GPS maximale ;
- coefficient de calibration ;
- vitesse maximale plausible ;
- seuil micro-déplacement.

Réglages cachés / debug :

```text
max_speed_kmh
max_distance_delta_m
min_distance_delta_m
gps_lost_after_ms
```

---

## 23. Invariants DistanceEngine v0.1

```text
DE-01 — DistanceEngine ne modifie jamais directement les compteurs.
DE-02 — DistanceEngine ne produit jamais de distance négative.
DE-03 — Aucune distance n’est produite sans position de référence valide.
DE-04 — La première position valide produit toujours REFERENCE_ONLY.
DE-05 — Une position rejetée ne devient jamais position de référence.
DE-06 — Une position avec précision > seuil dégradé ne produit jamais de distance.
DE-07 — Une distance dont la vitesse calculée dépasse max_speed_kmh est rejetée.
DE-08 — Aucune distance n’est produite en STOPPED.
DE-09 — Aucune distance n’est produite en PAUSED.
DE-10 — Après pause, la reprise impose une nouvelle référence.
DE-11 — Après perte GPS, la récupération impose une nouvelle référence.
DE-12 — DistanceEngine ne rattrape jamais une distance perdue.
DE-13 — DistanceEngine ne compense jamais silencieusement une absence GPS.
DE-14 — La calibration n’est pas appliquée dans DistanceEngine en v0.1.
DE-15 — Le statut GPS peut être mis à jour même sans distance produite.
DE-16 — Les micro-déplacements sous seuil ne sont pas ajoutés aux compteurs.
DE-17 — Un timestamp non monotone est toujours rejeté.
DE-18 — Les coordonnées invalides sont toujours rejetées.
DE-19 — Les décisions sont explicites : ACCEPTED, REFERENCE_ONLY, REJECTED ou GPS_STATUS_ONLY.
DE-20 — Toute décision rejetée doit pouvoir fournir une raison technique.
```

---

## 24. Contrat compact v0.1

## Rôle

DistanceEngine transforme des positions GPS brutes en décisions de distance.

Il produit :

- `ACCEPTED` ;
- `REFERENCE_ONLY` ;
- `REJECTED` ;
- `GPS_STATUS_ONLY`.

## Règles principales

- La première position valide sert de référence et n’ajoute aucune distance.
- Une position rejetée ne devient jamais référence.
- Aucune distance n’est produite hors session `ACTIVE`.
- Aucune distance n’est rattrapée après pause ou perte GPS.
- La calibration n’est pas appliquée par `DistanceEngine`.
- Les distances produites sont brutes, positives et validées.
- Les points GPS imprécis, anciens, incohérents ou trop rapides sont rejetés.
- Les micro-déplacements sous seuil ne sont pas ajoutés.
- Le statut GPS peut être mis à jour indépendamment de la distance.

## Seuils v0.1

- GPS OK : précision <= 15 m ;
- GPS dégradé : précision <= 30 m ;
- GPS rejeté : précision > 30 m ;
- vitesse maximale plausible : 200 km/h ;
- micro-déplacement ignoré : < 1,5 m ;
- GPS perdu : aucune position récente depuis 10 s.

## Invariant central

```text
DistanceEngine préfère sous-compter légèrement plutôt qu’ajouter une distance fausse.
```
