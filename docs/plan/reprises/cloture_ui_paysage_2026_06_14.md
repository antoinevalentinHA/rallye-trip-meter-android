# Clôture — chantier UI paysage (écran principal)

> **Navigation** — [← index des reprises](README.md) ·
> Actualise : [`tripdisplay_v0_1_2.md`](../../contrats/tripdisplay_v0_1_2.md),
> jalon [`usable_rally_milestone_2026_06_11.md`](../usable_rally_milestone_2026_06_11.md) ·
> [état courant](../../ETAT.md)

> **Nature** : document de clôture, non normatif. Il consigne le chantier d'ergonomie
> de l'écran principal en orientation paysage. **Aucun moteur GPS, seuil, contrat
> moteur, test métier ni JSONL modifié.** Le changement est strictement UI
> (`ui/screen/TripMeterScreen.kt`). La clôture est **actée** : la validation verte
> est acquise (voir §4).

## 1. Contexte

- Le jalon d'usabilité (`usable_rally_milestone_2026_06_11.md`) listait le mode
  paysage comme **non optimisé / non réalisé** : l'orientation n'était pas
  verrouillée, mais le layout était pensé portrait, et l'affichage paysage n'était
  pas adapté.
- Le contrat d'affichage `tripdisplay_v0_1_2.md` fixait pourtant le **mode paysage
  comme prioritaire** en voiture (TD-18, §5.2, §21), avec une cible deux colonnes :
  PARTIEL dominant, TOTAL/VITESSE, statut, corrections et commandes de session.
- Ce chantier réalise cette cible : il rapproche l'implémentation de l'intention
  déjà contractualisée. Le contrat n'avait donc pas besoin d'être modifié — il
  décrivait déjà l'écran cible.

## 2. Résumé des changements (UI uniquement)

Trois incréments successifs, tous dans `ui/screen/TripMeterScreen.kt` :

- **`feat(ui): add landscape layout`** (`28de473`) : détection d'orientation
  (`LocalConfiguration`), extraction du portrait existant dans `PortraitLayout`
  (rendu inchangé) et ajout d'un `LandscapeLayout` deux colonnes.
- **`fix(ui): make landscape main screen fit without scrolling`** (`32ace34`) :
  suppression du scroll de la colonne droite, répartition par poids, TOTAL/VITESSE
  sur une même ligne, StatusBar compacte lisible (correction de la concaténation
  des libellés), hauteurs de commandes réduites — tout tient à l'écran.
- **`fix(ui): stop label/value overlap in compact landscape cards`** (`f1443fb`) :
  en cartes compactes, passage d'un `Box` à deux alignements concurrents à une
  `Column` interne (label en haut, valeur centrée) — fin du chevauchement
  libellé/valeur.

Aucun nouvel événement UI, aucun champ d'état ajouté : les sous-composables
existants sont réutilisés, et les paramètres ajoutés aux composants partagés sont
**optionnels à défaut identique au portrait**.

## 3. État paysage validé (disposition principale en voiture)

En orientation paysage, sont visibles **simultanément, sans scroll** :

- **PARTIEL** dominant en colonne gauche (instrument de navigation au roadbook,
  cliquable pour reset) ;
- **TOTAL** et **VITESSE** lisibles en colonne droite ;
- **StatusBar** compacte lisible (GPS / précision / permission / session) ;
- **boutons de correction** du partiel (−100 / −10 / +10 / +100) ;
- **bouton de session** START / PAUSE / REPRISE / TERMINER selon l'état.

Le **mode portrait est strictement préservé** (extrait tel quel dans
`PortraitLayout`). L'orientation n'est pas verrouillée : l'application suit
l'orientation du téléphone.

## 4. Résultats de validation

Validation autoritative **acquise** côté mainteneur pour le HEAD de clôture :

- **Gradle local** : **OK** — `./gradlew.bat :app:testDebugUnitTest` et
  `./gradlew.bat :app:assembleDebug` verts.
- **CI GitHub Actions** : **verte** (workflow `android-ci.yml`).
- **Validation sur téléphone réel** : le mode paysage a été vérifié sur appareil ;
  l'écran principal est entièrement lisible et actionnable sans scroll, et le
  portrait reste conforme.

## 5. Garanties conservées

- **Moteur GPS, seuils, machine d'état, verdicts** : inchangés.
- **Contrats** : inchangés (le contrat d'affichage décrivait déjà le paysage cible).
- **Tests métier, corpus JSONL, goldens** : inchangés.
- **Mesure brute ⟂ correction d'affichage** : inchangée.
- **Pas de carte, pas d'aide à la navigation, pas d'Android Auto, pas de GPX** :
  hors périmètre de ce chantier, non engagés.

## 6. Mise en cohérence documentaire (ce chantier)

- **`usable_rally_milestone_2026_06_11.md`** : la limite « mode paysage non
  optimisé » devient « disposition paysage réalisée et validée » ; l'entrée « mode
  paysage dédié non réalisé » est retirée de la liste hors périmètre.
- **`tripdisplay_v0_1_2.md`** (contrat) : **non modifié** — il fixait déjà le
  paysage comme prioritaire ; l'implémentation l'a rejoint.
- Limite encore valable, **non touchée** : « UI non finalisée (pas de polish
  complet) » reste vraie au-delà de l'orientation.

## 7. Verdict

- **Chantier UI paysage clôturé** : la disposition paysage deux colonnes est
  réalisée, validée sur appareil, et la documentation reflète l'état réel.
- Le mode paysage est désormais la **disposition principale exploitable en
  voiture** ; le portrait reste pris en charge.

## 8. Suite recommandée

- **Ne pas ouvrir** de nouveau chantier par ce document.
- Prochain incrément applicatif naturel : **export GPX a posteriori** de la trace
  parcourue (consultation hors session, après arrêt) — fonctionnalité distincte,
  **non engagée ici**, à cadrer séparément. Elle reste soumise à l'interdiction
  ferme du contrat fonctionnel : **aucune carte ni aide à la navigation pendant une
  session active** ; la trace n'est exportable qu'**a posteriori**.
- Restent par ailleurs ouverts et distincts : P6.a (campagne terrain finale) et
  P6.c (doctrine de clôture du filtre).

---

Clôture documentaire du chantier UI paysage. Aucun code applicatif, test, JSONL ou
seuil modifié par ce document ; le changement UI (`28de473`, `32ace34`, `f1443fb`)
et sa validation (Gradle local + CI verte + validation appareil) sont acquis côté
mainteneur.
