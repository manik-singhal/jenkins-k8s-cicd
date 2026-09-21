def call(Map config = [:]) {
    String deployedImageTag = ""

    pipeline {
        agent any

        parameters {
            choice(name: 'ENVIRONMENT', choices: ['dev', 'staging', 'prod'], description: 'Deployment Target Environment')
            choice(name: 'CLUSTER_CONTEXT', choices: ['docker-desktop', 'gke-cluster', 'eks-cluster'], description: 'Target Kubernetes Cluster')
            booleanParam(name: 'RUN_SECURITY_SCAN', defaultValue: true, description: 'Execute Trivy Image Vulnerability Scan')
            booleanParam(name: 'ENABLE_AUTO_ROLLBACK', defaultValue: true, description: 'Rollback K8s deployment automatically on failure')
        }

        options {
            buildDiscarder(logRotator(numToKeepStr: '15'))
            timeout(time: 30, unit: 'MINUTES')
            timestamps()
            ansiColor('xterm')
            disableConcurrentBuilds()
        }

        environment {
            APP_NAME = "${config.get('appName', 'toorak-lending-api')}"
            DOCKER_REGISTRY = "${config.get('dockerRegistry', 'docker.io/maniksinghal29/toorak-lending-api')}"
            DOCKER_CREDS_ID = "${config.get('dockerCredentialsId', 'docker-hub-credentials')}"
            K8S_CREDS_ID = "${config.get('k8sCredentialsId', 'k8s-kubeconfig-credentials')}"
            APP_TYPE = "${config.get('appType', 'fastapi')}"
        }

        stages {
            stage('Checkout & SCM Verification') {
                steps {
                    echo "=========================================================="
                    echo "📥 STAGE: Git Checkout & Metadata Extraction"
                    echo "=========================================================="
                    sh '''
                        echo "[INFO] Current Git Branch: ${GIT_BRANCH:-local}"
                        echo "[INFO] Current Git Commit: $(git rev-parse HEAD 2>/dev/null || echo 'N/A')"
                        echo "[INFO] Workspace: ${WORKSPACE}"
                    '''
                }
            }

            stage('Unit Testing & Linting') {
                steps {
                    runUnitTests(appType: env.APP_TYPE)
                }
            }

            stage('Container Build & Security Scan') {
                steps {
                    script {
                        boolean scanEnabled = params.RUN_SECURITY_SCAN != null ? params.RUN_SECURITY_SCAN : config.get('enableSecurityScan', true)
                        deployedImageTag = buildAndPushImage(
                            dockerRegistry: env.DOCKER_REGISTRY,
                            dockerCredentialsId: env.DOCKER_CREDS_ID,
                            enableSecurityScan: scanEnabled,
                            trivySeverity: config.get('trivySeverity', 'CRITICAL,HIGH')
                        )
                    }
                }
            }

            stage('Kubernetes Deployment') {
                steps {
                    script {
                        String envTarget = params.ENVIRONMENT ?: config.get('environment', 'dev')
                        String namespace = config.get('targetNamespace', "finacplus-${envTarget}")
                        String cluster = params.CLUSTER_CONTEXT ?: config.get('targetClusterContext', 'docker-desktop')
                        boolean rollbackEnabled = params.ENABLE_AUTO_ROLLBACK != null ? params.ENABLE_AUTO_ROLLBACK : config.get('enableAutoRollback', true)

                        deployToK8s(
                            appName: env.APP_NAME,
                            imageTag: deployedImageTag,
                            targetNamespace: namespace,
                            targetClusterContext: cluster,
                            manifestsPath: config.get('manifestsPath', 'k8s/base'),
                            k8sCredentialsId: env.K8S_CREDS_ID,
                            enableAutoRollback: rollbackEnabled
                        )
                    }
                }
            }
        }

        post {
            always {
                cleanWs deleteDirs: true, notFailBuild: true
            }
            success {
                notifyBuildStatus('SUCCESS', appName: env.APP_NAME)
            }
            failure {
                notifyBuildStatus('FAILURE', appName: env.APP_NAME)
            }
            unstable {
                notifyBuildStatus('UNSTABLE', appName: env.APP_NAME)
            }
        }
    }
}
