// FinacPlus CI/CD Pipeline
// Deploys the Toorak Lending API to Kubernetes with automated testing,
// security scanning, and rollback on failure.

pipeline {
    agent any

    parameters {
        choice(name: 'ENVIRONMENT', choices: ['dev', 'staging', 'prod'], description: 'Deployment Target Environment')
        choice(name: 'CLUSTER_CONTEXT', choices: ['docker-desktop', 'gke-cluster'], description: 'Target Kubernetes Cluster')
        booleanParam(name: 'RUN_SECURITY_SCAN', defaultValue: true, description: 'Execute Trivy Image Vulnerability Scan')
        booleanParam(name: 'ENABLE_AUTO_ROLLBACK', defaultValue: true, description: 'Rollback K8s deployment automatically on failure')
    }

    triggers {
        githubPush()
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '15'))
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
        disableConcurrentBuilds()
    }

    environment {
        APP_NAME = "toorak-lending-api"
        DOCKER_REGISTRY = "docker.io/maniksinghal29/toorak-lending-api"
        DOCKER_CREDS_ID = "docker-hub-credentials"
        K8S_CREDS_ID = "k8s-kubeconfig-credentials"
        MANIFESTS_DIR = "k8s"
    }

    stages {
        stage('Checkout & SCM Verification') {
            steps {
                echo "--- Stage 1: Git Checkout ---"
                sh '''
                    echo "Running on Node: ${NODE_NAME}"
                    echo "Git Branch: ${GIT_BRANCH:-main}"
                    echo "Commit SHA: $(git rev-parse HEAD 2>/dev/null || echo 'local')"
                '''
            }
        }

        stage('Unit Testing') {
            steps {
                echo "--- Stage 2: Running PyTest ---"
                sh '''
                    python3 -m venv .venv
                    . .venv/bin/activate
                    pip install --quiet --upgrade pip
                    pip install --quiet -r app/requirements.txt
                    
                    python -m pytest app/tests/ -v
                '''
            }
        }

        stage('Container Build & Security Scan') {
            steps {
                script {
                    echo "--- Stage 3: Docker Build + Trivy Scan ---"
                    String commitSha = sh(script: 'git rev-parse --short HEAD 2>/dev/null || echo "latest"', returnStdout: true).trim()
                    String buildNum = env.BUILD_NUMBER ?: "1"
                    env.IMAGE_TAG = "${env.DOCKER_REGISTRY}:${commitSha}-${buildNum}"
                    env.IMAGE_LATEST = "${env.DOCKER_REGISTRY}:latest"

                    sh """
                        echo "Building image: ${env.IMAGE_TAG}"
                        docker build -t ${env.IMAGE_TAG} -t ${env.IMAGE_LATEST} -f Dockerfile .
                    """

                    if (params.RUN_SECURITY_SCAN) {
                        echo "Running Trivy vulnerability scan..."
                        sh """
                            if command -v trivy >/dev/null 2>&1; then
                                trivy image --severity CRITICAL,HIGH --exit-code 0 ${env.IMAGE_TAG}
                            else
                                docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
                                    aquasec/trivy:latest image --severity CRITICAL,HIGH --exit-code 0 ${env.IMAGE_TAG} || true
                            fi
                        """
                    }

                    // Push to Docker Hub
                    try {
                        withCredentials([usernamePassword(
                            credentialsId: env.DOCKER_CREDS_ID,
                            usernameVariable: 'DOCKER_USER',
                            passwordVariable: 'DOCKER_PASS'
                        )]) {
                            sh """
                                echo "\$DOCKER_PASS" | docker login -u "\$DOCKER_USER" --password-stdin
                                docker push ${env.IMAGE_TAG}
                                docker push ${env.IMAGE_LATEST}
                                echo "Image pushed successfully."
                            """
                        }
                    } catch (Exception e) {
                        echo "[WARNING] Docker credentials not found or push skipped. Image built locally as ${env.IMAGE_TAG}."
                    }
                }
            }
        }

        stage('Kubernetes Deployment') {
            steps {
                script {
                    echo "--- Stage 4: Deploy to K8s + Rollback Check ---"
                    String targetNamespace = "finacplus-${params.ENVIRONMENT}"
                    String clusterContext = params.CLUSTER_CONTEXT
                    int timeoutSeconds = 120

                    sh """
                        echo "Deploying to context=${clusterContext}, namespace=${targetNamespace}"
                        kubectl --context=${clusterContext} create namespace ${targetNamespace} --dry-run=client -o yaml | kubectl --context=${clusterContext} apply -f -

                        kubectl --context=${clusterContext} apply -n ${targetNamespace} -f ${env.MANIFESTS_DIR}/

                        echo "Updating deployment image to ${env.IMAGE_TAG}..."
                        kubectl --context=${clusterContext} set image deployment/${env.APP_NAME} ${env.APP_NAME}=${env.IMAGE_TAG} -n ${targetNamespace} --record=true

                        echo "Waiting for rollout to complete (timeout: ${timeoutSeconds}s)..."
                        if kubectl --context=${clusterContext} rollout status deployment/${env.APP_NAME} -n ${targetNamespace} --timeout=${timeoutSeconds}s; then
                            echo "✅ Deployment successful."
                            kubectl --context=${clusterContext} get pods,svc -n ${targetNamespace} -l app=${env.APP_NAME}
                        else
                            echo "❌ Deployment failed!"
                            if [ "${params.ENABLE_AUTO_ROLLBACK}" = "true" ]; then
                                echo "Rolling back to previous revision..."
                                kubectl --context=${clusterContext} rollout undo deployment/${env.APP_NAME} -n ${targetNamespace}
                                kubectl --context=${clusterContext} rollout status deployment/${env.APP_NAME} -n ${targetNamespace} --timeout=60s
                                echo "Rollback complete."
                            fi
                            exit 1
                        fi
                    """
                }
            }
        }
    }

    post {
        always {
            cleanWs deleteDirs: true, notFailBuild: true
        }
        success {
            echo "🎉 Pipeline SUCCESS: Build #${BUILD_NUMBER} completed."
        }
        failure {
            echo "🚨 Pipeline FAILED: Build #${BUILD_NUMBER}. Check console output for details."
        }
    }
}
