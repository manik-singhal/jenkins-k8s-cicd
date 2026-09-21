terraform {
  required_version = ">= 1.5.0"
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.25"
    }
  }
}

provider "google" {
  project = var.gcp_project_id
  region  = var.gcp_region
}

# 1. Google Kubernetes Engine (GKE) Cluster for FinacPlus Toorak Platform
resource "google_container_cluster" "primary" {
  name     = var.cluster_name
  location = var.gcp_zone

  # Remove default node pool to use custom managed node pool
  remove_default_node_pool = true
  initial_node_count       = 1

  network    = "default"
  subnetwork = "default"

  workload_identity_config {
    workload_pool = "${var.gcp_project_id}.svc.id.goog"
  }

  ip_allocation_policy {}

  release_channel {
    channel = "REGULAR"
  }
}

# 2. Hardened Custom Node Pool with Autoscaling
resource "google_container_node_pool" "primary_nodes" {
  name       = "${var.cluster_name}-node-pool"
  location   = var.gcp_zone
  cluster    = google_container_cluster.primary.name
  node_count = var.initial_node_count

  autoscaling {
    min_node_count = 1
    max_node_count = 5
  }

  node_config {
    preemptible  = false
    machine_type = "e2-medium"

    oauth_scopes = [
      "https://www.googleapis.com/auth/cloud-platform"
    ]

    labels = {
      environment = var.environment
      managed_by  = "terraform"
    }

    metadata = {
      disable-legacy-endpoints = "true"
    }
  }
}

# 3. Google Artifact Registry for Docker Images
resource "google_artifact_registry_repository" "finacplus_repo" {
  location      = var.gcp_region
  repository_id = "finacplus-docker-repo"
  description   = "Docker repository for Toorak Lending Engine services"
  format        = "DOCKER"
}

# 4. Service Account for Jenkins CI/CD Deployments (Least Privilege)
resource "google_service_account" "jenkins_deployer" {
  account_id   = "jenkins-k8s-deployer"
  display_name = "Jenkins CI/CD Kubernetes Deployer"
}

resource "google_project_iam_member" "artifact_writer" {
  project = var.gcp_project_id
  role    = "roles/artifactregistry.writer"
  member  = "serviceAccount:${google_service_account.jenkins_deployer.email}"
}

resource "google_project_iam_member" "container_developer" {
  project = var.gcp_project_id
  role    = "roles/container.developer"
  member  = "serviceAccount:${google_service_account.jenkins_deployer.email}"
}
