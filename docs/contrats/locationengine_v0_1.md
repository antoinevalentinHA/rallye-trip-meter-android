# Trip Meter Rallye Android — LocationEngine v0.1

Statut : VALIDÉ  
Dépend de :

- Contrat fonctionnel v0.2 validé
- TripState v0.1.2
- DistanceEngine v0.1.2
- TripController v0.1.2

Objet : acquisition de la localisation Android brute, détection de perte GPS, alimentation de DistanceEngine et de TripState en données GPS

---

## Décisions validées (arbitrages §15)

- **§9.3** — Acquisition en PAUSED : **option A**. Acquisition maintenue à cadence réduite
  (`location_interval_paused_ms = 2000`), pour le statut GPS et la reprise immédiate uniquement.
  Aucune distance produite en PAUSED.
- **§11** — Écriture directe de `last_gps_timestamp` par LocationEngine, et de **ce seul champ**.
  LocationEngine n'écrit jamais `gps_status`, `last_valid_position`, `distance_reference_dirty`,
  les compteurs, ni `session_state`.
- **§8** — Événement `GPS_DISCONTINUITY` **distinct** de `GPS_LOST`, jamais fusionné. TripController
  reste seul writer de `distance_reference_dirty`.

---

## 1. Rôle de LocationEngine

`LocationEngine` est la couche d'acquisition GPS. Il encapsule l'API Android de localisation et
fournit un flux de positions brutes au reste du système.

```text
Android FusedLocationProviderClient
→ LocationEngine
→ positions GPS brutes normalisées
→ DistanceEngine (validation)
→ TripController (arbitrage)
→ TripState (état)
```

Principe central :

```text
LocationEngine acquiert.
LocationEngine ne valide pas la distance.
LocationEngine ne calcule pas de distance métier.
LocationEngine ne décide pas de l'état de session.
LocationEngine n'affiche rien.
```

Il fait une seule chose : transformer le flux Android en positions brutes normalisées, détecter
l'absence de flux, et signaler les discontinuités.

Frontière nette avec DistanceEngine : LocationEngine dit « voici une position, ou voici un
silence ». DistanceEngine dit « cette position produit-elle de la distance ». LocationEngine ne
juge jamais la qualité métier d'une position au-delà de sa validité structurelle.

---

## 2. Source Android v0.1

Décision v0.1 :

```text
FusedLocationProviderClient = source primaire et unique.
LocationManager / GPS_PROVIDER = hors périmètre v0.1.
```

Raisons :

- API standard Android/Google pour des mises à jour régulières de localisation ;
- plus simple pour un MVP robuste ;
- suffisant tant que DistanceEngine reste conservateur.

Étude v0.2 (hors périmètre ici) :

- `LocationManager` / `GPS_PROVIDER` comme fallback ou mode expert si le comportement réel du
  FusedLocationProvider s'avère insuffisant (lissage interne masquant des arrêts, fusion de
  sources dégradant la précision en rallye, etc.).

---

## 3. Invariant fondateur sur la cadence

Décision v0.1 (structurante) :

```text
Les paramètres de cadence du FusedLocationProvider sont des OBJECTIFS, pas des GARANTIES.
```

Conséquences contractuelles :

- LocationEngine ne suppose jamais une cadence de 1 Hz ;
- LocationEngine mesure l'intervalle réel entre deux positions reçues ;
- aucune règle de sûreté du système ne repose sur une cadence garantie ;
- DistanceEngine consomme l'intervalle réel (`elapsed_s`) pour son seuil dynamique de grand saut
  (C1, v0.1.1), jamais une valeur supposée.

Cet invariant est la raison d'être de plusieurs règles ci-dessous. Il ne doit pas être affaibli.

---

## 4. Cadence cible

Configuration v0.1 :

```text
location_interval_target_ms      = 1000     # objectif ~1 Hz en ACTIVE
location_min_interval_ms         = 500      # plancher accepté (fastest interval)
location_priority                = HIGH_ACCURACY
```

Règles :

