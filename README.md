# CI/CD Pipeline: Git → Jenkins → Kubernetes

A CI/CD pipeline that automates the full delivery cycle — from Git push to a running deployment on Kubernetes — using Jenkins and Groovy scripting.

Built as a DevOps assignment for **FinacPlus / Toorak Capital Partners**.

---

## What This Does

1. Developer pushes code to Git → Jenkins picks it up via webhook
2. Jenkins runs unit tests, builds a Docker image, scans it for vulnerabilities (Trivy)
3. Jenkins deploys the image to a Kubernetes cluster with rolling updates
4. If the new deployment fails health checks, Jenkins **automatically rolls back** to the previous working version

---

## Architecture

```
Developer → Git Push → GitHub Webhook → Jenkins Pipeline
                                            │
                                    ┌───────┴───────┐
                                    │  Stage 1: Test │ (PyTest in container)
                                    │  Stage 2: Build│ (Docker multi-stage)
                                    │  Stage 3: Scan │ (Trivy CVE scan)
                                    │  Stage 4: Push │ (Docker Hub)
                                    │  Stage 5: Deploy│(kubectl → K8s cluster)
                                    │     └── Rollback if health check fails
                                    └───────────────┘
```

---

## Repository Structure

```
.
├── Jenkinsfile                       # Main pipeline — all stages in one file
├── Dockerfile                        # Multi-stage build, runs as non-root user
├── docker-compose.jenkins.yml        # Spin up local Jenkins with Docker + K8s access
├── .gitignore
├── .dockerignore
│
├── app/                              # Sample FastAPI microservice
│   ├── main.py                       # Endpoints: /healthz, /readyz, /metrics, /api/v1/loans
│   ├── requirements.txt
│   └── tests/
│       └── test_main.py              # 5 unit tests covering health, metrics, and API
│
├── jenkins/
│   ├── Dockerfile.jenkins            # Custom Jenkins image with Docker CLI, kubectl, Trivy
│   └── jenkins-shared-library/       # Groovy Shared Library (for scaling to multiple repos)
│       └── vars/
│           ├── standardPipeline.groovy
│           ├── runUnitTests.groovy
│           ├── buildAndPushImage.groovy
│           ├── deployToK8s.groovy
│           ├── securityScan.groovy
│           └── notifyBuildStatus.groovy
│
├── k8s/                              # Kubernetes manifests
│   ├── deployment.yaml               # RollingUpdate, probes, resource limits, non-root
│   ├── service.yaml                  # ClusterIP service
│   ├── configmap.yaml                # App environment config
│   └── jenkins-rbac.yaml             # Least-privilege ServiceAccount for Jenkins
│
├── monitoring/
│   └── prometheus-service-monitor.yaml
│
└── scripts/
    ├── verify-local.sh               # Run all pipeline stages locally in one command
    └── simulate-failure-rollback.sh  # Prove the auto-rollback works
```

---

## Pipeline Stages (Jenkinsfile)

The `Jenkinsfile` uses Jenkins Declarative Pipeline with Groovy scripting. Here's what each stage does:

### Stage 1: Checkout & Verification
Prints the current branch, commit SHA, and workspace path for traceability.

### Stage 2: Unit Testing
Runs `pytest` inside a Python 3.11 virtual environment against `app/tests/`. Tests cover:
- Health endpoints (`/healthz`, `/readyz`)
- Prometheus metrics endpoint (`/metrics`)
- Loan application API flow (POST + GET)

### Stage 3: Docker Build + Security Scan
- Builds a multi-stage Docker image tagged with `<commit-sha>-<build-number>` for traceability (not just `latest`)
- Runs **Trivy** vulnerability scanner against the built image to catch CRITICAL/HIGH CVEs before pushing
- Pushes the image to Docker Hub

### Stage 4: Kubernetes Deployment + Auto-Rollback
- Creates the target namespace if it doesn't exist
- Applies K8s manifests (`deployment.yaml`, `service.yaml`, `configmap.yaml`)
- Updates the deployment image to the newly built tag
- Watches `kubectl rollout status --timeout=120s`
- **If the rollout fails** (pods crash, fail readiness probes, or timeout):
  - Runs `kubectl rollout undo` to restore the previous stable version
  - Marks the build as failed

