/* Module Météo — chip discret dans la top bar + popup prévisions 3h.
   S'injecte après #gps-indicator sans toucher index.html.            */
(function () {

    /* --- Styles (utilise les variables CSS de thème) --- */
    const style = document.createElement('style');
    style.textContent = `
        #weather-chip {
            font-size:0.85em; font-weight:bold;
            padding:3px 10px; border-radius:6px;
            border:1px solid var(--c-border);
            color:var(--c-muted);
            cursor:pointer; user-select:none;
            display:none; position:relative;
        }
        #weather-popup {
            display:none; position:fixed; top:38px; left:50%;
            transform:translateX(-50%);
            background:var(--c-panel);
            border:2px solid var(--c-border);
            border-radius:10px;
            padding:10px 18px; z-index:9999;
            color:var(--c-text);
            font-family:"Courier New",monospace;
            font-size:0.9em;
            box-shadow:0 4px 16px rgba(0,0,0,0.4);
            min-width:220px; text-align:center;
        }
        .wp-title  { margin-bottom:6px; font-size:1.05em; }
        .wp-now    { color:var(--c-label); margin-bottom:8px; }
        .wp-slot   { text-align:center; }
        .wp-hour   { color:var(--c-muted); font-size:0.8em; }
        .wp-temp   { color:var(--c-text); }
        .wp-age    { color:var(--c-sep); font-size:0.7em; margin-top:8px; }
    `;
    document.head.appendChild(style);

    /* --- Chip météo dans la top bar --- */
    const chip = document.createElement('div');
    chip.id = 'weather-chip';
    chip.title = 'Météo — cliquer pour les prévisions';
    chip.textContent = '…';

    const gpsEl = document.getElementById('gps-indicator');
    if (gpsEl && gpsEl.parentNode) {
        gpsEl.parentNode.insertBefore(chip, gpsEl.nextSibling);
    }

    /* --- Popup prévisions --- */
    const popup = document.createElement('div');
    popup.id = 'weather-popup';
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

            const tempStr = d.temp !== null ? Math.round(d.temp) + '°' : '';
            const windStr = d.wind_kmh !== null ? ' · 💨' + Math.round(d.wind_kmh) + 'km/h' : '';
            chip.textContent = `${d.icon} ${tempStr}${windStr}`;
            chip.style.display = 'inline-block';

            let html = `<div class="wp-title"><strong>${d.icon} ${d.label}</strong></div>`;
            html += `<div class="wp-now">${tempStr}${windStr}</div>`;
            if (d.forecast && d.forecast.length) {
                html += `<div style="display:flex;gap:12px;justify-content:center;">`;
                for (const f of d.forecast) {
                    html += `<div class="wp-slot">`
                          + `<div style="font-size:1.3em;">${f.icon}</div>`
                          + `<div class="wp-hour">${f.hour}</div>`
                          + `<div class="wp-temp">${Math.round(f.temp)}°</div>`
                          + `</div>`;
                }
                html += `</div>`;
            }
            const age = d.last_update
                ? Math.round((Date.now() / 1000 - d.last_update) / 60) + ' min'
                : '—';
            html += `<div class="wp-age">màj il y a ${age}</div>`;
            popup.innerHTML = html;

        } catch (e) {
            chip.style.display = 'none';
        }
    }

    refreshWeather();
    setInterval(refreshWeather, 30000);

})();
