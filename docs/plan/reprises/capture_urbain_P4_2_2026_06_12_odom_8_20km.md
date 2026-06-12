# Capture urbaine référencée post-P4.2 — odomètre 8,20 km

Fiche de métadonnées pour le log replay urbain capturé après activation du gate
stationnaire P4.2. Documentation uniquement ; le fichier replay est déjà intégré
au corpus (commit `26e7ff3`).

## Identité du log

- **Fichier replay** : `app/src/test/resources/replay/real_urbain_voiture_20260612_odom_8_20km_p4_2.jsonl`
- **Fichier source Android** : `gpslog_20260612_174625.jsonl.txt`
  (chemin appareil : `/storage/emulated/0/Download/gpslogs/gpslog_20260612_174625.jsonl.txt`)
- **Date** : 2026-06-12
- **Type de trajet** : urbain réel (~8 km)
- **Appareil** : Pixel 10 Pro Fold
- **Moteur actif lors de la capture** : P4.2 (gate stationnaire actif)
- **Calibration** : 1.0 (ligne meta `commit:"1.0"`)

## Référence terrain (mesures utilisateur)

- **Compteur voiture (odomètre)** : 8,20 km
- **Trip meter affiché** : 7,98 km
- **Écart brut** : −0,22 km
- **Écart relatif** : ≈ −2,68 %

## Mesures relevées dans le fichier

- **Ticks** : 1127 (sur 1128 lignes, 1 ligne meta).
- **Durée approximative** : ~18,8 min.
- **Total enregistré** (`total_m` final) : **7975,96 m** ≈ 7,98 km, cohérent avec
  le trip meter affiché — confirme que le log est bien la course urbaine gated P4.2.
- Calculé vs odomètre 8200 m : **≈ −2,73 %** (l'écart −2,68 % de la référence
  utilisateur provient de l'arrondi au dixième). Ces valeurs ne sont pas assertées
  par la suite de tests ; elles relèvent de la mesure de replay / du terrain.

## Statut

Log **exploitable** pour P5 / audit urbain post-P4.2. Auto-découvert par
`RealLogReplayTest` (préfixe `real_`), qui ne fige qu'un invariant structurel
(somme des verdicts = nombre de ticks), sans assertion de total.

## Interprétation prudente

- Le compteur voiture est affiché **au dixième de km** : la référence est utile
  mais **peu précise**. À 8,2 km affichés, la vérité terrain peut raisonnablement
  se situer dans une plage de ±0,05 km, soit un écart réel compris approximativement
  entre −2,0 % et −3,3 %.
- L'écart est **au-delà** de la cible M2 (±2 %), contrairement au log route
  référencé 6,80 km (−1,76 %). C'est un signal à considérer, pas une preuve.

## Avertissement

**Ne pas utiliser ce log seul pour régler le moteur.** Une seule référence urbaine,
imprécise (arrondi au dixième), ne suffit pas à conclure à une sous-accumulation
systématique ni à justifier un changement de seuils. Tout réglage doit s'appuyer
sur un corpus élargi et, en priorité, sur une référence plus précise.

## Intérêt

Documenter une **possible sous-accumulation urbaine réelle après P4.2** : le trip
meter lit ~2,7 % sous l'odomètre sur ce trajet urbain, là où le log route
référencé restait à −1,76 %. Ce point alimente l'audit urbain post-P4.2 et la
préparation de P5, sans remettre en cause la clôture fonctionnelle de P4.
