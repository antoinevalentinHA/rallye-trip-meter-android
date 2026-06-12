# Contribuer

Merci de l'intérêt porté au projet. C'est un projet personnel et associatif,
maintenu principalement par son auteur : les contributions sont **bienvenues**, et
volontairement **sans bureaucratie**.

## Licence des contributions

Le projet est sous **GPL-3.0-or-later** (voir `LICENSE`). En contribuant, tu
acceptes que ta contribution soit distribuée sous cette **même licence**
(« inbound = outbound »). **Pas de CLA**, aucune cession de droits : tu **conserves
ton copyright** sur ce que tu apportes.

## DCO — Developer Certificate of Origin

Pas de CLA, mais un **DCO léger**. Concrètement, chaque commit doit être **signé**
(*sign-off*), ce qui atteste que tu as le droit de proposer ce code. C'est le
**Developer Certificate of Origin 1.1** (<https://developercertificate.org>).

Signer un commit = y ajouter une ligne :

```
Signed-off-by: Ton Nom <ton.email@example.com>
```

La commande l'ajoute automatiquement (avec le nom/e-mail de ta config Git) :

```bash
git commit -s -m "ton message"
```

Pour signer après coup une branche déjà commitée :

```bash
git rebase --signoff main
```

**Ce que le sign-off signifie, en clair** : tu certifies que tu es l'auteur du
changement (ou que tu as le droit de le soumettre), et que tu acceptes qu'il soit
distribué publiquement sous la licence du projet. Ce **n'est pas** une signature
cryptographique : c'est une **attestation**. Aucune autre formalité, aucun document
à signer.

## Workflow d'une PR

1. Fork, puis une **branche dédiée** par sujet.
2. Des **commits signés** (`-s`) et au message clair.
3. `./gradlew testDebugUnitTest assembleDebug` **au vert** en local.
4. Ouvre une **pull request** vers `main` et explique le *pourquoi*.
5. Revue par l'auteur ; quelques itérations si besoin.

Garde les PR **petites et ciblées**. Pour un changement de fond, ouvre d'abord une
**issue** pour en discuter.

## Style

Le dépôt suit une doctrine **contract-first** : les documents de `docs/contrats/`
font référence (voir aussi `docs/README.md`). Évite tout changement de comportement
sans justification, et reste cohérent avec les contrats existants.
