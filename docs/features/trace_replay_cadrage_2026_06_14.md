# Cadrage — carte de relecture a posteriori de la trace GPX

> **Nature** : document de cadrage, non normatif. Phase 1 (audit + décision
> d'architecture). **Aucun code, aucune dépendance, aucune carte implémentée par ce
> document.** Il prépare un éventuel chantier de relecture **hors session**.

## 0. Règle impérative (rappel, non négociable)

Cette fonctionnalité est une **relecture a posteriori** d'une trace **déjà
exportée**, hors session. Le contrat fonctionnel interdit fermement, **pendant une
session active** (`Running` / `Paused`) : toute carte, toute position live, tout
guidage, toute aide à la navigation. Aucune carte Android Auto. Le présent cadrage
ne lève rien de cette interdiction ; il la prend comme contrainte d'entrée.

## 1. État technique audité (HEAD `efb0609`)

- **Application mono-écran** : une seule `Activity` (`MainActivity`) montant un seul
  composable racine (`TripMeterRoute` → `TripMeterScreen`). **Aucun framework de
  navigation** (pas de Navigation Compose).
- **Dépendances** : Compose (BOM, Material3, activity-compose), lifecycle. **Aucune
  bibliothèque cartographique, aucune dépendance réseau, aucune clé API.**
- **Fichiers de trace déjà sur disque**, à deux endroits :
  - **interne à l'app** : `getExternalFilesDir("gpslogs")`
    (`Android/data/fr.arsenal.rallyetripmeter/files/gpslogs/`) — JSONL de session,
    **lisible par l'app sans aucune permission** (répertoire privé de l'app) ;
  - **copie exportée** : `Downloads/gpslogs/` via MediaStore — JSONL **et** GPX,
    pour l'utilisateur.
- **Outillage de lecture déjà présent** : `TickLogJsonl.parseEntry` (domaine pur)
  décode les ticks ; `GpxWriter` (domaine pur) sait sérialiser une trace. Un parseur
  GPX n'existe pas encore, mais la trace est aussi dérivable du JSONL interne.
- **États de session** disponibles pour un garde-fou : `TripSessionState`
  ∈ { `Stopped`, `Running`, `Paused` } (et `UiSessionStatus` côté UI).

## 2. Questions tranchées

