# Trip Meter Rallye Android — TripController v0.1.2

Statut : PROPOSITION (révision v0.1.2)  
Dépend de :

- Contrat fonctionnel v0.1 validé
- TripState v0.1.2
- DistanceEngine v0.1.2
- LocationEngine v0.1

Objet : couche de commande métier entre l’interface utilisateur et TripState

---

## Changelog v0.1.2 (passe de cohérence globale)

- **G1** — `GPS_DISCONTINUITY` ajouté aux événements système reçus (§3.2), avec règle de
  traitement : `distance_reference_dirty = true`, log `REFERENCE_RESET` (source SYSTEM, note =
  reason). Ajouté aux cas de marquage dirty (§9). Voir §3.2, §8.6, §9.
- **G6** — `SERVICE_STARTED` / `SERVICE_STOPPED` ajoutés aux événements système (§3.2), note de
  lifecycle léger à `APP_RESTORED` (§10). Pas de contrat lifecycle complet en v0.1.

## Changelog v0.1.1

- **C5** — Sur décision REFERENCE_ONLY portant `speed_kmh = 0`, le contrôleur applique
  `speed_kmh = 0`. Voir §8.3.
- **A4** — Persistance : état mémoire = source de vérité ; mutations métier explicites
  persistées immédiatement ; accumulation GPS throttlée (`persist_throttle_ms = 5000`) ;
  pas de rollback de l'affichage en cas d'échec. Voir §16.
- **M2** — `SET_STAGE_DISTANCE` et `SET_TOTAL_DISTANCE` ajoutés à la liste d'entrée §3.1.
- **M3** — `SET_TOTAL_DISTANCE` : unique voie de diminution du total, note explicite. Voir §7.10.
- **M4** — `RESET_STAGE` / `CORRECT_STAGE` en STOPPED : intention assumée. Voir §7.5, §7.7.

---

## 1. Rôle de TripController

`TripController` reçoit les commandes utilisateur et les événements validés, puis applique les mutations autorisées sur `TripState`.

Il est le gardien des règles métier.

```text
UI / boutons / gestes
→ TripController
→ validation commande
→ mutation TripState
→ persistance
→ affichage mis à jour
```

Principe central :

```text
L’interface demande.
TripController arbitre.
TripState stocke.
```

L’UI ne modifie jamais directement les compteurs.

---

## 2. Responsabilités

`TripController` doit :

- recevoir les commandes utilisateur ;
- vérifier si la commande est autorisée ;
- appliquer la mutation correspondante ;
- refuser proprement les commandes illégitimes ;
- journaliser les événements métier ;
- déclencher la persistance après mutation ;
- garantir l’idempotence des commandes répétées ;
- protéger les actions destructrices.

Il ne doit pas :

- lire directement le GPS ;
- calculer une distance GPS ;
- dessiner l’écran ;
- appliquer une distance non validée ;
- inventer une correction automatique ;
- masquer une commande refusée.

---

## 3. Entrées du contrôleur

### 3.1 Commandes utilisateur

```text
START_SESSION
PAUSE_SESSION
RESUME_SESSION
STOP_SESSION
RESET_STAGE
RESET_TOTAL
CORRECT_STAGE
CORRECT_TOTAL
SET_STAGE_DISTANCE
SET_TOTAL_DISTANCE
SET_CALIBRATION
```

### 3.2 Événements système

```text
DISTANCE_DECISION
GPS_LOST
GPS_RECOVERED
GPS_DISCONTINUITY
SERVICE_STARTED
SERVICE_STOPPED
APP_RESTORED
SCREEN_LOCK_REQUEST
```

`GPS_DISCONTINUITY` (G1) et `SERVICE_STARTED` / `SERVICE_STOPPED` (G6) sont émis par LocationEngine
(LocationEngine §10.2). Leur traitement est décrit en §8.6 (discontinuité) et §10 (lifecycle léger).

### 3.3 Commandes internes

```text
RESTORE_SAVED_SESSION
PERSIST_STATE
RESET_DISTANCE_REFERENCE
```

---

## 4. Sorties du contrôleur

Chaque commande retourne une décision explicite.

```text
CommandResult
├── status
├── command
├── previous_state
├── new_state
├── message
├── event_logged
└── persistence_required
```

Statuts possibles :

