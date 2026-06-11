# Protocole de validation device — B4 (neutralisation du pump UI)

> **Statut : à exécuter — NON ENCORE RÉALISÉ.** Ce document est une checklist de test device. Il ne constitue **pas** une validation : tant que la section « Gabarit de résultat » n'est pas remplie et concluante, **B4 reste device-pending**.

## 1. Statut

- **Date de rédaction** : 2026-06-11.
- **Cible** : palier **B4** (`d7d4a9f`) — pump UI neutralisé comme source d'accumulation ; service = seul moteur d'accumulation ; UI = miroir resynchronisé via `syncUiFromRuntime()`.
- **HEAD de référence** : `dc910c5` (ou ultérieur ; B4/B5 + retrait UI-only appliqués, CI verte).
- **Nature** : document **non normatif**. Les contrats `docs/contrats/` restent la référence.
- **Plan d'origine** : voir [`runtime_gps_b4_pump_neutralization_plan_2026_06_10.md`](runtime_gps_b4_pump_neutralization_plan_2026_06_10.md) §10 (Validation device) — le présent protocole le précise et le rend exécutable.

## 2. Ce que B4 change (et donc ce qu'on valide)

Avant B4, le pump UI (premier plan) **pilotait** l'accumulation. Depuis B4, le **service** est l'unique moteur d'accumulation et le pump ne fait plus que **rafraîchir** le miroir UI (`syncUiFromRuntime()`, lecture seule). On valide donc que, **au premier plan comme en arrière-plan**, l'accumulation reste correcte et que l'UI se **resynchronise** sans double comptage.

> Rappel : la sortie route du 2026-06-10 (17,56 / 17,80 km) est **antérieure à B4** — elle valide le mécanisme service (B3), **pas** le comportement premier plan propre à B4. C'est l'objet de ce protocole.

## 3. Prérequis

- Build **debug** installée depuis un HEAD contenant B4 (`dc910c5` ou ultérieur), CI verte.
- Permission de localisation **accordée** (« Pendant l'utilisation » au minimum).
- GPS activé, ciel dégagé ou trajet réel.
- Compteur de référence disponible (odomètre véhicule) pour le recoupement distance.
- Optimisations batterie OEM notées (constructeur / modèle), car elles influent sur l'écran éteint.

## 4. État attendu avant test (point de départ)

- Application ouverte, session **Stopped** (`ARRÊTÉ`).
- Distance partielle et totale à `0,00 km` (ou état restauré connu si reprise).
- Statut GPS affiché (recherche puis fixe).
- Notification de foreground service **absente** tant que la session n'est pas active.

## 5. Scénarios (à exécuter dans l'ordre)

Pour chaque scénario : **action**, **observation attendue**, **PASS/FAIL**.

### S1 — Démarrage session en premier plan
- **Action** : appuyer sur START.
- **Attendu** : session passe à `ACTIF` ; notification de foreground service apparaît ; statut GPS évolue vers « fixe ».
- **PASS si** : session active **et** notification présente.

### S2 — Accumulation en premier plan (UI miroir)
- **Action** : se déplacer ~300–500 m, écran allumé, app au premier plan.
- **Attendu** : la distance **augmente à l'écran** pendant le déplacement (le service accumule, le pump rafraîchit le miroir ~1 s).
- **PASS si** : la distance progresse de façon cohérente avec le déplacement réel, sans gel de l'affichage.

### S3 — Passage arrière-plan / écran éteint
- **Action** : éteindre l'écran (ou passer l'app en arrière-plan) et continuer à rouler ~1–2 km.
- **Attendu** : aucune interaction possible ; l'accumulation continue **côté service** (vérifiable au retour).
- **PASS si** : au retour, la distance reflète le tronçon parcouru écran éteint (cf. S4).

### S4 — Retour premier plan et resynchronisation UI
- **Action** : rallumer l'écran / ramener l'app au premier plan.
- **Attendu** : l'UI **rattrape immédiatement** la distance accumulée pendant S3 (resynchro via `syncUiFromRuntime()` au premier tick `STARTED`).
- **PASS si** : la distance affichée au retour ≈ distance réelle cumulée (S2 + S3), sans saut incohérent ni retour à une valeur antérieure.

### S5 — Absence de double comptage apparent
- **Action** : observer la distance pendant et juste après S4 ; comparer à l'odomètre sur l'ensemble S2→S4.
- **Attendu** : pas de bond soudain à la reprise ; l'écart global reste de l'ordre de grandeur déjà observé (≈ 1–2 %).
- **PASS si** : aucun doublement visible du tronçon arrière-plan ; écart global plausible (pas de surcompte marqué).

