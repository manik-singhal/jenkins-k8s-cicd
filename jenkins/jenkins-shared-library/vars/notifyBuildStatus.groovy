def call(String buildStatus, Map config = [:]) {
    String appName = config.get('appName', 'Application')
    String buildNumber = env.BUILD_NUMBER ?: "N/A"
    String buildUrl = env.BUILD_URL ?: "N/A"
    String gitCommit = sh(script: 'git rev-parse --short HEAD 2>/dev/null || echo "HEAD"', returnStdout: true).trim()

    echo "=========================================================="
    echo "📣 NOTIFICATION: Pipeline Execution Status [${buildStatus}]"
    echo "Application:  ${appName}"
    echo "Build Number: #${buildNumber}"
    echo "Git Commit:   ${gitCommit}"
    echo "Build URL:    ${buildUrl}"
    echo "=========================================================="

    if (buildStatus == 'SUCCESS') {
        echo "🎉 Pipeline finished successfully. All gates passed, image scanned, and deployment verified!"
    } else {
        echo "🚨 Pipeline failed! Detailed logs available at: ${buildUrl}console"
    }

    // Optional Slack / Webhook Integration
    // if (env.SLACK_WEBHOOK_URL) {
    //     sh "curl -X POST -H 'Content-type: application/json' --data '{\"text\":\"Pipeline ${appName} #${buildNumber}: ${buildStatus}\"}' ${env.SLACK_WEBHOOK_URL}"
    // }
}