```text
APPLIED
IGNORED_IDEMPOTENT
REJECTED
CONFIRMATION_REQUIRED
ERROR
```

---

## 5. Principe d’idempotence

Une commande répétée ne doit pas casser l’état.

Exemples :

```text
pauseSession alors que la session est déjà PAUSED
→ IGNORED_IDEMPOTENT

resumeSession alors que la session est déjà ACTIVE
→ IGNORED_IDEMPOTENT
```

Décision v0.1 :

```text
Les commandes de changement d’état déjà satisfaites retournent IGNORED_IDEMPOTENT.
Les commandes de correction ou reset explicite retournent APPLIED si elles sont volontairement demandées.
```

Raison : un appui répété sur `+100 m` doit bien ajouter deux fois 100 m.

---

## 6. États contrôlés

Le contrôleur s’appuie sur :

```text
session_state ∈ {STOPPED, ACTIVE, PAUSED}
```

---

## 7. Commandes utilisateur normatives

### 7.1 `startSession`

Commande : `START_SESSION`

Autorisé si :

```text
session_state = STOPPED
```

Effets :

```text
session_state = ACTIVE
total_distance_m = 0
stage_distance_m = 0
speed_kmh = 0
session_started_at = now
session_updated_at = now
distance_reference_dirty = true
```

Log : `SESSION_STARTED`  
Persistance : obligatoire  
Résultat : `APPLIED`

Si déjà `ACTIVE` : `IGNORED_IDEMPOTENT`.

Si `PAUSED` :

```text
REJECTED
message = "Session déjà existante en pause. Reprendre ou arrêter avant de démarrer une nouvelle session."
```

Décision importante : `START_SESSION` ne reprend jamais une session en pause.

### 7.2 `pauseSession`

Commande : `PAUSE_SESSION`

Autorisé si :

```text
session_state = ACTIVE
```

Effets :

```text
session_state = PAUSED
speed_kmh = 0
session_updated_at = now
distance_reference_dirty = true
```

Log : `SESSION_PAUSED`  
Persistance : obligatoire

Si déjà `PAUSED` : `IGNORED_IDEMPOTENT`.

Si `STOPPED` :

```text
REJECTED
message = "Aucune session active à mettre en pause."
```

### 7.3 `resumeSession`

Commande : `RESUME_SESSION`

Autorisé si :

```text
session_state = PAUSED
```

Effets :

```text
session_state = ACTIVE
speed_kmh = 0
session_updated_at = now
distance_reference_dirty = true
```

Log :

```text
SESSION_RESUMED
REFERENCE_RESET
```

Persistance : obligatoire.

Règle critique : la prochaine position GPS valide devient `REFERENCE_ONLY`. Aucune distance n’est ajoutée entre la pause et la reprise.

Si déjà `ACTIVE` : `IGNORED_IDEMPOTENT`.  
Si `STOPPED` : `REJECTED`.

### 7.4 `stopSession`

Commande : `STOP_SESSION`

Autorisé si :

```text
session_state = ACTIVE
ou
session_state = PAUSED
```

Effets :

```text
session_state = STOPPED
speed_kmh = 0
session_updated_at = now
distance_reference_dirty = true
```

Distances conservées.

Log : `SESSION_STOPPED`  
Persistance : obligatoire

Si déjà `STOPPED` : `IGNORED_IDEMPOTENT`.

Décision v0.1 : `STOP_SESSION` ne remet jamais les distances à zéro.

### 7.5 `resetStage`

Commande : `RESET_STAGE`

Autorisé dans tous les états.

Effets :

```text
previous_stage_distance_m = stage_distance_m
stage_distance_m = 0
session_updated_at = now
```

`total_distance_m` inchangé.

Log : `STAGE_RESET`  
Persistance : obligatoire

Si `stage_distance_m = 0` : `APPLIED`, car l’utilisateur peut vouloir poser explicitement un jalon.

Décision v0.1.1 (M4) : `RESET_STAGE` reste volontairement autorisé en STOPPED, pour recaler ou
nettoyer une étape après arrêt ou avant archivage. Cette action ne réactive jamais la session
et n'ajoute jamais de distance GPS.

### 7.6 `resetTotal`

Commande : `RESET_TOTAL`

Autorisé si :

```text
confirmation utilisateur explicite = true
```

Effets :

