/* Module Météo — chip discret dans la top bar + popup prévisions 3h.
   S'injecte après #gps-indicator sans toucher index.html.            */
(function () {

    /* --- Chip météo dans la top bar --- */
    const chip = document.createElement('div');
    chip.id = 'weather-chip';
    chip.title = 'Météo — cliquer pour les prévisions';
    chip.style.cssText =
        'font-size:0.85em;font-weight:bold;padding:3px 10px;border-radius:6px;' +
        'border:1px solid #3d2510;color:#8e6f43;cursor:pointer;user-select:none;' +
        'display:none;position:relative;';
    chip.textContent = '…';

    const gpsEl = document.getElementById('gps-indicator');
    if (gpsEl && gpsEl.parentNode) {
        gpsEl.parentNode.insertBefore(chip, gpsEl.nextSibling);
    }

    /* --- Popup prévisions --- */
    const popup = document.createElement('div');
    popup.id = 'weather-popup';
    popup.style.cssText =
        'display:none;position:fixed;top:38px;left:50%;transform:translateX(-50%);' +
        'background:#160e05;border:2px solid #8e6f43;border-radius:10px;' +
        'padding:10px 18px;z-index:9999;color:#ffeedd;font-family:"Courier New",monospace;' +
        'font-size:0.9em;box-shadow:0 4px 16px rgba(0,0,0,0.8);min-width:220px;text-align:center;';
    document.body.appendChild(popup);

    let popupOpen = false;
    chip.addEventListener('click', function (e) {
        e.stopPropagation();
        popupOpen = !popupOpen;
        popup.style.display = popupOpen ? 'block' : 'none';
    });
    document.addEventListener('click', function () {
        popupOpen = false;
        popup.style.display = 'none';
    });

    /* --- Poll /api/weather toutes les 30s --- */
    async function refreshWeather() {
        try {
            const d = await fetch('/api/weather').then(r => r.json());
            if (!d.ok) {
                chip.style.display = 'none';
                return;
            }

            // Chip : icone + température
            const tempStr = d.temp !== null ? Math.round(d.temp) + '°' : '';
            const windStr = d.wind_kmh !== null ? ' · 💨' + Math.round(d.wind_kmh) + 'km/h' : '';
            chip.textContent = `${d.icon} ${tempStr}${windStr}`;
            chip.style.display = 'inline-block';
            chip.style.borderColor = '#8e6f43';
            chip.style.color = '#ffeedd';

            // Popup : conditions actuelles + 3 prochaines heures
            let html = `<div style="margin-bottom:6px;font-size:1.05em;">`
                     + `<strong>${d.icon} ${d.label}</strong></div>`;
            html += `<div style="color:#d4a373;margin-bottom:8px;">`
                  + `${tempStr}${windStr}</div>`;
            if (d.forecast && d.forecast.length) {
                html += `<div style="display:flex;gap:12px;justify-content:center;">`;
                for (const f of d.forecast) {
                    html += `<div style="text-align:center;">`
                          + `<div style="font-size:1.3em;">${f.icon}</div>`
                          + `<div style="color:#8e6f43;font-size:0.8em;">${f.hour}</div>`
                          + `<div>${Math.round(f.temp)}°</div>`
                          + `</div>`;
                }
                html += `</div>`;
            }
            const age = d.last_update
                ? Math.round((Date.now() / 1000 - d.last_update) / 60) + ' min'
                : '—';
            html += `<div style="color:#3d2510;font-size:0.7em;margin-top:8px;">màj il y a ${age}</div>`;
            popup.innerHTML = html;

        } catch (e) {
            chip.style.display = 'none';
        }
    }

    refreshWeather();
    setInterval(refreshWeather, 30000);

})();
