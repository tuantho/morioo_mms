"""Module Anchor Watch — alarme de mouillage (⚓).

Migré depuis l'implémentation monolithique d'oli1313 vers un module isolé :
comportement et UI identiques (marqueur + cercle de rayon sur la carte, rayon
réglable, alarme visuelle + sonore), mais le code ne vit plus dans main.py.

Module autonome : il ne dépend de rien dans main.py (il reçoit la position via
tick()), et une exception au chargement ou dans tick() est attrapée par le cœur
sans jamais bloquer le dashboard.
Pour retirer la fonctionnalité : supprimer ce fichier + anchor_watch.js.
"""
import math
from pathlib import Path
from fastapi import APIRouter
from fastapi.responses import PlainTextResponse

UI_LABEL = "Anchor Watch"
router = APIRouter(prefix="/api/anchor", tags=["anchor"])

# État de l'alarme de mouillage. active=True → on surveille la distance entre la
# position courante et le point de mouillage ; si > radius_m, alarm passe à True.
anchor_data = {
    "active": False,
    "lat": None,
    "lon": None,
    "radius_m": 50,       # rayon par défaut en mètres
    "alarm": False,
    "distance_m": 0.0,
}
# Dernière position connue (alimentée par tick) : sert à armer l'ancre sans que
# le module ait besoin d'accéder à boat_data ailleurs que dans tick().
_last = {"lat": None, "lon": None}


def _haversine_m(lat1, lon1, lat2, lon2):
    r1, r2 = math.radians(lat1), math.radians(lat2)
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = math.sin(dlat / 2) ** 2 + math.cos(r1) * math.cos(r2) * math.sin(dlon / 2) ** 2
    return 6371000 * 2 * math.asin(math.sqrt(a))


def tick(boat_data):
    """Appelé ~1×/s par le cœur : mémorise la position et calcule la dérive."""
    _last["lat"] = boat_data.get("lat")
    _last["lon"] = boat_data.get("lon")
    if not anchor_data["active"] or anchor_data["lat"] is None:
        return
    if _last["lat"] is None or _last["lon"] is None:
        return
    dist_m = _haversine_m(anchor_data["lat"], anchor_data["lon"], _last["lat"], _last["lon"])
    anchor_data["distance_m"] = round(dist_m, 1)
    anchor_data["alarm"] = dist_m > anchor_data["radius_m"]


@router.get("")
def get_anchor():
    return anchor_data


@router.post("/set")
def set_anchor(radius: int = 50):
    """Arme l'alarme de mouillage à la position GPS courante."""
    if _last["lat"] is None or _last["lon"] is None:
        return {"status": "error", "message": "position inconnue (pas encore de point GPS)"}
    anchor_data["active"] = True
    anchor_data["lat"] = _last["lat"]
    anchor_data["lon"] = _last["lon"]
    anchor_data["radius_m"] = max(10, min(radius, 500))
    anchor_data["alarm"] = False
    anchor_data["distance_m"] = 0.0
    return {"status": "ok", "lat": anchor_data["lat"], "lon": anchor_data["lon"],
            "radius_m": anchor_data["radius_m"]}


@router.post("/clear")
def clear_anchor():
    """Désarme l'alarme de mouillage."""
    anchor_data["active"] = False
    anchor_data["alarm"] = False
    anchor_data["distance_m"] = 0.0
    return {"status": "ok"}


@router.get("/ui.js")
def ui_js():
    """Sert le frontend du module (fichier compagnon anchor_watch.js)."""
    js = (Path(__file__).parent / "anchor_watch.js").read_text()
    return PlainTextResponse(js, media_type="application/javascript")
