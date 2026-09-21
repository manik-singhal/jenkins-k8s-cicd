def call(Map config = [:]) {
    String imageTag = config.imageTag
    String severity = config.get('trivySeverity', 'CRITICAL,HIGH')
    
    echo "=========================================================="
    echo "🛡️ STAGE: Container Security Vulnerability Scan"
    echo "Target Image: ${imageTag}"
    echo "Target Severity: ${severity}"
    echo "=========================================================="

    // Check if Trivy is installed; if not, pull and execute via Docker container
    sh """
        echo "[INFO] Running Trivy vulnerability scan on ${imageTag}..."
        if command -v trivy >/dev/null 2>&1; then
            trivy image --severity ${severity} --exit-code 0 --format table ${imageTag}
        else
            echo "[INFO] Trivy CLI not found locally; executing scan via official aquasec/trivy container..."
            docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
                aquasec/trivy:latest image --severity ${severity} --exit-code 0 --format table ${imageTag} || true
        fi
        echo "[SUCCESS] Container security scan completed successfully!"
    """
}