```text
previous_total_distance_m = total_distance_m
previous_stage_distance_m = stage_distance_m
total_distance_m = 0
stage_distance_m = 0
speed_kmh = 0
session_updated_at = now
distance_reference_dirty = true
```

Log :

```text
TOTAL_RESET
STAGE_RESET
REFERENCE_RESET
```

Persistance : obligatoire.

Si confirmation absente :

```text
CONFIRMATION_REQUIRED
message = "La remise à zéro du total efface aussi l'étape. Confirmation requise."
```

Décision v0.1 : `RESET_TOTAL` remet aussi l’étape à zéro.

Protection recommandée : appui long, boîte de confirmation ou double appui temporisé.

### 7.7 `correctStage(delta_m)`

Commande : `CORRECT_STAGE`

Paramètre : `delta_m`

Valeurs UI v0.1 :

```text
+10
-10
+100
-100
```

Autorisé si :

```text
delta_m ≠ 0
```

Effets :

```text
previous_stage_distance_m = stage_distance_m
stage_distance_m = max(0, stage_distance_m + delta_m)
session_updated_at = now
```

`total_distance_m` inchangé.

Log : `STAGE_CORRECTED`  
Persistance : obligatoire

Invariant : `CORRECT_STAGE` ne modifie jamais `total_distance_m`.

Décision v0.1.1 (M4) : `CORRECT_STAGE` reste volontairement autorisé en STOPPED (recalage avant
archivage). Sans effet sur la session ni sur la distance GPS.

### 7.8 `correctTotal(delta_m)`

Commande : `CORRECT_TOTAL`

Paramètre : `delta_m`

Valeurs UI possibles :

```text
+10
-10
+100
-100
ou saisie manuelle
```

Autorisé si :

```text
delta_m ≠ 0
```

Effets :

```text
previous_total_distance_m = total_distance_m
total_distance_m = max(0, total_distance_m + delta_m)
session_updated_at = now
```

`stage_distance_m` inchangé.

Log : `TOTAL_CORRECTED`  
Persistance : obligatoire

Invariant : `CORRECT_TOTAL` ne modifie jamais `stage_distance_m`.

### 7.9 `setStageDistance(value_m)`

Commande optionnelle mais utile : `SET_STAGE_DISTANCE`

Paramètre : `value_m >= 0`

Effets :

```text
previous_stage_distance_m = stage_distance_m
stage_distance_m = value_m
session_updated_at = now
```

Log : `STAGE_SET`  
Persistance : obligatoire

Utilité : recaler l’étape exactement sur le roadbook.

### 7.10 `setTotalDistance(value_m)`

Commande optionnelle : `SET_TOTAL_DISTANCE`

Paramètre : `value_m >= 0`

Effets :

```text
previous_total_distance_m = total_distance_m
total_distance_m = value_m
session_updated_at = now
```

Log : `TOTAL_SET`  
Persistance : obligatoire

Invariant : `SET_TOTAL_DISTANCE` ne modifie jamais `stage_distance_m`.

Décision v0.1.1 (M3) : `SET_TOTAL_DISTANCE` est l'unique commande pouvant **réduire**
`total_distance_m`. Cette diminution est volontaire, journalisée (`TOTAL_SET`) et soumise à
confirmation (TripDisplay §29.2). L'interdiction de diminution automatique (I-05) reste entière.

### 7.11 `setCalibrationFactor(value)`

Commande : `SET_CALIBRATION`

Paramètre : `value > 0`

Valeurs recommandées : `0.9500` à `1.0500` en usage normal.

Effets :

```text
previous_calibration_factor = calibration_factor
calibration_factor = value
session_updated_at = now
```

Log : `CALIBRATION_CHANGED`  
Persistance : obligatoire

Décision v0.1 : calibration non rétroactive.

---

## 8. Commandes de distance issues de DistanceEngine

### 8.1 `handleDistanceDecision(decision)`

Commande interne : `HANDLE_DISTANCE_DECISION`

Entrée : `DistanceDecision`

