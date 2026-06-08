# Trip Meter Rallye Android — TripDisplay v0.1.1

Statut : PROPOSITION (révision v0.1.1)  
Dépend de :

- Contrat fonctionnel v0.1 validé
- TripState v0.1
- DistanceEngine v0.1
- TripController v0.1

Objet : affichage principal, interaction utilisateur et règles de lisibilité de l’application

---

## Changelog v0.1.1

- **A2** — Affichage vitesse forcé à 0 sous `seuil_stationnaire`. Voir §6.3.
- **M5** — Cadence d'affichage fixe `display_refresh_hz = 3`, découplée des mutations GPS.
  Voir §34.

---

## 1. Rôle de TripDisplay

`TripDisplay` est la couche d’affichage et d’interaction.

Il montre l’état courant de `TripState` et transmet les intentions utilisateur à `TripController`.

Principe central :

```text
TripDisplay affiche.
TripDisplay ne décide pas.
TripDisplay ne calcule pas.
TripDisplay ne modifie jamais TripState directement.
```

Flux attendu :

```text
TripState
→ TripDisplay
→ affichage lisible

Utilisateur
→ TripDisplay
→ TripController
→ TripState
```

---

## 2. Objectif d’usage

L’application doit être utilisable dans une voiture en rallye :

- vibration ;
- luminosité variable ;
- gants possibles ;
- copilote concentré sur le roadbook ;
- très peu de temps pour lire ;
- risque d’appui accidentel.

L’interface doit privilégier :

```text
lisibilité > richesse
stabilité > animation
gros boutons > menus subtils
contraste > esthétique
fonctionnel > joli
```

---

## 3. Écran principal v0.1

### 3.1 Informations obligatoires

L’écran principal affiche toujours :

- kilométrage total ;
- kilométrage étape ;
- vitesse instantanée ;
- statut GPS ;
- état de session.

### 3.2 Informations secondaires possibles

À afficher de manière discrète :

- précision GPS ;
- coefficient de calibration ;
- heure ou durée session.

Décision v0.1 : précision GPS affichée, coefficient de calibration masqué ou affiché en petit, durée session optionnelle.

---

## 4. Hiérarchie visuelle

Priorité d’affichage :

1. kilométrage étape ;
2. kilométrage total ;
3. vitesse ;
4. statut GPS ;
5. état session ;
6. commandes.

Décision importante : le kilométrage étape doit être l’information dominante.

---

## 5. Layout principal recommandé

### 5.1 Mode portrait

```text
┌──────────────────────────────┐
│ ÉTAPE                         │
│        12.84 km               │
├──────────────────────────────┤
│ TOTAL                         │
│       124.37 km               │
├──────────────────────────────┤
│ VITESSE                       │
│          76 km/h              │
├──────────────────────────────┤
│ GPS OK ±4 m       ACTIVE      │
├──────────────────────────────┤
│ -100   -10   RESET   +10 +100 │
├──────────────────────────────┤
│ START / PAUSE / RESUME / STOP │
└──────────────────────────────┘
```

### 5.2 Mode paysage

Mode recommandé en voiture.

```text
┌────────────────────────────────────────────────────────┐
│ ÉTAPE                         │ TOTAL                  │
│ 12.84 km                      │ 124.37 km              │
├───────────────────────────────┴────────────────────────┤
│ VITESSE  76 km/h       GPS OK ±4 m       ACTIVE         │
├────────────────────────────────────────────────────────┤
│ -100       -10       RESET ÉTAPE       +10       +100    │
├────────────────────────────────────────────────────────┤
│ PAUSE                    STOP                 OPTIONS   │
└────────────────────────────────────────────────────────┘
```

Décision v0.1 : le mode paysage est prioritaire. Le mode portrait existe, mais il n’est pas l’usage cible.

---

## 6. Format des valeurs

### 6.1 Kilométrage étape

Source : `TripState.stage_distance_m`

Affichage :

```text
stage_distance_m / 1000
```

Format :

```text
2 décimales
suffixe km
```

Toujours afficher deux décimales, même à zéro : `0.00 km`.

### 6.2 Kilométrage total

Source : `TripState.total_distance_m`

