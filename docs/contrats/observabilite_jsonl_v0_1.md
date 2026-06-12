# Trip Meter Rallye Android — Observabilité JSONL v0.1 (`TickLogJsonl`)

Statut : VALIDÉ (descriptif)
Dépend de :

- SampleVerdict (énumération de verdicts, `domain/diag/SampleVerdict`)
- TripState v0.1.2 (états de session)
- GpsAccumulationFilter v0.1 (émetteur des verdicts)

Objet : contrat normatif du **format de log d'observabilité JSONL** produit par le
pipeline GPS et consommé par l'outillage de replay et la CI. Ce document décrit
**exactement le format réellement utilisé** à HEAD `232fa0f` (`TickLogJsonl`,
`TickLogEntry`, `TickLogMeta`, `SampleVerdict`). Il **n'améliore ni ne modifie** le
format ; il le fige.

Référence d'implémentation : `domain/diag/TickLogJsonl` (codec),
`domain/diag/TickLogEntry` / `TickLogMeta` (modèle), `domain/diag/SampleVerdict`
(verdicts). Couverture : `TickLogJsonlTest` (round-trip).

---

## 0. Pourquoi un contrat — le format est une interface architecturale

Le JSONL n'est plus un détail interne : c'est la **frontière** entre un producteur
sur appareil et plusieurs consommateurs hors appareil.

- **Producteur** : `android/diag/FileTickLogSink` (écrit via `encodeMeta`/
  `encodeEntry`), exporté en `Downloads/gpslogs` par `TickLogExporter`, nommé
  `gpslog_*.jsonl` par `TickLogSessionFactory`.
- **Consommateurs** : `TickLogReplayReader` → `TickLogReplayAnalyzer` →
  `RealLogReplayTest` (**CI** : auto-découvre `real_*.jsonl`, asserte un invariant
  structurel), `TickLogPipelineReplay` / `TickLogReplayHarnessTest` (fidélité P2),
  `TuningGridReplay` / `TuningGridBaselineTest` (P5.a), et le **corpus** de 7 logs
  sous `app/src/test/resources/replay/`.

Une modification silencieuse du format casserait tout le replay P2→P5 et le corpus
CI. D'où ce contrat : verrouiller le format en vigueur et fixer les règles
d'évolution.

---

## 1. Schéma versionné

- Format : **JSONL** — une ligne = un objet JSON **plat** (aucune imbrication),
  encodage UTF-8, une entrée par ligne.
- Chaque ligne porte un champ entier **`"v"`** = version de schéma. Version
  courante : **`SCHEMA_VERSION = 1`**.
- Chaque ligne porte un champ chaîne **`"type"`** ∈ { `"meta"`, `"tick"` }.
- Deux formes de ligne disjointes : une ligne **meta** (en-tête de session) et des
  lignes **tick** (une par tick d'observabilité). Une ligne meta ne porte aucun
  champ de tick et réciproquement.
- Types de valeurs : entiers, doubles, booléens, chaînes, et le littéral `null`.
  Aucun objet ni tableau imbriqué.

Convention de fichier (corpus et captures device) : la ligne **meta** est en
première position, suivie des lignes **tick**. Voir §6 pour ce qui est *imposé* vs
*conventionnel*.

---

## 2. Ligne `meta` — champs

Ordre d'émission et types exacts :

| Clé | Type | Obligatoire | Sens |
|---|---|---|---|
| `v` | entier | oui (= 1) | version de schéma |
| `type` | chaîne | oui (= `"meta"`) | discriminant de ligne |
| `commit` | chaîne | oui | hash du commit de build **ou** identifiant de version équivalent |
| `device` | chaîne | oui | identifiant lisible de l'appareil (modèle) |
| `started_at_ms` | entier | oui | epoch (ms) du démarrage de la session de log |

Tous les champs meta sont **obligatoires et non nuls** (`requireString` /
`requireLong` au décodage).

---

## 3. Ligne `tick` — champs

Ordre d'émission, types et nullabilité exacts (la nullabilité est celle imposée par
le décodeur : `require*` = obligatoire non nul ; `optional*` = nullable) :

| # | Clé | Type | Nullable | Sens |
|---:|---|---|:---:|---|
| 1 | `v` | entier | non (= 1) | version de schéma |
| 2 | `type` | chaîne | non (= `"tick"`) | discriminant de ligne |
| 3 | `tick_elapsed_ms` | entier | **non** | horloge monotone de l'app au tick (ms écoulées) |
| 4 | `sample_ts_ms` | entier | oui | epoch (ms) de l'échantillon GPS courant |
| 5 | `sample_is_new` | booléen | oui | vrai si l'échantillon diffère du précédent |
| 6 | `lat` | double | oui | latitude (degrés décimaux) |
| 7 | `lon` | double | oui | longitude (degrés décimaux) |
| 8 | `accuracy_m` | double | oui | précision de la mesure (m) |
| 9 | `speed_mps` | double | oui | vitesse source (m/s) |
| 10 | `gps_status` | chaîne (énum) | **non** | `Unavailable` \| `Searching` \| `Fixed` |
| 11 | `session_state` | chaîne (énum) | **non** | `Running` \| `Paused` \| `Stopped` |
| 12 | `prev_ts_ms` | entier | oui | epoch (ms) de l'échantillon de référence (précédent) |
| 13 | `segment_m` | double | oui | distance brute du segment évalué (m) |
| 14 | `verdict` | chaîne (énum) | **non** | verdict du tick (§5) |
| 15 | `floor_m` | double | oui | plancher de mouvement appliqué (m) |
| 16 | `implied_speed_kmh` | double | oui | vitesse implicite du segment (km/h) |
| 17 | `delta_total_m` | double | **non** | distance ajoutée au total par ce tick (0.0 si rien) |
| 18 | `total_m` | double | **non** | distance totale brute après ce tick (m) |

