# Plan d'exécution runtime GPS v0.1

> **⚠️ Document historique — antérieur aux clôtures P1→P4.** Note de travail conservée pour l'historique. Plusieurs constats formulés au présent (boucle runtime GPS « inerte », acquisition « non validée sur appareil », filtrage de cohérence incomplet) **ne reflètent plus l'état du système** : la boucle runtime est active et validée sur appareil, et la mesure brute est désormais contractualisée. Pour l'état courant, voir `docs/contrats/gps_accumulation_filter_v0_1.md` et les reprises P4 dans `docs/plan/reprises/`. Aucune information n'est retirée de ce document.

## Statut

- Document de **cadrage d'exécution**, complémentaire de `docs/plan/roadmap_runtime_gps_v0_1.md` (qu'il ne remplace pas).
- Non normatif : les contrats `docs/contrats/` restent la référence.
- Basé sur l'audit du HEAD `50e24fd`.
- Objet : préparer chaque commit du plan d'action **un par un**, de façon exploitable plus tard sans historique de conversation.
- Aucun patch applicatif n'est fourni ici : ce document décrit l'approche, pas le code.

Chaque palier reste atomique, testable, committable séparément, et se termine par `./gradlew testDebugUnitTest assembleDebug` vert + CI verte au push.

---

## Palier 1 — `feat(location): pump location samples while route is started`

**Objectif fonctionnel.** Déclencher périodiquement `TripMeterUiEvent.ApplyLocationSample` (cadence ~1 s) tant que la route est au premier plan, afin que le statut GPS réel se rafraîchisse et que la distance s'accumule depuis le cache de `AndroidLocationEngine`. Sans ce palier, la boucle GPS reste inerte : seul le bouton `SimulateLocationStep` produit du mouvement.

**Pourquoi maintenant.** C'est le plus petit changement observable et le préalable à toute validation device. Toute la plomberie (`AndroidLocationEngine` qui stocke les fixes, `applyLocationEngineSample` dans le ViewModel) existe déjà ; il ne manque que le déclencheur.

**Fichiers concernés.** `app/src/main/java/fr/arsenal/rallyetripmeter/ui/screen/TripMeterRoute.kt` uniquement.

**Hors périmètre.** Aucun changement domaine, contrat, ViewModel. Pas de permission runtime. Pas de service. Pas de modification de `TripMeterScreen` (le bouton SIM GPS reste).

**Approche technique recommandée.**
- Insertion : un **nouvel** `LaunchedEffect(lifecycleOwner, viewModel)` placé après le bloc `DisposableEffect` existant (qui câble `ON_START/ON_STOP`) et avant l'appel `TripMeterScreen(...)`.
- Mécanisme : à l'intérieur de l'effet, `lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { … }` contenant une boucle qui émet `viewModel.onEvent(TripMeterUiEvent.ApplyLocationSample)` puis `delay(~1000 ms)`. Premier tick immédiat (le statut se rafraîchit sans attendre 1 s).
- Imports requis : `androidx.lifecycle.repeatOnLifecycle` (depuis `lifecycle-runtime-ktx`, déjà en dépendance) et `kotlinx.coroutines.delay` (coroutines déjà transitives ; le fichier utilise déjà `LaunchedEffect`). **Aucune dépendance Gradle ajoutée.**
- Cadence dans une constante nommée (ex. `LOCATION_PUMP_INTERVAL_MS = 1_000L`).

