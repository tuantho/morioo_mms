# Morioo MMS — Marine Management System

![Dashboard Morioo MMS](docs/screenshot.png)

## Overview
Dashboard tactile embarqué pour le *Boesch 510* (1964, V8 Crusader/Indmar). Tourne sur un Raspberry Pi monté en baie 1-DIN, accessible depuis un autoradio **Ainavi K40** (Android Auto via Pixel 8) et depuis n'importe quel navigateur sur le réseau bateau.

**Fonctionnalités :**
- Jauges temps réel : vitesse (km/h), profondeur (m), tension batterie (V)
- Carte nautique live (Leaflet + OpenSeaMap) avec suivi du bateau (type Waze)
- Contrôle de la pompe de cale (arrêt auto après 30 s) et des feux sous-marins OceanLED X-Series
- ⚓ Alarme de mouillage (anchor watch) : dérive + alarme visuelle et sonore
- Compteur de trip ODO (km, nautiques, temps de navigation) avec reset
- Lecture Spotify avec contrôle playback et playlists
- Communication USB/Série vers Wemos D1 Mini (ESP8266) pour les relais
- Diagnostic embarqué (`/api/diag`) et **architecture modulaire** pour les extensions

---

## Stack technique
| Couche | Technologie |
|--------|-------------|
| Backend | FastAPI (Python 3.13) + `pyserial` |
| Frontend | HTML5 + Canvas + Leaflet + vanilla JS |
| Matériel | Raspberry Pi 3, Ainavi K40 (Android Auto), Wemos D1 Mini + shield relais |
| Musique | Spotify API via `spotipy` |
| Android Auto | Kotlin + Car App Library 1.4.0 (voir `android/`) |

---

## Restore après crash

En cas de perte du système, cloner le repo et lancer le script de restore :

```bash
git clone https://github.com/oli1313/morioo_mms /home/ode/boesch_os
bash /home/ode/boesch_os/install/restore.sh
sudo reboot
```

Le script remet en place automatiquement :
- Les paquets système (`xdotool`, `fonts-noto-color-emoji`, etc.)
- L'environnement Python (venv + dépendances)
- Le service systemd `boesch_backend.service` (démarrage automatique)
- La règle udev pour le Wemos (port USB stable via `by-id`)
- Le cron de refresh Chromium toutes les 5 minutes

> **Spotify :** le token n'est pas dans le repo. Après un restore, aller sur `http://<IP-RASP>:8000/login` une fois pour se ré-authentifier.

---

## Installation manuelle (dev)

```bash
# 1. Cloner
git clone https://github.com/oli1313/morioo_mms /home/ode/boesch_os
cd /home/ode/boesch_os

# 2. Venv et dépendances
python3 -m venv venv
venv/bin/pip install -r requirements.txt

# 3. Lancer
venv/bin/python main.py
```

Interface accessible sur `http://<IP>:8000/`

---

## Structure du repo

```
.
├── main.py                  # Backend FastAPI — cœur + socle de chargement des modules
├── requirements.txt         # Dépendances Python
├── CLAUDE.md                # Guide contributeur (pièges, archi modulaire, sécurité)
├── modules/                 # Fonctionnalités optionnelles (1 par fichier, auto-chargées)
│   ├── anchor_watch.py      #   alarme de mouillage — backend
│   └── anchor_watch.js      #   alarme de mouillage — frontend
├── templates/
│   └── index.html           # Dashboard (jauges, carte, boutons, ODO, audio)
├── relais_usb/
│   └── relais_usb.ino       # Firmware Wemos D1 Mini (Arduino C++)
├── install/
│   ├── restore.sh           # Script de restore complet
│   ├── boesch_backend.service  # Service systemd (Type=notify + watchdog)
│   └── 99-wemos.rules       # Règle udev CH340 → /dev/ttyWEMOS
├── android/                 # Application Android Auto (Kotlin)
│   ├── app/src/main/kotlin/com/morioo/mms/
│   │   ├── MoriooCarService.kt   # Point d'entrée Car App Service
│   │   ├── MoriooSession.kt      # Session + écran d'accueil
│   │   ├── ApiClient.kt          # Appels HTTP vers le Pi (rasp-boesch:8000)
│   │   ├── DashboardScreen.kt    # Jauges, GPS, musique, météo
│   │   ├── ControlsScreen.kt     # Pompe de cale, feux sous-marins
│   │   ├── MapScreen.kt          # Carte CartoDB Dark (Surface rendering)
│   │   └── TileCache.kt          # Cache LRU des tuiles OSM (80 tuiles max)
│   ├── app/src/main/kotlin/com/morioo/mms/
│   │   ├── MoriooApp.kt          #   Application class (init SharedPreferences)
│   │   ├── AppPreferences.kt     #   URL Pi configurable (SharedPreferences)
│   │   ├── MainActivity.kt       #   WebView plein écran (app téléphone)
│   │   ├── SettingsActivity.kt   #   Écran config adresse Pi + test connexion
│   │   ├── MoriooCarService.kt   #   Point d'entrée CarAppService
│   │   ├── MoriooSession.kt      #   Session Android Auto
│   │   ├── ApiClient.kt          #   HTTP vers le Pi
│   │   ├── DashboardScreen.kt    #   PaneTemplate (vitesse, profondeur, batterie…)
│   │   ├── ControlsScreen.kt     #   Pompe, feux
│   │   ├── MapScreen.kt          #   Carte CartoDB Dark (Surface rendering)
│   │   └── TileCache.kt          #   Cache LRU tuiles OSM (80 max)
│   └── app/build.gradle          # AGP 8.7.3, Kotlin 2.2.10, Car App Library 1.4.0
├── tests/
│   └── smoke_test.py        # Smoke test : démarre l'app et vérifie les routes
├── docs/
│   └── screenshot.png       # Capture du dashboard
├── refresh_chromium.sh      # Envoi F5 à Chromium (appelé par cron)
├── trip.json / trail.json   # Données ODO + trace persistées (ignorés par git)
└── bin/                     # arduino-cli binary
```

