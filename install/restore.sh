#!/bin/bash
# Restore complet de Morioo MMS sur un Raspberry Pi vierge
# Usage : bash restore.sh
set -e

REPO_DIR="/home/ode/boesch_os"
USER="ode"

echo "=== [1/6] Paquets système ==="
sudo apt-get update -qq
sudo apt-get install -y \
    python3 python3-venv python3-pip \
    xdotool \
    fonts-noto-color-emoji \
    git

echo "=== [2/6] Ajout de l'utilisateur au groupe dialout (accès USB série) ==="
sudo usermod -aG dialout $USER

echo "=== [2.5/6] Récupération du repo ==="
if [ -d "$REPO_DIR/.git" ]; then
    echo "Repo existant — git pull"
    git -C $REPO_DIR pull
else
    echo "Nouveau clone"
    git clone https://github.com/oli1313/morioo_mms $REPO_DIR
fi

echo "=== [3/6] Environnement Python ==="
cd $REPO_DIR
python3 -m venv venv
venv/bin/pip install --upgrade pip -q
venv/bin/pip install -r requirements.txt -q

echo "=== [4/6] Service systemd ==="
sudo cp $REPO_DIR/install/boesch_backend.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable boesch_backend.service
sudo systemctl start boesch_backend.service

echo "=== [5/6] Règle udev Wemos D1 Mini (CH340) ==="
sudo cp $REPO_DIR/install/99-wemos.rules /etc/udev/rules.d/
sudo udevadm control --reload-rules
sudo udevadm trigger

echo "=== [6/7] mDNS avahi-daemon (résolution rasp-boesch.local sur le réseau bateau) ==="
sudo apt-get install -y avahi-daemon -qq
sudo systemctl enable avahi-daemon
sudo systemctl start avahi-daemon

echo "=== [7/7] Suppression du cron refresh Chromium (remplacé par bouton manuel) ==="
# On retire l'entrée si elle traîne d'une ancienne install — le reload
# est maintenant déclenché manuellement en cliquant sur "BOESCH 510".
crontab -u $USER -l 2>/dev/null | grep -v refresh_chromium | crontab -u $USER - || true

echo ""
echo "=== Restore terminé ==="
echo "Reconnecte-toi (ou reboot) pour que le groupe dialout soit actif."
echo "Status du service :"
systemctl status boesch_backend.service --no-pager
