def call(Map config = [:]) {
    String dockerRegistry = config.dockerRegistry
    String credentialsId = config.get('dockerCredentialsId', 'docker-hub-credentials')
    String commitSha = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
    String buildNumber = env.BUILD_NUMBER ?: "1"
    
    String fullImageTag = "${dockerRegistry}:${commitSha}-${buildNumber}"
    String latestImageTag = "${dockerRegistry}:latest"

    echo "=========================================================="
    echo "🐳 STAGE: Build & Push Docker Artifact"
    echo "Primary Image Tag: ${fullImageTag}"
    echo "Latest Image Tag:  ${latestImageTag}"
    echo "=========================================================="

    // 1. Build Docker Image
    sh """
        echo "[INFO] Building multi-stage container image..."
        docker build -t ${fullImageTag} -t ${latestImageTag} -f Dockerfile .
    """

    // 2. Optional Security Scan before pushing
    if (config.get('enableSecurityScan', true)) {
        securityScan(imageTag: fullImageTag, trivySeverity: config.get('trivySeverity', 'CRITICAL,HIGH'))
    }

    // 3. Authenticate and Push to Registry using Jenkins Credentials Binding
    withCredentials([usernamePassword(
        credentialsId: credentialsId,
        usernameVariable: 'DOCKER_USER',
        passwordVariable: 'DOCKER_PASS'
    )]) {
        sh """
            echo "[INFO] Authenticating to container registry..."
            echo "\$DOCKER_PASS" | docker login -u "\$DOCKER_USER" --password-stdin

            echo "[INFO] Pushing image artifact to registry..."
            docker push ${fullImageTag}
            docker push ${latestImageTag}
            echo "[SUCCESS] Docker images pushed successfully to ${dockerRegistry}!"
        """
    }

    return fullImageTag
}