Affichage :

```text
total_distance_m / 1000
```

Format : 2 décimales, suffixe km.

### 6.3 Vitesse instantanée

Source : `TripState.speed_kmh`

Format : 0 décimale, suffixe km/h.

En `STOPPED` : `0 km/h`.  
En `PAUSED` : `0 km/h`.

v0.1.1 (A2) : `TripState.speed_kmh` est déjà nul sous `seuil_stationnaire` et après vitesse
périmée (géré en amont par DistanceEngine/TripState/TripController). TripDisplay affiche
simplement la valeur reçue sans recalcul ; il n'applique aucun seuil lui-même.

---

## 7. États visuels de session

États affichés :

```text
STOPPED → ARRÊTÉ
ACTIVE  → ACTIF
PAUSED  → PAUSE
```

Couleurs conceptuelles :

```text
ACTIVE  → état positif / actif
PAUSED  → état attention
STOPPED → état neutre
```

Invariant : la couleur ne doit jamais être la seule information. Le texte doit toujours être présent.

---

## 8. États visuels GPS

Statuts affichés :

```text
UNKNOWN   → GPS ?
SEARCHING → GPS RECHERCHE
OK        → GPS OK
DEGRADED  → GPS DÉGRADÉ
LOST      → GPS PERDU
INVALID   → GPS INVALIDE
```

Si `gps_accuracy_m` disponible :

```text
GPS OK ±4 m
GPS DÉGRADÉ ±22 m
```

Si précision absente :

```text
GPS OK
GPS ?
```

Les statuts suivants doivent être visuellement visibles :

```text
GPS LOST
GPS INVALID
GPS DEGRADED
```

Décision v0.1 : alerte GPS sur bandeau statut, pas en popup bloquante.

---

## 9. Commandes visibles v0.1

### 9.1 Commandes principales

Toujours accessibles :

```text
RESET ÉTAPE
+10 m étape
-10 m étape
+100 m étape
-100 m étape
START / PAUSE / RESUME selon état
STOP
```

### 9.2 Commandes secondaires

Accessibles via bouton ou panneau :

```text
correction total
définir étape exacte
définir total exact
calibration
reset total
historique
paramètres GPS
```

Décision v0.1 : les corrections étape sont sur l’écran principal. Les corrections total sont dans un panneau secondaire.

---

## 10. Boutons de correction étape

Boutons obligatoires :

```text
-100 m
-10 m
RESET ÉTAPE
+10 m
+100 m
```

Ces boutons concernent `stage_distance_m` uniquement.

Effet :

```text
TripController.CORRECT_STAGE(delta_m)
```

ou :

```text
TripController.RESET_STAGE
```

Invariant UI : une commande visible dans la zone ÉTAPE ne modifie jamais le total.

---

## 11. Commandes session

### 11.1 Affichage selon état

`STOPPED` : afficher `START`.  
`ACTIVE` : afficher `PAUSE` et `STOP`.  
`PAUSED` : afficher `RESUME` et `STOP`.

### 11.2 Libellés recommandés

Décision v0.1 :

```text
START / PAUSE / RESUME / STOP
```

Raison : boutons courts, lisibles, cohérents avec l’univers trip meter.

---

## 12. Commandes dangereuses

### 12.1 RESET TOTAL

Ne doit jamais être sur l’écran principal en accès direct.

Placement :

```text
OPTIONS → SESSION → RESET TOTAL
```

Protection obligatoire : confirmation utilisateur.

Texte de confirmation :

```text
Remettre TOTAL et ÉTAPE à zéro ?
```

Boutons :

```text
ANNULER
CONFIRMER RESET
```

### 12.2 SET TOTAL DISTANCE

Panneau secondaire uniquement. Confirmation simple ou validation explicite.

### 12.3 SET CALIBRATION

Panneau secondaire uniquement.

Affichage recommandé :

```text
Calibration : 1.0000
```

---

## 13. Verrouillage d’écran et anti-erreur

### 13.1 Écran maintenu allumé

En `ACTIVE` : `keepScreenOn = true`.  
En `PAUSED` : `keepScreenOn = true` recommandé.  
En `STOPPED` : `keepScreenOn = false` possible.

