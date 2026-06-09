// Frontend du module Anchor Watch — autonome.
// Injecte son UI directement dans .lights-container pour former une grille 2×2 :
//   [POMPE]      [FEUX]
//   [MOUILLAGE]  [dérive + rayons]
// Utilise la carte Leaflet globale (window.map) et interroge /api/anchor.
(function () {
  const slot = document.querySelector('.lights-container');
  if (!slot) return;
  const map = window.map;

  // --- Style autonome ---
  const style = document.createElement('style');
  style.textContent = `
    /* Bouton mouillage : même apparence que .btn-toggle */
    .btn-anchor {
      background:var(--c-btn,#5c1d06); color:var(--c-text,#ffeedd);
      border:3px solid var(--c-border,#8e6f43);
      padding: clamp(8px,2.5vw,18px) clamp(4px,1vw,12px);
      font-size: clamp(0.72em,2vw,1.1em);
      border-radius:10px; cursor:pointer; font-weight:bold;
      text-transform:uppercase; font-family:inherit;
      box-shadow:0 4px 6px rgba(0,0,0,0.3);
      width:100%; height:100%; }
    .btn-anchor.armed { background:#00334e; border-color:#0a9396; box-shadow:0 0 10px #0a939688; }
    .btn-anchor.alarm { background:#7d0000; border-color:#e63946; animation:anchorFlash 0.6s infinite; }
    @keyframes anchorFlash { 0%,100%{box-shadow:0 0 6px #e63946;} 50%{box-shadow:0 0 20px #e63946; background:#c0001a;} }

    /* Cellule dérive + rayons : même taille que les autres boutons */
    .anchor-sub {
      background:var(--c-btn,#5c1d06); color:var(--c-text,#ffeedd);
      border:3px solid var(--c-border,#8e6f43);
      padding: clamp(6px,2vw,14px) clamp(4px,1vw,12px);
      border-radius:10px; font-family:inherit;
      box-shadow:0 4px 6px rgba(0,0,0,0.3);
      display:flex; flex-direction:row;
      justify-content:center; align-items:center; gap:8px; }
    .anchor-info { display:flex; flex-direction:column; align-items:center; }
    .anchor-info-label { color:var(--c-muted,#8e6f43); font-size:0.60em; text-transform:uppercase; }
    .anchor-info-value { color:var(--c-text,#ffeedd); font-size:clamp(0.8em,2.5vw,1.1em); font-weight:bold; }
    .anchor-sub.armed { border-color:#0a9396; }
    .anchor-sub.alarm { border-color:#e63946; }
    .anchor-radius-btns { display:flex; flex-direction:column; gap:4px; }
    .btn-radius { background:var(--c-panel,#160e05); color:var(--c-muted,#8e6f43);
      border:1px solid var(--c-sep,#3d2510);
      padding:3px 7px; font-size:0.68em; border-radius:5px; cursor:pointer;
      font-weight:bold; font-family:inherit; }
    .btn-radius.selected { border-color:#0a9396; color:#0a9396; }

    /* Ainavi K40 : même compacité que .btn-toggle */
    @media (min-aspect-ratio: 5/2) and (min-width: 400px) {
      .btn-anchor { padding:5px 4px; font-size:0.74em; border-width:2px; border-radius:7px; }
      .anchor-sub  { padding:5px 4px; font-size:0.70em; border-width:2px; border-radius:7px; }
    }`;
  document.head.appendChild(style);

  // --- Bouton mouillage (cellule 3 de la grille) ---
  const anchorBtn = document.createElement('button');
  anchorBtn.id = 'btn-anchor';
  anchorBtn.className = 'btn-anchor';
  anchorBtn.textContent = '⚓ MOUILLAGE : OFF';
  slot.appendChild(anchorBtn);

  // --- Cellule dérive + rayons (cellule 4 de la grille) ---
  const anchorSub = document.createElement('div');
  anchorSub.className = 'anchor-sub';
  anchorSub.innerHTML = `
    <div class="anchor-info">
      <div class="anchor-info-label">Dérive</div>
      <div class="anchor-info-value" id="anchor-dist">— m</div>
    </div>
    <div class="anchor-radius-btns">
      <button class="btn-radius" data-r="25">25m</button>
      <button class="btn-radius selected" data-r="50">50m</button>
      <button class="btn-radius" data-r="100">100m</button>
    </div>`;
  slot.appendChild(anchorSub);

  let anchorRadius = 50, anchorArmed = false, anchorMarker = null, anchorCircle = null;

  anchorBtn.onclick = toggleAnchor;
  anchorSub.querySelectorAll('.btn-radius').forEach(b => {
    b.onclick = () => setAnchorRadius(parseInt(b.dataset.r, 10));
  });

  function setAnchorRadius(r) {
    anchorRadius = r;
    anchorSub.querySelectorAll('.btn-radius').forEach(b =>
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
    const dist = document.getElementById('anchor-dist');
    if (!anchor.active) {
      anchorBtn.textContent = '⚓ MOUILLAGE : OFF';
      anchorBtn.className = 'btn-anchor';
      anchorSub.className = 'anchor-sub';
      dist.textContent = '— m';
      stopAlarm();
      return;
    }
    if (anchor.alarm) {
      anchorBtn.textContent = '⚓ DÉRIVE !';
      anchorBtn.className = 'btn-anchor alarm';
      anchorSub.className = 'anchor-sub alarm';
      playAlarm();
      if (anchorCircle) anchorCircle.setStyle({ color: '#e63946', fillColor: '#e63946' });
    } else {
      anchorBtn.textContent = '⚓ MOUILLAGE : ON';
      anchorBtn.className = 'btn-anchor armed';
      anchorSub.className = 'anchor-sub armed';
      stopAlarm();
      if (anchorCircle) anchorCircle.setStyle({ color: '#0a9396', fillColor: '#0a9396' });
    }
    dist.textContent = (anchor.distance_m || 0).toFixed(0) + ' m';
  }

  // Bip d'alarme via Web Audio
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
