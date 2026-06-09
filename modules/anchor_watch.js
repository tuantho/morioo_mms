// Frontend du module Anchor Watch — autonome.
// Injecte son style + son UI dans #modules-bar, utilise la carte Leaflet
// globale (window.map) pour le marqueur + le cercle de rayon, et interroge
// /api/anchor. Aucun couplage avec le reste de l'UI : supprimer le module
// backend retire aussi ce frontend (servi par /api/anchor/ui.js).
(function () {
  const slot = document.getElementById('modules-bar');
  if (!slot) return;
  const map = window.map;  // carte Leaflet définie par index.html

  // --- Style autonome (repris de l'implémentation d'origine) ---
  const style = document.createElement('style');
  style.textContent = `
    /* Colonne : bouton pleine largeur + ligne info centrée en dessous */
    .anchor-container { display:flex; flex-direction:column; gap:5px; width:100%; }
    .btn-anchor {
      background:var(--c-btn,#1a1205); color:var(--c-text,#ffeedd);
      border:3px solid var(--c-border,#8e6f43);
      padding: clamp(8px,2.5vw,18px) clamp(4px,1vw,12px);
      font-size: clamp(0.72em,2vw,1.1em);
      border-radius:10px; cursor:pointer; font-weight:bold;
      width:100%; text-transform:uppercase; font-family:inherit;
      box-shadow:0 4px 6px rgba(0,0,0,0.3); }
    .btn-anchor.armed { background:#00334e; border-color:#0a9396; box-shadow:0 0 10px #0a939688; }
    .btn-anchor.alarm { background:#7d0000; border-color:#e63946; animation:anchorFlash 0.6s infinite; }
    @keyframes anchorFlash { 0%,100%{box-shadow:0 0 6px #e63946;} 50%{box-shadow:0 0 20px #e63946; background:#c0001a;} }
    /* Ligne info + boutons rayon, centrée */
    .anchor-sub { display:flex; gap:8px; justify-content:center; align-items:center; }
    .anchor-info { background:var(--c-panel,#1a1205); border:2px solid var(--c-sep,#3d2510);
      border-radius:8px; padding:4px 12px; display:flex; flex-direction:column;
      justify-content:center; align-items:center; }
    .anchor-info.armed { border-color:#0a9396; }
    .anchor-info.alarm { border-color:#e63946; }
    .anchor-info-label { color:var(--c-muted,#8e6f43); font-size:0.60em; text-transform:uppercase; }
    .anchor-info-value { color:var(--c-text,#fff); font-size:0.90em; font-weight:bold; }
    .anchor-radius-btns { display:flex; flex-direction:row; gap:4px; }
    .btn-radius { background:var(--c-panel,#1a1205); color:var(--c-muted,#8e6f43);
      border:1px solid var(--c-sep,#3d2510);
      padding:4px 8px; font-size:0.68em; border-radius:5px; cursor:pointer;
      font-weight:bold; font-family:inherit; }
    .btn-radius.selected { border-color:#0a9396; color:#0a9396; }`;
  document.head.appendChild(style);

  // --- UI ---
  const box = document.createElement('div');
  box.className = 'anchor-container';
  box.innerHTML = `
    <button id="btn-anchor" class="btn-anchor">⚓ MOUILLAGE : OFF</button>
    <div class="anchor-sub">
      <div id="anchor-info" class="anchor-info">
        <div class="anchor-info-label">Dérive</div>
        <div class="anchor-info-value" id="anchor-dist">— m</div>
      </div>
      <div class="anchor-radius-btns">
        <button class="btn-radius" data-r="25">25m</button>
        <button class="btn-radius selected" data-r="50">50m</button>
        <button class="btn-radius" data-r="100">100m</button>
      </div>
    </div>`;
  slot.appendChild(box);

  let anchorRadius = 50, anchorArmed = false, anchorMarker = null, anchorCircle = null;

  document.getElementById('btn-anchor').onclick = toggleAnchor;
  box.querySelectorAll('.btn-radius').forEach(b => {
    b.onclick = () => setAnchorRadius(parseInt(b.dataset.r, 10));
  });

  function setAnchorRadius(r) {
    anchorRadius = r;
    box.querySelectorAll('.btn-radius').forEach(b =>
      b.classList.toggle('selected', parseInt(b.dataset.r, 10) === r));
    if (anchorArmed) {
      fetch(`/api/anchor/set?radius=${r}`, { method: 'POST' }).then(x => x.json()).then(d => {
        if (anchorCircle && d.radius_m) anchorCircle.setRadius(d.radius_m);
      });
    }
  }

  async function toggleAnchor() {
    if (anchorArmed) {
      await fetch('/api/anchor/clear', { method: 'POST' });
      anchorArmed = false;
      if (map && anchorMarker) { map.removeLayer(anchorMarker); anchorMarker = null; }
      if (map && anchorCircle) { map.removeLayer(anchorCircle); anchorCircle = null; }
      stopAlarm();
      updateUI({ active: false, alarm: false, distance_m: 0 });
    } else {
      const d = await fetch(`/api/anchor/set?radius=${anchorRadius}`, { method: 'POST' }).then(x => x.json());
      if (d.status === 'error') { alert('Pas encore de position GPS'); return; }
      anchorArmed = true;
      const pos = [d.lat, d.lon];
      if (map) {
        anchorMarker = L.marker(pos, { icon: L.divIcon({
          html: '<div style="font-size:24px;line-height:1;filter:drop-shadow(0 0 4px #0a9396)">⚓</div>',
          iconAnchor: [12, 12], className: '' }) }).addTo(map);
        anchorCircle = L.circle(pos, { radius: d.radius_m, color: '#0a9396',
          fillColor: '#0a9396', fillOpacity: 0.08, weight: 2, dashArray: '6 4' }).addTo(map);
      }
      updateUI({ active: true, alarm: false, distance_m: 0 });
    }
  }

  function updateUI(anchor) {
    const btn = document.getElementById('btn-anchor');
    const info = document.getElementById('anchor-info');
    const dist = document.getElementById('anchor-dist');
    if (!anchor.active) {
      btn.textContent = '⚓ MOUILLAGE : OFF'; btn.className = 'btn-anchor';
      info.className = 'anchor-info'; dist.textContent = '— m'; stopAlarm(); return;
    }
    if (anchor.alarm) {
      btn.textContent = '⚓ DÉRIVE !'; btn.className = 'btn-anchor alarm';
      info.className = 'anchor-info alarm'; playAlarm();
      if (anchorCircle) anchorCircle.setStyle({ color: '#e63946', fillColor: '#e63946' });
    } else {
      btn.textContent = '⚓ MOUILLAGE : ON'; btn.className = 'btn-anchor armed';
      info.className = 'anchor-info armed'; stopAlarm();
      if (anchorCircle) anchorCircle.setStyle({ color: '#0a9396', fillColor: '#0a9396' });
    }
    dist.textContent = (anchor.distance_m || 0).toFixed(0) + ' m';
  }

  // Bip d'alarme via Web Audio (pas de fichier audio requis)
  let _alarmInterval = null;
  function playAlarm() {
    if (_alarmInterval) return;
    _alarmInterval = setInterval(() => {
      try {
        const ctx = new (window.AudioContext || window.webkitAudioContext)();
        const osc = ctx.createOscillator(), gain = ctx.createGain();
        osc.connect(gain); gain.connect(ctx.destination);
        osc.type = 'square'; osc.frequency.value = 880;
        gain.gain.setValueAtTime(0.3, ctx.currentTime);
        gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.4);
        osc.start(ctx.currentTime); osc.stop(ctx.currentTime + 0.4);
        setTimeout(() => ctx.close(), 600);
      } catch (e) {}
    }, 1500);
  }
  function stopAlarm() {
    if (_alarmInterval) { clearInterval(_alarmInterval); _alarmInterval = null; }
  }

  // Le module poll son propre état (découplé de /api/status)
  async function refresh() {
    try {
      const a = await fetch('/api/anchor').then(r => r.json());
      anchorArmed = a.active;
      updateUI(a);
    } catch (e) {}
  }
  setInterval(refresh, 1000);
  refresh();
})();
