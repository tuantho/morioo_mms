# CLAUDE.md — Morioo MMS

Dashboard embarqué (Boesch 510) tournant sur Raspberry Pi 3, affiché en
kiosk Chromium sur un écran tactile Carpuride. Voir README.md pour le
restore et l'usage.

## Commandes

```bash
# Lancer en dev
venv/bin/python main.py            # → http://<IP>:8000/

# Sur le Pi (prod) : géré par systemd
sudo systemctl restart boesch_backend.service
journalctl -u boesch_backend.service -f   # logs en direct
```

### Tests

Le projet vise **Python 3.13**. En dev, le matériel (Wemos/GPS) est absent
→ l'app tourne en mode virtuel, ce qui suffit pour un smoke test :

```bash
python3.13 -m venv venv
venv/bin/pip install -r requirements.txt httpx
venv/bin/python tests/smoke_test.py     # démarre l'app + tape les routes
```

`tests/smoke_test.py` redirige les écritures vers `/tmp` (les chemins prod
`/home/ode/boesch_os` n'existent pas en dev) et vérifie `/api/status`, les
toggles relais (mode virtuel), le reset trip et `/api/trail`. La validation
complète (série, GPS réel) ne se fait que sur le Pi.

## Architecture

- `main.py` — backend FastAPI mono-fichier. Tout l'état vit dans le dict
  global `boat_data` + `trip_data` + `trail`. Deux boucles async lancées
  au `lifespan` via le supervisor `_supervise` (qui les relance si elles
  plantent) :
  - `read_gps()` — parse les trames NMEA RMC (`$GPRMC`/`$GNRMC`) du GPS
    u-blox, avec reconnexion à chaud du port.
  - `simulate_boat_and_spotify()` — profondeur/batterie simulées, ODO,
    position simulée si pas de fix GPS, polling Spotify.
  - Diagnostic exposé sur `/api/diag` (compteurs + uptime).
- `templates/index.html` — UI complète (vanilla JS + Canvas + Leaflet).
  Poll `/api/status` chaque seconde, `/api/trail` toutes les 5 s.
- `relais_usb/relais_usb.ino` — firmware Wemos D1 Mini : lit '1'/'0' sur
  la série (115200) → relais ON/OFF.
- `install/` — restore.sh, service systemd, règle udev.
- `modules/` — **fonctionnalités optionnelles**, une par fichier, chargées
  automatiquement (voir la section dédiée ci-dessous).

## Architecture modulaire (`modules/`) — LIRE AVANT D'AJOUTER UNE FONCTIONNALITÉ

⚠️ **Règle d'or, pour tout contributeur — humain OU assistant IA (Claude) :**
**toute nouvelle fonctionnalité va dans un module `modules/<nom>.py`, JAMAIS
en dur dans `main.py` ni `index.html`.** Le cœur ne contient que
l'infrastructure commune (jauges, GPS, ODO, relais, Spotify, et le socle qui
charge les modules). On ne modifie le cœur que pour l'infrastructure.

Pourquoi : **isolation des pannes** (un module qui plante n'affecte ni le
dashboard ni les autres modules — chaque `tick`/chargement est sous try/except)
et **indépendance** (proposer/retirer une feature sans entremêler le code).
Exemple de référence : `modules/anchor_watch.py` + `modules/anchor_watch.js`.

### Ajouter une fonctionnalité

1. Créer `modules/<nom>.py` exposant :
   - `router` : un `APIRouter(prefix="/api/<nom>")` avec ses endpoints.
   - `tick(boat_data)` *(optionnel)* : appelé ~1×/s par la boucle principale ;
     reçoit l'état du bateau, ne doit pas dépendre du reste de `main.py`.
   - `UI_LABEL` + `@router.get("/ui.js")` *(optionnel)* si le module a un frontend.
2. Créer `modules/<nom>.js` *(optionnel)* : frontend autonome qui s'injecte
   dans `#modules-bar` (et utilise `window.map` au besoin). Servi par le module.
3. **Rien à câbler** : le socle découvre le module au démarrage, l'expose sur
   `/api/modules`, et l'UI charge son JS automatiquement.

