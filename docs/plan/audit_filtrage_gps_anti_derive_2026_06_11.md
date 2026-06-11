# Audit de conception — Filtrage GPS / anti-dérive à l'arrêt

**Dépôt :** `antoinevalentinHA/rallye-trip-meter-android`
**HEAD audité :** `1d74bba5915aca66f127bbcc4a936a1ced19aa1b` (branche `main`, à jour après pull)
**Date :** 2026-06-11
**Mode :** lecture seule, aucun patch, aucune modification du dépôt.
**Périmètre :** qualité de la distance brute accumulée. La calibration d'affichage est hors sujet et n'est jamais proposée comme remède.

---

## 0. État réel du pipeline à HEAD (constaté, pas supposé)

Chaîne d'accumulation :

1. `AndroidLocationEngine` : abonnement `LocationManager.GPS_PROVIDER`, `minTime = 1000 ms`, `minDistance = 0 m`. Chaque fix écrase un cache `lastLocationSample` (lat/lon, altitude, `time` epoch, accuracy, speed — chacun nullable côté domaine).
2. `TripMeterForegroundService` : pump `Handler` à **1 Hz** qui poste `TripRuntimeEvent.ApplyLocationSample` (pump UI neutralisé en B4, le service est l'unique pompe).
3. `TripRuntime.applyLocationEngineSample` :
   - relit le cache ;
   - copie statut GPS / accuracy / speed dans `TripState` ;
   - **affecte `previousLocationSample = currentSample` inconditionnellement, avant d'appeler le moteur** ;
   - délègue à `DistanceTripProgressEngine`.
4. `DistanceTripProgressEngine.applyLocationSample` (le filtre actuel) :
   - rien sans échantillon précédent ; rien hors `Running` ;
   - **R-A** rejet si `speedMetersPerSecond != null && speed < 0.5 m/s` ;
   - distance Haversine entre les deux points ;
   - **R-B** plancher : si `speed != null` → plancher fixe `2.0 m` ; si `speed == null` → `max(2.0, worstAccuracy × 1.0)` ;
   - **R-C** rejet si vitesse implicite (distance/Δt) > 200 km/h, ou Δt ≤ 0 ;
   - accumulation `distance × calibrationFactor` (défaut 1.0, non câblé par `TripRuntime` ni par le ViewModel).

Cas du cache figé (aucun nouveau fix) : la relecture du même échantillon donne previous == current → distance 0 et Δt = 0 → rejeté. Pas de double comptage par relecture. Ce mécanisme est sain.

---

## 1. Critique : où le filtre actuel laisse probablement passer la dérive

### F1 — L'ancre de référence avance aveuglément (défaut structurel)

`TripRuntime` met à jour `previousLocationSample` **avant** la décision du moteur et **quel que soit le verdict** (accepté, rejeté, session en pause). Le filtre ne possède pas l'ancre.

Conséquence : à l'arrêt, chaque tick mesure le segment entre deux points de bruit consécutifs. Le moteur intègre donc la **longueur de chemin du bruit** (random walk), pas le **déplacement net** par rapport à une position stable. Une excursion de jitter de 5 m est créditée à l'aller *et* au retour. Une ancre fixe aurait borné l'accumulation par l'amplitude du jitter ; une ancre glissante la borne par la longueur de sa trajectoire, qui croît linéairement avec le temps. 130 m en quelques minutes est l'ordre de grandeur attendu de ce mécanisme.

C'est une violation par construction du contrat souhaité : « le filtre doit pouvoir rejeter un point sans déplacer aveuglément l'ancre de référence ».

### F2 — Le bypass vitesse abaisse le plancher à 2 m (fuite probable n°1)

Si le chipset rapporte une vitesse parasite ≥ 0,5 m/s à l'arrêt — courant en intérieur, multipath, fenêtre, canapé — alors :

- R-A ne rejette pas (vitesse ≥ seuil) ;
- R-B retombe sur le plancher fixe de 2 m, **l'accuracy est ignorée** ;
- tout segment de jitter ≥ 2 m est accepté.

À 1 Hz, quelques dizaines de segments de 2–6 m acceptés suffisent pour 130 m. Le commentaire du code assume ce compromis (« le plancher accuracy guillotinerait de vrais segments urbains lents ») : c'est précisément le piège filtre-lâche, résolu en sacrifiant l'arrêt.

### F3 — Vitesse nulle : plancher = 1 × accuracy, et l'accuracy ment (fuite probable n°2)

Quand `speed == null` (fréquent en intérieur : pas de Doppler exploitable), le plancher vaut `max(2, worstAccuracy × 1.0)`. Deux problèmes :

- l'`accuracy` Android est un rayon à ~68 % de confiance, souvent **optimiste** en intérieur (3–6 m rapportés pour une erreur réelle de 10–20 m) ;
- le facteur 1.0 place le seuil exactement à la frontière : statistiquement, ~1/3 des écarts dépassent 1σ. Le jitter franchit régulièrement 1 × accuracy.

Donc même sans vitesse parasite, la dérive fuit par excursions ponctuelles, chacune comptée deux fois (F1).

### F4 — Décision par paire, sans mémoire ni hystérésis

Le filtre ne voit que (previous, current). Il n'a aucune notion de :

- état stationnaire vs mouvement ;
- persistance / cohérence directionnelle sur N échantillons ;
- âge de l'ancre ;
- déplacement net cumulé sur une fenêtre.

Or le seul signal qui sépare fiablement *dérive à l'arrêt* et *mouvement lent réel* est temporel : le bruit **oscille** autour d'un point (déplacement net borné, signe alternant), le mouvement lent **s'éloigne de façon monotone** d'une ancre fixe. Aucun seuil par segment ne peut faire cette distinction — augmenter les seuils ne fait que déplacer le problème vers le sous-comptage.

### F5 — Transitions et trous non bornés

- Après perte GPS puis reprise, le segment ancre-ancienne → premier-nouveau-fix est accepté tant que la vitesse implicite ≤ 200 km/h. Sur un trou de 5 minutes, cela autorise un crédit de **~16 km** en un seul segment, sans distinguer trajet réel et téléportation. Aucune règle « gap ≥ T → ré-ancrage sans crédit (ou crédit borné et journalisé) ».
- Pendant `Paused`, le pump tourne et l'ancre continue d'avancer (le moteur, lui, n'accumule pas) ; à la reprise l'ancre est fraîche — comportement acceptable mais **implicite, non contractualisé, non testé**.
- R-C avec Δt ≤ 0 rejette la relecture du même échantillon : correct, mais c'est un effet de bord du test de vitesse, pas un invariant de fraîcheur explicite.

### F6 — Vestige `calibrationFactor` dans le moteur brut

Le moteur porte un `calibrationFactor` (non câblé, défaut 1.0, mais couvert par un test). Contraire à la doctrine « runtime / moteur / TripState restent bruts ». Risque latent : qu'un futur câblage « corrige » la dérive par coefficient. À purger (hors périmètre de ce palier, simple constat).

### Verdict sur la cause du test canapé

Deux routes de fuite plausibles, indiscernables sans données :

- **Route A (F2)** : vitesses parasites 0,5–1,5 m/s à l'arrêt → plancher 2 m → jitter accepté en masse.
- **Route B (F3 + F1)** : speed null, accuracy optimiste, excursions > 1 × accuracy comptées aller-retour.

**On ne doit pas choisir entre A et B au feeling.** C'est exactement la justification du palier 1 (observabilité) avant toute modification du filtre.

---

## 2. Invariants formels / semi-formels pour un tripmeter GPS

Notation : `D(t)` distance brute accumulée, `A` ancre de référence, `s_i` échantillons.

- **I1 — Zéro à l'arrêt (l'invariant central).** Pour toute séquence d'échantillons dont les positions restent dans un rayon `R` autour d'un point fixe pendant une durée `T ≥ T_min`, l'accumulation sur la fenêtre est bornée : `ΔD ≤ ε(R)`, avec `ε` indépendant de `T`. (La dérive actuelle croît linéairement en T : violation.)
- **I2 — Monotonie.** `D` ne décroît jamais hors reset/ajustement manuel explicite.
- **I3 — Borne par le chemin réel.** Sur toute fenêtre, `ΔD ≤ longueur du chemin réellement parcouru + ε`. Corollaire : à l'arrêt, `ΔD` est borné par l'amplitude du bruit, jamais par sa longueur de chemin.
- **I4 — Stabilité de l'ancre.** Un échantillon **rejeté** ne déplace jamais l'ancre de référence du calcul de distance. L'ancre ne bouge que sur acceptation, ou via une règle de ré-ancrage explicite, journalisée.
- **I5 — Pas de téléportation.** Aucun segment impliquant une vitesse implicite > V_max n'est accumulé. *(Existe : R-C.)*
- **I6 — Fraîcheur / monotonie temporelle.** Un échantillon dont `timestamp ≤ timestamp(ancre)` n'est jamais accumulé ni promu ancre. *(Existe seulement comme effet de bord de R-C — à expliciter.)*
- **I7 — Transitions bornées.** Pause→reprise et perte→reprise GPS ne créditent jamais un segment couvrant la coupure au-delà d'une borne explicite ; au-delà d'un trou `Δt > T_gap`, ré-ancrage sans crédit. *(Manquant.)*
- **I8 — Continuité du comptage réel.** Mouvement soutenu à `v ≥ v_min` pendant `T` → sous-comptage ≤ x % (l'invariant anti-filtre-dur, symétrique de I1). *(Non mesuré actuellement.)*
- **I9 — Autorité unique sur l'ancre.** Un seul composant (le filtre) lit et écrit l'ancre. *(Violé : `TripRuntime` la possède.)*
- **I10 — Moteur brut.** Aucun coefficient correctif dans le pipeline d'accumulation. La calibration n'existe qu'à l'affichage. *(Vestige F6 à purger.)*
- **I11 — Décision totale et explicable.** Chaque échantillon reçoit exactement un verdict parmi un ensemble fermé (ACCEPTÉ, REJET_STATIONNAIRE, REJET_BRUIT, REJET_SAUT, REJET_PÉRIMÉ, REJET_GAP, IGNORÉ_HORS_SESSION, IGNORÉ_DUPLIQUÉ), journalisable.

## 3. États dangereux

- Ancre absente avec session `Running` (premier fix : tout segment depuis un premier fix de mauvaise qualité est suspect).
- Ancre périmée après trou GPS (crédit de téléportation, F5).
- Faux état MOUVEMENT verrouillé à l'arrêt par vitesses parasites (Route A).
- Faux état STATIONNAIRE verrouillé pendant un déplacement très lent (embouteillage, manœuvre) — le risque symétrique à ne pas créer en corrigeant.
- Ancre contaminée par un outlier accepté à tort (un mauvais point devenu référence fausse toutes les décisions suivantes).
- Premier fix après démarrage à froid : accuracy très dégradée, position pouvant sauter de dizaines de mètres en se raffinant.

## 4. Faux positifs à rendre impossibles ou fortement bornés

- Accumulation > ε pendant immobilité prolongée (test canapé). **Impossible** sous I1.
- Crédit aller-retour d'une excursion de bruit. **Impossible** sous I4 (ancre fixe à l'arrêt).
- Crédit d'un segment couvrant un trou GPS long. **Borné** sous I7.
- Crédit déclenché par une vitesse parasite isolée. **Borné** : la vitesse seule ne doit jamais suffire à accepter, elle ne peut que corroborer.
- Crédit issu du raffinement du premier fix. **Borné** : période de stabilisation avant tout crédit.

---

## 5. Comment discriminer les cas demandés

Le discriminateur de base est le **déplacement net depuis une ancre fixe, observé dans le temps**, corroboré (jamais décidé) par vitesse et accuracy.

| Cas | Signature observable | Décision |
|---|---|---|
| Vraie progression | Déplacement net depuis l'ancre croît de façon monotone, directions cohérentes, vitesse implicite ≈ vitesse rapportée | Accumuler |
| Dérive à l'arrêt | Déplacement net oscille, borné (~accuracy), directions aléatoires, vitesse implicite incohérente avec speed | Ne rien accumuler, ancre immobile |
| Premier fix mauvais | Pas d'ancre, accuracy élevée puis décroissante, position qui converge | Fenêtre de stabilisation : aucune accumulation tant que l'ancre n'est pas qualifiée (accuracy sous seuil ou position stable sur N échantillons) |
| Perte puis reprise GPS | `Δt` entre ancre et nouveau fix ≫ période nominale | `Δt > T_gap` → ré-ancrage sans crédit (journalisé) ; sinon crédit normal sous I5 |
| Points anciens / re-livrés | `timestamp ≤ timestamp(ancre)` | Rejet I6, ancre intacte |
| Vitesse nulle mais position qui saute | speed ≈ 0 et déplacement net > gate | Conflit de capteurs : ne pas accumuler immédiatement ; exiger persistance (N échantillons cohérents) avant de basculer en MOUVEMENT |
| Vitesse faible réelle (marche, manœuvre) | speed faible mais déplacement net **monotone** depuis l'ancre, franchit le gate cumulé en quelques secondes | Bascule MOUVEMENT, crédit du déplacement net ancre→courant |
| Virage lent | Comme ci-dessus ; le crédit par segments courts sous-estime peu la corde à 1 Hz | Accumuler normalement en MOUVEMENT |
| Embouteillage / stop-and-go | Alternance : phases stationnaires courtes + déplacements nets francs | L'hystérésis doit être assez réactive (gate cumulé, pas durée fixe longue) pour ne pas avaler les redémarrages |

Le point clé anti-piège : **le jitter ne franchit pas un gate de déplacement net cumulé depuis une ancre fixe**, parce qu'il revient sur ses pas ; **le mouvement lent réel le franchit toujours**, parce qu'il s'éloigne. Le temps nécessaire pour trancher est le prix payé — d'où l'importance de créditer rétroactivement le déplacement net ancre→position-courante au moment de la bascule, pour ne pas perdre les premiers mètres (anti-sous-comptage).

---

## 6. Stratégie de filtrage recommandée

Machine d'états à deux états avec ancre possédée par le filtre.

**Propriété de l'ancre (corrige F1/I9).** L'ancre vit dans le filtre. `TripRuntime` ne fait que transmettre les échantillons. Un rejet ne déplace jamais l'ancre.

**État STATIONNAIRE** (état initial, et après qualification du premier fix) :
- ancre fixe ;
- chaque échantillon : calcul du déplacement net `d_net = dist(ancre, courant)` ;
- gate de sortie : bascule en MOUVEMENT si `d_net > G` de façon **persistante** (N échantillons consécutifs au-dessus du gate, ou `d_net` croissant sur N ticks), avec `G = max(G_min, K × accuracy_courante)` ;
- à la bascule : **créditer `d_net`** (le déplacement net, pas la somme des segments intermédiaires — c'est ce qui neutralise le chemin de bruit tout en ne perdant pas le départ) et promouvoir le point courant comme ancre ;
- tant qu'on reste STATIONNAIRE : zéro accumulation, ancre immobile ; ré-ancrage uniquement par règle explicite (ex. : un fix nettement plus précis remplace l'ancre, journalisé, sans crédit).

**État MOUVEMENT :**
- accumulation segment par segment (Haversine) entre points acceptés ;
- gardes conservées : I5 (saut implausible), I6 (fraîcheur), I7 (gap) ;
- gate de retour : si le déplacement net sur une fenêtre glissante de `T_stop` secondes reste sous `G`, retour STATIONNAIRE, ancre = position robuste courante. L'hystérésis (gate de sortie ≠ gate de retour, persistance requise) empêche l'oscillation d'état en embouteillage.

**Rôle des signaux secondaires :**
- `speed` : corroborant uniquement. Une vitesse > seuil **réduit** N (bascule plus rapide) ; elle ne suffit jamais seule. Une vitesse ≈ 0 **augmente** l'exigence de persistance ; elle n'interdit jamais seule (cas « vitesse nulle mais position qui saute » réel : démarrage avec Doppler en retard).
- `accuracy` : dimensionne le gate, jamais un verdict binaire. Accuracy absente → gate au plafond conservateur.
- Fréquence des points : `Δt` réel mesuré, jamais supposé à 1 s ; tout raisonnement en « N échantillons » doit être doublé d'une borne en secondes.

**Transitions de session :** pause → l'ancre est gelée et marquée périmée ; à la reprise, premier échantillon = nouvelle ancre, aucun crédit du segment de coupure (I7). Idem perte/reprise GPS via `T_gap`.

**Ce que cette stratégie n'est pas :** pas de Kalman, pas de lissage de trace, pas de map-matching, pas de coefficient. C'est une **décision d'accumulation** explicite, testable en JVM pure, dans l'esprit Perception/Décision/Exécution déjà en place.

---

## 7. Données minimales à journaliser (palier 1)

Une ligne JSONL par tick de pump, format stable, exportable, rejouable :

```
tick_monotonic_ms, sample_timestamp_ms, lat, lon, accuracy_m, speed_mps,
gps_status, session_state,
sample_is_new (timestamp ≠ précédent),
segment_distance_m (brut Haversine vs ancre/précédent),
anchor_lat, anchor_lon, anchor_age_ms, d_net_from_anchor_m,
verdict (énum I11), verdict_reason,
delta_total_m, total_m
```

Plus, par session : modèle du téléphone, version app, hash du commit. Sans `verdict` + `d_net` + `accuracy` + `speed` par tick, il est impossible de départager Route A et Route B — et toute correction serait une réparation au hasard.

## 8. Tests unitaires indispensables (JVM, moteur pur)

1. **Replay jitter stationnaire** : random walk synthétique borné (σ = 3–8 m) autour d'un point fixe, 600 ticks, speed null → accumulation ≤ ε (I1, cas Route B).
2. **Jitter + vitesses parasites** : même trace avec speed ∈ [0.5, 1.5] aléatoire → accumulation ≤ ε (tue la Route A ; le filtre actuel échoue à ce test).
3. **Excursion-retour** : sortie de 10 m puis retour exact → crédit ≤ gate, jamais ~20 m (tue le comptage aller-retour de F1).
4. **Marche lente réelle** : trace 1 m/s rectiligne 300 s → comptage ∈ [300 m × (1−x%), 300 m] (I8, anti-filtre-dur).
5. **Stop-and-go** : alternance 30 s arrêt / 20 s à 3 m/s, ×10 → erreur totale bornée, vérifie l'hystérésis dans les deux sens.
6. **Premier fix dégradé** : premier point à 40 m du vrai, accuracy 50 m, convergence → zéro crédit pendant la stabilisation.
7. **Gap GPS** : trou de 120 s puis fix à 500 m → ré-ancrage sans crédit (I7) ; trou de 3 s à vitesse plausible → crédit normal.
8. **Points périmés / dupliqués** : timestamp égal ou antérieur → rejet, ancre intacte (I6, I4).
9. **Rejet sans déplacement d'ancre** : injecter un outlier rejeté, vérifier que l'ancre n'a pas bougé (I4 testé directement).
10. **Δt variable** : mêmes traces rééchantillonnées à 0,5 Hz et 2 Hz → verdicts équivalents (le filtre ne dépend pas de l'hypothèse 1 Hz).

Les tests existants (paire par paire) restent valides comme tests de gardes unitaires, mais aucun ne couvre une **séquence** — c'est le trou de couverture principal.

## 9. Tests terrain indispensables

1. **Canapé 15 min**, écran éteint, session Running → relevé total + log JSONL.
2. **Rebord de fenêtre / voiture garée moteur tournant 15 min** (multipath + vibrations : le pire cas vitesse parasite).
3. **Marche lente 500 m** mesurée (trottoir connu, ou trace de référence d'une app GPX tierce en parallèle).
4. **Trajet urbain stop-and-go** ~5 km avec feux, comparé à une trace GPX de référence (pas au compteur voiture).
5. **Trajet routier** ~20 km, même comparaison.
6. **Scénario coupure** : tunnel ou parking couvert, vérifier le ré-ancrage.
Chaque test terrain produit un log rejouable → il devient un test de régression JVM permanent. C'est la passerelle preuve→code.

## 10. Métriques d'acceptation

- **M1 (arrêt)** : 15 min immobile → ΔD ≤ 10 m, et pente d'accumulation ~0 après stabilisation (pas seulement un total faible).
- **M2 (mouvement)** : trajets 3–20 km → |D − référence GPX| / référence ≤ 2 %.
- **M3 (stop-and-go)** : sous-comptage ≤ 3 % sur le scénario urbain.
- **M4 (transitions)** : aucun segment crédité > borne explicite à travers pause/reprise ou trou GPS (vérifiable dans le log).
- **M5 (explicabilité)** : 100 % des ticks ont un verdict ; tout mètre accumulé est traçable à un verdict ACCEPTÉ.
La référence est une trace GPS indépendante, jamais le compteur voiture (qui mesure autre chose, avec son propre biais).

## 11. Ce qu'il ne faut surtout pas faire

- **Monter les seuils existants** (`NOISE_FLOOR`, `ACCURACY_FLOOR_FACTOR`, `STATIONARY_SPEED`) pour faire passer le test canapé : cela déplace la fuite vers le sous-comptage sans toucher F1. Le piège exact que tu décris.
- **Corriger par coefficient** (calibration, facteur correctif moteur) : la dérive est additive et dépend du temps d'arrêt, un coefficient multiplicatif ne peut pas la modéliser. Interdit par I10.
- **Faire confiance à `accuracy` comme vérité** ou à `speed` comme autorité : corroborants, jamais décideurs.
- **Introduire un Kalman/lissage comme solution magique** : un lisseur sans état stationnaire explicite continue d'intégrer un chemin de bruit lissé ; il complexifie sans poser l'invariant I1.
- **Modifier filtre et constantes en même temps que la structure** : d'abord le refactor de propriété d'ancre à comportement constant, ensuite la machine d'états, ensuite les constantes — sur données rejouées, jamais au feeling.
- **Valider sur un seul téléphone / un seul environnement** : les routes A et B dépendent du chipset.
- Viser l'égalité avec le compteur voiture.

## 12. Plan de travail en paliers

- **P1 — Observabilité (aucun changement de comportement).** Logger de décisions JSONL côté runtime/moteur + harness de replay JVM (rejouer un fichier de log dans le moteur et comparer l'accumulation). Critère de sortie : un test canapé produit un log exploitable.
- **P2 — Corpus terrain.** Exécuter les tests terrain 1–3 avec P1. Diagnostic chiffré : part de la dérive passant par Route A (bypass vitesse) vs Route B (jitter > accuracy), distribution des segments acceptés à l'arrêt. Critère : la cause des 130 m est démontrée, pas supposée.
- **P3 — Refactor de propriété (comportement strictement identique).** Déplacer la gestion de l'ancre de `TripRuntime` vers le filtre (I9), introduire l'énum de verdict (I11). Tests existants verts, replays P2 bit-à-bit identiques. Aucun nouveau filtrage.
- **P4 — Machine d'états STATIONNAIRE/MOUVEMENT.** Implémenter §6 avec gardes I1, I4, I6, I7. Tests unitaires §8 (1–3, 6–9) verts ; replays P2 : test canapé ≤ M1 **et** traces mouvement inchangées à ±x %.
- **P5 — Calage des constantes sur données.** `G_min`, `K`, `N`, `T_stop`, `T_gap` ajustés par replay du corpus, jamais en modifiant le code pendant un test terrain. Tests §8 (4, 5, 10) verts.
- **P6 — Validation terrain finale.** Tests terrain 1–6, métriques M1–M5. Purge du vestige `calibrationFactor` moteur (I10) en clôture.

## 13. Premier palier recommandé

**P1, strictement.** Justification : les deux routes de fuite plausibles (A : vitesses parasites court-circuitant le plancher ; B : jitter dépassant 1 × accuracy, doublé par l'ancre glissante) ont des remèdes différents et la donnée actuelle (un total de 130 m) ne les départage pas. Toute modification du filtre avant P1/P2 serait une réparation au hasard — y compris une « bonne » modification dont on ne pourrait pas prouver qu'elle corrige la bonne cause. Le logger est en outre le seul livrable qui transforme chaque futur test terrain en test de régression permanent.

## 14. Synthèse de la réponse critique demandée

- **Où le filtre laisse passer la dérive :** ancre glissante intégrant le chemin du bruit (F1, structurel) ; bypass du plancher accuracy dès qu'une vitesse ≥ 0,5 m/s est rapportée (F2) ; plancher à 1 × accuracy avec accuracy optimiste en intérieur (F3) ; absence totale de mémoire temporelle (F4) ; trous GPS non bornés (F5).
- **Invariants manquants :** I1 (zéro à l'arrêt borné indépendamment de la durée), I4 (rejet sans déplacement d'ancre), I6 explicite, I7 (transitions bornées), I8 (anti-sous-comptage), I9 (autorité d'ancre), I11 (verdict total).
- **Preuves à ajouter avant de coder :** log de décision par tick (P1), corpus terrain rejouable (P2), tests de séquence — pas de paire — en JVM (§8), et le critère dur : la correction doit faire passer le replay canapé sous M1 **sans dégrader** les replays mouvement. Tant que cette double preuve n'existe pas, aucune modification du filtre n'est justifiable.
