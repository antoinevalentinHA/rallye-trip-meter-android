# Trip Meter Rallye Android — DistanceEngine v0.1.1

Statut : PROPOSITION (révision v0.1.1)  
Dépend de :

- Contrat fonctionnel v0.1 validé
- TripState v0.1

Objet : validation des positions GPS et production de distances métier applicables à TripState

---

## Changelog v0.1.1

Corrections issues du document d'arbitrage consolidé :

- **C1** — Filtrage des grands sauts : passage à un seuil dynamique
  `distance_max_plausible_m = (max_speed_kmh / 3.6) × elapsed_s × marge`.
  Rejet inconditionnel (ne dépend plus de la vitesse). Nouvelle raison `DISTANCE_TOO_LARGE`.
  `max_distance_delta_m` (seuil fixe) supprimé. Voir §5, §12, §17, §20, §21.
- **C2** — Distinction explicite entre `max_location_age_ms` (fraîcheur d'une position reçue)
  et `gps_lost_after_ms` (absence de réception). Voir §5, §16.1.
- **C3** — Le pseudo-algorithme §21 devient la source normative unique du micro-déplacement.
  §10 et §17 sont descriptifs. Voir §10, §17, §21.
- **A2** — Source de vitesse : `native_speed_kmh` primaire, `calculated_speed_kmh` repli,
  plancher stationnaire à `seuil_stationnaire`. Lissage reporté en v0.2. Voir §5, §13.
- **A3** — Gel stationnaire par vitesse native (< `seuil_stationnaire` → REFERENCE_ONLY,
  zéro distance). Repli `min_distance_delta_m = 3 m` si vitesse native absente. Voir §5, §10, §17, §21.
- **C5** — REFERENCE_ONLY pour micro-déplacement ou stationnaire porte `speed_kmh = 0`.
  Règle de vitesse périmée (`speed_timeout_ms`) portée par TripState/TripController. Voir §4, §13.

Interaction inter-règles à conserver : le seuil dynamique C1 dépend de `elapsed_s` ;
après une discontinuité GPS, `distance_reference_dirty` (TripController) force REFERENCE_ONLY,
donc aucune distance n'est calculée sur un grand `elapsed_s`. C1 et le re-référencement
se protègent mutuellement. Ne pas casser l'un sans réexaminer l'autre.

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
├── speed_kmh
├── accepted_position
├── should_update_reference
├── rejection_reason
└── event_type
```

Décision normative v0.1 : `DistanceEngine` produit uniquement `raw_distance_m`. `TripState` applique la calibration.

Précision v0.1.1 (C5) : `speed_kmh` est porté par la décision. Une décision `REFERENCE_ONLY`
due à un micro-déplacement ou à un état stationnaire porte `speed_kmh = 0`. Cela permet à
TripController de remettre la vitesse à zéro immédiatement à l'arrêt, sans attendre le timeout.

---

## 5. Configuration v0.1.1

Valeurs recommandées :

```text
max_accuracy_ok_m              = 15
max_accuracy_degraded_m        = 30
max_speed_kmh                  = 200
distance_jump_margin           = 1.5      # marge du seuil dynamique (C1)
min_distance_delta_m           = 3        # repli si vitesse native absente (A3)
seuil_stationnaire_kmh         = 1.8      # ≈ 0.5 m/s ; gel stationnaire + plancher affichage (A2/A3)
speed_timeout_ms               = 3000     # vitesse périmée (C5)
max_location_age_ms            = 5000     # fraîcheur d'une position REÇUE (C2)
gps_lost_after_ms              = 10000    # absence de réception → LOST (C2)
reference_reset_after_lost_ms  = 5000
```

Notes de configuration v0.1.1 :

- `max_distance_delta_m` (seuil fixe, ancien) est **supprimé** au profit du seuil dynamique (C1, voir §12).
- `max_speed_jump_kmh` (ancien) est **supprimé** : la validation des sauts passe désormais par
  la vitesse calculée (§11) et le seuil de distance dynamique (§12), sans paramètre intermédiaire.
- `seuil_stationnaire` est exprimé ici en km/h (1.8 ≈ 0.5 m/s) pour homogénéité d'affichage ;
  l'implémentation compare des vitesses. Un seul seuil partagé en v0.1.1 ; il pourra être scindé
  en deux paramètres (gel distance vs plancher affichage) en v0.2 si besoin.

Distinction des deux temporisations (C2) :

- `max_location_age_ms` (5000) : âge maximal d'une position **reçue**. Une position plus vieille
  est rejetée (`STALE_LOCATION`) même si le GPS émet encore.
- `gps_lost_after_ms` (10000) : durée d'**absence** de toute position exploitable au-delà de
  laquelle `gps_status = LOST`.
- Les deux ne sont pas redondants : le premier qualifie une donnée présente, le second un silence.
  Ils ne doivent jamais être fusionnés.

Lecture métier :

- `accuracy <= 15 m` → GPS OK ;
- `15 m < accuracy <= 30 m` → GPS DEGRADED ;
- `accuracy > 30 m` → GPS INVALID ou LOST selon fraîcheur, distance refusée ;
- vitesse calculée au-dessus de 200 km/h → rejet (§11) ;
- distance supérieure au seuil dynamique plausible → rejet `DISTANCE_TOO_LARGE` (§12) ;
- vitesse native < `seuil_stationnaire` → gel stationnaire, aucune distance (§10, A3) ;
- distance sous `min_distance_delta_m` (repli sans vitesse native) → ignorée (§10).

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

## 10. Gel stationnaire et filtrage du bruit à faible distance

Cette section est **descriptive**. La règle normative unique est le pseudo-algorithme §21 (C3).

### 10.1 Gel stationnaire (A3)

La meilleure barrière contre le gonflement à l'arrêt est la vitesse native, pas la distance
inter-points (le GPS dérive de 2–4 m à l'arrêt avec bon signal).

```text
Si native_speed_kmh disponible ET native_speed_kmh < seuil_stationnaire :
- decision = REFERENCE_ONLY
- raw_distance_m = 0
- should_update_reference = true
- speed_kmh = 0        (C5)
- aucune distance ajoutée, quel que soit raw_distance_m calculé
```

Ce test précède le calcul et la validation de distance.

### 10.2 Repli sans vitesse native

```text
Si native_speed_kmh absente :
- appliquer min_distance_delta_m = 3 m comme seuil de micro-déplacement
```

### 10.3 Micro-déplacement (vitesse native présente mais distance faible)

```text
Si raw_distance_m < min_distance_delta_m :
- ne pas ajouter de distance ;
- mettre à jour la référence si accuracy_m <= max_accuracy_ok_m ;
- speed_kmh = 0 si la cause est l'immobilité (C5).
```

Décision v0.1.1 : le comportement exact (REFERENCE_ONLY vs GPS_STATUS_ONLY) est fixé par §21.

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

## 12. Filtrage des grands sauts (seuil dynamique, C1)

Le rejet des grands sauts utilise un seuil dynamique fonction de l'intervalle réel entre deux
points, pas un seuil fixe. Cela suit la cadence GPS réelle au lieu de supposer 1 Hz.

Calcul :

```text
distance_max_plausible_m = (max_speed_kmh / 3.6) × elapsed_s × distance_jump_margin
```

Avec :

```text
max_speed_kmh         = 200
distance_jump_margin  = 1.5
```

Règle de rejet (inconditionnelle, ne dépend pas de la vitesse calculée) :

```text
Si raw_distance_m > distance_max_plausible_m :
→ REJECTED
→ rejection_reason = DISTANCE_TOO_LARGE
→ should_update_reference = false
```

La validation par vitesse calculée (§11) reste appliquée indépendamment : les deux filtres
sont en OU, une position est rejetée si l'un OU l'autre échoue.

Interaction avec le re-référencement (à conserver) : après une discontinuité GPS, `elapsed_s`
peut être grand, ce qui rendrait `distance_max_plausible_m` très permissif. Ce cas est neutralisé
en amont : `distance_reference_dirty` (TripController) force la première position post-discontinuité
en `REFERENCE_ONLY`, donc aucune distance n'est calculée sur ce grand `elapsed_s`. C1 et le
re-référencement se protègent mutuellement.

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

Décision recommandée (A2) :

```text
speed_for_display_kmh = native_speed_kmh si présente et fiable
sinon calculated_speed_kmh
```

Plancher stationnaire (A2) :

```text
Si native_speed_kmh < seuil_stationnaire :
→ speed_kmh = 0
```

Vitesse périmée (C5) — règle portée par TripState/TripController, rappelée ici pour cohérence :

```text
Si aucune décision ACCEPTED n'a mis à jour speed_kmh depuis speed_timeout_ms (3000 ms) :
→ speed_kmh = 0
```

Lissage : reporté en v0.2 (moyenne glissante 2–3 échantillons, sur l'affichage uniquement,
jamais sur la distance).

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

Cette matrice est **descriptive**. La règle normative unique est le pseudo-algorithme §21 (C3).

```text
Situation                                      Décision          Distance   Référence
-------------------------------------------------------------------------------------
Première position valide                       REFERENCE_ONLY    0          update
Stationnaire (native_speed < seuil)            REFERENCE_ONLY    0          update
Position valide, distance >= seuil             ACCEPTED          +          update
Position valide, distance < seuil, GPS OK      REFERENCE_ONLY    0          update
Position valide, distance < seuil, dégradé     GPS_STATUS_ONLY   0          no update
Précision trop mauvaise                        REJECTED          0          no update
Timestamp non monotone                         REJECTED          0          no update
Distance NaN/infinie                           REJECTED          0          no update
Distance > seuil dynamique plausible           REJECTED          0          no update
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
DISTANCE_TOO_LARGE
SPEED_TOO_HIGH
GPS_LOST
SESSION_NOT_ACTIVE
REFERENCE_MISSING
```

`DISTANCE_TOO_SMALL` n’est pas une erreur ; c’est un refus d’intégration normal.
`DISTANCE_TOO_LARGE` (v0.1.1, C1) indique un saut au-delà du seuil dynamique plausible.

---

## 21. Pseudo-algorithme normatif

Cette section est la **source normative unique** des décisions de DistanceEngine (C3).
Les sections §10, §12 et §17 sont descriptives et y renvoient.

```text
onLocationReceived(location, context):

  if location coordinates invalid:
      return REJECTED INVALID_COORDINATES

  if timestamp missing:
      return REJECTED MISSING_TIMESTAMP

  update last_gps_timestamp candidate

  if location too old (now - timestamp > max_location_age_ms):
      return REJECTED STALE_LOCATION

  gps_status = classifyGpsStatus(location.accuracy_m)

  if accuracy_m missing:
      return REJECTED ACCURACY_MISSING

  if accuracy_m > max_accuracy_degraded_m:
      return REJECTED ACCURACY_TOO_POOR

  if context.session_state != ACTIVE:
      return GPS_STATUS_ONLY

  if context.distance_reference_dirty:
      return REFERENCE_ONLY with current location, speed_kmh = native_speed_kmh or 0

  if context.last_valid_position is null:
      return REFERENCE_ONLY with current location, speed_kmh = native_speed_kmh or 0

  # --- Gel stationnaire (A3) : précède tout calcul de distance ---
  if native_speed_kmh available AND native_speed_kmh < seuil_stationnaire:
      return REFERENCE_ONLY with current location, speed_kmh = 0

  if location.timestamp <= last_valid_position.timestamp:
      return REJECTED NON_MONOTONIC_TIMESTAMP

  elapsed_s = location.timestamp - last_valid_position.timestamp

  if elapsed_s <= 0:
      return REJECTED NON_MONOTONIC_TIMESTAMP

  raw_distance_m = distance(last_valid_position, location)

  if raw_distance_m invalid (NaN, infinite, negative):
      return REJECTED INVALID_DISTANCE

  # --- Seuil dynamique de grand saut (C1) : rejet inconditionnel ---
  distance_max_plausible_m = (max_speed_kmh / 3.6) * elapsed_s * distance_jump_margin
  if raw_distance_m > distance_max_plausible_m:
      return REJECTED DISTANCE_TOO_LARGE

  calculated_speed_kmh = raw_distance_m / elapsed_s * 3.6

  if calculated_speed_kmh > max_speed_kmh:
      return REJECTED SPEED_TOO_HIGH

  # --- Micro-déplacement ---
  # min_distance_delta_m = 3 m en repli si native_speed_kmh absente (A3)
  if raw_distance_m < min_distance_delta_m:
      if gps_status == OK:
          return REFERENCE_ONLY with speed_kmh = 0
      else:
          return GPS_STATUS_ONLY

  speed_kmh = native_speed_kmh if reliable else calculated_speed_kmh
  if native_speed_kmh available AND native_speed_kmh < seuil_stationnaire:
      speed_kmh = 0

  return ACCEPTED raw_distance_m, speed_kmh
```

Ordre normatif des tests : coordonnées → timestamp présent → fraîcheur → précision → session
active → référence sale → référence absente → **gel stationnaire** → monotonie → distance valide
→ **grand saut dynamique** → vitesse calculée → micro-déplacement → ACCEPTED.

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
distance_jump_margin
min_distance_delta_m
seuil_stationnaire_kmh
speed_timeout_ms
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
DE-21 — Un saut au-delà du seuil dynamique plausible est rejeté (DISTANCE_TOO_LARGE), sans condition de vitesse. (C1)
DE-22 — En état stationnaire détecté par vitesse native (< seuil_stationnaire), aucune distance n'est produite. (A3)
DE-23 — Une décision REFERENCE_ONLY pour micro-déplacement ou stationnaire porte speed_kmh = 0. (C5)
DE-24 — La vitesse d'affichage est native_speed_kmh si fiable, sinon calculated_speed_kmh ; nulle sous seuil_stationnaire. (A2)
DE-25 — max_location_age_ms (fraîcheur reçue) et gps_lost_after_ms (absence de réception) ne sont jamais fusionnés. (C2)
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

## Seuils v0.1.1

- GPS OK : précision <= 15 m ;
- GPS dégradé : précision <= 30 m ;
- GPS rejeté : précision > 30 m ;
- vitesse maximale plausible : 200 km/h ;
- grand saut : raw_distance_m > (max_speed_kmh / 3.6) × elapsed_s × 1.5 → rejet (C1) ;
- gel stationnaire : vitesse native < seuil_stationnaire (≈ 0.5 m/s) → aucune distance (A3) ;
- micro-déplacement ignoré (repli sans vitesse native) : < 3 m (A3) ;
- vitesse périmée : retour à 0 après 3 s sans ACCEPTED (C5) ;
- GPS perdu : aucune position récente depuis 10 s.

## Invariant central

```text
DistanceEngine préfère sous-compter légèrement plutôt qu’ajouter une distance fausse.
```
