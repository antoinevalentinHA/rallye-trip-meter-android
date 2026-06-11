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

## Cohérence projet

Cette section relie le concept au reste du dépôt. Elle ne planifie rien ; elle
résout des points de cohérence pour qu'un relecteur futur ne trouve pas deux
documents qui se contredisent.

### Réconciliation avec le contrat fonctionnel

`docs/contrats/contrat_fonctionnel_v0_2.md` définit l'app par la négative et
exclut explicitement « une carte GPS » (§ identité) et « carte » (§12 hors
périmètre). La présente feature introduit une carte : il faut lever
l'ambiguïté, sinon les deux documents se contrediront pour qui ne connaît pas
l'intention.

La distinction de fond : le contrat exclut la carte **en tant qu'aide à la
navigation pendant l'épreuve** ; cette feature est une **visualisation a
posteriori** (souvenir, historique), qui n'assiste jamais la course. Les deux
positions sont compatibles une fois nommées.

Action proposée (à appliquer délibérément au contrat normatif, hors de ce
document) : ajouter au contrat fonctionnel une note du type —

> La carte est exclue **en tant qu'aide à la navigation pendant l'épreuve**.
> Une visualisation de trace **a posteriori** (post-rallye, non temps réel)
> est une fonctionnalité distincte, sans effet sur le calcul du trip meter :
> cf. `docs/features/trace_rallye_osm.md`.

### Réutilisation de l'acquis P1 (observabilité)

Le `TraceRecorder` ne doit pas être une seconde acquisition GPS. Le pipeline
d'observabilité P1 capture déjà, par tick, `lat`/`lon`/`accuracy`/`timestamp`
par session (cf. `docs/plan/p1_observabilite_jsonl_2026_06_11.md`). Une trace
de rallye est cette séquence d'échantillons, filtrée de ses points aberrants.

Conception cohérente avec l'existant : le `TraceRecorder` consomme le **même
flux d'échantillons** que le filtre d'accumulation (un consommateur de plus
sur le flux de localisation), pas une voie parallèle. Cela préserve l'invariant
posé plus haut — la trace n'entre jamais dans `DistanceEngine` — tout en
évitant de dupliquer l'acquisition.

### Le vrai sous-système neuf : la persistance durable

Les chantiers P1–P5 ne persistent que des scalaires (`TripStateSnapshot`) et
des logs JSONL éphémères. Une trace consultable a posteriori exige un stockage
**durable** par session (format type GPX ou GeoJSON, géré dans le temps :
écriture, relecture, rétention, suppression). C'est le sous-système réellement
nouveau de cette feature — pas la carte, qui n'est qu'un rendu. À concevoir
comme tel le moment venu, avec sa propre frontière et ses contrats.

### Question ouverte

L'UI envisagée liste « distance GPS brute ». Ce champ doit être défini avant
implémentation : s'agit-il du `total_m` du moteur (qui accumule la dérive
documentée en P2 — afficherait « 45 m » pour une session immobile), ou de la
longueur de la polyligne réellement tracée (qui dériverait davantage encore) ?
Tant que la qualité de la distance brute n'est pas stabilisée (P3–P5), aucune
de ces deux valeurs n'est un affichage fiable — ce qui rejoint la dépendance
de séquencement déjà posée.

### Séquencement — inchangé

Les dépendances listées plus haut (stabilisation du moteur, clôture P3/P4/P5)
restent la condition d'entrée. Cette section ne les modifie pas : elle prépare
seulement une conception cohérente pour le jour où la feature sera planifiée.