Champs **obligatoires non nuls** d'un tick : `v`, `type`, `tick_elapsed_ms`,
`gps_status`, `session_state`, `verdict`, `delta_total_m`, `total_m`. Tous les
autres sont nullables et émis comme `null` quand la valeur n'existe pas au tick
(pas d'échantillon, pas de précédent, garde non évaluée).

---

## 4. Règles d'encodage et de décodage

### 4.1 Encodage (producteur)

- Objet plat, **clés fixes**, jamais omises : un champ absent est émis comme
  littéral **`null`** (le jeu de clés d'une ligne est stable, cf. I4).
- Les doubles encodés doivent être **finis** : `NaN`/`±Inf` sont **refusés** à
  l'encodage (`IllegalArgumentException`).
- Les nombres sont écrits via leur représentation décimale standard
  (`Long.toString` / `Double.toString` / version entière pour `v`).
- Les chaînes sont échappées : `\\`, `\"`, `\n`, `\r`, `\t`, et tout caractère de
  contrôle (< espace) en `\uXXXX`.

### 4.2 Décodage par ligne (codec strict)

Le codec `TickLogJsonl` est **strict par ligne** :

- `v` doit valoir la version courante ; sinon `IllegalArgumentException`.
- `type` doit correspondre au type attendu ; sinon rejet.
- Champ obligatoire manquant → rejet (« Champ manquant »).
- Type de valeur incorrect (chaîne attendue, nombre attendu…) → rejet.
- Valeur d'énumération inconnue (`gps_status`, `session_state`, `verdict`) → rejet.
- Contenu inattendu après l'objet, séparateur invalide, échappement inconnu,
  séquence `\u` invalide → rejet.
- Le décodeur tolère les **clés inconnues** : toutes les paires sont lues dans une
  map, seules les clés connues sont extraites ; une clé supplémentaire est ignorée
  (propriété exploitée en §6).

### 4.3 Décodage au niveau fichier (lecteur de corpus tolérant)

`TickLogReplayReader` est **tolérant au niveau fichier** (distinct du codec) :

- lit ligne par ligne, retire un `\r` final, **ignore les lignes vides** ;
- tente d'abord `parseEntry` (tick) ; en échec, tente `parseMeta` ;
- conserve la **première** ligne meta ; une meta supplémentaire est consignée comme
  **malformée** (non fatale) ;
- une ligne qui échoue aux deux est consignée dans `malformedLines` (non fatale) ;
- expose `entries` (ticks valides), `meta` (optionnelle), `malformedLines`, et
  `tickCount = entries.size`.

Conséquence : une ligne corrompue **n'interrompt pas** la lecture d'un fichier de
corpus ; elle est collectée. La rigueur est au niveau du codec, la tolérance au
niveau du lecteur.

---

## 5. Sémantique des verdicts

`verdict` ∈ énumération **fermée** `SampleVerdict` (8 valeurs). Origine et effet :

| Verdict | Émis par | `delta_total_m` |
|---|---|---|
| `ACCEPTED_SEGMENT` | filtre (branche nominale ; crédit de départ à la bascule STATIONARY→MOVING) | > 0 |
| `REJECTED_STATIONARY` | filtre (quasi-immobilité par vitesse **ou** échantillon gaté par la machine STATIONARY) | 0.0 |
| `REJECTED_NOISE` | filtre (segment sous le plancher bruit/incertitude) | 0.0 |
| `REJECTED_IMPLAUSIBLE_JUMP` | filtre (vitesse implicite > plafond, ou délai non positif) | 0.0 |
| `IGNORED_NOT_RUNNING` | filtre (session non `Running`) | 0.0 |
| `IGNORED_NO_ANCHOR` | filtre (aucune ancre exploitable) | 0.0 |
| `IGNORED_DUPLICATE` | **runtime** (relecture du cache sans nouveau fix) | 0.0 |
| `IGNORED_NO_SAMPLE` | **runtime** (aucun échantillon au tick) | 0.0 |

Notes de sémantique, fidèles à l'implémentation :

- Seul `ACCEPTED_SEGMENT` produit `delta_total_m > 0` ; tous les autres laissent
  `delta_total_m = 0.0`.
- **Conflation connue** : `REJECTED_STATIONARY` recouvre deux causes (rejet vitesse
  et gate machine). Le format ne les distingue pas (aucun verdict dédié) ; sans
  impact sur la distance. Limite d'observabilité documentée, conservée telle quelle.
- `IGNORED_DUPLICATE` et `IGNORED_NO_SAMPLE` sont des verdicts de **niveau runtime**
  (cache de localisation), jamais produits par le filtre.

Le détail des branches du filtre est dans le contrat
`docs/contrats/gps_accumulation_filter_v0_1.md`.

---

## 6. Invariants

- **I1 — version portée** : toute ligne porte `"v"` = `SCHEMA_VERSION` ; une ligne
  d'autre version est rejetée par le codec.
- **I2 — discriminant** : toute ligne porte `"type"` ∈ { `"meta"`, `"tick"` }.
- **I3 — schéma plat** : aucune imbrication ; valeurs primitives uniquement (entier,
  double, booléen, chaîne, `null`).
- **I4 — clés stables** : aucune clé n'est omise ; un champ sans valeur est émis
  `null`. Une ligne tick valide porte les 18 clés, une ligne meta les 5.
- **I5 — doubles finis** : tout double encodé est fini (`NaN`/`Inf` refusés).
- **I6 — un verdict par tick** : toute ligne tick porte exactement un `verdict` de
  l'ensemble fermé. Invariant structurel garde-fou en CI : sur un fichier,
  **Σ(verdicts) = nombre de ticks** (asserté par `RealLogReplayTest`).
- **I7 — round-trip sans perte** : `parseEntry(encodeEntry(x)) == x` et
  `parseMeta(encodeMeta(x)) == x` (couvert par `TickLogJsonlTest`).
- **I8 — énumérations fermées** : `gps_status` ∈ {Unavailable, Searching, Fixed} ;
  `session_state` ∈ {Running, Paused, Stopped} ; `verdict` ∈ les 8 valeurs §5. Toute
  valeur hors ensemble est rejetée au décodage.
- **I9 — meta au plus une, première gardée** : le lecteur conserve la première meta
  et signale toute meta supplémentaire comme malformée. La meta est *optionnelle*
  pour le lecteur (un fichier sans meta reste exploitable) ; sa présence en tête est
  *conventionnelle*, non imposée par le codec.

---

## 7. Règles de compatibilité futures

Ces règles encadrent **toute évolution ultérieure** ; elles ne modifient pas le
format actuel. Elles découlent du comportement réel du codec.

- **C1 — bump de version pour tout changement cassant.** `SCHEMA_VERSION` doit être
  incrémenté dès qu'un changement rend les logs existants inparsables ou change le
  sens d'un champ. Le codec rejette toute ligne dont `v` ≠ version courante : un
  bump implique donc soit un parseur multi-version, soit une **re-capture du corpus**
  (qui est un asset CI).
- **C2 — ajout de clé : toléré sans bump, sous condition.** Le décodeur ignore les
  clés inconnues ; un producteur **peut** ajouter une clé sans casser les
  consommateurs de même version. Mais I4 impose de l'émettre systématiquement (au
  besoin `null`). Un consommateur ne doit pas dépendre d'une clé qui n'existait pas
  dans la version qu'il cible.
- **C3 — suppression / renommage / changement de type d'un champ obligatoire :
  cassant** → bump de version requis.
- **C4 — extension d'une énumération : schéma-affectante.** Ajouter une valeur à
  `gps_status`, `session_state` ou `verdict` fait **rejeter** par tout consommateur
  antérieur un log qui la contient (`valueOf` lève). Donc : mettre à jour les
  consommateurs **avant** d'émettre la nouvelle valeur, et traiter l'extension comme
  un changement de schéma (bump ou re-capture). *Exemple concret* : les verdicts
  évoqués pour de futurs paliers (`ACCEPTED_NET_ON_TRANSITION`, `REJECTED_STALE`,
  `REANCHORED_AFTER_GAP`) relèvent de ce cas.
- **C5 — nullabilité : contractuelle.** Un champ déclaré obligatoire (§2–§3) est
  présent et non nul ; un champ nullable peut valoir `null`. Basculer un champ
  obligatoire en nullable (ou l'inverse) est un changement cassant.
- **C6 — corpus = artefacts v1.** Les 7 logs de `app/src/test/resources/replay/`
  sont des artefacts de schéma v1. Tout bump impose soit de conserver un parseur
  capable de lire v1, soit de re-capturer le corpus ; la CI (`RealLogReplayTest`) en
  dépend.

---

## 8. Statut normatif du document

- **Normatif et descriptif** : ce contrat décrit le format **implémenté et testé** à
  HEAD `232fa0f`. En cas d'écart entre ce document et le codec, le codec et les
  tests font foi pour le **comportement**, ce contrat faisant foi pour le **schéma
  attendu** et les **règles d'évolution**.
- **Référence, pas planification** : il n'introduit aucun code, ne change pas le
  format ni le corpus, ne crée aucun tag.
- **Version** : v0.1 (schéma JSONL `"v":1`). Une révision de ce contrat accompagnera
  tout bump de `SCHEMA_VERSION`.
