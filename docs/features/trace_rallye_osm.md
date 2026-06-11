# Fonctionnalité — Trace Rallye et Visualisation Cartographique

## Statut

Idée validée.

Non planifiée.

Aucun développement engagé.

## Vision

À l'issue d'un rallye, le participant peut consulter la trace complète de sa journée sur une carte et revivre son parcours.

Cette fonctionnalité n'a pas vocation à assister la navigation pendant l'épreuve.

Elle constitue un historique visuel et un souvenir du rallye réalisé.

## Objectifs

Permettre au participant de :
- visualiser le parcours effectué ;
- revoir les routes empruntées ;
- identifier les points de départ et d'arrivée ;
- consulter l'historique de ses rallyes ;
- conserver un souvenir des événements auxquels il a participé.

## Principes d'architecture

La trace GPS ne doit jamais participer au calcul officiel du trip meter.

```text
Trip Meter
├── DistanceEngine
└── TraceRecorder
```

## Interface utilisateur envisagée

Carte OpenStreetMap affichant :
- la trace complète ;
- le point de départ ;
- le point d'arrivée ;
- la distance du trip meter ;
- la distance GPS brute.

## Dépendances

Avant toute implémentation :
- stabilisation du moteur GPS ;
- clôture des chantiers P3 ;
- clôture des chantiers P4 ;
- clôture des chantiers P5.

## Critère de succès

À l'issue d'un rallye, un participant doit pouvoir ouvrir une session terminée et répondre visuellement à la question :

> Où sommes-nous passés aujourd'hui ?