Décision v0.1 : écran maintenu allumé en `ACTIVE` et `PAUSED`.

### 13.2 Anti-double-tap

À protéger :

```text
RESET TOTAL
SET TOTAL DISTANCE
```

À ne pas protéger :

```text
+10 m
-10 m
+100 m
-100 m
```

### 13.3 RESET ÉTAPE

Décision v0.1 : pas de confirmation obligatoire. Bouton central clairement séparé.

---

## 14. États de boutons

- `START` actif si `session_state = STOPPED` ;
- `PAUSE` actif si `session_state = ACTIVE` ;
- `RESUME` actif si `session_state = PAUSED` ;
- `STOP` actif si `session_state = ACTIVE` ou `PAUSED` ;
- corrections étape actives dans tous les états ;
- `RESET ÉTAPE` actif dans tous les états ;
- `RESET TOTAL` disponible dans tous les états, mais protégé.

---

## 15. Affichage en cas de commande refusée

Si `TripController` retourne `REJECTED`, l’UI affiche un message court.

Exemples :

```text
Aucune session à reprendre.
Session en pause : utilisez RESUME.
Commande refusée.
```

Décision v0.1 : message non bloquant sauf action dangereuse nécessitant confirmation.

---

## 16. Affichage en cas d’erreur critique

Erreurs critiques :

```text
persistance échouée
GPS indisponible durablement
permission localisation absente
```

### 16.1 Persistance échouée

Affichage :

```text
ERREUR SAUVEGARDE
```

Bandeau rouge ou alerte persistante.

### 16.2 Permission GPS absente

Affichage :

```text
GPS NON AUTORISÉ
```

Action proposée : ouvrir paramètres / demander permission.

Décision v0.1 : `START` désactivé si permission GPS absente.

---

## 17. Affichage de la calibration

Écran principal, si calibration différente de `1.0000` :

```text
GPS OK ±4 m     ACTIF     CAL 1.0152
```

Décision v0.1 : afficher la calibration uniquement si elle est différente de `1.0000`.

---

## 18. Historique / journal

Le journal ne doit pas encombrer l’écran principal.

Accessible via :

```text
OPTIONS → HISTORIQUE
```

Événements visibles :

```text
SESSION_STARTED
SESSION_PAUSED
SESSION_RESUMED
SESSION_STOPPED
STAGE_RESET
STAGE_CORRECTED
TOTAL_CORRECTED
GPS_LOST
GPS_RECOVERED
CALIBRATION_CHANGED
```

Format :

```text
22:14:32  Étape +100 m
22:16:08  GPS perdu
22:16:21  GPS récupéré
```

---

## 19. Mode nuit / contraste

Exigence : proposer un thème sombre ou très contrasté.

Décision v0.1 : mode sombre prioritaire.

Contrat :

```text
grands chiffres
fond sombre ou clair très contrasté
pas de gris clair sur gris moyen
pas de typographie fine
```

---

## 20. Tailles minimales

Ratio conceptuel :

```text
ÉTAPE   = 100 %
TOTAL   = 75 %
VITESSE = 55 %
STATUT  = 25 %
BOUTONS = 35 %
```

Les boutons doivent être assez grands pour un usage en voiture : hauteur confortable, espacement net, aucun bouton dangereux collé à une correction rapide.

---

## 21. Orientation écran

Mode paysage prioritaire. Mode portrait supporté.

Décision v0.1 : support portrait + paysage, mais design prioritaire paysage.

Règle : la rotation ne doit jamais réinitialiser l’état.

---

## 22. Notifications et arrière-plan

En session `ACTIVE`, une notification de service actif peut être nécessaire si le GPS tourne en arrière-plan.

Affichage notification :

```text
Trip actif — Étape 12.84 km — Total 124.37 km
```

La notification peut proposer :

```text
PAUSE
REPRENDRE
STOP
```

Décision v0.1 : notification minimale si service GPS en arrière-plan.

---

## 23. Permission localisation

Au premier lancement, expliquer :

```text
L’application a besoin du GPS pour mesurer la distance.
```

Si permission refusée :

