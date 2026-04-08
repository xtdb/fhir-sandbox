module "xtdb_storage" {
  versioning = {
    enabled = true
  }

  lifecycle_rule = [
    {
      id      = "expire-old-versions"
      enabled = true

      noncurrent_version_expiration = {
        noncurrent_days = 1
      }
    }
  ]
}