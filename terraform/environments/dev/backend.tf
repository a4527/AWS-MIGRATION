# Remote state (IAC-01). The bucket name lives in the gitignored backend.hcl:
#   terraform init -backend-config=backend.hcl
# The bucket is created by terraform/bootstrap. use_lockfile keeps the lock as an
# S3 object next to the state, so no DynamoDB table is needed (Terraform >= 1.11).
terraform {
  backend "s3" {
    key          = "fileshare/dev/terraform.tfstate"
    region       = "us-east-1"
    encrypt      = true
    use_lockfile = true
  }
}