Pour **retirer** une fonctionnalité : supprimer son/ses fichier(s) dans
`modules/`. Rien d'autre.

### Ce qu'il NE faut PAS faire

- ❌ Définir un `xxx_data` global + des routes `@app.post("/api/xxx")` dans
  `main.py` pour une feature → ça va dans un module.
- ❌ Mettre le calcul d'une feature dans `simulate_boat_and_spotify` → utiliser
  le `tick(boat_data)` du module.
- ❌ Ajouter des boutons/JS d'une feature dans `index.html` → le module fournit
  son UI via son `.js`.
- ❌ Exposer l'état d'une feature dans `/api/status` → le module a sa route `/api/<nom>`.

### Note pour les assistants IA (Claude & autres comptes)

Si on te demande d'ajouter une fonctionnalité : **crée un module dans
`modules/`**, ne touche au cœur que pour de l'infrastructure réellement
partagée. **Avant de coder, vérifie que la feature n'existe pas déjà**
(regarde `modules/` et `GET /api/modules`) pour éviter les doublons. En cas de
doute « infra vs feature », demande plutôt que de modifier le cœur.

## Conventions & pièges (IMPORTANT)

- **Chemins absolus codés en dur** : tout pointe vers `/home/ode/boesch_os`
  (.env, trip.json, trail.json, cache Spotify). Ne pas « corriger » sans
  comprendre que la prod tourne sous cet utilisateur/chemin.
- **`boat_data["vitesse"]` est en NŒUDS**, pas en km/h. Le front
  re-multiplie par 1.852 pour l'affichage. Ne pas convertir deux fois.
