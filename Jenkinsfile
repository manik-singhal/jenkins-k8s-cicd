// ==============================================================================
// FinacPlus / Toorak Capital Partners - Enterprise CI/CD Pipeline
// ==============================================================================
// Uses Jenkins Shared Library for scalable, multi-repo, multi-cluster automation.
// Configure 'finacplus-shared-library' in Jenkins -> Manage Jenkins -> System -> Global Pipeline Libraries
// ==============================================================================

@Library('finacplus-shared-library') _

standardPipeline(
    appName: 'toorak-lending-api',
    appType: 'fastapi',
    dockerRegistry: 'docker.io/maniksinghal29/toorak-lending-api',
    dockerCredentialsId: 'docker-hub-credentials',
    targetNamespace: 'finacplus-lending',
    targetClusterContext: 'docker-desktop', // Easily toggle to 'gke_vaani-assignment-500510_asia-south1-a_gpu-gke-cluster'
    k8sCredentialsId: 'k8s-kubeconfig-credentials',
    manifestsPath: 'k8s/base',
    enableSecurityScan: true,
    enableAutoRollback: true
)
