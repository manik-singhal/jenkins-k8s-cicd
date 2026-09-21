#!/usr/bin/env bash
# ==============================================================================
# Local End-to-End CI/CD Pipeline Verification Script
# Simulates all Jenkins pipeline stages locally against Docker & Kubernetes
# ==============================================================================
set -euo pipefail

export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"

APP_NAME="toorak-lending-api"
IMAGE_NAME="docker.io/maniksinghal29/${APP_NAME}:local-test"
TARGET_NAMESPACE="finacplus-dev"
K8S_CONTEXT="docker-desktop"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}==========================================================${NC}"
echo -e "${BLUE}🚀 Starting End-to-End CI/CD Pipeline Verification${NC}"
echo -e "${BLUE}==========================================================${NC}"

# 1. Check Prerequisites
echo -e "\n${YELLOW}[Stage 1/5] Checking Docker & Kubernetes Prerequisites...${NC}"
docker info >/dev/null || { echo -e "${RED}Docker is not running! Please start Docker Desktop.${NC}"; exit 1; }
kubectl --context="${K8S_CONTEXT}" get nodes || { echo -e "${RED}Cannot connect to Kubernetes context ${K8S_CONTEXT}!${NC}"; exit 1; }
echo -e "${GREEN}✓ Docker daemon and Kubernetes cluster are Ready.${NC}"

# 2. Run Unit Tests (Containerized for environment parity)
echo -e "\n${YELLOW}[Stage 2/5] Running PyTest Unit & Integration Tests in Python 3.11 container...${NC}"
docker run --rm -v "$PWD":/app -w /app python:3.11-slim /bin/bash -c "pip install --quiet -r app/requirements.txt && python -m pytest app/tests/ -v"
echo -e "${GREEN}✓ All unit tests passed successfully.${NC}"

# 3. Build Container Image
echo -e "\n${YELLOW}[Stage 3/5] Building Multi-Stage Secure Container Image...${NC}"
docker build -t "${IMAGE_NAME}" -f Dockerfile .
echo -e "${GREEN}✓ Container built successfully: ${IMAGE_NAME}${NC}"

# 4. Container Security Vulnerability Scan (Trivy)
echo -e "\n${YELLOW}[Stage 4/5] Running Container Security Scan (Trivy)...${NC}"
if command -v trivy >/dev/null 2>&1; then
    trivy image --severity CRITICAL,HIGH --exit-code 0 "${IMAGE_NAME}"
else
    echo "Running Trivy via official Docker container..."
    docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
        aquasec/trivy:latest image --severity CRITICAL,HIGH --exit-code 0 "${IMAGE_NAME}" || true
fi
echo -e "${GREEN}✓ Security vulnerability assessment complete.${NC}"

# 5. Kubernetes Deployment & Rollout Verification
echo -e "\n${YELLOW}[Stage 5/5] Deploying to Kubernetes cluster (${K8S_CONTEXT} / ${TARGET_NAMESPACE})...${NC}"
kubectl --context="${K8S_CONTEXT}" create namespace "${TARGET_NAMESPACE}" --dry-run=client -o yaml | kubectl --context="${K8S_CONTEXT}" apply -f -
kubectl --context="${K8S_CONTEXT}" apply -n "${TARGET_NAMESPACE}" -f k8s/base/

# Update image in deployment
kubectl --context="${K8S_CONTEXT}" set image deployment/${APP_NAME} ${APP_NAME}="${IMAGE_NAME}" -n "${TARGET_NAMESPACE}"

echo "Waiting for deployment rollout to complete..."
kubectl --context="${K8S_CONTEXT}" rollout status deployment/${APP_NAME} -n "${TARGET_NAMESPACE}" --timeout=120s

echo -e "\n${GREEN}==========================================================${NC}"
echo -e "${GREEN}🎉 SUCCESS: Local Pipeline Simulation Passed Completely!${NC}"
echo -e "${GREEN}==========================================================${NC}"
kubectl --context="${K8S_CONTEXT}" get pods,svc,hpa -n "${TARGET_NAMESPACE}" -l app="${APP_NAME}"