```text
GPS NON AUTORISÉ
```

Décision v0.1 : `START` désactivé si permission GPS absente.

---

## 24. État initial

Au lancement sans session existante :

```text
ÉTAPE      0.00 km
TOTAL      0.00 km
VITESSE    0 km/h
GPS        RECHERCHE ou GPS ?
SESSION    ARRÊTÉ
```

Boutons :

```text
START actif
PAUSE masqué
RESUME masqué
STOP masqué ou désactivé
Corrections étape actives
OPTIONS actif
```

---

## 25. État ACTIVE

Affichage :

```text
ÉTAPE      distance courante
TOTAL      distance courante
VITESSE    vitesse courante
GPS        statut courant
SESSION    ACTIF
```

Boutons :

```text
PAUSE visible
STOP visible
START masqué
RESUME masqué
Corrections étape visibles
```

Écran maintenu allumé.

---

## 26. État PAUSED

Affichage :

```text
ÉTAPE      figée
TOTAL      figé
VITESSE    0 km/h
GPS        statut courant
SESSION    PAUSE
```

Boutons :

```text
RESUME visible
STOP visible
PAUSE masqué
START masqué
Corrections étape visibles
```

Écran maintenu allumé. `PAUSE` doit être très visible.

---

## 27. État STOPPED

Affichage :

```text
ÉTAPE      dernière valeur ou 0.00 km
TOTAL      dernière valeur ou 0.00 km
VITESSE    0 km/h
GPS        statut courant
SESSION    ARRÊTÉ
```

Boutons :

```text
START visible
STOP masqué ou désactivé
PAUSE masqué
RESUME masqué
```

---

## 28. Panneau OPTIONS v0.1

Contenu recommandé :

```text
- Correction total
- Définir étape exacte
- Définir total exact
- Calibration
- Historique
- Paramètres GPS
- Reset total
```

Ordre recommandé :

1. définir étape exacte ;
2. correction total ;
3. calibration ;
4. historique ;
5. paramètres GPS ;
6. reset total.

---

## 29. Saisie distance exacte

### 29.1 Définir étape exacte

Format : km avec deux décimales.

Exemple :

```text
12.35 km → 12350 m
```

Commande :

```text
TripController.SET_STAGE_DISTANCE(12350)
```

### 29.2 Définir total exact

Commande :

```text
TripController.SET_TOTAL_DISTANCE(value_m)
```

Protection : confirmation ou validation explicite.

---

## 30. Saisie calibration

Interface simple :

```text
Distance mesurée par l'application : 9.85 km
Distance réelle / roadbook : 10.00 km
Coefficient calculé : 1.0152
[APPLIQUER]
```

Formule :

```text
calibration_factor = distance_reference / distance_measured
```

Rappel v0.1 : non rétroactif, s’applique uniquement aux nouvelles distances.

---

## 31. Accessibilité et robustesse tactile

Règles :

```text
boutons larges
espacement suffisant
pas de micro-icônes seules
texte + éventuellement icône
```

À éviter en v0.1 :

```text
swipe critique
double tap caché
gestes invisibles
menus contextuels longs
```

Décision : toutes les commandes importantes doivent être visibles.

---

## 32. Icônes

Les icônes sont optionnelles. Une icône ne remplace jamais le texte.

Exemple mauvais :

```text
▶
```

Exemple meilleur :

```text
▶ START
```

---

## 33. Animations

Décision v0.1 : animations minimales.

Autorisées :

- transition légère de bouton ;
- changement de statut.

Déconseillées :

- compteurs qui roulent ;
- effets de rebond ;
- animations longues ;
- cartes qui se déplacent pendant la conduite.

---

## 34. Rafraîchissement affichage (v0.1.1, M5)

`TripDisplay` lit `TripState` à cadence fixe, indépendamment de la fréquence des mutations métier
et de l'arrivée des points GPS.

```text
display_refresh_hz = 3
```

- L'affichage ne se redessine pas à chaque point GPS reçu.
- 3 Hz suffit largement pour un instrument de lecture et évite le papillonnement du second
  décimal du km et le sautillement de la vitesse.
