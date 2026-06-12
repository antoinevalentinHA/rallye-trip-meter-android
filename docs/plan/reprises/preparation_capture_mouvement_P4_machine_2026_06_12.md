# Préparation de la capture de mouvement pour P4-machine

Document opérationnel : comment capturer un (ou plusieurs) log(s) de mouvement
exploitable(s), prérequis bloquant avant de coder P4-machine. Aucun code, aucun
golden : procédure de terrain et de dépôt uniquement.

Documents amont :
- Plan d'ensemble : `docs/plan/plan_action_filtrage_gps_P1_P6_2026_06_11.md`
- Clôture P3 / reprise P4 : `docs/plan/reprises/cloture_chantier_P3_reprise_P4_2026_06_12.md`
- Recalage P4 : `docs/plan/reprises/recalage_P4_anchor_only_invalide_2026_06_12.md`

## 1. Pourquoi cette capture

Le recalage P4 a montré que la correction d'ancre n'est valable que couplée à une
logique stationnaire/mouvement. Pour coder et prouver cette machine, il faut
vérifier les **deux** régimes :
- arrêt → total ≤ M1 (10 m / 15 min) : déjà couvert par trois fixtures d'arrêt ;
- mouvement → total fidèle (M2) : **aucun log de mouvement n'existe à ce jour**.

Sans log de mouvement, M2 est invérifiable et une machine trop agressive
pourrait écraser de la distance réelle sans qu'on le voie. Cette capture est donc
le prérequis avant tout code P4-machine.

## 2. Comment le log est produit et exporté (mécanisme réel)

Le sous-système d'observabilité existe déjà ; rien à modifier.

- **Démarrage** : lancer un run dans l'app démarre le service de premier plan, qui
  crée une session de log (`TickLogSessionFactory`), écrit une ligne d'en-tête
  `meta`, puis journalise **un tick par échantillon** (~1 Hz) via le runtime.
- **Fichier sur l'appareil** :
  `Android/data/fr.arsenal.rallyetripmeter/files/gpslogs/gpslog_<yyyyMMdd_HHmmss>.jsonl`
  (répertoire externe applicatif ; repli interne si indisponible).
- **Arrêt** : arrêter le run ferme la session (flush final) puis **exporte
  automatiquement** une copie via MediaStore vers `Download/gpslogs/`
  (`TickLogExporter`, Android 10+).
- **Récupération** : le fichier exporté est dans `Download/gpslogs/`, accessible
  depuis un gestionnaire de fichiers ou depuis Termux après
  `termux-setup-storage`, à `~/storage/downloads/gpslogs/`.
- **Format** : JSONL v1, première ligne `{"v":1,"type":"meta",...}` (version app,
  modèle d'appareil, horodatage de session), puis une ligne `{"v":1,"type":"tick",...}`
  par échantillon. Schéma figé par l'unique codec `TickLogJsonl`. **Le fichier est
  un enregistrement brut : ne jamais l'éditer à la main.**

## 3. Procédure de capture générique

Une capture exploitable est **une seule session Running, de bout en bout**, avec
trois phases :

1. **Arrêt initial** (~1 à 2 min) : run démarré, appareil posé **immobile**.
   Donne à la future machine une phase stationnaire de référence au départ.
2. **Mouvement réel** (le cœur de la capture) : déplacement continu.
3. **Arrêt final** (~1 à 2 min) : appareil **immobile**, run toujours actif, avant
   d'arrêter le run.

Règles :
- **Durée totale ≥ 15 min** (aligne sur M1 = 10 m / 15 min côté arrêt, et fournit
  assez de mouvement pour M2).
- **Ne pas mettre en pause ni arrêter en cours de session** : le replay force la
  session en Running ; toute phase non-Running dans le fichier divergerait au
  replay. La session doit rester Running du début à la fin.
- **Acquérir un fix stable avant de démarrer** : attendre que le statut GPS soit
  `Fixed` et l'`accuracy` raisonnable (ciel dégagé).
- **Référence de distance obligatoire pour M2** : enregistrer **en parallèle** une
  trace GPX (autre application) ou noter une distance de référence fiable
  (odomètre voiture, parcours mesuré, bornes). Sans référence, seul le delta
  P3/P4 sera vérifiable, pas l'exactitude absolue.

## 4. Trois types de captures recommandés

Au moins une de chaque, idéalement, pour border la machine sur tout le spectre.

### 4.1 Marche lente
- ~15 à 20 min, allure tranquille, boucle connue et mesurée (ou trace GPX).
- Régime le plus exigeant : segments lents, proches des planchers de filtrage.
  C'est le cas où une machine trop agressive risque de **sous-accumuler** (écraser
  du vrai mouvement lent). Capture-test prioritaire pour la protection M2.

