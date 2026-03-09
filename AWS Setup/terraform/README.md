Files in this directory are copies from XTDB AWS Sample at https://github.com/xtdb/xtdb/tree/main/aws

We want to distinguish:

- changes that are specific to this deployment: make them in new files or `<filename>_override.tf` files (see https://developer.hashicorp.com/terraform/language/files/override)
- changes that we'd want to feed back to the XTDB sample files: modify the original files directly

An exception is `providers.tf`, which can't be overriden, so changes particular to this deployment are done in the file directly.