# État actuel du projet

> Page d'état **vivante** : source unique de vérité sur ce qui est livré, limité et
> à venir. Les `README.md` (racine et `docs/`) pointent ici plutôt que de dupliquer.
> Non normative — en cas de désaccord, les contrats de `docs/contrats/` font foi.

**Tag courant : `v0.3.0-preview`** — utilisable, validé sur appareil ; validation
terrain finale non encore faite.

## Livré et validé sur appareil

- **Trip meter fonctionnel** : partiel (remis à zéro et corrigé au mètre), total,
  vitesse instantanée, calibration utilisateur (à l'affichage).
- **Runtime GPS foreground** : acquisition, permissions, pause/reprise, persistance
  de session, écran maintenu allumé en session.
- **Filtre anti-dérive** : machine stationnaire/mouvement neutralisant la dérive à
  l'arrêt sans dégrader la mesure en mouvement (série P1→P5).
- **Observabilité JSONL** par session + harness de replay JVM.
- **Export GPX a posteriori** à l'arrêt de session (Downloads/gpslogs).
- **Mode paysage** exploitable en voiture (sans scroll), portrait préservé.
- **Relecture locale de trace GPX** en polyligne (Canvas), hors session.

## Limites connues / non-promesses

- **Pas encore de validation terrain finale (P6.a)** : précision métrologique **non
  certifiée**.
- **Pas de vraie carte** (aucun fond géographique, aucune tuile, aucun réseau) — la
  relecture montre seulement la forme de la trace.
- **Aucune carte ni aide à la navigation pendant une session active** (interdiction
  ferme du contrat fonctionnel) ; la trace n'est exploitable qu'a posteriori.
- **Pas de Play Store**, **pas d'Android Auto**.

## Chantiers ouverts

- **P6.a** — campagne terrain / validation réelle (transformer des captures
  référencées en régressions permanentes ; vérifier M1–M5).
- **P6.c** — doctrine de clôture du filtre, une fois les données terrain disponibles.

## Prochains jalons possibles

- Après P6.a vert : première version **terrain-validée** (tag sans suffixe
  `-preview`, éventuelle release GitHub avec APK).
- Éventuelle **V2 de relecture** avec fond de carte (chantier distinct, à cadrer ;
  ajouterait réseau et licence de tuiles).

## Repères

- Vue d'ensemble du dépôt, build, CI : [`../README.md`](../README.md).
- Guide de lecture documentaire et contrats : [`README.md`](README.md).
- Historique des chantiers (clôtures, audits, cadrages) :
  [`plan/reprises/README.md`](plan/reprises/README.md).
