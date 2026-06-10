# Plan B4 — Neutralisation du pump UI

## 1. Statut

- **Date** : 2026-06-10.
- **HEAD de référence** : `2d78cf3` (`docs: update runtime GPS progress after service accumulation`).
- **CI** : verte (`testDebugUnitTest` + `assembleDebug`).
- **Nature du document** : cadrage d’exécution d’un palier à venir, **non normatif**. Il complète le point d’étape `runtime_gps_accumulation_status_2026_06_10.md` ; les contrats `docs/contrats/` restent la référence.
- **Aucun patch applicatif ici** : ce document décrit l’approche, pas le code. Le patch B4 sera produit après validation route de B3.

## 2. Objectif

Faire du foreground service l’**unique moteur d’accumulation**, en neutralisant le rôle d’accumulation du pump UI. Après B3, au premier plan, le pump UI **et** la boucle service émettent tous deux `ApplyLocationSample` ; B4 supprime ce double déclenchement résiduel.

## 3. État actuel (après B3)

- Le pump UI (`TripMeterRoute`, `repeatOnLifecycle(STARTED)`) émet `viewModel.onEvent(ApplyLocationSample)` à ~1 s tant que la route est au premier plan.
- `viewModel.onEvent(...)` fait deux choses : (a) `runtime.onEvent(event)` → accumulation + persistance ; (b) `stateMirror = runtime.state` → rafraîchissement du miroir Compose.
- La boucle service (B3) émet déjà `runtime.onEvent(ApplyLocationSample)` tant que le service tourne (sur le main Looper).
- **Point critique** : `stateMirror` n’est mis à jour **que** dans `onEvent`. Le miroir n’a aujourd’hui aucune autre source de rafraîchissement.

## 4. Le piège à éviter

**On ne peut pas simplement supprimer le pump.** Si le pump disparaît sans remplaçant, le service continue d’accumuler dans le runtime, mais le ViewModel ne relit plus jamais `runtime.state` → **l’UI se fige** (distance, statut GPS, précision, vitesse n’avancent plus à l’écran, alors que le runtime, lui, avance). B4 doit donc **transformer** le pump en rafraîchissement, pas le retirer.

## 5. Recommandation : pump → rafraîchissement lecture seule

Transformer le pump d’un **déclencheur d’accumulation** en un **tick de rafraîchissement du miroir**, sans accumulation ni persistance.

- **ViewModel** : nouvelle méthode pure
  ```kotlin
  fun syncUiFromRuntime() {
      stateMirror = runtime.state
  }
  ```
  Lecture seule : pas de `runtime.onEvent`, pas de persistance, pas de `syncForegroundService`.
- **Route** : le pump appelle `viewModel.syncUiFromRuntime()` au lieu de `viewModel.onEvent(TripMeterUiEvent.ApplyLocationSample)`. Le reste du bloc (`repeatOnLifecycle(STARTED)`, cadence `LOCATION_PUMP_INTERVAL_MS`) est inchangé.

Résultat :
- **Service = unique accumulateur** (B3). Le pump n’émet plus `ApplyLocationSample` → fin du double tick premier plan.
- **UI rafraîchie au premier plan** : le tick relit `runtime.state` dans le miroir.
- **Resynchronisation à la reprise** : `repeatOnLifecycle(STARTED)` relance la boucle au retour au premier plan ; le premier tick remet le miroir à jour avec l’état accumulé en arrière-plan par le service.
- **Statut GPS / précision / vitesse** : rafraîchis dans le runtime par la boucle service (en `Running`), propagés à l’UI par le tick miroir. En `Paused`/`Stopped`, GPS coupé (GPS-OWN-3) → statut indisponible, ce qui est correct.

## 6. Impact

- **ViewModel** : +1 méthode lecture seule. Constructeur, `onEvent`, permission, `syncForegroundService` inchangés.
- **Route** : 1 ligne modifiée dans le pump.
- **Domaine / runtime / service / persistance / UI visible** : inchangés.

## 7. Risque principal et arbitrage

Après B4, l’accumulation **au premier plan** dépend du fait que le foreground service tourne réellement. En `Running` avec permission accordée, `syncForegroundService` démarre le service → accumulation. Mais si le service échoue à démarrer (restrictions Android de démarrage de service de premier plan, cas rare), l’accumulation premier plan s’arrête aussi, car le pump ne la compense plus.

C’est le compromis assumé de l’architecture cible (« service = seule autorité d’accumulation »). Le démarrage du service est déjà validé device (notification). Si l’on veut une sécurité supplémentaire, une variante (non retenue ici) garderait le pump comme moteur de secours quand le service n’est pas confirmé actif — au prix de réintroduire un double pilote. **Recommandation : variante minimale (rafraîchissement seul)**, le risque étant marginal et conforme à la cible.

### Alternative si l’on veut différer le risque

Ne rien changer au pump et **documenter** que le double tick premier plan est inoffensif (runtime unique + `previousLocationSample` partagé → trajet pavé une seule fois ; ticks sérialisés sur le main thread). Dans ce cas, B4 se réduit à une note ; mais l’objectif « service = unique moteur » n’est pas atteint. **Non recommandé** sauf si la validation route de B3 révèle une fragilité du service au premier plan.

## 8. Découpage

**Palier B4 unique et atomique** : méthode VM `syncUiFromRuntime()` + bascule de la ligne du pump + test(s) JVM. Pas de sous-découpage utile.

## 9. Tests JVM

- **`syncUiFromRuntime` reflète l’état du runtime** : injecter un `TripRuntime` au ViewModel (paramètre disponible depuis B2), faire évoluer l’état du runtime (ex. `runtime.onEvent(SessionAction)` ou un `initialState` non trivial accumulé), appeler `viewModel.syncUiFromRuntime()`, et asserter que `viewModel.uiState` reflète le nouvel état (session, distance).
- **`syncUiFromRuntime` est lecture seule** (optionnel) : avec un `FakeTripStateStore` injecté, asserter qu’aucune sauvegarde n’est déclenchée et qu’aucune lambda foreground service n’est appelée.
- **Pump (Route)** : non testable en JVM (effet UI / lifecycle, sans Robolectric).

## 10. Validation device

- Au premier plan, session `Running` : la distance et le statut GPS avancent à l’écran (service accumule, pump rafraîchit).
- Arrière-plan → premier plan : l’UI **rattrape** immédiatement la distance accumulée en arrière-plan (resync au premier tick).
- Pas de double comptage au premier plan (le pump n’accumule plus).
- `Paused` / `Stopped` : pas d’accumulation, statut GPS indisponible.

## 11. Préalable

B4 ne doit être codé **qu’après la validation route de B3 (écran éteint en roulant)**. Tant que l’accumulation service en arrière-plan n’est pas confirmée sur le terrain, retirer le rôle d’accumulation du pump premier plan ferait reposer toute l’accumulation sur un mécanisme non encore validé.

## 12. Message de commit envisagé

```
refactor(ui): neutralize ui pump as accumulation source
```