- en `ACTIVE`, demander une cadence d'objectif `location_interval_target_ms` ;
- accepter que la cadence réelle varie (au-dessus comme en dessous de l'objectif) ;
- ne jamais rejeter une position au seul motif qu'elle arrive « trop tôt » ou « trop tard » —
  la validité temporelle est jugée par DistanceEngine (monotonie, fraîcheur).

Comportement par état de session (voir §9 pour le service) :

```text
ACTIVE   → mises à jour à location_interval_target_ms
PAUSED   → mises à jour maintenues (statut GPS reste utile) ou réduites — voir §9.3
STOPPED  → acquisition arrêtée
```

---

## 5. Position brute normalisée

LocationEngine convertit chaque `Location` Android en une structure normalisée :

```text
RawLocation
├── latitude
├── longitude
├── timestamp_ms          # horloge de réception normalisée (voir §6)
├── accuracy_m            # null si absente
├── native_speed_kmh      # null si hasSpeed() == false
├── native_speed_accuracy # optionnel, si disponible
└── provider
```

Règles de normalisation :

- `native_speed_kmh` est renseigné uniquement si `Location.hasSpeed()` est vrai ; sinon `null` ;
- `accuracy_m` est renseigné uniquement si `Location.hasAccuracy()` est vrai ; sinon `null` ;
- LocationEngine ne fabrique jamais une vitesse ou une précision absente ;
- LocationEngine ne filtre pas les positions imprécises : il les transmet, DistanceEngine décide.

Fourniture de `native_speed_kmh` (contrainte intégrée) : c'est la source primaire de vitesse de
DistanceEngine (A2, v0.1.1) et la base du gel stationnaire (A3). LocationEngine doit la fournir
chaque fois que l'API la fournit.

---

## 6. Horodatage et alimentation de `last_gps_timestamp`

### 6.1 Source d'horodatage

Décision v0.1 :

```text
timestamp_ms = horloge monotone de réception (SystemClock.elapsedRealtime base)
```

Raison : l'horloge murale (`System.currentTimeMillis`) peut sauter (synchro réseau, changement
manuel) et casserait la monotonie exigée par DistanceEngine. Une base monotone garantit
`timestamp` strictement croissant pour des positions successives.

`Location.getElapsedRealtimeNanos()` est la source recommandée quand disponible, ramenée en ms.

### 6.2 Alimentation de `last_gps_timestamp`

Contrainte intégrée : LocationEngine alimente `last_gps_timestamp` (champ TripState) à **chaque
position reçue**, qu'elle soit ensuite acceptée, rejetée ou seulement utilisée pour le statut.

```text
last_gps_timestamp = timestamp_ms de la dernière position REÇUE
```

Distinction à préserver (cohérente avec DistanceEngine §5, C2) :

- `last_gps_timestamp` : « le GPS parle encore », mis à jour à toute réception ;
- `last_valid_position.timestamp` : « la dernière position exploitable », mis à jour seulement
  par DistanceEngine sur ACCEPTED/REFERENCE_ONLY.

LocationEngine n'écrit que `last_gps_timestamp`, jamais `last_valid_position`.

---

## 7. Timer actif de perte GPS (watchdog)

### 7.1 Pourquoi un timer actif

Un GPS muet n'émet aucun événement. La perte ne peut donc pas être détectée de façon réactive
(en attendant une position). LocationEngine porte un timer actif qui vérifie périodiquement
l'ancienneté de la dernière réception.

### 7.2 Configuration

```text
gps_watchdog_interval_ms  = 1000     # période de vérification du watchdog
gps_lost_after_ms         = 10000    # seuil d'absence → LOST (aligné DistanceEngine §5)
```

### 7.3 Règle

```text
À chaque tick (toutes les gps_watchdog_interval_ms) :
  si now - last_gps_timestamp > gps_lost_after_ms ET gps_status != LOST :
    → émettre événement GPS_LOST
    → gps_status = LOST
```

