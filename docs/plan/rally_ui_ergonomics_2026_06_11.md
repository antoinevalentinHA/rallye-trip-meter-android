# Chantier UI ergonomie rallye — clôture (2026-06-11)

> **Nature** : note d'avancement **non normative**. Elle acte la clôture du chantier d'ergonomie UI au HEAD `1e1d124`. Les contrats `docs/contrats/` restent la référence. **Aucun code n'est introduit** par ce document ; **aucun tag Git** n'est créé.

## 1. Objectif du chantier

Rendre l'écran principal plus utilisable en conditions simples de rallye / roadbook : meilleure lisibilité, contrôle plus facile au doigt, action de reset partiel plus directe, et suppression d'une redondance d'affichage — **sans** toucher au moteur, au service, au runtime, au filtre ni à la calibration, et **sans** refonte.

## 2. Problèmes initiaux

- **Carte PARTIEL non interactive** : la plus grande zone de l'écran ne déclenchait aucune action.
- **Bouton `RAZ PARTIEL`** occupant une place dans la rangée d'ajustement, alors que le reset partiel est l'action fréquente du roadbook.
- **Boutons d'ajustement trop petits** (largeur comprimée par le bouton RAZ central).
- **Bande vide verticale centrale** importante (effet de l'`Arrangement.SpaceBetween` externe à deux enfants).
- **Précision GPS redondante** : affichée à la fois dans la ligne de statut (`GPS OK ±N m`) et en ligne secondaire (`±N m`).

## 3. Corrections validées

| Correction | Commit |
|---|---|
| PARTIEL **cliquable par tap simple** → reset partiel (gated, TOTAL/VITESSE inertes) | `6f9eeb0` |
| **Suppression du bouton `RAZ PARTIEL`** de la rangée | `1a4f2c8` |
| Rangée réduite à **`-100 / -10 / +10 / +100`**, boutons **plus confortables** (largeur regagnée + hauteur 80dp) | `1a4f2c8`, `fcd8588` |
| **`SpaceBetween` externe remplacé** par `spacedBy` pour réduire le vide central | `2306a76` |
| **Précision GPS affichée une seule fois** (statut seul en ligne principale, `±N m` en ligne secondaire si disponible) | `1e1d124` |

Choix d'interaction retenu pour le reset partiel : **tap simple** (et non appui long), pour la rapidité d'usage en rallye ; les actions dangereuses restent protégées par confirmation (Terminer, Réinitialiser total, Nouveau parcours), inchangées par ce chantier.

## 4. Statut

- **Validé Gradle local** (`testDebugUnitTest` + `assembleDebug`).
- **Validé APK** (vérification visuelle sur appareil).
- **Aucune modification** runtime / moteur GPS / service foreground / filtre anti-dérive / calibration.
- **Aucun changement fonctionnel métier** hors interaction UI (événements, logique de session, accumulation inchangés).

## 5. Limites

- **Test terrain long** en conditions rallye réelles **à refaire** pour valider l'ergonomie en usage roulant.
- **Calibration toujours non validée**.
- **Multi-appareils non validé**.
- **UI finale encore perfectible**, mais **utilisable**.

## 6. Liens

- Point d'étape central : [`runtime_gps_accumulation_status_2026_06_10.md`](runtime_gps_accumulation_status_2026_06_10.md).
- Jalon « version utilisable rallye simple » : [`usable_rally_milestone_2026_06_11.md`](usable_rally_milestone_2026_06_11.md).
