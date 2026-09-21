#!/usr/bin/env bash
# ==============================================================================
# Script: Simulate Deployment Failure & Validate Automated Rollback
# Demonstrates resilience and zero-downtime protection
# ==============================================================================
set -euo pipefail

export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"

APP_NAME="toorak-lending-api"
TARGET_NAMESPACE="finacplus-dev"
K8S_CONTEXT="docker-desktop"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}==========================================================${NC}"
echo -e "${BLUE}🧪 Test Scenario: Broken Deployment & Automated Rollback${NC}"
echo -e "${BLUE}==========================================================${NC}"

# Check current stable revision
echo -e "\n${YELLOW}[Step 1] Checking current stable rollout history...${NC}"
kubectl --context="${K8S_CONTEXT}" rollout history deployment/${APP_NAME} -n "${TARGET_NAMESPACE}"

# Deploy an intentionally invalid image (non-existent tag / crash loop)
INVALID_IMAGE="docker.io/maniksinghal29/toorak-lending-api:broken-test-tag-crash"
echo -e "\n${YELLOW}[Step 2] Intentionally setting invalid image: ${INVALID_IMAGE}...${NC}"
kubectl --context="${K8S_CONTEXT}" set image deployment/${APP_NAME} ${APP_NAME}="${INVALID_IMAGE}" -n "${TARGET_NAMESPACE}" --record=true

echo -e "\n${YELLOW}[Step 3] Monitoring rollout status (Expecting failure/timeout)...${NC}"
if kubectl --context="${K8S_CONTEXT}" rollout status deployment/${APP_NAME} -n "${TARGET_NAMESPACE}" --timeout=20s; then
    echo -e "${RED}Unexpected: Rollout succeeded when it should have failed.${NC}"
    exit 1
else
    echo -e "${RED}⚠️ Rollout failed as expected! Image cannot be pulled or failed healthcheck.${NC}"
    echo -e "\n${YELLOW}[Step 4] Executing automated rollback (kubectl rollout undo)...${NC}"
    kubectl --context="${K8S_CONTEXT}" rollout undo deployment/${APP_NAME} -n "${TARGET_NAMESPACE}"
    kubectl --context="${K8S_CONTEXT}" rollout status deployment/${APP_NAME} -n "${TARGET_NAMESPACE}" --timeout=45s
    echo -e "\n${GREEN}==========================================================${NC}"
    echo -e "${GREEN}✅ SUCCESS: Automated Rollback verified! Restored stable deployment.${NC}"
    echo -e "${GREEN}==========================================================${NC}"
    kubectl --context="${K8S_CONTEXT}" get pods -n "${TARGET_NAMESPACE}" -l app="${APP_NAME}"
fi
