"""Module Météo — conditions actuelles + prévisions 3h via Open-Meteo.

Aucune clé API requise. Poll toutes les 10 min avec la position GPS du bateau.
Expose /api/weather (état courant + prévisions horaires).
Frontend : chip météo discret dans la top bar (weather.js).
"""
import urllib.request
import urllib.parse
import json
import time
import logging
from fastapi import APIRouter
from fastapi.responses import PlainTextResponse
from pathlib import Path

log = logging.getLogger("morioo")
UI_LABEL = "Météo"
router = APIRouter(prefix="/api/weather", tags=["weather"])

POLL_INTERVAL = 600   # secondes entre deux appels réseau (10 min)

_state = {
    "ok":          False,
    "temp":        None,    # °C
    "wind_kmh":    None,
    "code":        None,    # WMO weather code
    "icon":        "?",
    "label":       "",
    "forecast":    [],      # liste de {hour, icon, temp} pour les 3 prochaines heures
    "last_update": 0,
    "error":       "",
}
_tick_count = 0


# WMO weather codes → (emoji, label court)
# https://open-meteo.com/en/docs#weathervariables
_WMO = {
    0:  ("☀️",  "Clair"),
    1:  ("🌤️", "Peu nuageux"),
    2:  ("⛅",  "Nuageux"),
    3:  ("☁️",  "Couvert"),
    45: ("🌫️", "Brouillard"),
    48: ("🌫️", "Brouillard givrant"),
    51: ("🌦️", "Bruine légère"),
    53: ("🌦️", "Bruine"),
    55: ("🌧️", "Bruine forte"),
    61: ("🌧️", "Pluie légère"),
    63: ("🌧️", "Pluie"),
    65: ("🌧️", "Pluie forte"),
    71: ("🌨️", "Neige légère"),
    73: ("🌨️", "Neige"),
    75: ("❄️",  "Neige forte"),
    80: ("🌦️", "Averses légères"),
    81: ("🌧️", "Averses"),
    82: ("⛈️",  "Averses fortes"),
    95: ("⛈️",  "Orage"),
    96: ("⛈️",  "Orage + grêle"),
    99: ("⛈️",  "Orage violent"),
}

def _wmo(code):
    if code is None:
        return "?", ""
    # Arrondi à la dizaine la plus proche si code inconnu
    icon, label = _WMO.get(code, _WMO.get((code // 10) * 10, ("🌡️", "Inconnu")))
    return icon, label


def _fetch(lat, lon):
    """Appel HTTP synchrone vers Open-Meteo (exécuté dans un thread par le cœur)."""
    params = urllib.parse.urlencode({
        "latitude":   round(lat, 4),
        "longitude":  round(lon, 4),
        "current":    "temperature_2m,weather_code,wind_speed_10m",
        "hourly":     "temperature_2m,weather_code",
        "timezone":   "Europe/Brussels",
        "forecast_days": 1,
        "wind_speed_unit": "kmh",
    })
    url = f"https://api.open-meteo.com/v1/forecast?{params}"
    with urllib.request.urlopen(url, timeout=10) as r:
        return json.loads(r.read())


def tick(boat_data):
    """Appelé ~1×/s par le cœur. Poll météo toutes les POLL_INTERVAL secondes."""
    global _tick_count
    _tick_count += 1
    # Premier appel immédiat, puis toutes les 10 min
    if _tick_count != 1 and _tick_count % POLL_INTERVAL != 0:
        return

    lat = boat_data.get("lat")
    lon = boat_data.get("lon")
    if lat is None or lon is None:
        return

    try:
        data = _fetch(lat, lon)
        cur  = data["current"]
        code = cur.get("weather_code")
        icon, label = _wmo(code)

        # Prévisions : 3 prochaines heures complètes
        hours  = data["hourly"]["time"]
        temps  = data["hourly"]["temperature_2m"]
        codes  = data["hourly"]["weather_code"]
        now_h  = time.strftime("%Y-%m-%dT%H:00")
        forecast = []
        for i, h in enumerate(hours):
            if h > now_h and len(forecast) < 3:
                hi, _ = _wmo(codes[i])
                forecast.append({
                    "hour":  h[11:16],   # "HH:MM"
                    "icon":  hi,
                    "temp":  temps[i],
                })

        _state.update({
            "ok":          True,
            "temp":        cur.get("temperature_2m"),
            "wind_kmh":    cur.get("wind_speed_10m"),
            "code":        code,
            "icon":        icon,
            "label":       label,
            "forecast":    forecast,
            "last_update": int(time.time()),
            "error":       "",
        })
        log.info("Météo mise à jour : %s %s°C vent %s km/h",
                 icon, _state["temp"], _state["wind_kmh"])
    except Exception as e:
        _state["ok"]    = False
        _state["error"] = str(e)
        log.warning("Météo indisponible : %s", e)


@router.get("")
def get_weather():
    return _state


@router.get("/ui.js")
def ui_js():
    js = (Path(__file__).parent / "weather.js").read_text()
    return PlainTextResponse(js, media_type="application/javascript")
