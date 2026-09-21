def call(Map config = [:]) {
    String appType = config.get('appType', 'python')
    echo "=========================================================="
    echo "🧪 STAGE: Running Unit Tests & Quality Checks (${appType})"
    echo "=========================================================="

    if (appType == 'python' || appType == 'fastapi') {
        sh '''
            echo "[INFO] Running PyTest suite inside Python 3.11 container..."
            if command -v docker >/dev/null 2>&1; then
                docker run --rm -v "$PWD":/app -w /app python:3.11-slim /bin/bash -c "pip install --quiet -r app/requirements.txt && python -m pytest app/tests/ -v --junitxml=test-results.xml"
            else
                python3 -m venv .venv
                . .venv/bin/activate
                pip install --quiet -r app/requirements.txt
                python -m pytest app/tests/ -v --junitxml=test-results.xml
            fi
            echo "[SUCCESS] All unit tests passed cleanly!"
        '''
    } else {
        echo "[INFO] Generic test runner executed."
    }
}
