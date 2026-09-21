package com.finacplus.cicd

import java.io.Serializable

class PipelineConfig implements Serializable {
    String appName
    String appType = "python"
    String gitBranch = "main"
    String dockerRegistry
    String dockerCredentialsId = "docker-hub-credentials"
    String targetNamespace = "default"
    String targetClusterContext = "docker-desktop"
    String k8sCredentialsId = "k8s-kubeconfig-credentials"
    String manifestsPath = "k8s/base"
    String trivySeverity = "CRITICAL,HIGH"
    boolean enableSecurityScan = true
    boolean enableAutoRollback = true
    int rolloutTimeoutSeconds = 120

    PipelineConfig(Map params) {
        if (!params.appName) {
            throw new IllegalArgumentException("PipelineConfig: 'appName' is required!")
        }
        if (!params.dockerRegistry) {
            throw new IllegalArgumentException("PipelineConfig: 'dockerRegistry' is required!")
        }
        this.appName = params.appName
        if (params.appType) this.appType = params.appType
        if (params.gitBranch) this.gitBranch = params.gitBranch
        this.dockerRegistry = params.dockerRegistry
        if (params.dockerCredentialsId) this.dockerCredentialsId = params.dockerCredentialsId
        if (params.targetNamespace) this.targetNamespace = params.targetNamespace
        if (params.targetClusterContext) this.targetClusterContext = params.targetClusterContext
        if (params.k8sCredentialsId) this.k8sCredentialsId = params.k8sCredentialsId
        if (params.manifestsPath) this.manifestsPath = params.manifestsPath
        if (params.trivySeverity) this.trivySeverity = params.trivySeverity
        if (params.containsKey('enableSecurityScan')) this.enableSecurityScan = params.enableSecurityScan
        if (params.containsKey('enableAutoRollback')) this.enableAutoRollback = params.enableAutoRollback
        if (params.rolloutTimeoutSeconds) this.rolloutTimeoutSeconds = params.rolloutTimeoutSeconds
    }
}