- Les valeurs changent sans animation de transition (cohérent §33 : pas de compteurs qui roulent).

Cohérent avec le principe « TripDisplay affiche un état, il ne réagit pas à chaque événement ».

---

## 35. Gestion des décimales et arrondis

Distance interne : mètres.  
Affichage : kilomètres à deux décimales, arrondi standard.

Exemples :

```text
12844 m → 12.84 km
12845 m → 12.85 km
```

Vitesse : arrondi à l’entier.

```text
76.4 → 76 km/h
76.5 → 77 km/h
```

---

## 36. Messages utilisateur

Messages courts uniquement.

Exemples :

```text
Étape remise à zéro
Étape +100 m
Total corrigé
GPS perdu
GPS récupéré
Session en pause
Sauvegarde impossible
```

---

## 37. Invariants TripDisplay v0.1

```text
TD-01 — TripDisplay ne modifie jamais TripState directement.
TD-02 — Toute action utilisateur est transmise à TripController.
TD-03 — L’écran principal affiche toujours étape, total, vitesse, GPS et état session.
TD-04 — Le kilométrage étape est visuellement prioritaire.
TD-05 — Les corrections étape visibles ne modifient jamais le total.
TD-06 — RESET_TOTAL n’est jamais déclenchable sans confirmation.
TD-07 — Les actions destructrices ne sont pas en accès principal direct.
TD-08 — Le statut GPS est toujours visible.
TD-09 — Une alerte GPS ne masque jamais les compteurs.
TD-10 — La couleur ne doit jamais être le seul indicateur d’état.
TD-11 — En ACTIVE et PAUSED, l’écran reste allumé.
TD-12 — En PAUSED, la vitesse affichée est 0 km/h.
TD-13 — La rotation écran ne réinitialise jamais l’état.
TD-14 — Les boutons critiques sont textuels, pas seulement iconographiques.
TD-15 — Les valeurs de distance sont affichées en km avec deux décimales.
TD-16 — La vitesse est affichée en km/h sans décimale.
TD-17 — Les messages utilisateur sont courts et non bloquants, sauf confirmation.
TD-18 — Le mode paysage est prioritaire.
TD-19 — L’interface doit rester lisible en plein jour et de nuit.
TD-20 — Aucune animation ne doit nuire à la lecture.
TD-21 — TripDisplay lit TripState à cadence fixe (3 Hz), sans redessiner à chaque point GPS. (M5)
TD-22 — TripDisplay affiche speed_kmh telle quelle, sans appliquer de seuil ni de recalcul. (A2)
```

---

## 38. Version compacte normative

## Rôle

TripDisplay affiche `TripState` et transmet les intentions utilisateur à `TripController`.  
Il ne calcule pas, ne décide pas et ne modifie jamais directement l’état métier.

## Écran principal

Affichage obligatoire :

- kilométrage étape ;
- kilométrage total ;
- vitesse instantanée ;
- statut GPS ;
- état de session.

Priorité visuelle :

1. étape ;
2. total ;
3. vitesse ;
4. GPS ;
5. session ;
6. commandes.

## Format

- étape : km, deux décimales ;
- total : km, deux décimales ;
- vitesse : km/h, zéro décimale ;
- GPS : statut + précision si disponible ;
- session : ARRÊTÉ / ACTIF / PAUSE.

## Commandes principales

- `START` ;
- `PAUSE` ;
- `RESUME` ;
- `STOP` ;
- `RESET ÉTAPE` ;
- étape `+10 m` ;
- étape `-10 m` ;
- étape `+100 m` ;
- étape `-100 m`.

## Commandes secondaires

- correction total ;
- définir étape exacte ;
- définir total exact ;
- calibration ;
- historique ;
- paramètres GPS ;
- reset total protégé.

## Règles

- L’étape est l’information dominante.
- Les corrections étape ne modifient jamais le total.
- `RESET_TOTAL` exige confirmation.
- Le statut GPS reste toujours visible.
- Les alertes ne masquent pas les compteurs.
- L’écran reste allumé en `ACTIVE` et `PAUSED`.
- Le mode paysage est prioritaire.
- L’interface doit être lisible, stable, sobre et utilisable en voiture.