### 8.2 Si décision = ACCEPTED

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
speed_kmh = decision.speed_kmh si disponible
gps_status = decision.gps_status
gps_accuracy_m = decision.gps_accuracy_m
last_valid_position = decision.accepted_position
session_updated_at = now
```

Log utilisateur : aucun obligatoire.  
Persistance : obligatoire.

Invariant : le contrôleur ne doit jamais appliquer une distance si `TripState` n’est pas `ACTIVE`.

### 8.3 Si décision = REFERENCE_ONLY

Effets :

```text
gps_status = decision.gps_status
gps_accuracy_m = decision.gps_accuracy_m
last_valid_position = decision.accepted_position
distance_reference_dirty = false
speed_kmh = decision.speed_kmh   (0 si micro-déplacement ou stationnaire, C5)
session_updated_at = now
```

Aucune distance ajoutée.

Persistance : selon la stratégie throttlée (§16). Un changement de `session_state` ou un
événement métier force le flush ; une REFERENCE_ONLY de routine à l'arrêt n'impose pas
d'écriture immédiate. (A4)

Log selon contexte : `REFERENCE_RESET`, `GPS_RECOVERED` si applicable.

### 8.4 Si décision = REJECTED

Effets :

```text
gps_status = INVALID ou decision.gps_status
gps_accuracy_m = decision.gps_accuracy_m
last_valid_position inchangée
session_updated_at = now
```

Aucune distance ajoutée.

Décision v0.1 : persistance obligatoire seulement si `gps_status` change.

### 8.5 Si décision = GPS_STATUS_ONLY

Effets :

```text
gps_status = decision.gps_status
gps_accuracy_m = decision.gps_accuracy_m
session_updated_at = now
```

Aucune distance ajoutée.

Persistance obligatoire seulement si changement de statut GPS.

---

## 8.6 Traitement de GPS_DISCONTINUITY (G1)

Événement système : `GPS_DISCONTINUITY`, émis par LocationEngine (LocationEngine §8) en cas de
rupture de continuité de mesure (gap temporel, perte GPS, redémarrage de service, reprise).

À réception :

```text
distance_reference_dirty = true
log REFERENCE_RESET
  source = SYSTEM
  note = reason de la discontinuité (TIME_GAP | GPS_LOST | SERVICE_RESTART | RESUME)
aucune mutation de compteur
aucun changement de session_state
```

Précisions normatives :

- `GPS_DISCONTINUITY` est l'événement système **reçu** ; `REFERENCE_RESET` est l'effet métier
  **journalisé**. Pas de nouvel événement de log dédié en v0.1.
- TripController reste seul writer de `distance_reference_dirty`. LocationEngine signale,
  TripController arbitre.
- Conséquence : la prochaine position valide sera traitée `REFERENCE_ONLY` par DistanceEngine
  (`context.distance_reference_dirty`), sans ajout de distance. Cela neutralise un éventuel grand
  `elapsed_s` post-discontinuité et protège le seuil dynamique de grand saut (DistanceEngine C1).
- `GPS_DISCONTINUITY` et `GPS_LOST` sont distincts (LocationEngine §8.2) : une perte GPS émet les
  deux ; un simple gap temporel peut n'émettre que `GPS_DISCONTINUITY`.

Champ conceptuel :

```text
distance_reference_dirty
```

Cas où `distance_reference_dirty = true` :

```text
startSession
pauseSession
resumeSession
stopSession
resetTotal
restore ACTIVE as PAUSED
GPS_LOST
GPS_DISCONTINUITY
```

Cas où il redevient `false` :

```text
première décision REFERENCE_ONLY avec position valide
```

Règle :

```text
Tant que distance_reference_dirty = true,
aucune décision ACCEPTED ne doit être appliquée.
```

---

## 10. Restauration au lancement

Commande : `RESTORE_SAVED_SESSION`

Cas `STOPPED` sauvegardé :

```text
session_state = STOPPED
distances conservées
speed_kmh = 0
```

Cas `PAUSED` sauvegardé :

```text
session_state = PAUSED
distances conservées
speed_kmh = 0
distance_reference_dirty = true
```

Cas `ACTIVE` sauvegardé :

```text
session_state = PAUSED
distances conservées
speed_kmh = 0
distance_reference_dirty = true
```

Log : `SESSION_RESTORED_AS_PAUSED`

Décision v0.1 : une session `ACTIVE` interrompue est toujours restaurée en `PAUSED`.

Lifecycle service à `APP_RESTORED` (G6, alignement léger) :

```text
Si l'état restauré est PAUSED :
  → le service de localisation est relancé en cadence réduite (LocationEngine §9.3).
Si l'état restauré est STOPPED :
  → aucun service de localisation n'est démarré.
