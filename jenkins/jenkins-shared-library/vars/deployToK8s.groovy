def call(Map config = [:]) {
    String appName = config.appName
    String imageTag = config.imageTag
    String targetNamespace = config.get('targetNamespace', 'default')
    String clusterContext = config.get('targetClusterContext', 'docker-desktop')
    String manifestsPath = config.get('manifestsPath', 'k8s/base')
    String k8sCredentialsId = config.get('k8sCredentialsId', 'k8s-kubeconfig-credentials')
    boolean enableAutoRollback = config.get('enableAutoRollback', true)
    int rolloutTimeout = config.get('rolloutTimeoutSeconds', 120)

    echo "=========================================================="
    echo "☸️ STAGE: Deploy Artifact to Kubernetes Cluster"
    echo "Target Cluster Context: ${clusterContext}"
    echo "Target Namespace:       ${targetNamespace}"
    echo "Target Manifest Path:   ${manifestsPath}"
    echo "Deployed Image:         ${imageTag}"
    echo "=========================================================="

    // Wrapper supporting either Jenkins Secret File Kubeconfig or existing local context
    def deployScript = { kubeconfigFlag ->
        sh """
            echo "[INFO] Verifying target Kubernetes cluster connectivity..."
            kubectl ${kubeconfigFlag} --context=${clusterContext} cluster-info || true

            echo "[INFO] Ensuring namespace '${targetNamespace}' exists..."
            kubectl ${kubeconfigFlag} --context=${clusterContext} create namespace ${targetNamespace} --dry-run=client -o yaml | kubectl ${kubeconfigFlag} --context=${clusterContext} apply -f -

            echo "[INFO] Applying base Kubernetes manifests..."
            kubectl ${kubeconfigFlag} --context=${clusterContext} apply -n ${targetNamespace} -f ${manifestsPath}/

            echo "[INFO] Updating deployment image to: ${imageTag}..."
            kubectl ${kubeconfigFlag} --context=${clusterContext} set image deployment/${appName} ${appName}=${imageTag} -n ${targetNamespace} --record=true

            echo "[INFO] Awaiting rolling update rollout completion (timeout: ${rolloutTimeout}s)..."
            if kubectl ${kubeconfigFlag} --context=${clusterContext} rollout status deployment/${appName} -n ${targetNamespace} --timeout=${rolloutTimeout}s; then
                echo "=========================================================="
                echo "✅ [SUCCESS] Deployment healthy and ready to serve traffic!"
                echo "=========================================================="
                kubectl ${kubeconfigFlag} --context=${clusterContext} get pods,svc,hpa -n ${targetNamespace} -l app=${appName}
            else
                echo "❌ [ERROR] Rollout timed out or failed readiness probes!"
                if [ "${enableAutoRollback}" = "true" ]; then
                    echo "🔄 [ROLLBACK TRIGGERED] Executing automated rollback to previous revision..."
                    kubectl ${kubeconfigFlag} --context=${clusterContext} rollout undo deployment/${appName} -n ${targetNamespace}
                    kubectl ${kubeconfigFlag} --context=${clusterContext} rollout status deployment/${appName} -n ${targetNamespace} --timeout=60s
                    echo "⚠️ [ROLLBACK COMPLETE] Restored previous stable deployment."
                fi
                exit 1
            fi
        """
    }

    // Check if Jenkins Kubeconfig file credential is provided
    try {
        withCredentials([file(credentialsId: k8sCredentialsId, variable: 'KUBECONFIG_FILE')]) {
            deployScript("--kubeconfig=\$KUBECONFIG_FILE")
        }
    } catch (Exception e) {
        echo "[NOTICE] Kubeconfig credential '${k8sCredentialsId}' not bound; using host default kubeconfig context '${clusterContext}'..."
        deployScript("")
    }
}
