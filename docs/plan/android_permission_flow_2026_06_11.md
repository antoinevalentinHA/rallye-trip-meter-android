# Flux de permissions Android — notifications & position (2026-06-11)

> **Nature** : note d'avancement **non normative**. Elle décrit le flux de permissions Android tel qu'implémenté au HEAD `40a53a8`. Les contrats `docs/contrats/` restent la référence. Aucun changement de code n'est introduit par ce document.

## 1. Objet

Consigner les correctifs récents du flux de permissions Android, après leur validation device par l'utilisateur. Deux permissions runtime sont concernées : **notifications** (`POST_NOTIFICATIONS`, Android 13+) et **position** (`ACCESS_FINE_LOCATION`).

## 2. Commits concernés

| Commit | Objet |
|---|---|
| `28cbe72` | `feat(android): request notification permission on Android 13+` |
| `4414fcc` | `fix(android): request location permission when needed` (premier correctif position) |
| `40a53a8` | `fix(android): request location permission before starting session` (correctif final position) |

## 3. Permissions déclarées (manifest)

`AndroidManifest.xml` déclare : `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` (position), `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` (service de premier plan), `POST_NOTIFICATIONS` (notifications). Ces déclarations préexistaient aux correctifs ci-dessous ; ces derniers ne portent que sur la **demande runtime**.

## 4. Notifications (`POST_NOTIFICATIONS`)

- Permission **déjà déclarée** au manifest.
- **Demande runtime au lancement**, **uniquement sur Android 13+** (`TIRAMISU`), depuis `MainActivity.onCreate` : si la permission n'est pas déjà accordée, elle est demandée (`28cbe72`).
- **Refus possible sans crash** : le résultat est volontairement ignoré, l'application continue de fonctionner.
- **Conséquence d'un refus** : la **notification du foreground service peut ne pas être visible**, mais **l'accumulation** (côté service) **n'est pas affectée**.
- Sur Android antérieur à 13, aucune demande runtime n'est nécessaire (permission accordée à l'installation).

Le comportement de demande des notifications au lancement a été **observé fonctionnel sur l'appareil de test** (la nouvelle version demande bien les notifications).

## 5. Position (`ACCESS_FINE_LOCATION`)

### 5.1 Problème corrigé

Le premier correctif (`4414fcc`) rendait la demande explicite mais la **lançait à l'entrée de la Route** (au chargement de l'écran). Cette demande entrait alors **en concurrence avec la demande `POST_NOTIFICATIONS`** faite au lancement : une seule boîte système s'affichait, la demande position pouvait **passer à la trappe** (aucune popup position observée, écran figé sur `POSITION REFUSÉE` avec `START` actif).

### 5.2 Flux retenu (correctif final, `40a53a8`)

- **Plus de demande automatique à l'entrée de la Route** : la demande position fragile au chargement est **supprimée**.
- **Demande déclenchée au moment utile — à l'appui sur START** — si la position est **absente/refusée**. L'interception se fait **dans la Route** (couche UI/Android) ; le ViewModel et le runtime **n'affichent aucune popup** et **ne connaissent pas** les permissions système.
- **Position déjà accordée** : `START` démarre la session normalement (aucune popup inutile).
- **Position acceptée après demande** : la session **démarre après l'accord** (l'intention de START est honorée).
- **Position refusée** : la session **ne démarre pas silencieusement** ; l'application reste stable et l'état affiché (`POSITION REFUSÉE`, session `ARRÊTÉ`) reste **cohérent**.
- **Refus définitif** : le système n'affiche plus de boîte ; un appui sur START correspond à **une seule tentative**, **sans boucle infinie**.

L'interception ne vise que l'action de session **quand la session est à l'arrêt** : **pause**, **reprise** et **terminer** ne sont pas interceptés et restent transmis tels quels.

Ce flux a été **validé device par l'utilisateur** : la demande position n'est plus lancée au chargement ; un START sans permission déclenche la demande ; un accord démarre la session ; un refus ne démarre pas la session. **CI verte** après le correctif final.

## 6. Périmètre — ce qui n'est pas modifié

Ces correctifs de permissions **ne changent pas** :

- le **moteur GPS** ;
- le **foreground service lifecycle** ;
- l'**accumulation** de distance ;
- le **filtre anti-dérive** ;
- la **calibration**.

Ils ne touchent que la couche UI/Android (`MainActivity`, `TripMeterRoute`) et la **demande** de permissions ; les notifications et la position ne sont pas mélangées au-delà de l'ordre d'apparition au lancement.

## 7. Réserves

- **Aucune validation de calibration** n'est affirmée ici : la calibration reste **bloquée** (plusieurs trajets de référence requis).
- **Pause/reprise** et **terminer** ne sont **pas** validés par ce travail sur les permissions ; leur validation device reste à compléter (cf. [`runtime_gps_accumulation_status_2026_06_10.md`](runtime_gps_accumulation_status_2026_06_10.md) §6/§7 et [`b4_device_validation_protocol_2026_06_11.md`](b4_device_validation_protocol_2026_06_11.md)).

## 8. Liens

- Point d'étape central : [`runtime_gps_accumulation_status_2026_06_10.md`](runtime_gps_accumulation_status_2026_06_10.md).
- Validation device B4 (scénario critique) : [`b4_device_validation_2026_06_11.md`](b4_device_validation_2026_06_11.md).
- Notification du foreground service : [`foreground_service_notification_validation_2026_06_10.md`](foreground_service_notification_validation_2026_06_10.md).