Le watchdog n'est actif qu'en session `ACTIVE` et `PAUSED` (quand l'acquisition tourne). En
`STOPPED`, l'acquisition et le watchdog sont arrêtés.

### 7.4 Récupération

La récupération est réactive (une position arrive de nouveau), pas portée par le watchdog :

```text
Si gps_status == LOST ET une nouvelle position est reçue :
  → émettre événement GPS_RECOVERED
  → la suite (REFERENCE_ONLY, pas de rattrapage) est gérée par DistanceEngine/TripController
```

---

## 8. Détection de discontinuité et marquage de référence

### 8.1 Définition opérationnelle d'une discontinuité

Une discontinuité d'acquisition est détectée dans l'un de ces cas :

```text
1. gap temporel : intervalle réel entre deux positions reçues > discontinuity_gap_ms ;
2. perte GPS déclarée (GPS_LOST par le watchdog) ;
3. redémarrage du service / du flux de localisation ;
4. reprise après pause (RESUME_SESSION) — déjà géré par TripController, rappelé ici.
```

Configuration :

```text
discontinuity_gap_ms = 10000     # aligné sur gps_lost_after_ms
```

### 8.2 Modèle d'événement (writer souverain unique préservé)

Décision d'architecture v0.1 validée :

```text
LocationEngine NE marque PAS distance_reference_dirty directement.
LocationEngine ÉMET un événement de discontinuité distinct.
TripController reçoit l'événement et marque distance_reference_dirty.
TripController reste seul writer de distance_reference_dirty.
```

Distinction sémantique stricte (validée) — les deux événements ne sont jamais fusionnés :

```text
GPS_LOST          décrit une PERTE GPS (absence prolongée de réception, watchdog §7).
GPS_DISCONTINUITY décrit une RUPTURE DE CONTINUITÉ DE MESURE imposant un re-référencement.
```

Les deux peuvent coïncider (une perte GPS est aussi une discontinuité), mais ne sont pas
équivalents : un gap temporel d'une seule position manquante peut invalider la référence (donc
exiger GPS_DISCONTINUITY) sans atteindre le seuil de perte (donc sans GPS_LOST). Inversement, une
perte GPS déclarée émet les deux.

Raison de la séparation : `distance_reference_dirty` a un writer souverain unique (TripController).
LocationEngine signale, TripController arbitre l'état. Cela évite deux écrivains sur le même drapeau.

Événement émis :

```text
GPS_DISCONTINUITY
├── reason : TIME_GAP | GPS_LOST | SERVICE_RESTART | RESUME
├── gap_ms (si TIME_GAP)
└── timestamp_ms
```

### 8.3 Chaîne complète

```text
LocationEngine détecte discontinuité
→ émet GPS_DISCONTINUITY
→ TripController marque distance_reference_dirty = true
→ DistanceEngine voit context.distance_reference_dirty
→ prochaine position valide = REFERENCE_ONLY (aucune distance ajoutée)
→ TripController applique REFERENCE_ONLY, remet distance_reference_dirty = false
```

Cette chaîne ferme l'équilibre du seuil dynamique C1 (v0.1.1) : après un grand `gap`, `elapsed_s`
serait grand et le seuil dynamique permissif, mais aucune distance n'est calculée car la première
position post-discontinuité est forcée en REFERENCE_ONLY. La discontinuité protège C1.

---

## 9. Foreground service et cycle de vie Android

### 9.1 Exigence

Décision v0.1 :

```text
Session ACTIVE ou PAUSED ⇒ foreground service avec notification persistante.
```

Raison : sans foreground service, l'acquisition GPS s'arrête quand l'écran se verrouille ou que
l'app passe en arrière-plan — incompatible avec la mesure en roulage.

### 9.2 Contraintes Android 14+ (API 34+)

À respecter impérativement (sinon `SecurityException` au démarrage du service) :

```text
Manifest :
- <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
- <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
- <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
- <service android:foregroundServiceType="location" ... />

Runtime :
- ACCESS_FINE_LOCATION accordée avant de démarrer le service ;
- POST_NOTIFICATIONS accordée (Android 13+) pour la notification persistante.
```

Périmètre v0.1 :

