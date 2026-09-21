variable "gcp_project_id" {
  description = "The GCP Project ID where resources will be provisioned"
  type        = string
  default     = "toorak-finacplus-platform"
}

variable "gcp_region" {
  description = "Default GCP Region"
  type        = string
  default     = "asia-south1"
}

variable "gcp_zone" {
  description = "Default GCP Zone"
  type        = string
  default     = "asia-south1-a"
}

variable "cluster_name" {
  description = "Name of the GKE cluster"
  type        = string
  default     = "toorak-lending-gke"
}

variable "environment" {
  description = "Environment identifier (dev, staging, prod)"
  type        = string
  default     = "production"
}

variable "initial_node_count" {
  description = "Initial number of nodes in GKE node pool"
  type        = number
  default     = 2
}
