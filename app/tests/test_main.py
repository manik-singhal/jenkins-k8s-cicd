import os
import sys
import pytest
from fastapi.testclient import TestClient

# Ensure app directory is on sys.path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
from main import app

client = TestClient(app)

def test_root_endpoint():
    response = client.get("/")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "online"
    assert "FinacPlus" in data["organization"]

def test_liveness_probe_healthz():
    response = client.get("/healthz")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "healthy"
    assert "timestamp" in data

def test_readiness_probe_readyz():
    response = client.get("/readyz")
    assert response.status_code == 200
    assert response.json()["status"] == "ready"

def test_prometheus_metrics_endpoint():
    response = client.get("/metrics")
    assert response.status_code == 200
    assert "http_requests_total" in response.text or "http_request_duration_seconds" in response.text

def test_loan_application_flow():
    payload = {
        "borrower_name": "Acme Real Estate LLC",
        "loan_amount": 1250000.0,
        "property_type": "Multi-Family Residential"
    }
    post_res = client.post("/api/v1/loans", json=payload)
    assert post_res.status_code == 201
    created_loan = post_res.json()
    assert created_loan["id"] is not None
    assert created_loan["status"] == "APPROVED_FOR_UNDERWRITING"

    get_res = client.get("/api/v1/loans")
    assert get_res.status_code == 200
    assert len(get_res.json()) >= 1
