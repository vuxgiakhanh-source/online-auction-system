#!/bin/bash
# setup-k3s.sh
# Cài k3s (Kubernetes nhẹ nhất) trên VPS Ubuntu 22.04+
# Tạo 3 namespace + GHCR image pull secret + xuất kubeconfig cho GitHub Actions
#
# Usage: sudo bash setup-k3s.sh <github_username> <ghcr_token>
#
# <ghcr_token>: tạo tại GitHub → Settings → Developer settings
#               → Personal access tokens → Fine-grained → read:packages

set -euo pipefail

GITHUB_USER="${1:?Usage: $0 <github_username> <ghcr_token>}"
GHCR_TOKEN="${2:?Usage: $0 <github_username> <ghcr_token>}"
NAMESPACES=("auction-dev" "auction-staging" "auction-prod")

echo "=== k3s Setup for Online Auction System ==="
echo ""

# ── 1. Cài k3s ────────────────────────────────────────────────
echo "[1/5] Installing k3s..."
curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC="server \
  --disable traefik \
  --disable servicelb" sh -

echo "Waiting for k3s to be ready..."
sleep 10
until kubectl get nodes | grep -q " Ready"; do sleep 3; done
echo "  ✅ k3s is running"
kubectl get nodes

# ── 2. Tạo namespaces ─────────────────────────────────────────
echo ""
echo "[2/5] Creating namespaces..."
for NS in "${NAMESPACES[@]}"; do
  kubectl create namespace "$NS" --dry-run=client -o yaml | kubectl apply -f -
  echo "  ✅ namespace/$NS"
done

# ── 3. Tạo GHCR image pull secret trong mỗi namespace ─────────
echo ""
echo "[3/5] Creating GHCR image pull secrets..."
for NS in "${NAMESPACES[@]}"; do
  kubectl create secret docker-registry ghcr-pull-secret \
    --namespace="$NS" \
    --docker-server=ghcr.io \
    --docker-username="$GITHUB_USER" \
    --docker-password="$GHCR_TOKEN" \
    --dry-run=client -o yaml | kubectl apply -f -

  # Gắn secret vào default service account để pod tự pull được image
  kubectl patch serviceaccount default \
    --namespace="$NS" \
    -p '{"imagePullSecrets": [{"name": "ghcr-pull-secret"}]}'

  echo "  ✅ ghcr-pull-secret in $NS"
done

# ── 4. Xuất kubeconfig ────────────────────────────────────────
echo ""
echo "[4/5] Exporting kubeconfig for GitHub Actions..."

# Lấy IP public của server
PUBLIC_IP=$(curl -sf https://api.ipify.org 2>/dev/null || hostname -I | awk '{print $1}')

# Tạo kubeconfig với IP thực thay vì 127.0.0.1
KUBECONFIG_FILE="/tmp/auction-kubeconfig.yaml"
k3s kubectl config view --raw > "$KUBECONFIG_FILE"
# Thay internal IP bằng public IP
sed -i "s|https://127.0.0.1:6443|https://${PUBLIC_IP}:6443|g" "$KUBECONFIG_FILE"

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📋 KUBECONFIG (base64) — Paste vào GitHub Secret 'KUBECONFIG':"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
base64 -w0 "$KUBECONFIG_FILE"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# ── 5. Mở firewall port 6443 (Kubernetes API) ────────────────
echo ""
echo "[5/5] Opening firewall port 6443..."
if command -v ufw &>/dev/null; then
  ufw allow 6443/tcp comment "k3s API server"
  echo "  ✅ ufw: port 6443 opened"
elif command -v firewall-cmd &>/dev/null; then
  firewall-cmd --permanent --add-port=6443/tcp
  firewall-cmd --reload
  echo "  ✅ firewalld: port 6443 opened"
else
  echo "  ⚠️  No firewall detected — open port 6443 manually"
fi

echo ""
echo "=== k3s Setup Complete ==="
echo ""
echo "Next steps:"
echo "  1. Copy KUBECONFIG (base64) above → GitHub Secret 'KUBECONFIG'"
echo "  2. Add DB credentials as GitHub Secrets (see README-CICD.md)"
echo "  3. Cài MySQL trong cluster: helm install mysql bitnami/mysql ..."
echo "     hoặc dùng RDS/Cloud SQL bên ngoài"
echo ""
rm -f "$KUBECONFIG_FILE"
