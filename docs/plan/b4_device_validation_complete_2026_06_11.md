# Rapport de validation device — B4 (complément S6/S7) — 2026-06-11

> **Verdict : PASS device sur S6 (pause/reprise) et S7 (terminer)**, sur un **second trajet route réel**. Combiné au premier rapport ([`b4_device_validation_2026_06_11.md`](b4_device_validation_2026_06_11.md), scénario critique PASS), B4 est désormais **validé device complet sur un appareil**. Toujours **un seul appareil** ; calibration **non** validée.

## 1. Statut

- **Date** : 2026-06-11.
- **HEAD au moment du test** : runtime B4/B5 en place (service = unique moteur d'accumulation ; UI = miroir resynchronisé ; runtime découplé de l'UI).
- **Type** : second trajet route réel (complément du premier rapport du même jour).
- **Objet** : couvrir les scénarios non testés du premier essai — **S6 pause/reprise** et **S7 terminer**.
- **Nature** : rapport d'essai daté, **non normatif**. Les contrats `docs/contrats/` restent la référence.

## 2. Mesure

- Distance compteur voiture : **7,80 km**.
- Distance appli : **7,65 km**.
- Écart : **−0,15 km** (≈ **−1,9 %**).

## 3. Conditions du trajet

- Écran éteint quasiment tout le trajet : **OK**.
- Retour app cohérent : **OK**.
- **Pause à l'arrêt** (véhicule immobilisé) : **OK** — distance **figée** pendant la pause.
- **Reprise** : **OK** — accumulation reprise sans bond.
- **Terminer** : **OK** — session arrêtée.
- **Notification foreground** : visible en session, puis **retirée après Terminer** : **OK**.

## 4. Gabarit de résultat (rempli)

```
Trajet (km odomètre) : 7,80

S1 Démarrage premier plan ............ PASS  — session démarrée, notification foreground apparue
S2 Accumulation premier plan ......... NON COUVERT — écran éteint quasi tout le trajet
S3 Arrière-plan / écran éteint ....... PASS  — accumulation poursuivie écran éteint
S4 Retour premier plan / resynchro ... PASS  — UI cohérente au retour
S5 Pas de double comptage ............ PASS  — aucun bond à la reprise
S6 Pause / reprise ................... PASS  — distance figée en pause, reprise correcte
S7 Terminer .......................... PASS  — session terminée, notification retirée
S8 Non-régression écran éteint ....... PASS  — écart ≈ −1,9 %, comparable au premier trajet

Distance référence (odomètre) : 7,80 km
Distance appli                : 7,65 km
Écart                         : −0,15 km (≈ −1,9 %)

Verdict B4 device : PASS sur S6 et S7 (en complément du scénario critique déjà PASS).
                    -> Avec le premier rapport, couverture device complète sur un appareil.
```

## 5. Interprétation (honnête)

- **S6 (pause/reprise)** : **PASS** — la distance reste **figée** à l'arrêt en pause, et l'accumulation **reprend** correctement, sans double comptage.
- **S7 (terminer)** : **PASS** — la session se **termine** proprement et la **notification foreground disparaît**.
- **Cohérence des écarts** : deux trajets réels donnent **−1,7 %** (8,20 / 8,06 km) et **−1,9 %** (7,80 / 7,65 km). Le sous-comptage est **cohérent autour de −1,8 %**, mais **insuffisant pour calibrer** : deux mesures ne constituent pas une vérité générale, et le **compteur voiture n'est pas une vérité absolue**. **Aucun coefficient** n'est activé.
- **Portée** : validation sur **un seul appareil**. Ne pas conclure à une **précision certifiée** ni à une **validation multi-appareils**.

## 6. Hors périmètre (inchangé)

- Aucun changement **GPS / foreground service / accumulation / filtre anti-dérive / calibration / UI**.
- Ce rapport documente un **comportement observé**, il ne modifie pas le code.

## 7. Suites recommandées

- **Filtre anti-dérive** : à **confirmer sur davantage de trajets** (les écarts restent à surveiller).
- **Second appareil** : validation device encore limitée à un appareil.
- **Calibration** : seulement après **plusieurs trajets de référence** cohérents — pas maintenant.

## 8. Rappels

- Document **non normatif** ; les contrats `docs/contrats/` restent la référence.
- B4 est désormais **validé device complet sur un appareil** (scénario critique + S6 + S7), **un seul appareil**, **sans** calibration validée.
- Voir aussi : protocole [`b4_device_validation_protocol_2026_06_11.md`](b4_device_validation_protocol_2026_06_11.md), premier rapport [`b4_device_validation_2026_06_11.md`](b4_device_validation_2026_06_11.md), status central [`runtime_gps_accumulation_status_2026_06_10.md`](runtime_gps_accumulation_status_2026_06_10.md).