```

Cette note pose le lien minimal entre restauration et service. Un contrat de lifecycle de service
complet n'est pas rédigé en v0.1 ; `SERVICE_STARTED` / `SERVICE_STOPPED` sont reçus comme événements
système (§3.2) et l'orchestration fine est laissée à l'implémentation.

---

## 11. Confirmations et protections

Actions protégées :

```text
RESET_TOTAL
START_SESSION si une session non arrêtée existe
éventuellement SET_TOTAL_DISTANCE
```

Actions non protégées :

```text
RESET_STAGE
CORRECT_STAGE +10/-10/+100/-100
CORRECT_TOTAL +10/-10/+100/-100
PAUSE_SESSION
RESUME_SESSION
STOP_SESSION
```

Décision sur `RESET_STAGE` : pas de confirmation, car l’action doit rester rapide en rallye.

---

## 12. Commande optionnelle : undo

Commande : `UNDO_LAST_CORRECTION`

Périmètre v0.1 optionnel :

```text
annuler dernière correction étape
annuler dernière correction total
annuler dernier reset étape
```

Décision recommandée : prévoir dans le modèle d’événements, ne pas implémenter dans l’UI v0.1.

---

## 13. Journalisation métier

Événements utilisateur :

```text
SESSION_STARTED
SESSION_PAUSED
SESSION_RESUMED
SESSION_STOPPED
STAGE_RESET
TOTAL_RESET
STAGE_CORRECTED
TOTAL_CORRECTED
STAGE_SET
TOTAL_SET
CALIBRATION_CHANGED
```

Événements système :

```text
GPS_LOST
GPS_RECOVERED
REFERENCE_RESET
SESSION_RESTORED_AS_PAUSED
```

Structure d’événement :

```text
TripEvent
├── timestamp
├── type
├── source
├── target
├── delta_m
├── previous_value
├── new_value
├── session_state_before
├── session_state_after
└── note
```

Sources possibles :

```text
USER
SYSTEM
DISTANCE_ENGINE
RESTORE
```

---

## 14. Matrice des commandes utilisateur

```text
Commande              STOPPED              ACTIVE               PAUSED
---------------------------------------------------------------------------
START_SESSION          APPLIED              IDEMPOTENT/REJECTED  REJECTED
PAUSE_SESSION          REJECTED             APPLIED              IDEMPOTENT
RESUME_SESSION         REJECTED             IDEMPOTENT           APPLIED
STOP_SESSION           IDEMPOTENT           APPLIED              APPLIED
RESET_STAGE            APPLIED              APPLIED              APPLIED
RESET_TOTAL            CONFIRM/APPLIED      CONFIRM/APPLIED      CONFIRM/APPLIED
CORRECT_STAGE          APPLIED              APPLIED              APPLIED
CORRECT_TOTAL          APPLIED              APPLIED              APPLIED
SET_STAGE_DISTANCE     APPLIED              APPLIED              APPLIED
SET_TOTAL_DISTANCE     APPLIED              APPLIED              APPLIED
SET_CALIBRATION        APPLIED              APPLIED              APPLIED
```

Décision recommandée v0.1 :

```text
ACTIVE → START_SESSION = IGNORED_IDEMPOTENT
PAUSED → START_SESSION = REJECTED
```

---

## 15. Matrice des effets sur compteurs

```text
Commande              Total        Étape        Vitesse      Référence distance
-----------------------------------------------------------------------------
START_SESSION          reset        reset        0            dirty
PAUSE_SESSION          idem         idem         0            dirty
RESUME_SESSION         idem         idem         0            dirty
STOP_SESSION           idem         idem         0            dirty
RESET_STAGE            idem         reset        idem         idem
RESET_TOTAL            reset        reset        0            dirty
CORRECT_STAGE          idem         +/-/set      idem         idem
CORRECT_TOTAL          +/-/set      idem         idem         idem
SET_CALIBRATION        idem         idem         idem         idem
ACCEPTED_DISTANCE      +            +            update       update
REFERENCE_ONLY         idem         idem         idem         clean
REJECTED_DISTANCE      idem         idem         idem         idem
GPS_STATUS_ONLY        idem         idem         idem         idem
```

---

## 16. Règles de persistance (v0.1.1, A4)

Principe : l'état mémoire de `TripState` est la **source de vérité immédiate**. La persistance
est asynchrone et best-effort ; un échec ne provoque jamais de rollback de l'affichage, mais
remonte un statut `ERROR` visible (§17, TripDisplay §16.1).

### 16.1 Deux classes de mutation

Mutations métier explicites → **persistance immédiate** :

```text
START_SESSION
PAUSE_SESSION
RESUME_SESSION
STOP_SESSION
RESET_STAGE
RESET_TOTAL
CORRECT_STAGE
CORRECT_TOTAL
SET_STAGE_DISTANCE
SET_TOTAL_DISTANCE
SET_CALIBRATION
RESTORE_ACTIVE_AS_PAUSED
```

Accumulation de distance GPS → **persistance throttlée** :

```text
ACCEPTED_DISTANCE
REFERENCE_ONLY
```

### 16.2 Throttle

```text
persist_throttle_ms = 5000
```

L'accumulation GPS est flushée :

- au plus toutes les `persist_throttle_ms` ;
- **et** systématiquement à chaque mutation métier explicite ;
- **et** à chaque changement de `session_state`.

### 16.3 Persistance conditionnelle des statuts

```text
REJECTED_DISTANCE
GPS_STATUS_ONLY
```

Persist si `gps_status` change ou événement système important.

### 16.4 Garanties

- Aucune mutation métier explicite appliquée ne reste uniquement en mémoire.
- Au pire, une interruption brutale perd la distance accumulée depuis le dernier flush throttlé
  (≤ `persist_throttle_ms`), jamais la session ni les compteurs métier persistés.
- La règle du contrat fonctionnel « aucune session active perdue sans confirmation » reste vraie :
  le throttle ne concerne que l'accumulation fine de distance, pas l'état de session.

---

## 17. Gestion des erreurs

Commande refusée :

```text
status = REJECTED
message explicite
aucune mutation TripState
aucune persistance obligatoire
```

Paramètre invalide :

```text
delta_m = 0
value_m < 0
calibration_factor <= 0
```

Résultat : `REJECTED`.

Erreur de persistance :

```text
status = ERROR
message = "État modifié mais sauvegarde échouée."
```

Décision v0.1 : une erreur de persistance doit être visible.

---

## 18. Interaction avec l’UI

L’UI ne connaît que des intentions.

Exemples :

```text
Bouton Start       → START_SESSION
Bouton Pause       → PAUSE_SESSION
Bouton Étape 0     → RESET_STAGE
Bouton +10 étape   → CORRECT_STAGE(+10)
Bouton -10 étape   → CORRECT_STAGE(-10)
Bouton +100 étape  → CORRECT_STAGE(+100)
Bouton -100 étape  → CORRECT_STAGE(-100)
```

L’UI ne doit jamais modifier elle-même `TripState`.

---

## 19. Interaction avec DistanceEngine

Flux nominal :

```text
LocationEngine reçoit une position GPS brute
DistanceEngine produit DistanceDecision
TripController.handleDistanceDecision(decision)
TripController mute TripState si autorisé
TripState persisté
TripDisplay actualisé
```

Règle de sécurité : même si `DistanceEngine` retourne `ACCEPTED`, `TripController` revérifie `session_state = ACTIVE`.

---

## 20. Commandes rapides rallye

Alias :

```text
STAGE_PLUS_10    → CORRECT_STAGE(+10)
STAGE_MINUS_10   → CORRECT_STAGE(-10)
STAGE_PLUS_100   → CORRECT_STAGE(+100)
STAGE_MINUS_100  → CORRECT_STAGE(-100)