Points de conception à acter (et à documenter dans le commit) :
- **`repeatOnLifecycle(STARTED)`** : il suspend la boucle quand on passe sous STARTED et la relance au retour, et **annule** la boucle (le `delay` lève `CancellationException`) quand la route quitte le premier plan. C'est exactement « pompe tant que visible, s'arrête sinon », symétrique du `ON_STOP → onStopLocation()` déjà câblé. C'est plus sûr qu'un `LaunchedEffect` nu (qui continuerait en arrière-plan) et plus simple qu'un observer manuel.
- **Cadence 1 s** : alignée sur `location_interval_target_ms = 1000` du contrat `locationengine_v0_1`. Suffisante pour un trip meter (le copilote lit au regard), assez basse pour un coût batterie/CPU marginal, et cohérente avec la cadence d'acquisition côté moteur. Une cadence plus rapide n'apporterait rien tant que le moteur n'émet pas plus vite.
- **Le pump tourne même en `Stopped` / `Paused`** : il rafraîchit le statut GPS en continu (le copilote voit que le GPS est prêt avant de démarrer) et il maintient `previousLocationSample` frais. Garder cette référence à jour évite un **saut fantôme** au passage en `Running` ou à la reprise après pause (le delta suivant part de la dernière position vue, pas d'une position vieille de plusieurs minutes).
- **La distance ne s'accumule malgré tout qu'en `Running`** : la garde est déjà dans `DistanceTripProgressEngine` (`if (state.sessionState != Running) return state`). Le pump ne fait que fournir des échantillons ; c'est le moteur de progression qui décide d'intégrer ou non. Pomper en `Stopped/Paused` est donc sans effet sur les compteurs.
- **Aucun changement ViewModel/domaine** : `ApplyLocationSample` est déjà géré par `applyLocationEngineSample`, qui rafraîchit le statut et délègue au moteur de progression. Le pump est purement un déclencheur côté UI.

**Risques.** Cadence trop agressive (batterie) ; pump non borné au lifecycle (acquisition fantôme en arrière-plan) — neutralisé par `repeatOnLifecycle(STARTED)`.

**Tests à ajouter ou modifier.** Aucun test JVM : le pump est un effet UI/lifecycle, non unitaire sans test instrumenté (le runner CI n'a pas d'émulateur). Les 47 tests existants restent inchangés.

**Vérification locale attendue.** `./gradlew testDebugUnitTest assembleDebug` vert (la compilation valide le nouvel effet).

**Validation CI attendue.** Verte au push (compilation + tests JVM).

**Validation device.** Requise : sur appareil/émulateur avec permission accordée, le statut passe `GPS ?` → `GPS RECHERCHE` → `GPS OK`, et après START la distance progresse en roulant.

**Message de commit.** `feat(location): pump location samples while route is started`

**Critères d'acceptation.**
- Un seul fichier modifié (`TripMeterRoute.kt`).
- `ApplyLocationSample` est émis périodiquement uniquement en STARTED et cesse hors STARTED.
- Aucune dépendance ajoutée, aucun changement domaine/VM/contrat.
- `testDebugUnitTest assembleDebug` verts, CI verte.

---

## Palier 2 — `feat(permission): request fine location at route entry`

**Objectif fonctionnel.** Demander `ACCESS_FINE_LOCATION` à l'entrée de la route, pour qu'un install neuf obtienne une popup et puisse démarrer le GPS. Sans cela, l'utilisateur n'a aucune invite et le GPS ne démarre jamais.

**Pourquoi maintenant.** Juste après le pump : c'est la deuxième condition pour qu'un test device soit concluant. Le cas « permission accordée après coup via les réglages système » fonctionne déjà via le refresh `ON_START` ; il manque l'invite in-app.

**Fichiers concernés.** `TripMeterRoute.kt` (+ éventuel petit helper). Le manifeste déclare déjà la permission (à vérifier au moment du commit).

**Hors périmètre.** Foreground service, `ACCESS_BACKGROUND_LOCATION`, `POST_NOTIFICATIONS`, gestion fine du « refusé définitivement » au-delà d'un message. Pas de logique métier dans la route.

**Approche technique recommandée.** `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())` déclenché à l'entrée de la route si la permission n'est pas accordée ; au retour du callback, rafraîchir l'état via l'événement de permission existant. Ne pas dupliquer la garde du ViewModel (qui reste l'autorité sur `start`).

**Risques.** Boucle de demande si mal conditionné ; comportement « ne plus demander » (deux refus) à gérer sobrement (message + pas de relance automatique).

**Tests.** Aucun JVM (UI/permission). Validation device.

**Vérification locale.** `./gradlew testDebugUnitTest assembleDebug` vert.

**Validation CI.** Verte au push.

**Validation device.** Install neuf → popup affichée ; refus → message GPS non autorisé, START cohérent ; acceptation → GPS démarre.

**Message de commit.** `feat(permission): request fine location at route entry`

**Critères d'acceptation.** Popup système affichée sur install neuf ; après acceptation, la boucle pump produit un fix réel ; aucune régression de la garde `start` côté ViewModel.

---

## Palier 3 — `feat(ui): keep screen on during active session`

**Objectif fonctionnel.** Maintenir l'écran allumé pendant une session Active (invariant fonctionnel §6, et confort de test en roulage).

**Pourquoi maintenant.** Complète le trio « APK réellement testable » (pump + permission + écran maintenu) avant les paliers de qualité.

**Fichiers concernés.** `TripMeterScreen.kt`.

**Hors périmètre.** Foreground service (l'écran maintenu ne couvre pas le verrouillage volontaire ni l'arrière-plan).

**Approche technique recommandée.** Activer le maintien d'écran (`KeepScreenOn` / `FLAG_KEEP_SCREEN_ON`) conditionné à l'état de session Active exposé par le `TripDisplayState`. Le désactiver hors Active.

**Risques.** Faible. Veiller à ne pas laisser l'écran allumé hors session.

**Tests.** UI/manuel ; compilation.

**Vérification locale.** `./gradlew testDebugUnitTest assembleDebug` vert.

**Validation CI.** Verte au push.

**Validation device.** En session Active, l'écran ne s'éteint pas ; en Stopped/Paused, comportement système normal.

**Message de commit.** `feat(ui): keep screen on during active session`

**Critères d'acceptation.** Écran maintenu uniquement en Active ; aucune logique métier introduite dans Compose.

---

## Palier 4 — `test(mapper): cover current trip display formatting`

**Objectif fonctionnel.** Poser un filet de caractérisation sur `TripDisplayMapper` **avant** de l'étendre (paliers 6 et 7) : formatage des distances en km, mapping `GpsStatus → UiGpsStatus`, mapping `TripSessionState → UiSessionStatus`.

**Pourquoi maintenant.** `TripDisplayMapper` n'a aucun test aujourd'hui. Le couvrir avant de le modifier garantit que l'ajout de la précision et de la vitesse ne casse pas le formatage existant.

**Fichiers concernés.** `app/src/test/java/fr/arsenal/rallyetripmeter/ui/mapper/TripDisplayMapperTest.kt` (nouveau). Aucun fichier de production touché.

**Hors périmètre.** Toute modification du mapper lui-même (ce palier ne fait que le tester).

**Approche technique recommandée.** Tests JVM purs vérifiant : `0.0 m → "0.00 km"`, une valeur représentative (ex. `1234.0 m → "1.23 km"`), les trois statuts GPS et les trois statuts de session. Verrouiller le `Locale.US` du formatage (séparateur décimal point).

**Risques.** Nul (ajout de tests).

**Tests.** N tests JVM ajoutés ; aucun retiré.

**Vérification locale.** `./gradlew testDebugUnitTest` vert, compteur de tests en hausse.

**Validation CI.** Verte au push.

**Validation device.** Sans objet.

**Message de commit.** `test(mapper): cover current trip display formatting`

**Critères d'acceptation.** Le mapping actuel est intégralement couvert ; les tests passent sur le code inchangé.

---

## Palier 5 — `feat(progress): ignore noise and implausible jumps`

**Objectif fonctionnel.** Respecter, dans sa version minimale, l'invariant « les points GPS incohérents sont ignorés » (contrat fonctionnel §6 et §8) : ne pas intégrer les micro-déplacements de bruit, ni les sauts physiquement implausibles.

**Pourquoi maintenant.** Dès que le GPS réel alimente la boucle (palier 1), l'accumulation naïve produit de la distance parasite (jitter à l'arrêt, saut au retour de masquage). Sans ce garde-fou, l'instrument n'est pas fiable.

**Fichiers concernés.** Domaine progression : `DistanceTripProgressEngine`, ou un filtre pur dédié dans `domain/progress`. Aucune dépendance Android.

**Hors périmètre — à marquer explicitement comme tel.** Le filtrage doit rester **minimal**. Ne **pas** implémenter à ce stade le modèle contractuel complet de `locationengine_v0_1` : pas de `REFERENCE_ONLY`, pas de watchdog actif, pas d'événement `GPS_DISCONTINUITY`, pas de `distance_reference_dirty`, pas de seuil dynamique de grand saut. Ce palier est un sous-ensemble assumé, pas la cible.

**Approche technique recommandée.** Dans le calcul de delta entre `previousSample` et `currentSample` :
- **Plancher de bruit** : si la distance calculée est inférieure à un seuil bas, l'ignorer (ne rien ajouter).
- **Plafond de plausibilité** : calculer la vitesse implicite `distance / Δt` à partir des `timestampMillis` des deux samples ; si elle dépasse un plafond, rejeter le delta (saut GPS).
- Seuils en constantes nommées, **clairement marqués MVP** dans le code et un commentaire, prudents :
  - distance minimale (bruit) : ~2 m (aligné contrat §8) ;
  - vitesse maximale plausible : ~200 km/h (aligné contrat §8) ;
  - garde Δt : si `Δt <= 0` (horodatage non strictement croissant), rejeter le delta (pas de division par zéro, pas de vitesse infinie).
- Le filtre n'altère ni la mise à jour de la référence (`previousSample`) côté ViewModel, ni la sémantique « accumule seulement en Running ».

**Risques.** Seuils arbitraires : les garder conservateurs et nommés ; un seuil de bruit trop haut « mange » de la distance réelle à basse vitesse. Documenter qu'ils sont provisoires.

**Tests à ajouter.** Tests JVM purs sur le moteur de progression :
- delta normal (vitesse réaliste) → distance ajoutée ;
- micro-déplacement sous le plancher → rien ajouté ;
- saut implausible (vitesse > plafond) → rien ajouté ;
- `Δt <= 0` → rien ajouté ;
- hors `Running` → rien ajouté (régression de la garde existante).

**Vérification locale.** `./gradlew testDebugUnitTest assembleDebug` vert, nouveaux tests inclus.

**Validation CI.** Verte au push.

**Validation device.** Recommandée : à l'arrêt moteur, le partiel ne dérive plus ; au retour d'un masquage bref, pas de bond de distance.

**Message de commit.** `feat(progress): ignore noise and implausible jumps`

**Critères d'acceptation.** Bruit et sauts rejetés par des seuils nommés et marqués MVP ; aucun élément du modèle contractuel avancé introduit ; tests JVM couvrant les quatre cas de rejet + le cas nominal.

---

## Palier 6 — `feat(display): surface gps accuracy from last sample`

**Objectif fonctionnel.** Afficher la précision réelle (`±X m`) issue du dernier échantillon, au lieu du placeholder `null` actuel.

**Pourquoi maintenant.** Après la fiabilisation de la distance (palier 5) et la couverture du mapper (palier 4), exposer la précision aide le copilote à juger la confiance du fix.

**Fichiers concernés.** `domain/model/TripState.kt` (champ pur), chemin d'application du sample dans `TripMeterViewModel`, `ui/mapper/TripDisplayMapper.kt`, et le test mapper (palier 4 étendu).

**Hors périmètre.** Toute logique de qualité GPS (dégradé/perdu) ; ici on affiche une valeur, on ne juge pas.

**Approche technique recommandée — champs purs et non-fuite Android.**
- Champ pur candidat à ajouter à `TripState` : `accuracyMeters: Double? = null` (nullable = absence de fix / précision indisponible). C'est une donnée numérique brute, **sans** type Android.
- La non-fuite est déjà garantie par l'architecture : `AndroidLocationSampleMapper` convertit `android.location.Location` en `LocationSample` (type `domain/geo`, pur). Le ViewModel lit `getLastLocationSample()` (un `LocationSample`) et reporte `accuracyMeters` dans `TripState`. **À aucun moment un type `android.location.*` n'entre dans le domaine.**
- Le `TripDisplayMapper` formate `accuracyMeters` en `gpsAccuracyText` (ex. `"±4 m"`), `null` → pas de suffixe (le getter `gpsStatusText` gère déjà le cas `null`).

**Risques.** Faible. Veiller à propager `null` proprement (fix absent) sans afficher `±0 m` trompeur.

**Tests mapper à ajouter.** Formatage `accuracyMeters = 4.0 → "±4 m"` ; `null → ` pas de suffixe ; intégration avec `gpsStatusText`.

**Vérification locale.** `./gradlew testDebugUnitTest assembleDebug` vert.

**Validation CI.** Verte au push.

**Validation device.** La précision affichée varie de façon plausible selon les conditions.

**Message de commit.** `feat(display): surface gps accuracy from last sample`

**Critères d'acceptation.** `TripState` enrichi d'un champ pur nullable ; aucun type Android dans le domaine ; mapper testé pour valeur et `null`.

---

## Palier 7 — `feat(display): surface instantaneous speed from gps`

**Objectif fonctionnel.** Afficher la vitesse instantanée native si présente, sinon un fallback explicite (`—`), au lieu du placeholder `0 km/h`.

**Pourquoi maintenant.** Dernière information du triptyque d'affichage (partiel > total > vitesse selon le contrat). Vient après la précision car elle est secondaire métier.

**Fichiers concernés.** `domain/model/TripState.kt` (champ pur), chemin d'application du sample dans le ViewModel, `ui/mapper/TripDisplayMapper.kt`, test mapper.

**Hors périmètre.** Dérivation de vitesse à partir de la distance/temps (le contrat privilégie la vitesse native ; la dérivation est un chantier ultérieur). Pas de lissage.

**Approche technique recommandée — champs purs et non-fuite Android.**
- Champ pur candidat à ajouter à `TripState` : `speedMetersPerSecond: Double? = null` (on stocke l'unité SI brute dans le domaine, `null` si `LocationSample.speedMetersPerSecond` est absent). `LocationSample` porte déjà cette donnée pure, fournie par le mapper uniquement quand `Location.hasSpeed()` est vrai.
- Conversion en km/h **dans le mapper UI** (`* 3.6`), pas dans le domaine : le domaine reste en SI, l'UI met en forme. `null → "—"`.
- Même garantie de non-fuite qu'au palier 6 : la donnée transite par `LocationSample` (domaine/geo), jamais par un type Android.

**Risques.** Vitesse native absente sur certains fix → fallback `—` à afficher proprement (ne pas afficher `0 km/h` qui laisserait croire à l'arrêt).

**Tests mapper à ajouter.** `speedMetersPerSecond = 21.0 → "76 km/h"` (à l'arrondi retenu) ; `null → "—"`.

**Vérification locale.** `./gradlew testDebugUnitTest assembleDebug` vert.

**Validation CI.** Verte au push.

**Validation device.** Vitesse plausible en roulage ; `—` quand l'API ne fournit pas de vitesse.

**Message de commit.** `feat(display): surface instantaneous speed from gps`

**Critères d'acceptation.** `TripState` enrichi d'un champ SI pur nullable ; conversion km/h côté mapper uniquement ; mapper testé pour valeur et `null`.

---

## Palier 8 (optionnel) — calibration globale vs artefact APK debug

Deux candidats indépendants ; en faire **un seul** selon l'objectif du moment.

**Option A — Calibration globale.**
- Objectif : appliquer un coefficient correcteur (`distance_affichée = distance_gps × coefficient`, contrat fonctionnel §7.2) pour recaler la mesure GPS sur le roadbook.
- Fichiers : domaine (le coefficient s'applique dans le moteur de progression ou un décorateur pur) ; éventuellement une commande UI ultérieure. Data et calcul purs, testables JVM.
- Apport : **précision métier** — transforme un compteur GPS décoratif en instrument recalable.
- Coût/risque : choix d'API (coefficient fixe vs réglable), tests de non-régression sur l'accumulation.

**Option B — Upload d'APK debug en artefact CI.**
- Objectif : publier l'APK debug comme artefact de la CI pour l'installer directement depuis le téléphone, sans PC.
- Fichiers : `.github/workflows/android-ci.yml` uniquement (étape `actions/upload-artifact` après `assembleDebug`).
- Apport : **facilité de test device** — récupération du build en un tap depuis l'app GitHub.
- Coût/risque : minime (YAML), aucun impact code.

**Recommandation.** Si l'objectif immédiat est de **tester sur appareil réel** (cas courant tant que les paliers 1–3 ne sont pas validés device, et en travaillant depuis le téléphone), faire **l'option B en premier** : elle débloque l'installation et la validation device des paliers runtime à coût quasi nul. Réserver l'option A (calibration) à la phase où la **précision métier** devient la priorité, une fois la boucle runtime validée. Messages de commit : `ci: upload debug apk artifact` (B) ou `feat(domain): apply global calibration coefficient` (A).

---

## Rappel transversal

- Aucun palier ne touche les contrats `docs/contrats/`.
- Aucun palier n'introduit foreground service, persistance, publication Play Store, ni le modèle complet discontinuité/watchdog/`REFERENCE_ONLY` (hors périmètre v0.1, cf. roadmap §9).
- Chaque palier finit par `./gradlew testDebugUnitTest assembleDebug` vert et une CI verte ; les paliers runtime (1, 2, 3) exigent en plus une validation device.