> **Architecture modulaire** : chaque fonctionnalité optionnelle est un module
> autoporté dans `modules/` (auto-découvert au démarrage, isolé des autres via
> `try/except`). Pour **ajouter** une feature → créer `modules/<nom>.py`
> (+ `.js` optionnel) ; pour **retirer** → supprimer le fichier. Rien à câbler
> dans le cœur. Règles détaillées dans **`CLAUDE.md`**.

---

## API

| Méthode | Route | Description |
|---------|-------|-------------|
| `GET` | `/api/status` | Toutes les données bateau + trip ODO |
| `GET` | `/api/trail` | Trace GPS (liste de points) |
| `GET` | `/api/diag` | Compteurs de diagnostic + uptime |
| `GET` | `/api/modules` | Modules chargés + URL de leur frontend |
| `POST` | `/api/switch/pompe_de_cale` | Toggle pompe de cale (arrêt auto 30 s) |
| `POST` | `/api/switch/lumieres_sous_marines` | Toggle feux sous-marins OceanLED |
| `POST` | `/api/trip/reset` | Remet le compteur ODO à zéro |
| `POST` | `/api/spotify/{action}` | play / pause / next / previous |
| `POST` | `/api/spotify/playlist?playlist_id=` | Lancer une playlist |

Routes fournies par des **modules** (cf. `modules/`) :

| Méthode | Route | Description |
|---------|-------|-------------|
| `GET` | `/api/anchor` | État de l'alarme de mouillage |
| `POST` | `/api/anchor/set?radius=` | Pose l'ancre / arme l'alarme |
| `POST` | `/api/anchor/clear` | Lève l'ancre / désarme |

---

## Application Android — Boesch 510

L'application Android (`android/`) fait **deux choses en un seul APK** :

1. **App téléphone** : WebView plein écran → charge le dashboard Pi (`http://rasp-boesch.local:8000`)
2. **Android Auto** : interface Car App Library projetée sur l'**Ainavi K40** via USB

### Architecture réseau

```
Hotspot Pixel 8
    ├── Raspberry Pi (rasp-boesch.local:8000) ← API HTTP
    └── Ainavi K40 (Android Auto) ← USB ← Pixel 8
```

### Fonctionnalités Android Auto

| Écran | Contenu |
|-------|---------|
| **Dashboard** | Vitesse, profondeur, batterie, GPS, ODO, météo |
| **Contrôles** | Boutons pompe de cale + feux sous-marins (colorés selon état) |

### Fichiers principaux

| Fichier | Rôle |
|---------|------|
| `MainActivity.kt` | WebView plein écran (app téléphone) |
| `SettingsActivity.kt` | Config IP/URL du Pi + bouton "Tester la connexion" |
| `AppPreferences.kt` | Stockage SharedPreferences de l'URL Pi |
| `DashboardScreen.kt` | PaneTemplate Android Auto |
| `MoriooCarService.kt` | Point d'entrée CarAppService |
| `ApiClient.kt` | HTTP vers le Pi (lit `AppPreferences.piUrl`) |

