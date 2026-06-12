"""Module Supabase Sync — envoi des métadonnées du bateau vers Supabase.

Toutes les SAMPLE_INTERVAL secondes, un snapshot de boat_data est mis en
buffer RAM ; toutes les UPLOAD_INTERVAL secondes, le buffer est envoyé en un
seul batch, dans un thread — jamais de blocage de l'event loop. Hors réseau
(au large), le buffer s'accumule en RAM (cap MAX_BUFFER, les plus anciens
sont jetés) et repart au retour du réseau. Aucune écriture sur la carte SD.

Deux modes selon SUPABASE_URL dans .env (module inactif si absente) :

  - postgresql://user:pass@host:port/db  → INSERT direct via pg8000.
    ⚠️ L'hôte « direct » db.<ref>.supabase.co est IPv6-only : injoignable
    depuis le Pi (pas d'IPv6 routable sur le hotspot). Le module le détecte
    et bascule automatiquement sur le session pooler IPv4
    (aws-X-<région>.pooler.supabase.com, user postgres.<ref>), en sondant
    les régions usuelles puis en mémorisant celle qui répond.

  - https://xxxx.supabase.co (+ SUPABASE_KEY) → API REST PostgREST.

    SUPABASE_TABLE=boat_telemetry        # optionnel (défaut)

Table (déjà créée, RLS activé — le rôle postgres la bypasse) :
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
"""
import json
import logging
import os
import re
import socket
import threading
import time
import urllib.request
from fastapi import APIRouter

log = logging.getLogger("morioo")
router = APIRouter(prefix="/api/supabase", tags=["supabase"])

SUPABASE_URL   = os.getenv("SUPABASE_URL", "").rstrip("/")
SUPABASE_KEY   = os.getenv("SUPABASE_KEY", "")
SUPABASE_TABLE = re.sub(r"\W", "", os.getenv("SUPABASE_TABLE", "boat_telemetry"))
PG_MODE        = SUPABASE_URL.startswith("postgres")
ENABLED        = bool(SUPABASE_URL) and (PG_MODE or bool(SUPABASE_KEY))

SAMPLE_INTERVAL = 10     # s entre deux snapshots mis en buffer
UPLOAD_INTERVAL = 60     # s entre deux envois batch
MAX_BUFFER      = 1080   # ~3 h de points en RAM si hors réseau

# Régions sondées pour le pooler IPv4 (ordre = plus probables d'abord)
_POOLER_REGIONS = ["eu-west-1", "eu-central-1", "eu-west-2", "eu-west-3",
                   "eu-north-1", "eu-central-2", "us-east-1"]

_buffer = []                       # snapshots en attente d'envoi
_lock = threading.Lock()           # tick (event loop) vs thread d'upload
_tick_count = 0
_upload_running = False
_pg_endpoint = None                # (user, host, port, db, password) une fois résolu

_state = {
    "enabled":      ENABLED,
    "mode":         "postgres" if PG_MODE else "rest",
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


# ── Mode Postgres direct (pg8000) ──────────────────────────────────────────

def _has_ipv4(host):
    try:
        socket.getaddrinfo(host, 5432, socket.AF_INET)
        return True
    except OSError:
        return False


def _resolve_pg_endpoint():
    """Parse SUPABASE_URL ; si l'hôte direct n'a pas d'IPv4, sonde le pooler.

    Parsing à la main : le password peut contenir des caractères réservés
    (#, %…) non URL-encodés, qu'urllib refuserait.
    """
    global _pg_endpoint
    if _pg_endpoint:
        return _pg_endpoint

    m = re.match(r"postgres(?:ql)?://([^:]+):(.+)@([^@/]+?)(?::(\d+))?/([^?]+)",
                 SUPABASE_URL)
    if not m:
        raise ValueError("SUPABASE_URL postgres non parsable")
    user, password, host, port, db = m.groups()
    port = int(port or 5432)

    if _has_ipv4(host):
        _pg_endpoint = (user, host, port, db, password)
        return _pg_endpoint

    # Hôte direct IPv6-only → session pooler IPv4 (user postgres.<ref>)
    dm = re.match(r"db\.([a-z0-9]+)\.supabase\.co$", host)
    if not dm:
        raise OSError(f"{host} : pas d'IPv4 et pas un hôte direct Supabase")
    ref = dm.group(1)
    import pg8000.native
    for aws in ("aws-0", "aws-1"):
        for region in _POOLER_REGIONS:
            pooler = f"{aws}-{region}.pooler.supabase.com"
            if not _has_ipv4(pooler):
                continue
            try:
                con = pg8000.native.Connection(
                    f"postgres.{ref}", host=pooler, port=5432,
                    database=db, password=password, timeout=10)
                con.close()
                log.info("Supabase : pooler résolu → %s", pooler)
                _pg_endpoint = (f"postgres.{ref}", pooler, 5432, db, password)
                return _pg_endpoint
            except Exception:
                continue   # mauvaise région : « tenant not found »
    raise OSError("aucun pooler Supabase joignable")


def _upload_pg(batch):
    import pg8000.native
    user, host, port, db, password = _resolve_pg_endpoint()
    con = pg8000.native.Connection(user, host=host, port=port,
                                   database=db, password=password, timeout=15)
    try:
        for p in batch:
            con.run(
                f"insert into {SUPABASE_TABLE} "
                "(ts, lat, lon, vitesse_kn, profondeur, batterie, gps_fix, pompe, feux) "
                "values (:ts, :lat, :lon, :vitesse_kn, :profondeur, :batterie, "
                ":gps_fix, :pompe, :feux)", **p)
    finally:
        con.close()


# ── Mode REST (PostgREST) ──────────────────────────────────────────────────

def _upload_rest(batch):
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


# ── Boucle d'envoi ─────────────────────────────────────────────────────────

def _upload_in_background():
    """Envoie le buffer en un seul batch. Exécuté dans un thread."""
    global _upload_running
    try:
        with _lock:
            batch = list(_buffer)
        if not batch:
            return

        (_upload_pg if PG_MODE else _upload_rest)(batch)

        with _lock:
            del _buffer[:len(batch)]
            _state["buffered"] = len(_buffer)
        _state["sent_total"] += len(batch)
        _state["last_upload"] = int(time.time())
        _state["last_error"]  = ""
        log.info("Supabase : %d point(s) envoyé(s)", len(batch))
    except Exception as e:
        # Réseau absent au large : normal — le buffer repartira plus tard
        _state["last_error"] = str(e)[:300]
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
