from fastapi import FastAPI, HTTPException, status
from pydantic import BaseModel, Field
from typing import Optional, List
import os
import time
from prometheus_fastapi_instrumentator import Instrumentator

app = FastAPI(
    title="Toorak Lending Engine API",
    description="Mission-critical microservice for loan origination and risk evaluation - FinacPlus / Toorak Capital Partners",
    version="1.0.0"
)

# Initialize Prometheus metrics instrumentation
Instrumentator().instrument(app).expose(app, endpoint="/metrics")

# In-memory mock database for loan applications
LOAN_APPLICATIONS_DB = []

class LoanApplication(BaseModel):
    id: Optional[int] = None
    borrower_name: str = Field(..., example="Jane Doe")
    loan_amount: float = Field(..., gt=1000, example=350000.0)
    property_type: str = Field(..., example="Residential Multi-Family")
    status: Optional[str] = "SUBMITTED"

class HealthResponse(BaseModel):
    status: str
    version: str
    environment: str
    timestamp: float

@app.get("/healthz", response_model=HealthResponse, tags=["Health"])
def health_check():
    """Liveness probe to ensure the service container is alive."""
    return {
        "status": "healthy",
        "version": os.getenv("APP_VERSION", "1.0.0"),
        "environment": os.getenv("APP_ENV", "production"),
        "timestamp": time.time()
    }

@app.get("/readyz", tags=["Health"])
def readiness_check():
    """Readiness probe verifying that dependencies and configs are ready to accept traffic."""
    # Add any simulated DB connection check here
    return {"status": "ready", "ready": True}

@app.get("/", tags=["Root"])
def root():
    return {
        "service": "Toorak Lending Engine API",
        "organization": "FinacPlus / Toorak Capital Partners",
        "status": "online",
        "documentation": "/docs"
    }

@app.get("/api/v1/loans", response_model=List[LoanApplication], tags=["Loans"])
def list_loan_applications():
    return LOAN_APPLICATIONS_DB

@app.post("/api/v1/loans", response_model=LoanApplication, status_code=status.HTTP_201_CREATED, tags=["Loans"])
def submit_loan_application(loan: LoanApplication):
    loan.id = len(LOAN_APPLICATIONS_DB) + 1
    loan.status = "APPROVED_FOR_UNDERWRITING"
    LOAN_APPLICATIONS_DB.append(loan)
    return loan