### 2.1 Dernier GPX, ou liste de traces ?
**Liste de traces**, triée par date décroissante, avec une action « ouvrir » par
trace. Une simple « dernière trace » serait plus pauvre et ne couvre pas l'historique
(objectif « consulter l'historique de ses rallyes » de la feature). La liste reste
triviale : énumération du répertoire `gpslogs`.

### 2.2 Où placer l'accès ?
**Écran séparé**, atteint depuis le menu d'options de l'écran principal (entrée
« Traces » / « Historique »), **désactivée quand une session est active**. Pas
d'intégration dans l'écran du trip meter lui-même (interdit), pas de carte dans le
mode paysage validé.

### 2.3 Quelle bibliothèque ? → **V1 sans carte**
**V1 = parse de la trace + rendu vectoriel local de la polyligne sur un `Canvas`
Compose**, plus un écran résumé (nombre de points, début/fin, durée, distance GPX
brute indicative). **Aucune bibliothèque cartographique en V1.** Raisons :
- **Google Maps SDK** : compte Google Cloud + clé API + facturation + réseau →
  contraire à « sans compte développeur ni clé externe ». Écarté pour la V1.
- **osmdroid** : pas de clé, mais **réseau requis** pour les tuiles + politique
  d'usage des serveurs de tuiles OSM + dépendance externe non triviale. Écarté pour
  la V1 ; **candidat raisonnable pour une V2** si un fond de carte devient nécessaire.
- **MapLibre / Mapbox** : style/clé requis, lourd. Écarté.
- **Canvas Compose (retenu)** : zéro dépendance, zéro permission, zéro réseau, zéro
  clé. Robuste, hors-ligne, suffisant pour « revoir la forme du parcours ».

### 2.4 Dépendances ajoutées
**Aucune** en V1 (Compose `Canvas` est déjà disponible). C'est l'argument central du
choix.

### 2.5 Licence / clé API / réseau
**Aucune** en V1 : pas de tuiles, pas de fond de carte tiers, pas d'appel réseau,
pas de clé. (Une V2 osmdroid impliquerait la licence des tuiles et le réseau ;
hors périmètre ici.)

### 2.6 Première version simple, robuste, sans compte ni clé ?
**Oui** — c'est exactement la V1 proposée. Elle répond au critère de succès de la
feature (« où sommes-nous passés ? ») par la **forme** de la trace, sans fond de
carte.

### 2.7 Garantie technique « jamais pendant une session active »
Plusieurs garde-fous cumulés (cf. §6).

## 3. Options d'architecture comparées (synthèse)

| Option | Dépendance | Réseau | Clé/compte | Hors-ligne | Verdict |
|---|---|---|---|---|---|
| Canvas local (polyligne) | aucune | non | non | oui | **V1 retenue** |
| osmdroid (tuiles OSM) | externe | oui | non | partiel | V2 possible |
| Google Maps SDK | externe | oui | oui | non | écartée |
| MapLibre/Mapbox | externe | oui | oui (style) | partiel | écartée |

## 4. Recommandation V1 (argumentée)

Un **écran de relecture hors session** : liste des traces de `gpslogs`, puis, à
l'ouverture d'une trace, un **rendu vectoriel de la polyligne** (Canvas Compose,
auto-cadré sur l'emprise lat/lon) accompagné d'un **résumé** (points, début/fin,
durée). Source des points : le **GPX déjà exporté** (ajout d'un petit parseur GPX
domaine pur, symétrique de `GpxWriter`), ou à défaut le JSONL interne. Aucune
dépendance, aucune permission, aucun réseau : c'est la voie qui maximise la
robustesse et minimise la surface de risque, tout en restant strictement une
relecture a posteriori.

## 5. Périmètre exact de la V1

**Inclus** : entrée « Traces » dans le menu d'options (désactivée en session
active) ; écran liste (énumération `gpslogs`, tri par date) ; écran détail (polyligne
Canvas + résumé) ; parseur GPX domaine pur + tests ; navigation minimale entre les
deux écrans.
**Exclus** : tout fond de carte / tuiles ; toute bibliothèque carto ; tout réseau ;
toute position live ; toute intégration dans l'écran trip meter ou le mode paysage ;
Android Auto ; toute modification du moteur, des seuils, de l'accumulation, du JSONL
ou de l'export GPX (hors correction strictement nécessaire).

## 6. Garde-fous « pas de carte en session active »

1. **Déclenchement de l'accès** : l'entrée « Traces » du menu est **désactivée**
   (non cliquable) tant que `sessionStatus != Stopped`.
2. **Garde à l'entrée de l'écran** : l'écran de relecture vérifie l'état de session
   à l'ouverture et **refuse de s'afficher** (ou se referme) si une session est
   active — la garde ne dépend pas seulement de l'UI appelante.
3. **Nature des données** : l'écran ne lit que des **fichiers déjà écrits** ; il
   n'accède jamais au flux de localisation ni à la position courante. Aucune
   acquisition GPS, aucun point live possible par construction.
4. **Aucun lien au moteur** : l'écran ne référence ni `LocationEngine`, ni le
   service, ni `TripState` courant autrement que pour lire `sessionStatus`.

## 7. Fichiers probablement modifiés / ajoutés (V2 d'implémentation, pas ici)

- **Ajout** : un parseur GPX domaine pur (`domain/.../GpxTrackReader`) + tests ;
  un écran liste et un écran détail Compose (`ui/screen/...`) ; un petit composant
  Canvas de polyligne.
- **Navigation** : soit un état local simple dans `MainActivity` (un `when` sur un
  écran courant, sans dépendance), soit Navigation Compose (dépendance à justifier).
  **Préférence V1 : état local, zéro dépendance.**
- **Modification minime** : l'écran principal expose une entrée de menu
  « Traces » (désactivée en session active). Le mode paysage validé n'est pas
  modifié.
- **Non touchés** : moteur, seuils, accumulation, JSONL, export GPX, contrats moteur.

## 8. Risques Android / permissions / dépendances

- **Permissions** : **aucune nouvelle** si la lecture se fait depuis le répertoire
  interne `getExternalFilesDir("gpslogs")`. Lire `Downloads/gpslogs/` via MediaStore
  est aussi possible sans permission pour les fichiers créés par l'app. À préférer :
  le répertoire interne, sans MediaStore en lecture.
- **Dépendances** : **aucune** en V1. Toute carto réelle (V2) ajouterait une
  dépendance lourde et du réseau — à cadrer séparément.
- **Rendu Canvas** : projection lat/lon → écran à faire avec soin (auto-cadrage,
  ratio), mais c'est du dessin local sans risque externe.
- **Volumétrie** : une trace = milliers de points ; le Canvas devra sous-échantillonner
  si nécessaire (détail d'implémentation, pas un risque structurel).

## 9. Suite recommandée

- Si ce cadrage est validé, la **V1 d'implémentation** peut être engagée comme
  chantier distinct : parseur GPX + écran liste + écran détail Canvas + garde-fous,
  **sans aucune dépendance ni permission nouvelle**.
- La **V2 (fond de carte osmdroid)** reste une option ultérieure distincte, à
  n'engager que si la relecture vectorielle se révèle insuffisante, avec son propre
  cadrage (réseau, licence des tuiles).
- Ce document **n'engage pas** l'implémentation ; il fixe seulement l'architecture
  cible et les garde-fous.

---

Cadrage phase 1 de la carte de relecture a posteriori. Aucun code, aucune
dépendance, aucune carte. L'interdiction de carte en session active demeure
inconditionnelle.