### 4.2 Trajet urbain voiture
- ~15 à 30 min, avec arrêts (feux, stops) inclus : transitions arrêt ↔ mouvement
  multiples.
- Teste la robustesse de la machine aux arrêts **intermédiaires** sans crédit de
  dérive, tout en préservant le mouvement entre les arrêts.
- Référence : odomètre du véhicule et/ou GPX.

### 4.3 Route plus stable
- ~20 à 30 min, vitesse soutenue et régulière (départementale / voie rapide), peu
  d'arrêts.
- Régime établi, le cas M2 le plus « propre » pour vérifier l'exactitude
  d'accumulation.
- Référence : odomètre, bornes kilométriques, GPX.

## 5. À éviter

- **Capture trop courte** (quelques minutes) : pas assez de mouvement pour M2.
- **Uniquement canapé / arrêt** : on a déjà trois logs d'arrêt ; il faut du
  mouvement.
- **Mauvais signal GPS** (intérieur, tunnel, fix non acquis) : bruit non
  représentatif, ingérable.
- **Trajet sans référence** : M2 invérifiable.
- **Pas d'arrêt initial / final** : impossible de valider les transitions
  arrêt ↔ mouvement de la machine.
- **Pause ou arrêt en cours de session** : crée des phases non-Running que le
  replay (forcé Running) ne reproduit pas → divergence.
- **Édition manuelle du JSONL** : casse la fidélité et la preuve. Le fichier doit
  rester l'enregistrement brut produit par l'app.

## 6. Nommage, dépôt, Git, contrôles

**Nommage.** Renommer le fichier exporté `gpslog_<ts>.jsonl` selon la convention
attendue par `RealLogReplayTest` : préfixe `real_`, extension `.jsonl`, par exemple
`real_marche_<yyyyMMdd>.jsonl`, `real_urbain_<yyyyMMdd>.jsonl`,
`real_route_<yyyyMMdd>.jsonl`.

**Emplacement dans le dépôt.** Copier le(s) fichier(s) dans
`app/src/test/resources/replay/` (aux côtés des fixtures existantes).

**Contrôles avant commit :**
- Première ligne = `meta` (`"type":"meta"`) ; lignes suivantes = `tick`
  (`"type":"tick"`).
- `session_state` toujours `Running` (aucune phase pause/stop intermédiaire).
- `gps_status` majoritairement `Fixed`, `accuracy_m` raisonnable.
- Présence visible des bornes d'arrêt (début/fin) et d'une phase de mouvement au
  milieu.
- Nombre de ticks cohérent avec la durée (~1 par seconde).
- Référence de distance notée à part (GPX / odomètre) pour M2.
- (Facultatif) lancer le replay local pour lire le rapport généré :
  `./gradlew :app:testDebugUnitTest --tests "fr.arsenal.rallyetripmeter.replay.*"`.
  `RealLogReplayTest` auto-découvre `real_*.jsonl`, écrit un rapport dans
  `build/reports/gpslog-replay/<nom>.txt` et vérifie l'invariant structurel
  (somme des verdicts = nombre de ticks). Il **n'asserte pas** le total : un log de
  mouvement diverge légitimement, c'est voulu.

**Git (Termux) :**
```bash
git add app/src/test/resources/replay/real_<scenario>_<date>.jsonl
git commit -m "test(replay): add movement field log <scenario>"
git push
```
Chaque log déposé devient une régression permanente du corpus.

## 7. Comment cette capture servira P4-machine

- **Validation M2** : comparer le total rejoué sous P4 à la référence
  (|D − référence| / référence ≤ 2 %).
- **Comparaison P3 / P4** : sur les segments de mouvement,
  |D_P4 − D_P3| ≤ 2 % — la machine ne doit pas dévier du comportement
  d'accumulation déjà validé en P3 hors arrêt.
- **Détection d'une sous-accumulation** : si la machine, en supprimant la dérive
  d'arrêt, écrase aussi du mouvement réel (surtout en marche lente), le total
  passe sous la référence → visible immédiatement au replay.
- **Protection contre une machine trop agressive** : les bornes d'arrêt vérifient
  la suppression de dérive (≤ M1) et le mouvement vérifie la préservation
  (≤ 2 %). Les deux ensemble bornent la machine des deux côtés ; un log d'arrêt
  seul ne borne qu'un côté, d'où l'insuffisance actuelle.

## 8. Conclusion

Le prochain prérequis avant P4-machine est la capture d'au moins un log de
mouvement exploitable, idéalement marche lente + voiture urbaine + route stable.

## 9. Suite — corpus mouvement capturé

Trois logs voiture (urbain ×2, route ×1 avec odomètre 6.80 km) ont été ajoutés au
corpus. Leur audit chiffré M1/M2 figure dans
`audit_corpus_M1_M2_avant_P4_machine_2026_06_12.md`. Reste recommandé avant
P4-machine : un log de marche lente avec référence.
