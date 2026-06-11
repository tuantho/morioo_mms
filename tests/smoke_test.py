"""Smoke test de Morioo MMS — à lancer en dev / CI (sans matériel).

Démarre l'app FastAPI via TestClient (Wemos/GPS absents → mode virtuel) et
vérifie les routes du cœur + chaque module chargé. **Auto-extensible** : tout
nouveau module exposé par /api/modules est testé automatiquement, sans toucher
à ce fichier.

Les écritures sont redirigées vers /tmp (les chemins prod /home/ode/boesch_os
n'existent pas hors du Pi).

Usage : python tests/smoke_test.py
"""
import os
import sys
from pathlib import Path

# Credentials Spotify factices : sans .env, spotipy refuse un client_id vide à
# la construction. Aucune requête réseau n'est émise (refresh_token vide).
os.environ.setdefault("SPOTIFY_CLIENT_ID", "dummy")
os.environ.setdefault("SPOTIFY_CLIENT_SECRET", "dummy")

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

import main

# Rediriger les écritures hors de /home/ode/boesch_os (absent hors du Pi)
main.TRIP_FILE = Path("/tmp/mms_trip_test.json")
main.TRAIL_FILE = Path("/tmp/mms_trail_test.json")

from fastapi.testclient import TestClient


def run():
    with TestClient(main.app) as c:
        # --- Cœur ---
        d = c.get("/api/status").json()
        for k in ("vitesse", "profondeur", "batterie", "lat", "lon", "gps_fix", "trip"):
            assert k in d, f"clé manquante dans /api/status : {k}"
        print("OK /api/status :", {k: d[k] for k in ("vitesse", "profondeur", "gps_fix")})

        assert c.get("/api/trail").status_code == 200
        assert isinstance(c.get("/api/trail").json(), list)
        print("OK /api/trail")

        diag = c.get("/api/diag").json()
        assert "uptime_s" in diag, "diag incomplet"
        print("OK /api/diag")

        # Toggle relais (mode virtuel — pas de Wemos en dev)
        r = c.post("/api/switch/pompe_de_cale")
        assert r.status_code == 200, r.status_code
        assert r.json().get("device") == "pompe_de_cale", r.json()
        print("OK /api/switch/pompe_de_cale :", r.json().get("status"))

        # Device inconnu : refusé proprement, sans planter
        assert c.post("/api/switch/inconnu_xyz").json().get("status") == "error"
        print("OK /api/switch/<inconnu> rejeté")

        assert c.post("/api/trip/reset").status_code == 200
        print("OK /api/trip/reset")

        # --- Modules (auto-extensible) ---
        mods = c.get("/api/modules").json()
        assert isinstance(mods, list)
        print(f"OK /api/modules : {len(mods)} module(s) ->", [m["name"] for m in mods])
        for m in mods:
            ui = m.get("ui_js")
            if ui:
                base = ui[: -len("/ui.js")]   # ex. "/api/anchor"
                assert c.get(base).status_code == 200, f"{base} ne répond pas"
                assert c.get(ui).status_code == 200, f"{ui} ne répond pas"
                print(f"   OK module '{m['name']}' : {base} + {ui}")

    # --- Fonctions pures (logique testable sans matériel ni I/O) ---
    assert main._haversine_km((50.0, 5.0), (50.0, 5.0)) == 0.0
    assert main._parse_nmea_coord("5029.000", "N") == 50.483333
    print("OK fonctions pures (_haversine_km, _parse_nmea_coord)")

    print("\nSMOKE TEST OK")


if __name__ == "__main__":
    run()