- `ACCESS_BACKGROUND_LOCATION` n'est **pas** requise : le service est lancé alors que l'app est
  au premier plan (l'utilisateur démarre la session, écran allumé). Le service de localisation
  « while-in-use » suffit. Acquérir la localisation en arrière-plan pur est hors périmètre v0.1.
- Déclaration Play Console du type de service de premier plan (avec justification « navigation /
  mesure de distance en rallye ») : contrainte de publication, hors périmètre technique mais à
  ne pas oublier avant mise en ligne.

### 9.3 Acquisition en PAUSED

Décision v0.1 validée : **option A**.

```text
En PAUSED, l'acquisition est maintenue à cadence réduite :
location_interval_paused_ms = 2000
```

Elle sert uniquement au statut GPS et à une reprise immédiate. Aucune distance ne peut être
produite en PAUSED (garanti par DistanceEngine et TripController). Le coût batterie est marginal
et le copilote voit que le GPS est prêt avant de reprendre.

### 9.4 Permissions refusées ou localisation désactivée

```text
Si ACCESS_FINE_LOCATION refusée :
  → START_SESSION désactivé (déjà prévu TripDisplay §23) ;
  → gps_status = UNKNOWN ou état dédié ;
  → message UI : GPS NON AUTORISÉ.

Si localisation système désactivée alors qu'une session est ACTIVE :
  → aucune position reçue ;
  → watchdog déclenche GPS_LOST après gps_lost_after_ms ;
  → aucune distance ajoutée (comportement nominal de perte).
```

LocationEngine ne demande jamais les permissions lui-même : il signale l'état, l'UI orchestre la
demande (séparation des responsabilités).

---

## 10. Sorties de LocationEngine

LocationEngine produit deux types de sorties :

### 10.1 Flux de positions

```text
onRawLocation(RawLocation)
→ consommé par DistanceEngine (via le pipeline TripController)
→ alimente last_gps_timestamp
```

### 10.2 Événements système

```text
GPS_LOST            # watchdog : absence > gps_lost_after_ms
GPS_RECOVERED       # réception après LOST
GPS_DISCONTINUITY   # gap temporel, restart, perte, reprise (→ TripController marque dirty)
LOCATION_PERMISSION_MISSING
LOCATION_DISABLED
SERVICE_STARTED
SERVICE_STOPPED
```

Ces événements sont consommés par TripController (arbitrage d'état) et, pour certains, reflétés
par TripDisplay (bandeau de statut, jamais en overlay sur les compteurs — TripDisplay §8).

---

## 11. Interaction avec le reste du système

```text
LocationEngine → RawLocation        → DistanceEngine.onLocationReceived(location, context)
LocationEngine → last_gps_timestamp → TripState (écriture directe du seul champ last_gps_timestamp)
LocationEngine → GPS_DISCONTINUITY  → TripController → distance_reference_dirty = true
LocationEngine → GPS_LOST/RECOVERED → TripController → gps_status, log
LocationEngine → SERVICE_*          → TripController → orchestration cycle de vie
```

Règle de souveraineté :

```text
LocationEngine écrit uniquement last_gps_timestamp dans TripState.
Tout autre champ (gps_status, last_valid_position, compteurs) est écrit par DistanceEngine
ou TripController, jamais par LocationEngine.
```

Règle de souveraineté (validée) :

```text
LocationEngine écrit UNIQUEMENT last_gps_timestamp dans TripState.
Il n'écrit JAMAIS gps_status, last_valid_position, distance_reference_dirty,
les compteurs, ni session_state.
Ces champs sont arbitrés par DistanceEngine ou TripController.
```

Motif : `last_gps_timestamp` est un champ technique de réception, nécessaire au watchdog en temps
réel (§7). Il n'a aucun impact métier (ce n'est pas un compteur). L'écriture directe évite une
latence sur le watchdog. Tous les champs métier restent sous writer souverain unique
(TripController / DistanceEngine).

---

## 12. Configuration complète v0.1

```text
# Cadence
location_interval_target_ms   = 1000
location_min_interval_ms      = 500
location_interval_paused_ms   = 2000      # PAUSED : statut GPS + reprise immédiate (§9.3)
location_priority             = HIGH_ACCURACY

# Watchdog / perte
gps_watchdog_interval_ms      = 1000
gps_lost_after_ms             = 10000

# Discontinuité
discontinuity_gap_ms          = 10000
```

Ces valeurs sont alignées avec DistanceEngine v0.1.2 (`gps_lost_after_ms`). Tout changement de
`gps_lost_after_ms` doit être répercuté des deux côtés (candidat à une config partagée en v0.2).

Note (G5) — distinction `discontinuity_gap_ms` / `gps_lost_after_ms` : les deux valent 10000 en
v0.1, mais sont distincts par nature. `discontinuity_gap_ms` déclenche un **re-référencement**
(GPS_DISCONTINUITY → REFERENCE_ONLY) ; `gps_lost_after_ms` déclare la **perte GPS** (GPS_LOST). Leur
égalité est un choix de réglage, pas une équivalence sémantique. Ils peuvent diverger en v0.2 (par
exemple re-référencer dès 5 s de gap sans déclarer la perte avant 10 s) et ne doivent jamais être
fusionnés.

---

## 13. Invariants LocationEngine v0.1

```text
LE-01 — LocationEngine ne calcule jamais de distance métier.
LE-02 — LocationEngine ne valide jamais la qualité métier d'une position (rôle de DistanceEngine).
LE-03 — LocationEngine ne suppose jamais une cadence GPS régulière ; il mesure l'intervalle réel.
LE-04 — Aucune règle de sûreté du système ne repose sur une cadence GPS garantie.
LE-05 — LocationEngine fournit native_speed_kmh chaque fois que l'API la fournit, sinon null.
LE-06 — LocationEngine ne fabrique jamais une vitesse ou une précision absente.
LE-07 — last_gps_timestamp est mis à jour à chaque position reçue, valide ou non.
LE-08 — LocationEngine n'écrit jamais last_valid_position.
LE-08b — LocationEngine écrit uniquement last_gps_timestamp ; jamais gps_status, last_valid_position, distance_reference_dirty, les compteurs, ni session_state.
LE-09 — La détection de perte GPS repose sur un timer actif, pas sur l'attente d'un événement.
LE-10 — LocationEngine ne marque jamais distance_reference_dirty directement ; il émet GPS_DISCONTINUITY.
LE-11 — Toute discontinuité d'acquisition produit un événement GPS_DISCONTINUITY.
LE-12 — Session ACTIVE ou PAUSED implique un foreground service avec notification persistante.
LE-13 — Le service de localisation respecte foregroundServiceType=location et les permissions Android 14+.
LE-14 — LocationEngine ne demande jamais les permissions lui-même ; il signale, l'UI orchestre.
LE-15 — L'horodatage utilisé est monotone, jamais l'horloge murale.
LE-16 — En STOPPED, l'acquisition et le watchdog sont arrêtés.
```

---

## 14. Hors périmètre v0.1

- `LocationManager` / `GPS_PROVIDER` (étude v0.2) ;
- `ACCESS_BACKGROUND_LOCATION` et acquisition en arrière-plan pur ;
- lissage de la cadence ou interpolation de positions manquantes ;
- fusion multi-capteurs (accéléromètre, gyroscope) ;
- capteur de roue / OBD / Bluetooth (déjà hors périmètre, contrat fonctionnel §12).

---

## 15. Décisions actées

Les trois arbitrages ouverts en proposition sont tranchés :

1. **§9.3** — Acquisition en PAUSED : **option A** (cadence réduite 2000 ms, statut + reprise
   immédiate, aucune distance).
2. **§11** — LocationEngine écrit **directement** `last_gps_timestamp`, et **uniquement** ce champ.
3. **§8** — Événement `GPS_DISCONTINUITY` **distinct** de `GPS_LOST`, jamais fusionné ; TripController
   reste seul writer de `distance_reference_dirty`.

Aucune décision LocationEngine n'est en suspens. Les seuls champs marqués pour une éventuelle
évolution v0.2 (config partagée, fallback LocationManager) sont signalés dans leurs sections
respectives et restent hors périmètre v0.1.