TOTAL_PLUS_10    → CORRECT_TOTAL(+10)
TOTAL_MINUS_10   → CORRECT_TOTAL(-10)
TOTAL_PLUS_100   → CORRECT_TOTAL(+100)
TOTAL_MINUS_100  → CORRECT_TOTAL(-100)
```

---

## 21. Priorité en cas de commandes simultanées

Règle v0.1 :

```text
TripController traite les commandes séquentiellement.
Une seule mutation TripState à la fois.
```

Ordre recommandé :

1. commandes utilisateur destructrices ou explicites ;
2. changements session start/pause/resume/stop ;
3. corrections utilisateur ;
4. décisions DistanceEngine ;
5. GPS status only.

Invariant : une commande utilisateur ne doit pas être perdue derrière un flux GPS.

---

## 22. Verrouillage anti-double-tap

Non protégées :

```text
CORRECT_STAGE
CORRECT_TOTAL
```

Protégées :

```text
RESET_TOTAL
START_SESSION si session existante
SET_TOTAL_DISTANCE
```

Semi-protégée :

```text
RESET_STAGE
```

Décision v0.1 : `RESET_STAGE` sans confirmation, mais bouton visuellement distinct, éventuellement appui long.

---

## 23. Invariants TripController v0.1

```text
TC-01 — L’UI ne modifie jamais TripState directement.
TC-02 — Toute commande utilisateur passe par TripController.
TC-03 — Toute commande retourne un CommandResult explicite.
TC-04 — Toute mutation métier appliquée est persistée.
TC-05 — Une commande refusée ne modifie jamais TripState.
TC-06 — Une commande idempotente répétée ne modifie pas inutilement TripState.
TC-07 — RESET_TOTAL exige une confirmation utilisateur.
TC-08 — RESET_TOTAL remet total_distance_m et stage_distance_m à zéro.
TC-09 — RESET_STAGE ne modifie jamais total_distance_m.
TC-10 — CORRECT_STAGE ne modifie jamais total_distance_m.
TC-11 — CORRECT_TOTAL ne modifie jamais stage_distance_m.
TC-12 — SET_CALIBRATION exige une valeur strictement positive.
TC-13 — Une distance ACCEPTED n’est appliquée que si session_state = ACTIVE.
TC-14 — Aucune distance issue du GPS n’est appliquée en PAUSED.
TC-15 — Aucune distance issue du GPS n’est appliquée en STOPPED.
TC-16 — Après pause, reprise, stop, start ou reset total, la référence distance est marquée dirty.
TC-17 — Une session ACTIVE restaurée après interruption est convertie en PAUSED.
TC-18 — Les actions destructrices sont protégées.
TC-19 — Les corrections utilisateur sont toujours journalisées.
TC-20 — Le contrôleur ne calcule jamais lui-même une distance GPS.
TC-21 — Sur REFERENCE_ONLY portant speed_kmh = 0, le contrôleur applique speed_kmh = 0. (C5)
TC-22 — L'état mémoire est la source de vérité ; un échec de persistance ne provoque jamais de rollback de l'affichage. (A4)
TC-23 — Les mutations métier explicites sont persistées immédiatement ; l'accumulation GPS est throttlée. (A4)
TC-24 — SET_TOTAL_DISTANCE est l'unique commande pouvant réduire total_distance_m. (M3)
```

---

## 24. Version compacte normative

## Rôle

TripController reçoit les commandes utilisateur et les décisions du `DistanceEngine`.  
Il valide les commandes, applique les mutations autorisées sur `TripState`, journalise les événements et déclenche la persistance.

## Commandes utilisateur

- `START_SESSION`
- `PAUSE_SESSION`
- `RESUME_SESSION`
- `STOP_SESSION`
- `RESET_STAGE`
- `RESET_TOTAL`
- `CORRECT_STAGE(delta_m)`
- `CORRECT_TOTAL(delta_m)`
- `SET_STAGE_DISTANCE(value_m)`
- `SET_TOTAL_DISTANCE(value_m)`
- `SET_CALIBRATION(value)`

## Commandes internes

- `HANDLE_DISTANCE_DECISION`
- `RESTORE_SAVED_SESSION`
- `RESET_DISTANCE_REFERENCE`

## Résultats

- `APPLIED`
- `IGNORED_IDEMPOTENT`
- `REJECTED`
- `CONFIRMATION_REQUIRED`
- `ERROR`

## Règles principales

- L’UI ne modifie jamais directement `TripState`.
- Toute commande passe par `TripController`.
- Une commande refusée ne modifie rien.
- Une mutation appliquée est persistée.
- `RESET_TOTAL` exige confirmation et remet total + étape à zéro.
- `RESET_STAGE` ne modifie jamais le total.
- `CORRECT_STAGE` ne modifie jamais le total.
- `CORRECT_TOTAL` ne modifie jamais l’étape.
- Une distance GPS validée n’est appliquée qu’en `ACTIVE`.
- Après pause, reprise ou perte GPS, aucune distance de rattrapage n’est admise.
- Les commandes utilisateur explicites sont journalisées.
