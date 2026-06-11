# Rapport de validation device — B4 (scénario critique) — 2026-06-11

> **Verdict : PASS device sur le scénario critique** (écran éteint → retour app → resynchronisation UI), **pas** une validation complète de tous les scénarios. **Pause/reprise (S6) non testée** et **terminer (S7) non documenté** sur ce trajet. Sur **un seul appareil**, **un seul trajet**.

## 1. Statut

- **Date de l'essai** : 2026-06-11.
- **Type** : trajet route réel.
- **Cible** : palier **B4** (`d7d4a9f`) — service = seul moteur d'accumulation ; pump UI = rafraîchissement lecture seule (`syncUiFromRuntime()`).
- **Protocole suivi** : [`b4_device_validation_protocol_2026_06_11.md`](b4_device_validation_protocol_2026_06_11.md).
- **Nature** : document **non normatif**. Les contrats `docs/contrats/` restent la référence.
- **Portée** : un appareil, un trajet. Résultat **encourageant et probant sur le verrou critique**, mais **non** généralisable à tous les scénarios ni à tous les appareils.

## 2. Mesure

- Distance compteur voiture : **8,20 km**.
- Distance appli : **8,06 km**.
- Écart : **−0,14 km**, soit ≈ **−1,7 %** (l'appli lit légèrement **en dessous** du compteur).

> Le compteur véhicule n'est **pas** une vérité absolue (usure pneus, tracé réel vs trace GPS). L'écart est du même ordre de grandeur et de même sens que le trajet de référence du 2026-06-10 (≈ −1,35 %), ce qui est **cohérent**, sans permettre d'en tirer une précision certifiée.

## 3. Conditions du trajet

- **Écran éteint** quasiment tout le trajet : OUI.
- **Retours ponctuels** dans l'application (aux feux) : OUI.
- **UI cohérente au retour** : OUI.
- **Notification foreground visible** : OUI.
- Pause/reprise : **non testé** sur ce trajet.
- Terminer : **non documenté** dans les données de cet essai.

## 4. Gabarit de résultat (rempli)

```
Date de l'essai      : 2026-06-11
Appareil / Android   : (à compléter)
Réglage batterie app : (à compléter)
Trajet (km odomètre) : 8,20

S1 Démarrage premier plan ............ PASS  — notification foreground apparue (service démarré)
S2 Accumulation premier plan ......... NON COUVERT — écran éteint quasi tout le trajet ; brèves vérifications au feu (cf. S4)
S3 Arrière-plan / écran éteint ....... PASS  — accumulation poursuivie écran éteint
S4 Retour premier plan / resynchro ... PASS  — UI cohérente aux retours ponctuels (resynchro effective)
S5 Pas de double comptage ............ PASS  — distance finale cohérente, aucun bond visible à la reprise
S6 Pause / reprise ................... NON TESTÉ — hors périmètre de ce trajet
S7 Terminer .......................... NON DOCUMENTÉ — non consigné dans les données fournies
S8 Non-régression écran éteint ....... PASS  — écart ≈ −1,7 %, comparable au trajet de référence du 2026-06-10

Distance référence (odomètre) : 8,20 km
Distance appli                : 8,06 km
Écart                         : −0,14 km (≈ −1,7 %)

Verdict B4 device : PASS sur le scénario critique (S1, S3, S4, S5, S8) ;
                    S2 non couvert, S6 non testé, S7 non documenté.
                    -> PAS un PASS global de tous les scénarios.
```

## 5. Interprétation (honnête)

**Validé device sur le verrou critique de B4** :
- **accumulation écran éteint** (S3) ;
- **service comme unique source d'accumulation** (cohérent avec S3/S5) ;
- **resynchronisation UI au retour au premier plan** (S4) ;
- **notification foreground visible** (S1) ;
- **distance finale cohérente** (S5/S8, ≈ −1,7 %).

**Non couvert / réserves** :
- **Pause/reprise (S6)** : non testée sur ce trajet → **ne pas** considérer comme validée.
- **Terminer (S7)** : non documenté → **ne pas** affirmer.
- **Accumulation premier plan soutenue (S2)** : non focalisée (trajet écran éteint).
- **Multi-appareils** : un seul appareil → pas de garantie OEM/batterie au-delà.

## 6. Hors périmètre (inchangé)

- **Calibration** : **non réalisée, non validée, toujours bloquée**. L'écart ≈ −1,7 % sur un trajet ne justifie aucun coefficient ; attendre plusieurs trajets de référence.
- **Filtre anti-dérive** : **non modifié** ; ne pas conclure largement sur sa précision à partir de ce seul trajet.
- Service / GPS / accumulation / runtime : non touchés (validation, pas de changement de code).

## 7. Suites recommandées

- Compléter la validation B4 par : **pause/reprise (S6)**, **terminer (S7)**, et idéalement un **second appareil**.
- Confirmer le filtre anti-dérive sur d'autres trajets (déjà au plan).

## 8. Rappels

- Document **non normatif** ; les contrats `docs/contrats/` restent la référence.
- Ce rapport **ne vaut pas** validation complète : il atteste un **PASS sur le scénario critique**, sur **un appareil** et **un trajet**.
