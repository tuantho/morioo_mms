"""Module Supabase Sync — envoi des métadonnées du bateau vers Supabase.

Toutes les SAMPLE_INTERVAL secondes, un snapshot de boat_data est mis en
buffer RAM ; toutes les UPLOAD_INTERVAL secondes, le buffer est envoyé en un
seul INSERT batch via l'API REST de Supabase (PostgREST), dans un thread —
jamais de blocage de l'event loop. Hors réseau (au large), le buffer
s'accumule en RAM (cap MAX_BUFFER, les plus anciens sont jetés) et repart au
retour du réseau. Aucune écriture sur la carte SD.

Configuration (.env) — module inactif (sans erreur) si absente :
    SUPABASE_URL=https://xxxx.supabase.co
    SUPABASE_KEY=<clé anon ou service_role>
    SUPABASE_TABLE=boat_telemetry        # optionnel (défaut)

Table à créer côté Supabase (SQL editor) :
    create table boat_telemetry (
        id          bigint generated always as identity primary key,
        ts          timestamptz not null,
        lat         double precision,
        lon         double precision,
        vitesse_kn  double precision,
        profondeur  double precision,
        batterie    double precision,
        gps_fix     boolean,
        pompe       boolean,
        feux        boolean
    );
    -- Avec la clé anon, activer RLS + une policy d'insert ;
    -- ou utiliser la clé service_role (le .env n'est jamais commité).
"""
import json
import logging
import os
import threading
import time
import urllib.request
from fastapi import APIRouter

log = logging.getLogger("morioo")
router = APIRouter(prefix="/api/supabase", tags=["supabase"])

SUPABASE_URL   = os.getenv("SUPABASE_URL", "").rstrip("/")
SUPABASE_KEY   = os.getenv("SUPABASE_KEY", "")
SUPABASE_TABLE = os.getenv("SUPABASE_TABLE", "boat_telemetry")
ENABLED        = bool(SUPABASE_URL and SUPABASE_KEY)

SAMPLE_INTERVAL = 10     # s entre deux snapshots mis en buffer
UPLOAD_INTERVAL = 60     # s entre deux envois batch
MAX_BUFFER      = 1080   # ~3 h de points en RAM si hors réseau

_buffer = []                       # snapshots en attente d'envoi
_lock = threading.Lock()           # tick (event loop) vs thread d'upload
_tick_count = 0
_upload_running = False

_state = {
    "enabled":      ENABLED,
    "buffered":     0,
    "sent_total":   0,
    "dropped":      0,      # points jetés (buffer plein hors réseau)
    "last_upload":  0,      # epoch du dernier envoi réussi
    "last_error":   "",
}


def _snapshot(boat_data):
    """Métadonnées envoyées — uniquement des scalaires de boat_data."""
    return {
        "ts":         time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "lat":        boat_data.get("lat"),
        "lon":        boat_data.get("lon"),
        "vitesse_kn": boat_data.get("vitesse"),     # NŒUDS (convention projet)
        "profondeur": boat_data.get("profondeur"),
        "batterie":   boat_data.get("batterie"),
        "gps_fix":    boat_data.get("gps_fix"),
        "pompe":      boat_data.get("pompe_de_cale"),
        "feux":       boat_data.get("lumieres_sous_marines"),
    }


def _upload_in_background():
    """Envoie le buffer en un seul INSERT batch. Exécuté dans un thread."""
    global _upload_running
    try:
        with _lock:
            batch = list(_buffer)
        if not batch:
            return

        req = urllib.request.Request(
            f"{SUPABASE_URL}/rest/v1/{SUPABASE_TABLE}",
            data=json.dumps(batch).encode(),
            headers={
                "apikey":        SUPABASE_KEY,
                "Authorization": f"Bearer {SUPABASE_KEY}",
                "Content-Type":  "application/json",
                "Prefer":        "return=minimal",
            },
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=15):
            pass

        with _lock:
            del _buffer[:len(batch)]
            _state["buffered"] = len(_buffer)
        _state["sent_total"] += len(batch)
        _state["last_upload"] = int(time.time())
        _state["last_error"]  = ""
        log.info("Supabase : %d point(s) envoyé(s)", len(batch))
    except Exception as e:
        # Réseau absent au large : normal — le buffer repartira plus tard
        _state["last_error"] = str(e)
        log.warning("Supabase injoignable (%d point(s) en buffer) : %s",
                    _state["buffered"], e)
    finally:
        _upload_running = False


def tick(boat_data):
    """Appelé ~1×/s par le cœur. Échantillonne puis upload — non bloquant."""
    global _tick_count, _upload_running
    if not ENABLED:
        return
    _tick_count += 1

    if _tick_count % SAMPLE_INTERVAL == 0:
        with _lock:
            _buffer.append(_snapshot(boat_data))
            if len(_buffer) > MAX_BUFFER:
                del _buffer[0]
                _state["dropped"] += 1
            _state["buffered"] = len(_buffer)

    if _tick_count % UPLOAD_INTERVAL == 0 and not _upload_running:
        _upload_running = True
        threading.Thread(target=_upload_in_background, daemon=True).start()


@router.get("")
def get_state():
    return _state


@router.post("/flush")
def flush():
    """Force un envoi immédiat du buffer (test / fin de sortie)."""
    global _upload_running
    if not ENABLED:
        return {"status": "disabled"}
    if not _upload_running:
        _upload_running = True
        threading.Thread(target=_upload_in_background, daemon=True).start()
    return {"status": "flushing", "buffered": _state["buffered"]}