### S6 — Pause / reprise
- **Action** : PAUSE en roulant, continuer ~200 m, puis REPRISE, continuer ~200 m.
- **Attendu** : pendant la pause, la distance **n'augmente pas** ; après reprise, elle reprend ; le tronçon « en pause » n'est **pas** compté.
- **PASS si** : distance gelée pendant la pause, reprise correcte ensuite.

### S7 — Terminer
- **Action** : STOP (terminer la session).
- **Attendu** : session repasse `ARRÊTÉ` ; notification de foreground service **disparaît** ; GPS coupé ; distance finale figée/persistée selon le comportement attendu.
- **PASS si** : session arrêtée **et** notification retirée.

### S8 — Non-régression écran éteint
- **Action** : refaire un court tronçon écran éteint (comme S3) et recouper avec l'odomètre.
- **Attendu** : l'accumulation écran éteint reste **au moins** aussi bonne qu'avant B4 (mécanisme service inchangé).
- **PASS si** : écart écran éteint comparable au trajet de référence du 2026-06-10 (pas de dégradation).

## 6. Observations à relever (pendant l'essai)

- Appareil (constructeur / modèle / version Android) et réglage batterie de l'app.
- Distances : référence odomètre **vs** appli, pour S2→S4 et pour S8.
- Comportement de l'affichage à la reprise (S4) : immédiat / différé / incohérent.
- Présence/disparition de la notification (S1, S7).
- Tout gel d'UI, saut de distance, ou throttling visible.

## 7. Critères PASS / FAIL globaux

- **PASS global** : S1→S8 tous PASS, en particulier **S4 (resynchro)** et **S5 (pas de double comptage)**.
- **FAIL** si l'un des cas suivants : UI figée au premier plan malgré le déplacement (S2) ; distance non rattrapée au retour (S4) ; tronçon arrière-plan compté double (S5) ; régression nette de l'écran éteint (S8).
- En cas de FAIL ciblé sur la resynchro/refresh : envisager un **correctif UI minimal** (cadence du tick ou `syncUiFromRuntime`), **sans** toucher service/accumulation/filtre.

## 8. Gabarit de résultat (à remplir après essai)

```
Date de l'essai      : __________
Appareil / Android   : __________
Réglage batterie app : __________
Trajet (km odomètre) : __________

S1 Démarrage premier plan ............ PASS / FAIL  — notes :
S2 Accumulation premier plan ......... PASS / FAIL  — notes :
S3 Arrière-plan / écran éteint ....... PASS / FAIL  — notes :
S4 Retour premier plan / resynchro ... PASS / FAIL  — notes :
S5 Pas de double comptage ............ PASS / FAIL  — notes :
S6 Pause / reprise ................... PASS / FAIL  — notes :
S7 Terminer .......................... PASS / FAIL  — notes :
S8 Non-régression écran éteint ....... PASS / FAIL  — notes :

Distance référence (odomètre) : ______ km
Distance appli                : ______ km
Écart                         : ______ km (≈ ____ %)

Verdict B4 device : PASS global / FAIL (préciser le(s) scénario(s))
```

## 9. Après l'essai

- **Si PASS global** : produire un rapport de validation device daté (pattern `*_validation_2026_*.md`) et basculer, dans le status, B4 de « validation device EN ATTENTE » à « **validé device sur cet appareil** » (rester prudent : un appareil ≠ tous appareils).
- **Si FAIL** : ouvrir un correctif **ciblé** (UI/refresh) ; ne pas toucher au service, à l'accumulation, au filtre ni à la calibration.

## 9 bis. Essais consignés

- **2026-06-11 (premier essai, trajet route)** : **PASS sur le scénario critique** (S1, S3, S4, S5, S8) ; S2 non couvert, **S6 (pause/reprise) non testé**, **S7 (terminer) non documenté**. Écart ≈ −1,7 % (voiture 8,20 km / appli 8,06 km). Rapport complet : [`b4_device_validation_2026_06_11.md`](b4_device_validation_2026_06_11.md).
- **2026-06-11 (second essai, trajet route)** : **S6 (pause/reprise) PASS** (distance figée en pause, reprise correcte) et **S7 (terminer) PASS** (session terminée, notification retirée). Écart ≈ −1,9 % (voiture 7,80 km / appli 7,65 km). → avec le premier essai, **couverture device complète sur un appareil**. Rapport complet : [`b4_device_validation_complete_2026_06_11.md`](b4_device_validation_complete_2026_06_11.md).

## 10. Rappels

- Document **non normatif** ; les contrats `docs/contrats/` restent la référence.
- Ce protocole **ne vaut pas** validation : **B4 reste device-pending** tant que le gabarit §8 n'est pas rempli et concluant.
- **Pas de calibration** déclenchée par cet essai (un trajet de plus ne suffit pas ; attendre plusieurs trajets de référence).