### Build & installation

```bash
cd android
./gradlew assembleDebug
# APK : app/build/outputs/apk/debug/app-debug.apk

# Installer sur le Pixel 8
adb install app/build/outputs/apk/debug/app-debug.apk
```

> **Prérequis :** Java 17, Android SDK 35, Kotlin 2.2.10, AGP 8.7.3, Gradle 8.11.1.

### Connexion réseau

Le Pi se connecte au hotspot du Pixel 8. Si `rasp-boesch.local` ne résout pas
(DNS_PROBE_FINISHED_NXDOMAIN), ouvrir l'app → bouton **⚙** → entrer l'IP directe
du Pi (ex : `http://192.168.43.100:8000`) et taper "Tester".

> Le Pi doit avoir `avahi-daemon` installé pour la résolution mDNS (`restore.sh` le fait).

### Android Auto en voiture — sideload

Google bloque les APKs non-Play-Store en voiture réelle. Solutions :
- **AAAD** (Android Auto Apps Downloader) : patche AA pour accepter les sideloads — recommandé
- Ou : `adb install -i com.android.vending app-debug.apk` (simule Play Store)
- Activer le mode développeur AA : Paramètres AA → taper 10× sur la version → "Sources inconnues"

---

## Wemos D1 Mini — Firmware

Le sketch `relais_usb.ino` écoute sur le port série (115200 baud) :
- `1` → active le relais (lumières ON)
- `0` → désactive le relais (lumières OFF)

Le port est fixé via udev : le Wemos (chip CH340, `idVendor=1a86`) est toujours accessible via `/dev/serial/by-id/usb-1a86_USB_Serial-if00-port0`, peu importe le port USB physique utilisé.

---

## TODO
- [x] GPS réel — parse NMEA `$GPRMC`/`$GNRMC` depuis `/dev/ttyACM0` (u-blox 7)
- [ ] Capteur de profondeur réel — parse sonar NMEA
- [ ] Tension batterie réelle — lecture analogique depuis le Wemos
- [ ] Contrôle couleur projecteurs OceanLED

---

## Roadmap — idées de fonctionnalités

Classées de la plus intéressante à la moins, du point de vue du propriétaire
du *Boesch 510* (balades sur la Meuse, sécurité, confort à bord).

### 🛟 Sécurité (priorité haute)
1. ~~⚓ Alarme de mouillage (anchor watch)~~ — ✅ **réalisé** (module `anchor_watch`).
2. **🌊 Alarme de hauts-fonds** — alerte dès que la profondeur passe sous un
   seuil réglable (dès que le sondeur réel sera branché). *La Meuse a des
   zones peu profondes — protège l'hélice et la coque.*
3. **🌡️ Surveillance moteur** — température eau/moteur du V8 Crusader, avec
   alerte surchauffe. *Un V8 ancien qui chauffe = panne au milieu de l'eau.*
4. **🔋 Surveillance batterie réelle** — tension mesurée + alerte décharge
   (au lieu de la valeur simulée actuelle).

### 🧭 Navigation (priorité moyenne)
5. **🧭 Cap (COG) + heure depuis le GPS** — boussole de route, et heure juste
   même sans connexion ni horloge matérielle (RTC).
6. **📊 Statistiques de sortie** — vitesse max / moyenne, durée, distance,
   affichées en direct et conservées par sortie.
7. **🗺️ Historique des sorties + export GPX** — revoir et partager ses
   trajets (compatible Google Earth, OpenCPN…).

### 😎 Confort & plaisir (priorité basse)
8. **🌙 Mode jour / nuit** — atténuation automatique de l'écran le soir
   (confort visuel, ne pas être ébloui en navigation nocturne).
9. **📡 Météo & niveau de la Meuse** — conditions et hauteur d'eau en temps
   réel quand une connexion est disponible.
10. **📷 Caméra de poupe** — aide à l'accostage et à la marche arrière.
11. **🎚️ Contrôle couleur OceanLED** — choix de couleur/ambiance des
    projecteurs sous-marins (déjà au TODO).

> Chaque fonctionnalité doit respecter les principes du projet : dégradation
> gracieuse (jamais de crash si un capteur manque) et écritures SD minimales
> (cf. `CLAUDE.md`).

---

## Licence
Projet privé — usage personnel sur le *Boesch 510*. Redistribution interdite sans autorisation explicite.