- **GPS** : les u-blox multi-constellation émettent `$GNRMC`, pas
  forcément `$GPRMC`. Le filtre accepte désormais les deux (sinon le fix
  réel n'arrive jamais) — acquis sur `main`, à préserver.
- **Spotify redirect URI** = `http://127.0.0.1:8000/callback`. L'auth
  (`/login`) doit se faire DEPUIS le Pi, ou bien aligner l'URI sur l'IP
  réelle ET la déclarer dans le dashboard Spotify.
- **Dégradation gracieuse** : Wemos ou GPS absents ⇒ mode virtuel, jamais
  de crash. Garder ce principe pour tout nouveau matériel.
- **Seuil ODO/trail à 3 km/h** : en dessous, ni l'ODO ni la trace ne
  s'incrémentent — filtre le bruit GPS à l'arrêt (~0.2 kn parasites).
- **Trail : pas de point avant GPS fix** : au démarrage, on n'ajoute des
  points à la trace que si `gps_has_fix` est True (ou simulation pure),
  pour éviter un trait parasite depuis la position initiale vers le fix.

### Correctifs intégrés sur `main`

Ces points étaient des « pièges » ; ils sont désormais **mergés sur `main`**
et deviennent du comportement acquis à préserver :

- `$GNRMC` accepté en plus de `$GPRMC` (bug GPS ci-dessus).
- Supervisor de tâches async, reconnexion série à chaud, I/O protégées,
  `switch_device` fiable (renvoie `status: virtual` si le relais est absent).
- Écritures SD rares / atomiques (`.tmp`→rename) / conditionnelles
  (drapeaux *dirty*), via `SAVE_INTERVAL`.
- Spotify optionnel : l'app démarre même sans `.env`.
- **Spotify non-bloquant** : `current_playback()` passe par un thread +
  timeout (sinon un réseau lent figeait toute l'event loop). Sélection auto
  du device + diffusion forcée en priorité sur le device « Boesch 510 Audio »
  (`PREFERRED_DEVICE` dans `main.py`). Raspotify tourne comme service séparé
  (`raspotify.service`) et expose le Pi comme speaker Spotify Connect.
- **Parsing NMEA robuste** : une trame corrompue est ignorée (`gps_parse_errors`)
  sans déclencher de reconnexion ; seule une vraie erreur d'I/O série
  (`gps_read_errors`) reconnecte le port.
- **Carte type Waze** (frontend) : suivi du bateau centré en temps réel,
  désengagé dès qu'on touche la carte, bouton 🎯 Recentrer pour réengager.
- **Watchdog systemd** : voir la section Résilience.
- Logging structuré + endpoint `/api/diag` (compteurs de diagnostic).

## Contraintes Raspberry Pi (à respecter dans tout changement)

- **Usure carte SD** : éviter les écritures fréquentes. `trip.json`/
  `trail.json` sont déjà écrits en boucle — ne pas augmenter la fréquence,
  plutôt la réduire ou batcher.
- **Pas de RTC garanti** : l'heure peut être fausse au boot à froid hors
  réseau ; ne pas se fier à l'horloge pour de la logique critique.
- **CPU/GPU limités** : le front redessine 3 jauges Canvas + recentre la
  carte chaque seconde. Ne pas alourdir la boucle de rendu.

## Logging (en place — sans tuer la carte SD)

Objectif : pouvoir récupérer des logs après une sortie en bateau pour
diagnostiquer un souci (et les transmettre pour analyse). Contrainte
absolue : **ne jamais écrire les logs en continu sur la carte SD** (même
raison que `trip.json`/`trail.json` — usure).

Deux stratégies acceptables, l'une ou l'autre :

1. **En continu mais en RAM (« doucement »)** — approche **retenue et en
   place** : le backend logge via le module `logging` (`logger "morioo"`)
   sur stdout/stderr, capté par journald. Reste l'étape ops côté Pi : passer
   le journal en RAM avec `Storage=volatile` dans
   `/etc/systemd/journald.conf` (logs perdus au reboot, mais récupérables
   tant que le Pi tourne).
2. **Dump unique à la demande (« en une fois »)** — bufferiser en RAM
   (ou tmpfs) et n'écrire le fichier sur SD qu'**une seule fois**, sur
   action manuelle ou au shutdown propre, jamais à chaque événement.

Récupération pour analyse (à me transmettre ensuite) :

```bash
journalctl -u boesch_backend.service --since "today" > /tmp/mms.log
# puis copier /tmp/mms.log (scp / clé USB) hors du Pi
```

Règle d'or : **aucune écriture de log à la seconde sur la SD**. Le logging
applicatif (option 1) est en place ; reste à configurer `Storage=volatile`
côté Pi.

## Résilience / éviter les plantages

Principe clé : une boucle async qui lève une exception non rattrapée
meurt en silence — uvicorn continue de servir, donc `systemd` ne
redémarre rien et l'UI fige sur la dernière valeur. À éviter absolument.

Les garde-fous ci-dessous sont **implémentés sur `main`** (supervisor
`_supervise`, écritures atomiques, reconnexion série) : c'est le comportement
à préserver, pas du travail à faire.

- Toute I/O fichier (`save_trip`, `save_trail`, lecture/écriture `.env`)
  doit être protégée par try/except + écriture atomique (`.tmp` → rename).
- Les boucles de fond doivent survivre à une exception (relance interne
  plutôt que mort de la tâche).
- Série (Wemos/GPS) : prévoir une reconnexion à chaud — un connecteur qui
  bouge ne doit pas nécessiter un reboot du service.
- `switch_device` : ne mettre à jour l'état logiciel que si le `write`
  série a réussi, sinon l'UI ment sur l'état réel du relais.

### Watchdog systemd (en place — PIÈGE)

`Restart=always` relance si le process **crashe**, mais pas s'il **se fige**.
Un watchdog couvre le gel :
- `main.py` appelle `_sd_notify("WATCHDOG=1")` à chaque tour de la boucle
  `simulate_boat_and_spotify` (~1 s), et `_sd_notify("READY=1")` à la fin du
  `lifespan`.
- `boesch_backend.service` : `Type=notify` + `NotifyAccess=main` + `WatchdogSec=30`.

⚠️ **PIÈGE** : comme le service est en `Type=notify`, systemd **attend
`READY=1`** avant de considérer le démarrage réussi. Si tu modifies le
`lifespan` et que `READY=1` n'est plus envoyé (ou trop tard), **le service
ne démarrera jamais** (systemd le tue après le timeout). Et si la boucle ne
ping plus `WATCHDOG=1` pendant 30 s, systemd redémarre le service. Donc :
toujours préserver les deux appels `_sd_notify`.

## Application Android Auto (`android/`)

Application Kotlin (Car App Library 1.4.0) projetée sur l'autoradio **Ainavi**
via USB depuis le **Pixel 8**. Le Pi et le téléphone sont sur le même réseau
(hotspot ou routeur de bord).

### Structure

| Fichier | Rôle |
|---------|------|
| `MoriooCarService.kt` | Point d'entrée `CarAppService` |
| `MoriooSession.kt` | Session + écran d'accueil |
| `ApiClient.kt` | HTTP vers `http://rasp-boesch:8000` — modifier `BASE` si hostname ne résout pas |
| `DashboardScreen.kt` | Jauges (vitesse, profondeur, batterie, GPS, ODO, musique, météo) |
| `ControlsScreen.kt` | Pompe de cale (30 s auto-OFF) + feux sous-marins |
| `MapScreen.kt` | Carte CartoDB Dark sur `Surface` Android Auto + trace GPS |
| `TileCache.kt` | Cache LRU 80 tuiles OSM |

### Contraintes & pièges Android Auto

- **Car App Library** : `NavigationTemplate` nécessite la category
  `androidx.car.app.category.NAVIGATION` dans le Manifest **et** la permission
  `androidx.car.app.ACCESS_SURFACE` pour le rendu de carte.
- **`AppManager`** : utiliser `carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)` — `AppManager.create()` est package-private depuis la 1.3+.
- **`SurfaceContainer.surface`** est nullable depuis la 1.4 → toujours tester avant `lockCanvas`.
- **HTTP cleartext** : `android:usesCleartextTraffic="true"` requis (Pi = HTTP non chiffré).
- **Build** : AGP 8.3.2 + Gradle 8.6 + Java 17 + compileSdk/targetSdk 35.
  Ne pas changer les versions sans tester — des incompatibilités subtiles existent.
- **Tuiles CartoDB** : `https://cartodb-basemaps-a.global.ssl.fastly.net/dark_all/{z}/{x}/{y}.png` — même thème sombre que le dashboard Pi.

### Build

```bash
cd android
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Secrets

`.env` (jamais commité, voir `.env.example`) : `SPOTIFY_CLIENT_ID`,
`SPOTIFY_CLIENT_SECRET`, `SPOTIFY_REFRESH_TOKEN`. Le refresh token est
réécrit dans `.env` à chaque renouvellement.

Audit (2026-06-01) : aucun secret en dur dans le code ni dans
l'historique git. Le `.gitignore` couvre bien `.env`, `.cache*`,
`trip.json`, `trail.json`. À durcir un jour : `chmod 600` sur `.env`/
`.cache` (appareil physiquement accessible) et migration de l'auth
Spotify vers PKCE (supprimerait le besoin de `client_secret`).

## Sécurité — état actuel & dette connue

ATTENTION : pour le moment, l'application n'a AUCUNE sécurité réseau.
C'est un choix assumé pour un usage sur réseau bateau isolé, mais à
réévaluer si le Pi est un jour exposé (Wi-Fi partagé, port forwarding…).

- **CORS grand ouvert** : `allow_origins=["*"]` + toutes méthodes/headers
  (`main.py`). N'importe quelle page web peut taper l'API.
- **Endpoints `POST` non authentifiés** : `/api/switch/*` (relais),
  `/api/trip/reset`, `/api/spotify/*`. Quiconque est sur le même réseau
  peut piloter le matériel et la musique sans aucune authentification.
- **`/login` et `/callback` Spotify** ouverts également.

Pistes si durcissement nécessaire (à ajouter PLUS TARD, pas urgent) :
restreindre `allow_origins` à l'IP/host réel, ajouter un token/clé
partagée ou un mot de passe sur les routes `POST`, ou binder le serveur
sur le réseau local uniquement plutôt que `0.0.0.0`.