This is the core reliability feature — bad code never stays running in the cluster.

---

## How Scalability is Handled

The assignment asks for a solution that works across **different Git repositories and Kubernetes clusters**.

### Multiple Clusters
The pipeline uses parameterized `CLUSTER_CONTEXT` and `ENVIRONMENT`. To deploy to a different cluster, you just change the context:
```groovy
// Local development
CLUSTER_CONTEXT = 'docker-desktop'

// Production GKE
CLUSTER_CONTEXT = 'gke_project_region_cluster-name'
```

No manifest changes needed — `kubectl --context=<name>` handles the routing.

### Multiple Repositories
The `jenkins/jenkins-shared-library/` folder contains the same pipeline logic broken into reusable Groovy steps. If FinacPlus has 20 microservices, each repo would only need a short Jenkinsfile:

```groovy
@Library('finacplus-shared-library') _

standardPipeline(
    appName: 'new-service',
    dockerRegistry: 'docker.io/maniksinghal29/new-service',
    targetNamespace: 'finacplus-prod',
    targetClusterContext: 'gke-prod-cluster'
)
```

One central library update improves all pipelines at once.

---

## Security Practices

| Layer | What's Done |
|-------|-------------|
| **Container** | Multi-stage Dockerfile; runs as non-root user (UID 10001), `capabilities: drop: ALL` |
| **Image Scanning** | Trivy scans for CRITICAL/HIGH CVEs before pushing to registry |
| **Kubernetes** | `runAsNonRoot: true` in pod security context; readiness + liveness probes |
| **RBAC** | `jenkins-rbac.yaml` creates a dedicated ServiceAccount with only the permissions Jenkins needs (deployments, services, pods, configmaps) |
| **Credentials** | Docker Hub credentials stored in Jenkins Credential Store, never hardcoded |

---

## Setup Instructions

### Prerequisites
- Docker Desktop with Kubernetes enabled
- Git

### 1. Start Jenkins
```bash
docker-compose -f docker-compose.jenkins.yml up -d
```
Access at `http://localhost:8080`.

### 2. Add Credentials in Jenkins
Go to **Manage Jenkins → Credentials → Global**:

- **Docker Hub**: Kind = *Username with password*, ID = `docker-hub-credentials`
- **Kubeconfig** (optional): Kind = *Secret file*, ID = `k8s-kubeconfig-credentials`, File = `~/.kube/config`

### 3. Set Up Git Webhook
In your GitHub repo → **Settings → Webhooks → Add Webhook**:
- **URL**: `http://<your-jenkins-ip>:8080/github-webhook/`
- **Content type**: `application/json`
- **Events**: Just the push event

### 4. Create Pipeline Job
In Jenkins → **New Item → Pipeline**:
- SCM: Git, Repository URL: your repo URL
- Script Path: `Jenkinsfile`

---

## Quick Verification (Without Jenkins)

### Run all pipeline stages locally:
```bash
./scripts/verify-local.sh
```
This tests Docker, pytest, image build, Trivy scan, and K8s deployment in sequence.

### Test the auto-rollback:
```bash
./scripts/simulate-failure-rollback.sh
```
Deploys a broken image tag → K8s detects failure → script runs `rollout undo` → cluster restores to healthy state.

---

## Monitoring Recommendations

The application exposes Prometheus metrics at `/metrics` using `prometheus-fastapi-instrumentator`. A `ServiceMonitor` definition is included in `monitoring/prometheus-service-monitor.yaml`.

For a production CI/CD pipeline, I'd recommend:
- **Pipeline metrics**: Track build duration, success/failure rate, and deployment frequency using the Jenkins Prometheus plugin
- **Application metrics**: Scrape the `/metrics` endpoint with Prometheus for request latency, error rates, and throughput
- **Dashboarding**: Use Grafana to visualize golden signals (latency, traffic, errors, saturation)
- **Alerting**: Set up alerts for build failures, deployment rollbacks, and pod restarts

---

## Author
- **Candidate:** Manik Singhal
- **Role:** DevOps Engineer – Intern
