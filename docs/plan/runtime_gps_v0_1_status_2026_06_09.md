# Point d’étape — Runtime GPS v0.1

## 1. Statut global

- **Date** : 2026-06-09.
- **HEAD** : `c7c4c52` (`feat(display): surface instantaneous speed from gps`).
- **CI** : verte (`testDebugUnitTest` + `assembleDebug` sur `push` et `pull_request` vers `main`).
- **APK artifact** : opérationnel (artefact `rallye-trip-meter-debug-apk` publié à chaque run vert).
- **Validation Pixel** : effectuée pour les paliers runtime critiques (pump, permission, GPS réel, distance réelle, écran maintenu).

Ce document est une synthèse d’avancement. Il ne remplace ni la roadmap (`docs/plan/roadmap_runtime_gps_v0_1.md`) ni le plan d’exécution (`docs/plan/runtime_gps_v0_1_execution_plan.md`), et n’a aucune valeur normative — les contrats `docs/contrats/` restent la référence.

## 2. Résumé exécutif

Le projet est passé d’un prototype Compose/GPS, où seul un bouton de simulation faisait bouger les compteurs, à une application Android réellement installable et testable sur appareil. La boucle GPS runtime est désormais vivante : un pump périodique borné au cycle de vie de la route alimente le ViewModel, qui consomme le `LocationEngine` réel et accumule la distance.

Sur le plan de l’usage, l’application demande la permission de localisation directement à l’ouverture (plus besoin de passer par les réglages système), maintient l’écran allumé pendant une session active, et présente des libellés de commande lisibles pour un usage rallye. Sur le plan de la fiabilité, un filtrage minimal ignore le bruit GPS à l’arrêt et les sauts physiquement implausibles, et l’affichage expose la précision et la vitesse réelles issues du dernier échantillon.

L’ensemble a été livré en petits paliers atomiques, chacun validé par la CI (compilation debug + tests unitaires JVM) avant intégration, et l’APK debug est récupérable depuis GitHub Actions pour validation sur Pixel. Le socle est donc passé d’« architecture prometteuse » à « instrument utilisable et vérifiable sur le terrain ».

## 3. Paliers réalisés

| Palier / commit | Statut |
|---|---|
| `docs: add runtime GPS roadmap` (`ef1efc3`) | Fait |
| `docs: add runtime GPS execution plan` (`f9b6cf0`) | Fait |
| `ci: add Android debug build and unit test workflow` (`50e24fd`) | Fait |
| `feat(location): pump location samples while route is started` (`5c1e7a5`) | Fait, validé device |
| `ci: upload debug apk artifact` (`98ced92`) | Fait |
| `feat(permission): request fine location at route entry` (`17cfafc`) | Fait, validé device |
| `feat(ui): keep screen on during active session` (`f0ca116`) | Fait, validé device |
| `feat(ui): improve rally control labels` (`239244f`) | Fait |
| `test(mapper): cover current trip display formatting` (`0227e82`) | Fait |
| `feat(progress): ignore noise and implausible jumps` (`67ce840`) | Fait, validation device différée |
| `feat(display): surface gps accuracy from last sample` (`d50ef03`) | Fait, validation device différée |
| `feat(display): surface instantaneous speed from gps` (`c7c4c52`) | Fait, validation device différée |

## 4. Validations réalisées

- CI `testDebugUnitTest assembleDebug` verte sur le HEAD courant.
- APK debug téléchargé depuis l’artefact GitHub Actions.
- Installation sur Pixel réussie.
- Popup Android de permission de position affichée et acceptée depuis l’application.
- Statut GPS réel : `GPS OK`.
- Position réelle obtenue.
- Distance réelle accumulée en roulant, en session active.
- Écran maintenu allumé en session `ACTIF`.

## 5. Validations device différées

Ces comportements sont implémentés et couverts par des tests JVM, mais leur validation en conditions réelles n’a pas encore été faite (impossibilité de tester sur le terrain au moment de la rédaction) :

- filtrage du bruit GPS à l’arrêt (le partiel ne doit pas dériver, moteur éteint) ;
- rejet des sauts GPS implausibles (pas de bond de distance au retour de masquage) ;
- affichage de la précision GPS (`±N m`) en conditions réelles variées ;
- affichage de la vitesse GPS en roulage (et fallback `—` quand l’API ne fournit pas de vitesse).

## 6. État fonctionnel actuel

L’application sait aujourd’hui :

- démarrer / mettre en pause / reprendre / arrêter une session ;
- lire le GPS réel via le `LocationEngine` Android, sur un pump périodique borné au premier plan ;
- accumuler la distance totale et partielle (uniquement en session active) ;
- corriger le partiel (±10 m, ±100 m) ;
- remettre le partiel à zéro ;
- afficher le statut GPS, l’état de permission, l’état de session, la précision GPS et la vitesse instantanée ;
- rester allumée pendant une session active ;
- conserver un bouton de simulation (`TEST GPS`) pour injecter un pas sans GPS réel.

## 7. Limites connues

- calibration de distance non faite (pas de coefficient correcteur) ;
- UI finale non faite ; dark mode rallye à venir ;
- bouton de simulation `TEST GPS` encore visible en production ;
- aucune persistance (session et compteurs perdus à la fermeture) ;
- pas de foreground service : l’acquisition s’arrête en arrière-plan / écran verrouillé ;
- modèle contractuel complet non implémenté (discontinuité, watchdog, `REFERENCE_ONLY`) ; le filtrage actuel est un sous-ensemble MVP assumé ;
- validations terrain encore nécessaires (cf. §5).

## 8. Prochaine étape recommandée

Trois options sont sur la table :

1. `feat(domain): apply global calibration coefficient` — coefficient correcteur de distance.
2. Validation terrain avant calibration — confirmer sur route le filtrage, les sauts, la précision et la vitesse.
3. Dark mode rallye — si la priorité est l’ergonomie d’usage.

**Recommandation : la validation terrain (option 2) d’abord.** La calibration n’a de sens que si la distance brute est déjà jugée fiable en conditions réelles : calibrer un coefficient sur une mesure dont on n’a pas encore vérifié le filtrage et la stabilité reviendrait à corriger un signal non validé. Une sortie de validation (arrêt prolongé pour vérifier l’absence de dérive, parcours connu pour comparer la distance, passage en zone de masquage pour observer le rejet de saut) lève les quatre validations différées du §5 et fournit en prime les données nécessaires pour fixer un coefficient de calibration crédible ensuite. La calibration (option 1) vient donc en second, et le dark mode (option 3), purement ergonomique, reste le moins prioritaire tant que l’instrument n’est pas validé.

## 9. Estimation d’avancement

- **MVP utilisable** : ~75–80 %. La chaîne complète (permission → GPS réel → distance accumulée → affichage) fonctionne et est testable sur device ; il manque surtout la validation terrain et quelques finitions d’usage.
- **Application rallye robuste** : ~55–60 %. Le filtrage est minimal, sans calibration ni gestion fine des discontinuités / pertes de signal ; l’acquisition s’arrête en arrière-plan (pas de service).
- **Version propre / publiable** : ~40 %. Pas de persistance, UI non finalisée, bouton de simulation encore présent, et plusieurs chantiers contractuels lourds restent à mener avant une diffusion.

Ces pourcentages traduisent un socle solide et vérifiable, mais encore distant d’un outil de rallye éprouvé sur le terrain et d’un livrable propre.
